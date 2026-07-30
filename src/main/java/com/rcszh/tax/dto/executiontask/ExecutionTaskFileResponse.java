package com.rcszh.tax.dto.executiontask;

import lombok.Data;

import java.time.LocalDateTime;

/** 执行任务材料文件。 */
@Data
public class ExecutionTaskFileResponse {
    /** 文件记录 ID。 */
    private String id;

    /** 用户上传时的原始文件名。 */
    private String name;

    /** 文件 MIME 类型。 */
    private String contentType;

    /** 文件扩展名，例如 xlsx。 */
    private String extension;

    /** 文件字节数。 */
    private Long size;

    /** 关联的内部解析任务项 ID，尚未提交时为空。 */
    private String parseTaskItemId;

    /** 文件下载地址。 */
    private String downloadUrl;

    /** 文件上传时间。 */
    private LocalDateTime createdAt;
}
