CREATE TABLE IF NOT EXISTS tax_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_task_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    workflow_code VARCHAR(128) NOT NULL,
    need_human_review TINYINT(1) DEFAULT 0,
    task_result LONGTEXT,
    file_url VARCHAR(1000) NOT NULL,
    change_result LONGTEXT,
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

CREATE TABLE IF NOT EXISTS tax_execution_task_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_task_id BIGINT NOT NULL,
    parse_task_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message VARCHAR(1000),
    started_at DATETIME NOT NULL,
    finished_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_execution_attempt_no (execution_task_id, attempt_no),
    UNIQUE KEY uk_execution_attempt_parse_task (parse_task_id),
    KEY idx_execution_attempt_task (execution_task_id)
);

CREATE TABLE IF NOT EXISTS tax_chat_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prompt LONGTEXT,
    result LONGTEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_review_learning (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    task_item_id BIGINT NOT NULL,
    workflow_code VARCHAR(128),
    review_reasons LONGTEXT,
    reviewed_records LONGTEXT,
    reviewer VARCHAR(128),
    comment LONGTEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tax_word_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT,
    label VARCHAR(255),
    value VARCHAR(255)
);
