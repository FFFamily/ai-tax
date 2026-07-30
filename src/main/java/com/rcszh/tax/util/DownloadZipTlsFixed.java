package com.rcszh.tax.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
// https://cdn-mineru.openxlab.org.cn/pdf/2025-10-28/8d944840-9fb5-4eac-9bc4-4f9110783832.zip


public class DownloadZipTlsFixed {
    public static void main(String[] args) throws Exception {
        String zipUrl = "https://cdn-mineru.openxlab.org.cn/pdf/2025-10-28/8d944840-9fb5-4eac-9bc4-4f9110783832.zip"; // 改为你的 URL
        try {
            String fileName = downloadZip(zipUrl);
            System.out.println("Downloaded: " + fileName);
        } catch (Exception e) {
            System.err.println("下载失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String downloadZip(String fileUrl) throws IOException {
        // 强制使用 TLS 1.2/1.3（对大多数现代服务器有效）
        // 这会影响所有后续的 HTTPS 连接
        System.setProperty("https.protocols", "TLSv1.3,TLSv1.2");

        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // 基本 request 设置
        conn.setRequestProperty("User-Agent", "JavaDownloader/1.0");
        conn.setRequestProperty("Accept", "*/*");
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        // 如果服务要求 Host header（SNI 通常由 URL 主机自动处理），确保 URL host 与证书域名匹配
        // 打印一些诊断信息
        System.out.println("Connecting to: " + fileUrl);
        conn.connect();
        int code = conn.getResponseCode();
        System.out.println("HTTP response code: " + code);

        if (code != HttpURLConnection.HTTP_OK) {
            // 读取错误流以获取更多信息（如果服务器返回）
            InputStream err = conn.getErrorStream();
            if (err != null) {
                String errText = new String(err.readAllBytes());
                throw new IOException("HTTP " + code + " - " + errText);
            }
            throw new IOException("HTTP " + code);
        }

        // 生成文件名
        String pathName = new java.io.File(url.getPath()).getName();
        if (pathName == null || pathName.isBlank()) {
            pathName = "downloaded.zip";
        }
        if (!pathName.toLowerCase().endsWith(".zip")) pathName += ".zip";

        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(Paths.get(pathName))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        conn.disconnect();
        return pathName;
    }
}

