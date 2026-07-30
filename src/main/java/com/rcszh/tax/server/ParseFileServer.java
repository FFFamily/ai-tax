package com.rcszh.tax.server;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.rcszh.tax.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ParseFileServer {
    private final ApiTokenServer apiTokenServer;
    private final AppProperties appProperties;

    public ParseFileServer(ApiTokenServer apiTokenServer, AppProperties appProperties) {
        this.apiTokenServer = apiTokenServer;
        this.appProperties = appProperties;
    }

    public String sendParseRequest(String fileUrl) {
        String token = apiTokenServer.getMinerUToken();
        JSONObject data = new JSONObject();
        data.set("url", fileUrl);
        data.set("is_ocr", appProperties.getMineru().isOcr());
        data.set("enable_formula", appProperties.getMineru().isEnableFormula());
        data.set("model_version", appProperties.getMineru().getModelVersion());
        try (HttpResponse response = HttpRequest.post(appProperties.getMineru().getCreateTaskUrl())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .body(JSONUtil.toJsonStr(data))
                .execute()) {
            JSONObject responseJson = JSONUtil.parseObj(response.body());
            if (response.getStatus() != 200) {
                throw new RuntimeException("文件解析请求失败，失败原因：" + responseJson.getStr("msg"));
            }
            return responseJson.getByPath(".data.task_id").toString();
        } catch (Exception e) {
            throw new RuntimeException("文件解析请求失败: " + e.getMessage(), e);
        }
    }

    public JSONArray getParseResult(String remoteTaskId) {
        return downloadAndExtractJson(remoteTaskId);
    }

    private static void findJsonFiles(File dir, List<File> jsonFiles) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (Optional.ofNullable(FileNameUtil.extName(file)).orElse("other").equalsIgnoreCase("json")
                        && Optional.ofNullable(FileNameUtil.getName(file)).orElse("other").contains("content_list")) {
                    jsonFiles.add(file);
                }
            }
        }
    }

    public JSONArray downloadAndExtractJson(String taskId) {
        String token = apiTokenServer.getMinerUToken();
        String url = appProperties.getMineru().getTaskResultUrl() + taskId;
        Path tempPath = null;
        try (HttpResponse response = HttpRequest.get(url)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .execute()) {
            if (response.getStatus() != 200) {
                throw new RuntimeException("文件解析请求失败，失败原因：" + response.body());
            }
            JSONObject resultData = JSONUtil.parseObj(response.body());
            Integer code = resultData.getInt("code");
            if (code != 0) {
                throw new RuntimeException("文件解析请求失败，失败原因：" + resultData.getStr("msg"));
            }
            if (!resultData.containsKey("data")) {
                return null;
            }
            JSONObject data = resultData.getJSONObject("data");
            if (!data.containsKey("full_zip_url")) {
                return null;
            }
            String zipUrl = data.getStr("full_zip_url");
            try (HttpResponse zipResponse = HttpRequest.get(zipUrl).execute()) {
                if (zipResponse.getStatus() != 200) {
                    return null;
                }
                tempPath = Files.createTempDirectory("mineru_" + taskId + "_");
                File tempDir = tempPath.toFile();
                File extractDir = new File(tempDir, "extracted");
                FileUtil.mkdir(extractDir);
                File zipFile = new File(tempDir, "downloaded_file.zip");
                FileUtil.writeBytes(zipResponse.bodyBytes(), zipFile);
                ZipUtil.unzip(zipFile, extractDir);
                List<File> jsonFiles = new ArrayList<>();
                findJsonFiles(extractDir, jsonFiles);
                if (jsonFiles.isEmpty()) {
                    return null;
                }
                String jsonContent = FileUtil.readString(jsonFiles.getFirst(), StandardCharsets.UTF_8);
                return JSONUtil.parseArray(jsonContent);
            } catch (Exception e) {
                throw new RuntimeException("文件解析请求失败: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new RuntimeException("处理过程中发生错误: " + e.getMessage(), e);
        } finally {
            if (tempPath != null) {
                try {
                    FileUtil.del(tempPath.toFile());
                } catch (Exception ignored) {
                }
            }
        }
    }
}
