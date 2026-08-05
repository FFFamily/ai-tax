package com.rcszh.tax.service;

import com.rcszh.tax.entity.ChatLog;
import com.rcszh.tax.mapper.ChatLogMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class ChatLogService {
    @Resource
    private ChatLogMapper chatLogMapper;

    public void save(ChatLog chatLog) {
        chatLogMapper.insert(chatLog);
    }
}
