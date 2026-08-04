ALTER TABLE tax_task_item
    DROP COLUMN route_variant,
    DROP COLUMN route_confidence,
    DROP COLUMN route_reason,
    DROP COLUMN remote_task_id,
    DROP COLUMN parse_status,
    DROP COLUMN table_result;

ALTER TABLE tax_chat_log
    DROP COLUMN token;

ALTER TABLE tax_review_learning
    DROP COLUMN route_summary;
