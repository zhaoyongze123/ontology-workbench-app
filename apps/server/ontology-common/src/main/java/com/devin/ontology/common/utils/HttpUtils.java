package com.devin.ontology.common.utils;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Http请求工具类.
 * @author joey
 */
@Slf4j
@Component
public class HttpUtils {

    private static RestTemplate restTemplate;

    @Resource
    private RestTemplate restTemplateBean;

    /**
     * 初始化.
     */
    @PostConstruct
    public void init() {
        HttpUtils.restTemplate = this.restTemplateBean;
    }

    /**
     * get请求.
     *
     * @param url url
     * @return 返回结果
     */
    public static String get(final String url) {
        return get(url, null, null);
    }

    /**
     * get请求.
     *
     * @param url url
     * @param params 请求参数
     * @return 返回结果
     */
    public static String get(final String url, final Map<String, String> params) {
        return get(url, params, null);
    }
    
    /**
     * get请求.
     *
     * @param url url
     * @param params  请求参数
     * @param headers 请求头
     * @return 返回结果
     */
    public static String get(final String url, final Map<String, String> params, final Map<String, String> headers) {
        return request(url, params, headers, HttpMethod.GET);
    }

    /**
     * get请求.
     *
     * @param url url
     * @return 返回结果
     */
    public static Object getForObject(final String url) {
        String json = get(url, null);
        return JSONObject.parseObject(json, Object.class);
    }

    /**
     * get请求.
     *
     * @param url url
     * @param params 请求参数
     * @return 返回结果
     */
    public static Object getForObject(final String url, final Map<String, String> params) {
        String json = get(url, params);
        return JSONObject.parseObject(json, Object.class);
    }

    /**
     * get请求.
     *
     * @param url url
     * @param classOfT 类型
     * @param <T> 类型
     * @return 返回结果
     */
    public static <T> T getForJsonObject(final String url, final Class<T> classOfT) {
        String json = get(url, null);
        return JSONObject.parseObject(json, classOfT);
    }

    /**
     * get请求.
     *
     * @param url url
     * @param params 请求参数
     * @param classOfT 类型
     * @param <T> 类型
     * @return 返回结果
     */
    public static <T> T getForJsonObject(final String url, final Map<String, String> params, final Class<T> classOfT) {
        String json = get(url, params);
        return JSONObject.parseObject(json, classOfT);
    }

    /**
     * post请求.
     *
     * @param url url
     * @param params 请求参数
     * @return 返回结果
     */
    public static String post(final String url, final Map<String, String> params) {
        return post(url, params, null);
    }
    
    /**
     * post请求.
     *
     * @param url url
     * @param params  请求参数
     * @param headers 请求头
     * @return 返回结果
     */
    public static String post(final String url, final Map<String, String> params, final Map<String, String> headers) {
        return request(url, params, headers, HttpMethod.POST);
    }

    /**
     * post请求.
     *
     * @param url url
     * @param params  请求参数
     * @param headers 请求头
     * @return 返回结果
     */
    public static String post(final String url, final Object params, final Map<String, String> headers) {
        return request(url, params, headers, HttpMethod.POST, MediaType.APPLICATION_JSON);
    }
    
    /**
     * put请求.
     *
     * @param url url
     * @param params 请求参数
     * @return 返回结果
     */
    public static String put(final String url, final Map<String, String> params) {
        return put(url, params, null);
    }
    
    /**
     * put请求.
     *
     * @param url url
     * @param params  请求参数
     * @param headers 请求头
     * @return 返回结果
     */
    public static String put(final String url, final Map<String, String> params, final Map<String, String> headers) {
        return request(url, params, headers, HttpMethod.PUT);
    }
    
    /**
     * delete请求.
     *
     * @param url url
     * @param params 请求参数
     * @return 返回结果
     */
    public static String delete(final String url, final Map<String, String> params) {
        return delete(url, params, null);
    }
    
    /**
     * delete请求.
     *
     * @param url url
     * @param params  请求参数
     * @param headers 请求头
     * @return 返回结果
     */
    public static String delete(final String url, final Map<String, String> params, final Map<String, String> headers) {
        return request(url, params, headers, HttpMethod.DELETE);
    }
    
    /**
     * 表单请求.
     *
     * @param url url
     * @param params  请求参数
     * @param headers 请求头
     * @param method  请求方式
     * @return 返回结果
     */
    public static String request(final String url, final Map<String, String> params, final Map<String, String> headers, final HttpMethod method) {
    
        Map<String, String> paramsNew = params;
        if (paramsNew == null) {
            paramsNew = new HashMap<>();
        }
        return request(url, paramsNew, headers, method, MediaType.APPLICATION_JSON);
    }
    
    /**
     * http请求.
     *
     * @param url url
     * @param params    请求参数
     * @param headers   请求头
     * @param method    请求方式
     * @param mediaType 参数类型
     * @return 返回结果
     */
    public static String request(final String url, final Object params, final Map<String, String> headers, final HttpMethod method, final MediaType mediaType) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        // header
        HttpHeaders httpHeaders = new HttpHeaders();
        if (headers != null) {
            headers.forEach(httpHeaders::add);
        }
        // 提交方式：表单、json
        httpHeaders.setContentType(mediaType);
        HttpEntity<Object> httpEntity = new HttpEntity<>(params, httpHeaders);
        ResponseEntity<String> response = restTemplate.exchange(url, method, httpEntity, String.class);
        return response.getBody();
    }

    /**
     * 从request获取body的json数据.
     *
     * @param request /
     * @return /
     * @author joey
     */
    public static String getBodyString(final ServletRequest request) {
        StringBuilder sb = new StringBuilder();
        try (ServletInputStream inputStream = request.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line = "";
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            log.error("Exception:", e);
        }
        return sb.toString();
    }

    /**
     * 获取图片base64.
     *
     * @param imageUrl 图片地址
     * @return 图片base64
     * @throws IOException IOException
     */
    public static String getImageBase64(final String imageUrl) throws IOException {
        byte[] imageBytes = getByteFromUrl(imageUrl);

        // 将 byte[] 转换为 Base64 编码的字符串
        return Base64.encodeBase64String(imageBytes);
    }

    /**
     * 通过url获取字节码.
     *
     * @param url url
     * @return 字节码
     * @throws IOException IOException
     */
    public static byte[] getByteFromUrl(final String url) throws IOException {
        try (InputStream inputStream = new URL(url).openStream()) {
            // 将 InputStream 转换为 byte[]
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            return byteArrayOutputStream.toByteArray();
        }
    }

    /**
     * 获取目标主机的ip.
     *
     * @param request 请求
     * @return ip
     */
    public static String getRemoteHost(final HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip.contains("0:0:0:0:0:0:0:1") ? "127.0.0.1" : ip;
    }
}
