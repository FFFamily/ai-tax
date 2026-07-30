package com.rcszh.tax.service;

import com.rcszh.tax.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class StorageService {
    private final AppProperties properties;

    public StorageService(AppProperties properties) {
        this.properties = properties;
    }

    public Path getBaseDir() {
        return Paths.get(properties.getStorage().getBaseDir()).toAbsolutePath().normalize();
    }

    public Path resolve(String relativePath) {
        Path baseDir = getBaseDir();
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return resolved;
    }

    public Path ensureParent(String relativePath) throws IOException {
        Path resolved = resolve(relativePath);
        Files.createDirectories(resolved.getParent());
        return resolved;
    }

    public String buildDownloadUrl(String fileName) {
        String baseUrl = properties.getStorage().getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return "/files/download?fileName=" + fileName;
        }
        return baseUrl.replaceAll("/$", "") + "/files/download?fileName=" + fileName;
    }
}
