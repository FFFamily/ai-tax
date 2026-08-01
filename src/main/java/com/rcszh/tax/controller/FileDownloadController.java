package com.rcszh.tax.controller;

import com.rcszh.tax.service.StorageService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/files")
public class FileDownloadController {
    private static final Logger logger = LoggerFactory.getLogger(FileDownloadController.class);

    @Resource
    private StorageService storageService;

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> fileDownload(@RequestParam("fileName") String fileName,
                                                            @RequestParam(value = "delete", defaultValue = "false") Boolean delete,
                                                            HttpServletResponse response) throws IOException {
        Path path = storageService.resolve(fileName);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(path));
        ResponseEntity<InputStreamResource> entity = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .body(resource);
        if (Boolean.TRUE.equals(delete)) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                logger.warn("删除文件失败: {}", path, e);
            }
        }
        return entity;
    }
}
