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

INSERT IGNORE INTO tax_execution_task_attempt (
    execution_task_id,
    parse_task_id,
    attempt_no,
    status,
    error_message,
    started_at,
    finished_at,
    created_at,
    updated_at
)
SELECT
    task.id,
    task.parse_task_id,
    1,
    task.status,
    task.error_message,
    COALESCE(task.submitted_at, task.created_at),
    CASE WHEN task.status IN ('COMPLETED', 'FAILED') THEN task.updated_at ELSE NULL END,
    COALESCE(task.submitted_at, task.created_at),
    task.updated_at
FROM tax_execution_task task
WHERE task.parse_task_id IS NOT NULL;
