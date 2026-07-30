package com.rcszh.tax.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rcszh.tax.config.AppProperties;
import com.rcszh.tax.entity.ApiToken;
import com.rcszh.tax.mapper.ApiTokenMapper;
import org.springframework.stereotype.Component;

@Component
public class ApiTokenServer {
    public static final String PROVIDER_MINERU = "mineru";

    private final ApiTokenMapper apiTokenMapper;
    private final AppProperties appProperties;

    public ApiTokenServer(ApiTokenMapper apiTokenMapper, AppProperties appProperties) {
        this.apiTokenMapper = apiTokenMapper;
        this.appProperties = appProperties;
    }

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
