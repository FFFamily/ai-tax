package com.rcszh.tax.server;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.rcszh.tax.config.AppProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ParseFileServer {
    private static final int MAX_BATCH_SIZE = 50;
    private static final Set<String> PENDING_STATES = Set.of(
            "waiting-file", "pending", "running", "converting"
    );

    @Resource
    private AppProperties appProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 申请 MinerU 批量上传地址、流式上传本地文件并等待全部解析完成。
     * 批次信息只存在当前调用栈中，任意文件失败都会终止整个本地任务。
     */
    public Map<Long, JSONArray> parseLocalFiles(List<LocalParseFile> files) {
        if (files == null || files.isEmpty()) {
            return Map.of();
        }
        validateFiles(files);

        Map<Long, JSONArray> results = new LinkedHashMap<>();
        for (int from = 0; from < files.size(); from += MAX_BATCH_SIZE) {
            int to = Math.min(from + MAX_BATCH_SIZE, files.size());
            List<LocalParseFile> batchFiles = List.copyOf(files.subList(from, to));
            results.putAll(parseBatch(batchFiles));
        }
        return Map.copyOf(results);
    }

    private Map<Long, JSONArray> parseBatch(List<LocalParseFile> files) {
        UploadBatch batch = createUploadBatch(files);
        if (batch.uploadUrls().size() != files.size()) {
            throw new IllegalStateException("MinerU 返回的上传地址数量与文件数量不一致");
        }
        for (int index = 0; index < files.size(); index++) {
            uploadFile(batch.uploadUrls().get(index), files.get(index));
        }

        Map<String, CompletedResult> completed = waitForBatch(batch.batchId(), files);
        Map<Long, JSONArray> results = new LinkedHashMap<>();
        for (LocalParseFile file : files) {
            CompletedResult result = completed.get(file.dataId());
            if (result == null) {
                throw new IllegalStateException("MinerU 批量结果缺少文件: " + file.originalFileName());
            }
            results.put(file.taskItemId(), downloadContentList(result.fullZipUrl(), file.originalFileName()));
        }
        return results;
    }

    private UploadBatch createUploadBatch(List<LocalParseFile> files) {
        JSONArray requestFiles = new JSONArray();
        for (LocalParseFile file : files) {
            JSONObject requestFile = new JSONObject();
            requestFile.set("name", file.originalFileName());
            requestFile.set("data_id", file.dataId());
            requestFile.set("is_ocr", appProperties.getMineru().isOcr());
            requestFiles.add(requestFile);
        }

        JSONObject requestBody = new JSONObject();
        requestBody.set("files", requestFiles);
        requestBody.set("enable_formula", appProperties.getMineru().isEnableFormula());
        requestBody.set("enable_table", appProperties.getMineru().isEnableTable());
        requestBody.set("model_version", appProperties.getMineru().getModelVersion());

        HttpRequest request = HttpRequest.newBuilder(URI.create(appProperties.getMineru().getCreateBatchUrl()))
                .timeout(requestTimeout())
                .header("Authorization", authorization())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                .build();
        JSONObject responseJson = sendJson(request, "申请 MinerU 批量上传地址");
        JSONObject data = requireData(responseJson, "申请 MinerU 批量上传地址");
        String batchId = data.getStr("batch_id");
        JSONArray fileUrls = data.getJSONArray("file_urls");
        if (StrUtil.isBlank(batchId) || fileUrls == null) {
            throw new IllegalStateException("MinerU 批量上传响应缺少 batch_id 或 file_urls");
        }
        return new UploadBatch(batchId, fileUrls.toList(String.class));
    }

    private void uploadFile(String uploadUrl, LocalParseFile file) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl))
                    .timeout(Duration.ofSeconds(Math.max(1, appProperties.getMineru().getUploadTimeoutSeconds())))
                    .PUT(HttpRequest.BodyPublishers.ofFile(file.localPath()))
                    .build();
            HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
                    "上传文件到 MinerU: " + file.originalFileName());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("上传文件到 MinerU 失败: " + file.originalFileName()
                        + "，HTTP " + response.statusCode());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取本地文件失败: " + file.originalFileName(), exception);
        }
    }

    private Map<String, CompletedResult> waitForBatch(String batchId, List<LocalParseFile> files) {
        Map<String, LocalParseFile> expected = new LinkedHashMap<>();
        for (LocalParseFile file : files) {
            expected.put(file.dataId(), file);
        }
        Map<String, CompletedResult> completed = new LinkedHashMap<>();
        long deadline = System.nanoTime()
                + Duration.ofMillis(Math.max(1, appProperties.getMineru().getMaxWaitMillis())).toNanos();

        while (System.nanoTime() < deadline) {
            JSONObject responseJson = queryBatch(batchId);
            JSONObject data = requireData(responseJson, "查询 MinerU 批量结果");
            JSONArray extractResults = data.getJSONArray("extract_result");
            if (extractResults != null) {
                for (Object value : extractResults) {
                    JSONObject result = JSONUtil.parseObj(value);
                    String dataId = result.getStr("data_id");
                    if (StrUtil.isBlank(dataId) || !expected.containsKey(dataId)) {
                        continue;
                    }
                    String state = StrUtil.blankToDefault(result.getStr("state"), "unknown")
                            .toLowerCase(Locale.ROOT);
                    if ("failed".equals(state)) {
                        String message = StrUtil.blankToDefault(result.getStr("err_msg"), "未知错误");
                        throw new IllegalStateException("MinerU 解析失败: "
                                + expected.get(dataId).originalFileName() + "，" + message);
                    }
                    if ("done".equals(state)) {
                        String fullZipUrl = result.getStr("full_zip_url");
                        if (StrUtil.isBlank(fullZipUrl)) {
                            throw new IllegalStateException("MinerU 已完成但未返回结果地址: "
                                    + expected.get(dataId).originalFileName());
                        }
                        completed.put(dataId, new CompletedResult(fullZipUrl));
                    } else if (!PENDING_STATES.contains(state)) {
                        throw new IllegalStateException("MinerU 返回未知解析状态: " + state);
                    }
                }
            }
            if (completed.size() == expected.size()) {
                return completed;
            }
            sleepBeforeNextPoll();
        }
        Set<String> unfinished = new LinkedHashSet<>(expected.keySet());
        unfinished.removeAll(completed.keySet());
        List<String> names = unfinished.stream().map(id -> expected.get(id).originalFileName()).toList();
        throw new IllegalStateException("等待 MinerU 批量解析超时，未完成文件: " + String.join(", ", names));
    }

    private JSONObject queryBatch(String batchId) {
        String baseUrl = appProperties.getMineru().getBatchResultUrl().replaceAll("/$", "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + batchId))
                .timeout(requestTimeout())
                .header("Authorization", authorization())
                .GET()
                .build();
        return sendJson(request, "查询 MinerU 批量结果");
    }

    private JSONArray downloadContentList(String fullZipUrl, String originalFileName) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(fullZipUrl))
                .timeout(Duration.ofSeconds(Math.max(1, appProperties.getMineru().getUploadTimeoutSeconds())))
                .GET()
                .build();
        HttpResponse<InputStream> response = send(request, HttpResponse.BodyHandlers.ofInputStream(),
                "下载 MinerU 解析结果: " + originalFileName);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            closeQuietly(response.body());
            throw new IllegalStateException("下载 MinerU 解析结果失败: " + originalFileName
                    + "，HTTP " + response.statusCode());
        }

        try (InputStream body = response.body(); ZipInputStream zip = new ZipInputStream(body, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName().toLowerCase(Locale.ROOT);
                if (!entry.isDirectory() && entryName.endsWith(".json") && entryName.contains("content_list")) {
                    return JSONUtil.parseArray(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取 MinerU 结果压缩包失败: " + originalFileName, exception);
        }
        throw new IllegalStateException("MinerU 结果压缩包缺少 content_list.json: " + originalFileName);
    }

    private JSONObject sendJson(HttpRequest request, String action) {
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), action);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(action + "失败，HTTP " + response.statusCode());
        }
        JSONObject responseJson;
        try {
            responseJson = JSONUtil.parseObj(response.body());
        } catch (Exception exception) {
            throw new IllegalStateException(action + "失败，响应不是合法 JSON", exception);
        }
        Object code = responseJson.get("code");
        if (code == null || !"0".equals(code.toString())) {
            throw new IllegalStateException(action + "失败: "
                    + StrUtil.blankToDefault(responseJson.getStr("msg"), "未知错误"));
        }
        return responseJson;
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler, String action) {
        try {
            return httpClient.send(request, bodyHandler);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(action + "被中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException(action + "失败: " + exception.getMessage(), exception);
        }
    }

    private JSONObject requireData(JSONObject responseJson, String action) {
        JSONObject data = responseJson.getJSONObject("data");
        if (data == null) {
            throw new IllegalStateException(action + "失败，响应缺少 data");
        }
        return data;
    }

    private void validateFiles(List<LocalParseFile> files) {
        Set<Long> taskItemIds = new LinkedHashSet<>();
        for (LocalParseFile file : files) {
            if (file == null || file.taskItemId() == null || StrUtil.isBlank(file.originalFileName())
                    || file.localPath() == null) {
                throw new IllegalArgumentException("本地解析文件信息不完整");
            }
            if (!taskItemIds.add(file.taskItemId())) {
                throw new IllegalArgumentException("存在重复的任务项文件: " + file.taskItemId());
            }
            if (!Files.isRegularFile(file.localPath())) {
                throw new IllegalArgumentException("本地解析文件不存在: " + file.originalFileName());
            }
        }
    }

    private void sleepBeforeNextPoll() {
        try {
            Thread.sleep(Math.max(1, appProperties.getMineru().getPollIntervalMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 MinerU 解析时被中断", exception);
        }
    }

    private Duration requestTimeout() {
        return Duration.ofSeconds(Math.max(1, appProperties.getMineru().getRequestTimeoutSeconds()));
    }

    private String authorization() {
        return "Bearer " + appProperties.getMineru().getToken();
    }

    private static void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    public record LocalParseFile(Long taskItemId, String originalFileName, Path localPath) {
        public String dataId() {
            return "task_item_" + taskItemId;
        }
    }

    private record UploadBatch(String batchId, List<String> uploadUrls) {
        private UploadBatch {
            uploadUrls = List.copyOf(new ArrayList<>(uploadUrls));
        }
    }

    private record CompletedResult(String fullZipUrl) {
    }
}
