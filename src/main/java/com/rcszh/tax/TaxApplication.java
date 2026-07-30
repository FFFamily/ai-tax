package com.rcszh.tax;

import com.rcszh.tax.config.AppProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@MapperScan("com.rcszh.tax.mapper")
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class TaxApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaxApplication.class, args);
    }
}
