package com.rcszh.tax.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rcszh.tax.entity.ChatLog;
import com.rcszh.tax.mapper.ChatLogMapper;
import org.springframework.stereotype.Service;

@Service
public class ChatLogService extends ServiceImpl<ChatLogMapper, ChatLog> {
}
