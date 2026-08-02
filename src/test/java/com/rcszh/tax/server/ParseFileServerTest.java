package com.rcszh.tax.server;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.rcszh.tax.config.AppProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParseFileServerTest {
    @TempDir
    Path tempDir;

    private HttpServer httpServer;
    private String baseUrl;
    private final AtomicBoolean uploadHadContentType = new AtomicBoolean();
    private final AtomicReference<byte[]> uploadedBytes = new AtomicReference<>();
    private final AtomicReference<JSONObject> createRequest = new AtomicReference<>();
    private final AtomicReference<String> resultState = new AtomicReference<>("done");

    @BeforeEach
    void startServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
        httpServer.createContext("/batch", this::handleCreateBatch);
        httpServer.createContext("/upload", this::handleUpload);
        httpServer.createContext("/results/batch-1", this::handleBatchResult);
        httpServer.createContext("/result.zip", this::handleZipResult);
        httpServer.start();
    }

    @AfterEach
    void stopServer() {
        httpServer.stop(0);
    }

    @Test
    void uploadsLocalFileWithoutContentTypeAndReturnsContentList() throws IOException {
        Path sourceFile = tempDir.resolve("statement.pdf");
        byte[] sourceBytes = "local-pdf-content".getBytes(StandardCharsets.UTF_8);
        Files.write(sourceFile, sourceBytes);

        AppProperties properties = new AppProperties();
        properties.getMineru().setCreateBatchUrl(baseUrl + "/batch");
        properties.getMineru().setBatchResultUrl(baseUrl + "/results/");
        properties.getMineru().setPollIntervalMillis(1);
        properties.getMineru().setMaxWaitMillis(1000);

        ApiTokenServer tokenServer = mock(ApiTokenServer.class);
        when(tokenServer.getMinerUToken()).thenReturn("test-token");
        ParseFileServer server = new ParseFileServer();
        ReflectionTestUtils.setField(server, "apiTokenServer", tokenServer);
        ReflectionTestUtils.setField(server, "appProperties", properties);

        Map<Long, JSONArray> results = server.parseLocalFiles(
                java.util.List.of(new ParseFileServer.LocalParseFile(7L, "statement.pdf", sourceFile)));

        assertThat(uploadHadContentType).isFalse();
        assertThat(uploadedBytes.get()).isEqualTo(sourceBytes);
        assertThat(results).containsOnlyKeys(7L);
        assertThat(results.get(7L).getJSONObject(0).getStr("text")).isEqualTo("parsed");

        JSONObject body = createRequest.get();
        assertThat(body.getStr("model_version")).isEqualTo("vlm");
        JSONObject requestedFile = body.getJSONArray("files").getJSONObject(0);
        assertThat(requestedFile.getStr("name")).isEqualTo("statement.pdf");
        assertThat(requestedFile.getStr("data_id")).isEqualTo("task_item_7");
    }

    @Test
    void failsWholeCallWhenMineruReportsFileFailure() throws IOException {
        Path sourceFile = tempDir.resolve("broken.pdf");
        Files.writeString(sourceFile, "broken");
        resultState.set("failed");

        ParseFileServer server = createServer();

        assertThatThrownBy(() -> server.parseLocalFiles(
                java.util.List.of(new ParseFileServer.LocalParseFile(7L, "broken.pdf", sourceFile))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MinerU 解析失败")
                .hasMessageContaining("解析服务错误");
    }

    private ParseFileServer createServer() {
        AppProperties properties = new AppProperties();
        properties.getMineru().setCreateBatchUrl(baseUrl + "/batch");
        properties.getMineru().setBatchResultUrl(baseUrl + "/results/");
        properties.getMineru().setPollIntervalMillis(1);
        properties.getMineru().setMaxWaitMillis(1000);

        ApiTokenServer tokenServer = mock(ApiTokenServer.class);
        when(tokenServer.getMinerUToken()).thenReturn("test-token");
        ParseFileServer server = new ParseFileServer();
        ReflectionTestUtils.setField(server, "apiTokenServer", tokenServer);
        ReflectionTestUtils.setField(server, "appProperties", properties);
        return server;
    }

    private void handleCreateBatch(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestMethod()).isEqualTo("POST");
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-token");
        createRequest.set(JSONUtil.parseObj(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        respondJson(exchange, """
                {"code":0,"data":{"batch_id":"batch-1","file_urls":["%s/upload"]},"msg":"ok"}
                """.formatted(baseUrl));
    }

    private void handleUpload(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestMethod()).isEqualTo("PUT");
        uploadHadContentType.set(exchange.getRequestHeaders().containsKey("Content-Type"));
        uploadedBytes.set(exchange.getRequestBody().readAllBytes());
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }

    private void handleBatchResult(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-token");
        String state = resultState.get();
        respondJson(exchange, """
                {"code":0,"data":{"batch_id":"batch-1","extract_result":[
                  {"data_id":"task_item_7","file_name":"statement.pdf","state":"%s","err_msg":"解析服务错误","full_zip_url":"%s/result.zip"}
                ]},"msg":"ok"}
                """.formatted(state, baseUrl));
    }

    private void handleZipResult(HttpExchange exchange) throws IOException {
        byte[] zipBytes = contentListZip();
        exchange.sendResponseHeaders(200, zipBytes.length);
        exchange.getResponseBody().write(zipBytes);
        exchange.close();
    }

    private void respondJson(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private byte[] contentListZip() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("statement_content_list.json"));
            zip.write("[{\"type\":\"text\",\"text\":\"parsed\"}]".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
