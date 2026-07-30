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
    }

    @Data
    public static class Ai {
        private String deepseekApiKey = "";
        private String deepseekBaseUrl = "https://api.deepseek.com";
        private String deepseekModel = "deepseek-chat";
    }

    @Data
    public static class Mineru {
        private String createTaskUrl = "https://mineru.net/api/v4/extract/task";
        private String taskResultUrl = "https://mineru.net/api/v4/extract/task/";
        private boolean ocr = false;
        private boolean enableFormula = false;
        private String modelVersion = "vlm";
        private String token = "";
    }
}
