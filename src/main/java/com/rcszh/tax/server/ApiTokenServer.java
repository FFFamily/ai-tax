package com.rcszh.tax.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rcszh.tax.config.AppProperties;
import com.rcszh.tax.entity.ApiToken;
import com.rcszh.tax.mapper.ApiTokenMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class ApiTokenServer {
    public static final String PROVIDER_MINERU = "mineru";

    @Resource
    private ApiTokenMapper apiTokenMapper;
    @Resource
    private AppProperties appProperties;

    public String getMinerUToken() {
        if (appProperties.getMineru().getToken() != null && !appProperties.getMineru().getToken().isBlank()) {
            return appProperties.getMineru().getToken();
        }
        ApiToken token = apiTokenMapper.selectOne(new LambdaQueryWrapper<ApiToken>()
                .eq(ApiToken::getProvider, PROVIDER_MINERU)
                .eq(ApiToken::getEnabled, true)
                .last("limit 1"));
        return token == null ? "" : token.getToken();
    }
}
