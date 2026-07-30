package com.rcszh.tax.util;

import cn.hutool.core.util.URLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

public class FileUtil {
    private static  final Logger logger = LoggerFactory.getLogger(FileUtil.class);
    /**
     * 下载文件到临时目录
     */
    public static File downloadFile(String fileUrl) throws IOException {
        URL url = URI.create(fileUrl).toURL();
        String fileName = getFileNameFromUrl(URLUtil.decode(fileUrl));
        // 创建临时文件
        File tempFile = File.createTempFile("excel_", "_" + fileName);
        logger.info("当前文件下载临时目录：{}",tempFile.getAbsolutePath());
        tempFile.deleteOnExit();
        try (InputStream in = url.openStream();
             FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        return tempFile;
    }
    /**
     * 删除临时文件
     * @param tempFile 临时文件
     */
    public static void deleteTempFile(File tempFile)  {
        if (tempFile == null){
            return;
        }
        if (tempFile.exists()) {
            boolean delete = tempFile.delete();
        }
    }
    /**
     * 从URL中提取文件名
     */
    private static String getFileNameFromUrl(String url) {
        String[] parts = url.split("/");
        String fileName = parts[parts.length - 1];

        // 如果文件名包含查询参数，则移除
        if (fileName.contains("?")) {
            fileName = fileName.substring(0, fileName.indexOf("?"));
        }

        return fileName;
    }
}
