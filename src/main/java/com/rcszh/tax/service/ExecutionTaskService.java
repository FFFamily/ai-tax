package com.rcszh.tax.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rcszh.tax.common.BusinessException;
import com.rcszh.tax.dto.CreateDocumentTaskDto;
import com.rcszh.tax.dto.executiontask.ExecutionIncomeTypeOptionResponse;
import com.rcszh.tax.dto.executiontask.ExecutionMaterialOptionResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskDetailResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskFileResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskMaterialResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskOptionsResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskPageResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskResultResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskSummaryResponse;
import com.rcszh.tax.entity.task.TaxExecutionTask;
import com.rcszh.tax.entity.task.TaxExecutionTaskFile;
import com.rcszh.tax.enums.ExecutionTaskStatusEnum;
import com.rcszh.tax.enums.IncomeMaterialTypeEnum;
import com.rcszh.tax.enums.OverseasIncomeTypeEnum;
import com.rcszh.tax.mapper.TaxExecutionTaskFileMapper;
import com.rcszh.tax.mapper.TaxExecutionTaskMapper;
import com.rcszh.tax.server.DocumentTaskServer;
import com.rcszh.tax.util.ExcelUtil;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户执行任务领域服务，管理任务生命周期、材料文件和内部解析任务关联。
 */
