package com.rcszh.tax.service;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 内部解析任务创建事件监听器，确保数据库事务提交后再启动异步处理。
 */
@Component
public class DocumentParseTaskEventListener {
    @Resource
    private DocumentTaskAsyncRunner asyncRunner;

    /**
     * 在创建解析任务的事务提交后触发异步执行，避免异步线程读取到未提交数据。
     *
     * @param event 解析任务创建事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DocumentParseTaskCreatedEvent event) {
        asyncRunner.start(event.parseTaskId(), event.executionTaskId());
    }
}
