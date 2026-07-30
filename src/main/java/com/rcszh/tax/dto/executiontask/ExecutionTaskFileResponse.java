package com.rcszh.tax.dto.executiontask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "执行任务材料文件")
public class ExecutionTaskFileResponse {
    @Schema(description = "文件记录 ID")
    private String id;

    @Schema(description = "用户上传时的原始文件名", example = "2025年工资明细.xlsx")
    private String name;

    @Schema(description = "文件 MIME 类型", example = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    private String contentType;

    @Schema(description = "文件扩展名", example = "xlsx")
    private String extension;

    @Schema(description = "文件字节数", example = "102400")
    private Long size;

    @Schema(description = "关联的内部解析任务项 ID，尚未提交时为空")
    private String parseTaskItemId;

    @Schema(description = "文件下载地址")
    private String downloadUrl;

    @Schema(description = "文件上传时间")
    private LocalDateTime createdAt;
}
