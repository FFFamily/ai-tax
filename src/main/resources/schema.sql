CREATE TABLE IF NOT EXISTS tax_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(128),
    variant VARCHAR(128),
    filter_type VARCHAR(64),
    page_type VARCHAR(32),
    page_step INT,
    match_rule LONGTEXT,
    prompt LONGTEXT,
    global_prompt LONGTEXT,
    error_record LONGTEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_document_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    title_filter VARCHAR(255),
    table_head_check_rule VARCHAR(1000),
    sort_num INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_document_field_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    field_label VARCHAR(255),
    field_code VARCHAR(128),
    field_desc VARCHAR(1000),
    sort_num INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_task_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    document_id BIGINT,
    requested_document_type VARCHAR(128),
    resolved_document_id BIGINT,
    route_variant VARCHAR(128),
    route_confidence DECIMAL(5,4),
    route_reason LONGTEXT,
    need_human_review TINYINT(1) DEFAULT 0,
    remote_task_id VARCHAR(128),
    task_result LONGTEXT,
    file_url VARCHAR(1000) NOT NULL,
    parse_status VARCHAR(32),
    change_result LONGTEXT,
    table_result LONGTEXT,
    file_rule LONGTEXT,
    review_reasons LONGTEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_execution_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    income_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    parse_task_id BIGINT,
    submitted_at DATETIME,
    error_message VARCHAR(1000),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_execution_task_parse_task (parse_task_id),
    KEY idx_execution_task_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS tax_execution_task_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_task_id BIGINT NOT NULL,
    material_type VARCHAR(64) NOT NULL,
    original_file_name VARCHAR(500) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    content_type VARCHAR(255),
    extension VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL,
    parse_task_item_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_execution_file_task (execution_task_id),
    KEY idx_execution_file_material (execution_task_id, material_type),
    KEY idx_execution_file_parse_item (parse_task_item_id)
);

CREATE TABLE IF NOT EXISTS tax_api_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    token VARCHAR(2000) NOT NULL,
    enabled TINYINT(1) DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_chat_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prompt LONGTEXT,
    result LONGTEXT,
    token INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_review_learning (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    task_item_id BIGINT NOT NULL,
    requested_document_type VARCHAR(128),
    resolved_document_id BIGINT,
    route_summary LONGTEXT,
    review_reasons LONGTEXT,
    reviewed_records LONGTEXT,
    reviewer VARCHAR(128),
    comment LONGTEXT,
    suggested_match_rule LONGTEXT,
    few_shot_example LONGTEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_word_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT,
    label VARCHAR(255),
    value VARCHAR(255)
);
