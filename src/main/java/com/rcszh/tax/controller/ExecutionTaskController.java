package com.rcszh.tax.controller;

import com.rcszh.tax.common.ApiResponse;
import com.rcszh.tax.dto.CreateExecutionTaskRequest;
import com.rcszh.tax.dto.executiontask.ExecutionTaskDetailResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskOptionsResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskPageResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskResultResponse;
import com.rcszh.tax.service.ExecutionTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 用户执行任务接口，负责任务创建、材料收集、提交处理和结果查询。
 */
@Tag(name = "用户执行任务", description = "境外所得任务创建、材料上传、提交处理和结果查询")
@Validated
@RestController
@RequestMapping("/execution-tasks")
public class ExecutionTaskController {
    @Resource
    private ExecutionTaskService executionTaskService;

    /**
     * 查询可选所得类型、对应材料清单以及文件上传限制。
     *
     * @return 任务创建选项和上传配置
     */
    @Operation(summary = "查询任务创建选项", description = "返回所得类型、对应材料以及文件上传限制")
    @GetMapping("/options")
    public ApiResponse<ExecutionTaskOptionsResponse> options() {
        return ApiResponse.success(executionTaskService.options());
    }

    /**
     * 根据选定的境外所得类型创建一条材料收集中的执行任务。
     *
     * @param request 创建任务请求
     * @return 新建任务详情及预期材料清单
     */
    @Operation(summary = "创建用户执行任务", description = "一个任务只能选择一种所得类型，创建后不可修改")
    @PostMapping
    public ApiResponse<ExecutionTaskDetailResponse> create(
            @Parameter(description = "创建任务请求，incomeType 为境外所得类型编码", required = true)
            @Valid @RequestBody CreateExecutionTaskRequest request) {
        return ApiResponse.success(executionTaskService.create(request.getIncomeType()));
    }

    /**
     * 分页查询执行任务，按创建时间倒序返回。
     *
     * @param page 页码，从 1 开始
     * @param size 每页数量，服务层限制为 1 至 100
     * @return 执行任务分页数据
     */
    @Operation(summary = "分页查询用户执行任务", description = "按创建时间和任务 ID 倒序返回")
    @GetMapping
    public ApiResponse<ExecutionTaskPageResponse> list(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(name = "page", defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") int page,
            @Parameter(description = "每页数量，范围为 1 至 100", example = "20")
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = "每页数量不能小于 1")
            @Max(value = 100, message = "每页数量不能超过 100") int size) {
        return ApiResponse.success(executionTaskService.list(page, size));
    }

    /**
     * 查询执行任务详情，包括材料分组、文件、完整度和缺失材料。
     *
     * @param taskId 执行任务 ID
     * @return 执行任务详情
     */
    @Operation(summary = "查询用户执行任务详情", description = "返回材料清单、文件、完整度和缺失材料")
    @GetMapping("/{taskId}")
    public ApiResponse<ExecutionTaskDetailResponse> get(
            @Parameter(description = "用户执行任务 ID", required = true)
            @PathVariable("taskId") @Positive(message = "任务 ID 必须为正数") Long taskId) {
        return ApiResponse.success(executionTaskService.get(taskId));
    }

    /**
     * 为指定材料类型上传一个或多个文件，仅允许在材料收集阶段调用。
     *
     * @param taskId 执行任务 ID
     * @param materialType 材料类型枚举 code
     * @param files 待上传文件列表
     * @return 上传后的任务详情
     */
    @Operation(summary = "上传任务材料", description = "同一种材料允许上传多个文件，仅材料收集阶段可用")
    @PostMapping(path = "/{taskId}/materials/{materialType}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ExecutionTaskDetailResponse> upload(
            @Parameter(description = "用户执行任务 ID", required = true)
            @PathVariable("taskId") @Positive(message = "任务 ID 必须为正数") Long taskId,
            @Parameter(description = "材料类型编码，必须属于该任务的所得类型", required = true,
                    example = "SALARY_PAYMENT_DETAIL")
            @PathVariable("materialType") @NotBlank(message = "材料类型不能为空") String materialType,
            @Parameter(description = "材料文件列表，支持一次上传多个文件", required = true)
            @RequestParam("files") @Size(min = 1, message = "请选择需要上传的文件") List<MultipartFile> files) {
        return ApiResponse.success(executionTaskService.upload(taskId, materialType, files));
    }

    /**
     * 删除任务下的指定材料文件，仅允许在材料收集阶段调用。
     *
     * @param taskId 执行任务 ID
     * @param fileId 文件记录 ID
     * @return 空成功响应
     */
    @Operation(summary = "删除任务材料文件", description = "仅材料收集阶段允许删除")
    @DeleteMapping("/{taskId}/files/{fileId}")
    public ApiResponse<Void> deleteFile(
            @Parameter(description = "用户执行任务 ID", required = true)
            @PathVariable("taskId") @Positive(message = "任务 ID 必须为正数") Long taskId,
            @Parameter(description = "待删除的材料文件记录 ID", required = true)
            @PathVariable("fileId") @Positive(message = "文件 ID 必须为正数") Long fileId) {
        executionTaskService.deleteFile(taskId, fileId);
        return ApiResponse.success();
    }

    /**
     * 下载任务下的指定材料文件，响应文件名使用 UTF-8 编码。
     *
     * @param taskId 执行任务 ID
     * @param fileId 文件记录 ID
     * @return 文件流响应
     * @throws IOException 文件读取失败
     */
    @Operation(summary = "下载任务材料文件")
    @GetMapping("/{taskId}/files/{fileId}")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable @Parameter(description = "用户执行任务 ID", required = true)
            @Positive(message = "任务 ID 必须为正数") Long taskId,
            @PathVariable @Parameter(description = "待下载的材料文件记录 ID", required = true)
            @Positive(message = "文件 ID 必须为正数") Long fileId) throws IOException {
        ExecutionTaskService.FileDownload file = executionTaskService.download(taskId, fileId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(file.contentType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(Files.size(file.path()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.originalName(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(new InputStreamResource(Files.newInputStream(file.path())));
    }

    /**
     * 锁定已上传材料并创建内部解析任务，重复提交返回已创建的关联任务。
     *
     * @param taskId 执行任务 ID
     * @return 提交后的任务详情
     */
    @Operation(summary = "提交用户执行任务", description = "锁定材料并创建内部解析任务，重复提交不会重复创建")
    @PostMapping("/{taskId}/submit")
    public ApiResponse<ExecutionTaskDetailResponse> submit(
            @PathVariable @Parameter(description = "待提交的用户执行任务 ID", required = true)
            @Positive(message = "任务 ID 必须为正数") Long taskId) {
        return ApiResponse.success(executionTaskService.submit(taskId));
    }

    /**
     * 通过用户执行任务查询其关联的内部解析结果。
     *
     * @param taskId 执行任务 ID
     * @return 内部解析任务及文件项结果
     */
    @Operation(summary = "查询用户执行任务结果", description = "通过用户执行任务访问关联的内部解析结果")
    @GetMapping("/{taskId}/result")
    public ApiResponse<ExecutionTaskResultResponse> result(
            @PathVariable @Parameter(description = "用户执行任务 ID", required = true)
            @Positive(message = "任务 ID 必须为正数") Long taskId) {
        return ApiResponse.success(executionTaskService.result(taskId));
    }
}
