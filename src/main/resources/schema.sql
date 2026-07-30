CREATE TABLE IF NOT EXISTS tax_document (
    id VARCHAR(64) PRIMARY KEY,
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
    id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    title_filter VARCHAR(255),
    table_head_check_rule VARCHAR(1000),
    sort_num INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_document_field_mapping (
    id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    field_label VARCHAR(255),
    field_code VARCHAR(128),
    field_desc VARCHAR(1000),
    sort_num INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_task (
    id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_task_item (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64),
    requested_document_type VARCHAR(128),
    resolved_document_id VARCHAR(64),
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

CREATE TABLE IF NOT EXISTS tax_api_token (
    id VARCHAR(64) PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    token VARCHAR(2000) NOT NULL,
    enabled TINYINT(1) DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_chat_log (
    id VARCHAR(64) PRIMARY KEY,
    prompt LONGTEXT,
    result LONGTEXT,
    token INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_review_learning (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64),
    task_item_id VARCHAR(64) NOT NULL,
    requested_document_type VARCHAR(128),
    resolved_document_id VARCHAR(64),
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
    id BIGINT PRIMARY KEY,
    parent_id BIGINT,
    label VARCHAR(255),
    value VARCHAR(255)
);