@Service
public class ExecutionTaskService {
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "xls", "xlsx", "xlsm", "xlsb", "csv", "png", "jpg", "jpeg"
    );
    private static final Map<String, Set<String>> CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("xls", Set.of("application/vnd.ms-excel")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("xlsm", Set.of("application/vnd.ms-excel.sheet.macroenabled.12", "application/vnd.ms-excel")),
            Map.entry("xlsb", Set.of("application/vnd.ms-excel.sheet.binary.macroenabled.12", "application/vnd.ms-excel")),
            Map.entry("csv", Set.of("text/csv", "application/csv", "application/vnd.ms-excel", "text/plain")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg"))
    );

    @Resource
    private TaxExecutionTaskMapper taskMapper;
    @Resource
    private TaxExecutionTaskFileMapper fileMapper;
    @Resource
    private StorageService storageService;
    @Resource
    private DocumentTaskServer documentTaskServer;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    /**
     * 生成前端创建任务所需的所得类型、材料映射和文件上传限制。
     *
     * @return 创建任务选项
     */
    public ExecutionTaskOptionsResponse options() {
        List<ExecutionIncomeTypeOptionResponse> incomeTypes = new ArrayList<>();
        for (OverseasIncomeTypeEnum incomeType : OverseasIncomeTypeEnum.values()) {
            ExecutionIncomeTypeOptionResponse option = new ExecutionIncomeTypeOptionResponse();
            option.setCode(incomeType.name());
            option.setLabel(incomeType.getLabel());
            option.setMaterials(incomeType.getMaterials().stream().map(this::materialOption).toList());
            incomeTypes.add(option);
        }
        ExecutionTaskOptionsResponse result = new ExecutionTaskOptionsResponse();
        result.setIncomeTypes(incomeTypes);
        result.setAllowedExtensions(ALLOWED_EXTENSIONS.stream().sorted().toList());
        result.setMaxFileSize(MAX_FILE_SIZE);
        return result;
    }

    /**
     * 创建处于材料收集阶段的执行任务，所得类型创建后不可修改。
     *
     * @param incomeTypeCode 境外所得类型枚举 code
     * @return 新建任务详情
     */
    @Transactional(rollbackFor = Exception.class)
    public ExecutionTaskDetailResponse create(String incomeTypeCode) {
        OverseasIncomeTypeEnum incomeType = parseIncomeType(incomeTypeCode);
        LocalDateTime now = LocalDateTime.now();
        TaxExecutionTask task = new TaxExecutionTask();
        task.setIncomeType(incomeType.name());
        task.setStatus(ExecutionTaskStatusEnum.COLLECTING.name());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        return detail(task, List.of());
    }

    /**
     * 分页查询任务摘要，页码最小为 1，每页数量限制在 1 至 100。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 任务分页数据
     */
    public ExecutionTaskPageResponse list(int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long total = taskMapper.selectCount(null);
        long offset = (long) (normalizedPage - 1) * normalizedSize;
        List<TaxExecutionTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<TaxExecutionTask>()
                .orderByDesc(TaxExecutionTask::getCreatedAt)
                .orderByDesc(TaxExecutionTask::getId)
                .last("LIMIT " + normalizedSize + " OFFSET " + offset));
        List<ExecutionTaskSummaryResponse> items = tasks.stream().map(task -> {
            List<TaxExecutionTaskFile> files = listFiles(task.getId());
            return summary(task, files);
        }).toList();
        ExecutionTaskPageResponse result = new ExecutionTaskPageResponse();
        result.setItems(items);
        result.setTotal(total);
        result.setPage(normalizedPage);
        result.setSize(normalizedSize);
        return result;
    }

    /**
     * 查询任务详情并计算材料完整度和缺失材料。
     *
     * @param taskId 执行任务 ID
     * @return 任务详情
     */
    public ExecutionTaskDetailResponse get(Long taskId) {
        TaxExecutionTask task = requireTask(taskId);
        return detail(task, listFiles(taskId));
    }

    /**
     * 上传指定材料类型的一个或多个文件，并在数据库失败回滚时清理已落盘文件。
     *
     * @param taskId 执行任务 ID
     * @param materialTypeCode 材料类型枚举 code
     * @param uploads 待上传文件列表
     * @return 上传后的任务详情
     */
    @Transactional(rollbackFor = Exception.class)
    public ExecutionTaskDetailResponse upload(Long taskId,
                                              String materialTypeCode,
                                              List<MultipartFile> uploads) {
        TaxExecutionTask task = requireTaskForUpdate(taskId);
        requireCollecting(task);
        OverseasIncomeTypeEnum incomeType = parseIncomeType(task.getIncomeType());
        IncomeMaterialTypeEnum materialType = parseMaterialType(materialTypeCode);
        if (!incomeType.supports(materialType)) {
            throw BusinessException.badRequest("该材料不属于当前所得类型: " + materialType.getLabel());
        }
        if (uploads == null || uploads.isEmpty()) {
            throw BusinessException.badRequest("请选择需要上传的文件");
        }

        List<String> storedPaths = new ArrayList<>();
        registerRollbackCleanup(storedPaths);
        try {
            for (MultipartFile upload : uploads) {
                ValidatedFile validated = validateFile(upload);
                TaxExecutionTaskFile file = new TaxExecutionTaskFile();
                file.setExecutionTaskId(taskId);
                file.setMaterialType(materialType.name());
                file.setOriginalFileName(validated.originalName());
                file.setContentType(upload.getContentType());
                file.setExtension(validated.extension());
                file.setSizeBytes(upload.getSize());
                file.setCreatedAt(LocalDateTime.now());
                file.setUpdatedAt(file.getCreatedAt());
                file.setStoragePath("");
                fileMapper.insert(file);
                String storagePath = "execution-tasks/%s/%s/%s.%s".formatted(
                        taskId, materialType.name(), file.getId(), validated.extension());
                storageService.store(upload, storagePath);
                storedPaths.add(storagePath);
                file.setStoragePath(storagePath);
                fileMapper.updateById(file);
            }
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败: " + exception.getMessage());
        }
        return detail(task, listFiles(taskId));
    }

    /**
     * 删除材料文件记录，并在数据库事务提交后删除实际存储文件。
     *
     * @param taskId 执行任务 ID
     * @param fileId 文件记录 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long taskId, Long fileId) {
        TaxExecutionTask task = requireTaskForUpdate(taskId);
        requireCollecting(task);
        TaxExecutionTaskFile file = requireFile(taskId, fileId);
        fileMapper.deleteById(fileId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storageService.delete(file.getStoragePath());
                }
            });
        } else {
            storageService.delete(file.getStoragePath());
        }
    }

    /**
     * 获取可下载文件的路径和响应元数据。
     *
     * @param taskId 执行任务 ID
     * @param fileId 文件记录 ID
     * @return 文件下载信息
     */
    public FileDownload download(Long taskId, Long fileId) {
        TaxExecutionTaskFile file = requireFile(taskId, fileId);
        Path path = storageService.resolve(file.getStoragePath());
        if (!java.nio.file.Files.isRegularFile(path)) {
            throw BusinessException.notFound("文件不存在");
        }
        return new FileDownload(path, file.getOriginalFileName(), file.getContentType());
    }

    /**
     * 提交执行任务：锁定材料、创建内部解析任务并关联每个解析项。
     * 已关联内部任务时直接返回现有任务详情，避免重复创建。
     *
     * @param taskId 执行任务 ID
     * @return 提交后的任务详情
     */
    @Transactional(rollbackFor = Exception.class)
    public ExecutionTaskDetailResponse submit(Long taskId) {
        TaxExecutionTask task = requireTaskForUpdate(taskId);
        if (task.getParseTaskId() != null) {
            return detail(task, listFiles(taskId));
        }
        requireCollecting(task);
        List<TaxExecutionTaskFile> files = listFiles(taskId);
        if (files.isEmpty()) {
            throw BusinessException.badRequest("至少上传一份材料后才能开始处理");
        }
        boolean hasRemoteFile = files.stream().anyMatch(file -> !ExcelUtil.checkFileSuffix(file.getOriginalFileName()));
        if (hasRemoteFile && !storageService.hasPublicBaseUrl()) {
            throw BusinessException.badRequest("PDF 或图片解析需要配置可公网访问的 APP_PUBLIC_BASE_URL");
        }

        task.setStatus(ExecutionTaskStatusEnum.PROCESSING.name());
        task.setSubmittedAt(LocalDateTime.now());
        task.setErrorMessage(null);
        taskMapper.updateById(task);

        CreateDocumentTaskDto dto = new CreateDocumentTaskDto();
        CreateDocumentTaskDto.Item[] items = new CreateDocumentTaskDto.Item[files.size()];
        for (int index = 0; index < files.size(); index++) {
            TaxExecutionTaskFile file = files.get(index);
            IncomeMaterialTypeEnum materialType = parseMaterialType(file.getMaterialType());
            CreateDocumentTaskDto.Item item = new CreateDocumentTaskDto.Item();
            item.setDocumentType(materialType.getRequestedDocumentType());
            boolean publicUrl = !ExcelUtil.checkFileSuffix(file.getOriginalFileName());
            item.setFileUrl(storageService.buildExecutionFileUrl(taskId, file.getId(), publicUrl));
            items[index] = item;
        }
        dto.setItems(items);
        DocumentTaskServer.CreatedTask createdTask = documentTaskServer.createTaskWithItems(dto);
        task.setParseTaskId(createdTask.taskId());
        taskMapper.updateById(task);
        for (int index = 0; index < files.size(); index++) {
            TaxExecutionTaskFile file = files.get(index);
            file.setParseTaskItemId(createdTask.itemIds().get(index));
            fileMapper.updateById(file);
        }
        eventPublisher.publishEvent(new DocumentParseTaskCreatedEvent(createdTask.taskId(), taskId));
        return detail(task, files);
    }

    /**
     * 查询执行任务关联的内部解析结果。
     *
     * @param taskId 执行任务 ID
     * @return 内部解析任务及文件项结果
     */
    public ExecutionTaskResultResponse result(Long taskId) {
        TaxExecutionTask task = requireTask(taskId);
        if (task.getParseTaskId() == null) {
            throw BusinessException.conflict("任务尚未提交处理");
        }
        ExecutionTaskResultResponse result = documentTaskServer.getExecutionTaskResultById(task.getParseTaskId());
        if (result == null) {
            throw BusinessException.notFound("内部解析任务不存在");
        }
        return result;
    }

    /**
     * 组装任务详情，按预期材料顺序合并文件并计算缺失项。
     */
    private ExecutionTaskDetailResponse detail(TaxExecutionTask task, List<TaxExecutionTaskFile> files) {
        OverseasIncomeTypeEnum incomeType = parseIncomeType(task.getIncomeType());
        Map<String, List<TaxExecutionTaskFile>> grouped = files.stream().collect(Collectors.groupingBy(
                TaxExecutionTaskFile::getMaterialType, LinkedHashMap::new, Collectors.toList()));
        List<ExecutionTaskMaterialResponse> materials = new ArrayList<>();
        List<ExecutionMaterialOptionResponse> missing = new ArrayList<>();
        for (IncomeMaterialTypeEnum materialType : incomeType.getMaterials()) {
            List<TaxExecutionTaskFile> materialFiles = grouped.getOrDefault(materialType.name(), List.of());
            ExecutionTaskMaterialResponse material = new ExecutionTaskMaterialResponse();
            material.setCode(materialType.name());
            material.setLabel(materialType.getLabel());
            material.setRequired(true);
            material.setUploaded(!materialFiles.isEmpty());
            material.setFiles(materialFiles.stream().map(this::fileResponse).toList());
            materials.add(material);
            if (materialFiles.isEmpty()) {
                missing.add(materialOption(materialType));
            }
        }
        ExecutionTaskDetailResponse result = new ExecutionTaskDetailResponse();
        populateSummary(result, task, files);
        result.setMaterials(materials);
        result.setMissingMaterials(missing);
        result.setComplete(missing.isEmpty());
        result.setSubmittedAt(task.getSubmittedAt());
        result.setErrorMessage(task.getErrorMessage());
        return result;
    }

    /**
     * 组装任务列表摘要和材料完成数量。
     */
    private ExecutionTaskSummaryResponse summary(TaxExecutionTask task, List<TaxExecutionTaskFile> files) {
        ExecutionTaskSummaryResponse result = new ExecutionTaskSummaryResponse();
        populateSummary(result, task, files);
        return result;
    }

    /**
     * 将任务实体和文件统计填充到任务摘要 DTO。
     */
    private void populateSummary(ExecutionTaskSummaryResponse result,
                                 TaxExecutionTask task,
                                 List<TaxExecutionTaskFile> files) {
        OverseasIncomeTypeEnum incomeType = parseIncomeType(task.getIncomeType());
        Set<String> uploadedTypes = files.stream().map(TaxExecutionTaskFile::getMaterialType)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        result.setId(task.getId());
        result.setIncomeType(incomeType.name());
        result.setIncomeTypeLabel(incomeType.getLabel());
        result.setStatus(task.getStatus());
        result.setStatusLabel(statusLabel(task.getStatus()));
        result.setParseTaskId(task.getParseTaskId());
        result.setExpectedMaterialCount(incomeType.getMaterials().size());
        result.setUploadedMaterialCount(uploadedTypes.size());
        result.setMissingMaterialCount(incomeType.getMaterials().size() - uploadedTypes.size());
        result.setFileCount(files.size());
        result.setCreatedAt(task.getCreatedAt());
        result.setUpdatedAt(task.getUpdatedAt());
    }

    /**
     * 将材料枚举转换为前端选项结构。
     */
    private ExecutionMaterialOptionResponse materialOption(IncomeMaterialTypeEnum materialType) {
        ExecutionMaterialOptionResponse result = new ExecutionMaterialOptionResponse();
        result.setCode(materialType.name());
        result.setLabel(materialType.getLabel());
        return result;
    }

    /**
     * 将文件实体转换为前端展示和下载所需结构。
     */
    private ExecutionTaskFileResponse fileResponse(TaxExecutionTaskFile file) {
        ExecutionTaskFileResponse result = new ExecutionTaskFileResponse();
        result.setId(file.getId());
        result.setName(file.getOriginalFileName());
        result.setContentType(file.getContentType());
        result.setExtension(file.getExtension());
        result.setSize(file.getSizeBytes());
        result.setParseTaskItemId(file.getParseTaskItemId());
        result.setDownloadUrl("/execution-tasks/%s/files/%s".formatted(file.getExecutionTaskId(), file.getId()));
        result.setCreatedAt(file.getCreatedAt());
        return result;
    }

    /**
     * 按上传时间查询任务下的全部材料文件。
     */
    private List<TaxExecutionTaskFile> listFiles(Long taskId) {
        return fileMapper.selectList(new LambdaQueryWrapper<TaxExecutionTaskFile>()
                .eq(TaxExecutionTaskFile::getExecutionTaskId, taskId)
                .orderByAsc(TaxExecutionTaskFile::getCreatedAt)
                .orderByAsc(TaxExecutionTaskFile::getId));
    }

    /**
     * 查询任务，不存在时返回业务层 404 异常。
     */
    private TaxExecutionTask requireTask(Long taskId) {
        TaxExecutionTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BusinessException.notFound("任务不存在");
        }
        return task;
    }

    /**
     * 使用行锁查询任务，串行化上传、删除和提交等状态变更。
     */
    private TaxExecutionTask requireTaskForUpdate(Long taskId) {
        TaxExecutionTask task = taskMapper.selectOne(new LambdaQueryWrapper<TaxExecutionTask>()
                .eq(TaxExecutionTask::getId, taskId)
                .last("FOR UPDATE"));
        if (task == null) {
            throw BusinessException.notFound("任务不存在");
        }
        return task;
    }

    /**
     * 校验文件属于指定任务并返回文件记录。
     */
    private TaxExecutionTaskFile requireFile(Long taskId, Long fileId) {
        TaxExecutionTaskFile file = fileMapper.selectOne(new LambdaQueryWrapper<TaxExecutionTaskFile>()
                .eq(TaxExecutionTaskFile::getId, fileId)
                .eq(TaxExecutionTaskFile::getExecutionTaskId, taskId));
        if (file == null) {
            throw BusinessException.notFound("材料文件不存在");
        }
        return file;
    }

    /**
     * 校验任务仍处于材料收集阶段，提交后禁止增删材料。
     */
    private void requireCollecting(TaxExecutionTask task) {
        if (!ExecutionTaskStatusEnum.COLLECTING.name().equals(task.getStatus())) {
            throw BusinessException.conflict("任务已经提交，不能再修改材料");
        }
    }

    /**
     * 校验文件非空、大小、文件名、扩展名和 MIME 类型。
     */
    private ValidatedFile validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("不能上传空文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "单个文件不能超过 50MB");
        }
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (!StringUtils.hasText(originalName) || originalName.contains("..")) {
            throw BusinessException.badRequest("文件名不合法");
        }
        String extension = StringUtils.getFilenameExtension(originalName);
        extension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw BusinessException.badRequest("不支持的文件格式: " + extension);
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)
                && !"application/octet-stream".equalsIgnoreCase(contentType)
                && CONTENT_TYPES.getOrDefault(extension, Set.of()).stream().noneMatch(type -> type.equalsIgnoreCase(contentType))) {
            throw BusinessException.badRequest("文件扩展名与内容类型不匹配: " + originalName);
        }
        return new ValidatedFile(originalName, extension);
    }

    /**
     * 注册事务回滚回调，避免数据库失败后遗留已保存文件。
     */
    private void registerRollbackCleanup(List<String> paths) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    paths.forEach(storageService::delete);
                }
            }
        });
    }

    /**
     * 将所得类型 code 转为枚举，并统一转换为参数错误。
     */
    private OverseasIncomeTypeEnum parseIncomeType(String code) {
        try {
            return OverseasIncomeTypeEnum.fromCode(code);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest(exception.getMessage());
        }
    }

    /**
     * 将材料类型 code 转为枚举，并统一转换为参数错误。
     */
    private IncomeMaterialTypeEnum parseMaterialType(String code) {
        try {
            return IncomeMaterialTypeEnum.fromCode(code);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest(exception.getMessage());
        }
    }

    /**
     * 将任务状态 code 转换为中文名称，未知状态保留原值。
     */
    private String statusLabel(String status) {
        try {
            return ExecutionTaskStatusEnum.valueOf(status).getLabel();
        } catch (Exception ignored) {
            return status;
        }
    }

    public record FileDownload(Path path, String originalName, String contentType) {
    }

    private record ValidatedFile(String originalName, String extension) {
    }
}
