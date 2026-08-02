package com.rcszh.tax.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Storage storage = new Storage();
    private final Ai ai = new Ai();
    private final Mineru mineru = new Mineru();

    @Data
    public static class Storage {
        private String baseDir = "./data";
        private String publicBaseUrl = "";
        private String internalBaseUrl = "http://127.0.0.1:8080";
    }

    @Data
    public static class Ai {
        private String deepseekApiKey = "sk-9463061a14864d99854fc8f5698e539b";
        private String deepseekBaseUrl = "https://api.deepseek.com";
        private String deepseekModel = "deepseek-v4-flash";
    }

    @Data
    public static class Mineru {
        private String createBatchUrl = "https://mineru.net/api/v4/file-urls/batch";
        private String batchResultUrl = "https://mineru.net/api/v4/extract-results/batch/";
        private boolean ocr = false;
        private boolean enableFormula = false;
        private boolean enableTable = true;
        private String modelVersion = "vlm";
        private long pollIntervalMillis = 5000;
        private long maxWaitMillis = 600000;
        private int requestTimeoutSeconds = 60;
        private int uploadTimeoutSeconds = 600;
        private String token = "sk-gr3jQ0XI9PgGksxOaJWOY3YvFCdrcGibjXHPt1APSltRlk3m";
    }
}
