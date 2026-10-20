package com.devin.ontology.common.utils;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 2025/7/10 19:57.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
public class ExportUtils {

    /**
     * 导出数据.
     *
     * @param response response
     * @param jsonData json数据
     * @param fileName 文件名
     */
    public static void exportData(final HttpServletResponse response, final String jsonData, final String fileName) {
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + ".json\"");
        response.setContentType("application/octet-stream");

        try (ServletOutputStream outputStream = response.getOutputStream()) {
            outputStream.write(jsonData.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 导入数据.
     *
     * @param file 文件
     * @return 数据
     * @throws IOException IOException
     */
    public static String importData(final MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }
}
