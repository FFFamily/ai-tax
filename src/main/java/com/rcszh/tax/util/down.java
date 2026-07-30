package com.rcszh.tax.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class down {
    public static void main(String[] args) {
        String zipUrl = "https://cdn-mineru.openxlab.org.cn/pdf/2025-10-28/8d944840-9fb5-4eac-9bc4-4f9110783832.zip";
        try (HttpResponse zipResponse = HttpRequest.get(zipUrl).execute()) {
            Path path = Paths.get("/Users/tutu/Downloads/project/rong_cheng/pms-admin-taosheng/pms-admin/mineru");
            Path tempPath = Files.createDirectory(path);
            File tempDir = tempPath.toFile();
            File extractDir = new File(tempDir, "extracted");
            FileUtil.mkdir(extractDir);
            // 保存zip文件到临时目录
            File zipFile = new File(tempDir, "downloaded_file.zip");
            FileUtil.writeBytes(zipResponse.bodyBytes(), zipFile);
            // 解压zip文件
            ZipUtil.unzip(zipFile, extractDir);

            // 查找JSON文件
            List<File> jsonFiles = new ArrayList<>();
            findJsonFiles(extractDir, jsonFiles);
            if (jsonFiles.isEmpty()) {
                return;
            }
            // 读取并打印JSON文件内容
            try {
                String jsonContent = FileUtil.readString(jsonFiles.getFirst(), StandardCharsets.UTF_8);
                System.out.println(jsonContent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static void findJsonFiles(File dir, List<File> jsonFiles) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (FileNameUtil.extName(file).equalsIgnoreCase("json") && FileNameUtil.getName(file).contains("model")) {
                    jsonFiles.add(file);
                }
            }
        }
    }
}
