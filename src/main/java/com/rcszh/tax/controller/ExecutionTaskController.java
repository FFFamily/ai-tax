package com.rcszh.tax.controller;

import com.rcszh.tax.common.ApiResponse;
import com.rcszh.tax.dto.CreateExecutionTaskRequest;
import com.rcszh.tax.service.ExecutionTaskService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.util.Map;

/**
 * 用户执行任务接口，负责任务创建、材料收集、提交处理和结果查询。
 */
@RestController
@RequestMapping("/execution-tasks")
public class ExecutionTaskController {
    private final ExecutionTaskService executionTaskService;

    public ExecutionTaskController(ExecutionTaskService executionTaskService) {
        this.executionTaskService = executionTaskService;
    }

    /**
     * 查询可选所得类型、对应材料清单以及文件上传限制。
     *
     * @return 任务创建选项和上传配置
     */
    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.success(executionTaskService.options());
    }

    /**
     * 根据选定的境外所得类型创建一条材料收集中的执行任务。
     *
     * @param request 创建任务请求
     * @return 新建任务详情及预期材料清单
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateExecutionTaskRequest request) {
        return ApiResponse.success(executionTaskService.create(request.getIncomeType()));
    }

    /**
     * 分页查询执行任务，按创建时间倒序返回。
     *
     * @param page 页码，从 1 开始
     * @param size 每页数量，服务层限制为 1 至 100
     * @return 执行任务分页数据
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(executionTaskService.list(page, size));
    }

    /**
     * 查询执行任务详情，包括材料分组、文件、完整度和缺失材料。
     *
     * @param taskId 执行任务 ID
     * @return 执行任务详情
     */
    @GetMapping("/{taskId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String taskId) {
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
    @PostMapping(path = "/{taskId}/materials/{materialType}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@PathVariable String taskId,
                                                   @PathVariable String materialType,
                                                   @RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.success(executionTaskService.upload(taskId, materialType, files));
    }

    /**
     * 删除任务下的指定材料文件，仅允许在材料收集阶段调用。
     *
     * @param taskId 执行任务 ID
     * @param fileId 文件记录 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{taskId}/files/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable String taskId, @PathVariable String fileId) {
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
    @GetMapping("/{taskId}/files/{fileId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String taskId,
                                                        @PathVariable String fileId) throws IOException {
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
    @PostMapping("/{taskId}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable String taskId) {
        return ApiResponse.success(executionTaskService.submit(taskId));
    }

    /**
     * 通过用户执行任务查询其关联的内部解析结果。
     *
     * @param taskId 执行任务 ID
     * @return 内部解析任务及文件项结果
     */
    @GetMapping("/{taskId}/result")
    public ApiResponse<Map<String, Object>> result(@PathVariable String taskId) {
        return ApiResponse.success(executionTaskService.result(taskId));
    }
}
