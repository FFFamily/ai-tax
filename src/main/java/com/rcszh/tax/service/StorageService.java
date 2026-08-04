package com.rcszh.tax.service;

import com.rcszh.tax.config.AppProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class StorageService {
    @Resource
    private AppProperties properties;

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

    public Path store(MultipartFile file, String relativePath) throws IOException {
        Path target = ensureParent(relativePath);
        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ignored) {
            // Metadata is authoritative; stale files can be cleaned by maintenance jobs.
        }
    }

    public String buildExecutionFileUrl(Long taskId, Long fileId, String originalFileName, boolean publicUrl) {
        String baseUrl = publicUrl
                ? properties.getStorage().getPublicBaseUrl()
                : properties.getStorage().getInternalBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException(publicUrl ? "未配置 APP_PUBLIC_BASE_URL" : "未配置 APP_INTERNAL_BASE_URL");
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl.replaceAll("/$", ""))
                .pathSegment("execution-tasks", taskId.toString(), "files", fileId.toString());
        if (StringUtils.hasText(originalFileName)) {
            builder.queryParam("fileName", originalFileName);
        }
        return builder.build()
                .encode()
                .toUriString();
    }

    public String buildDownloadUrl(String fileName) {
        String baseUrl = properties.getStorage().getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return UriComponentsBuilder.fromPath("/files/download")
                    .queryParam("fileName", fileName)
                    .build()
                    .encode()
                    .toUriString();
        }
        return UriComponentsBuilder.fromUriString(baseUrl.replaceAll("/$", ""))
                .path("/files/download")
                .queryParam("fileName", fileName)
                .build()
                .encode()
                .toUriString();
    }
}
