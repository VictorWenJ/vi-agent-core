package com.vi.agent.core.common.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
public final class HttpUtils {

    /** 最大总连接数 */
    private static final int MAX_TOTAL_CONNECTIONS = 200;

    /** 每个路由的默认最大连接数 */
    private static final int MAX_CONNECTIONS_PER_ROUTE = 50;

    /** 建立连接超时时间 */
    private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(3);

    /** 从连接池中获取连接的超时时间 */
    private static final Timeout CONNECTION_REQUEST_TIMEOUT = Timeout.ofSeconds(3);

    /** 响应超时时间 */
    private static final Timeout RESPONSE_TIMEOUT = Timeout.ofSeconds(10);

    /** 空闲连接清理时间 */
    private static final TimeValue IDLE_CONNECTION_EVICT_TIME = TimeValue.ofSeconds(30);

    /** 连接池管理器 */
    private static final PoolingHttpClientConnectionManager CONNECTION_MANAGER;

    /** 单例 HttpClient */
    private static final CloseableHttpClient HTTP_CLIENT;

    static {
        CONNECTION_MANAGER = PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(
                ConnectionConfig.custom()
                    .setConnectTimeout(CONNECT_TIMEOUT)
                    .setSocketTimeout(RESPONSE_TIMEOUT)
                    .build()
            )
            .build();

        CONNECTION_MANAGER.setMaxTotal(MAX_TOTAL_CONNECTIONS);
        CONNECTION_MANAGER.setDefaultMaxPerRoute(MAX_CONNECTIONS_PER_ROUTE);

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
            .setResponseTimeout(RESPONSE_TIMEOUT)
            .build();

        HTTP_CLIENT = HttpClients.custom()
            .setConnectionManager(CONNECTION_MANAGER)
            .setDefaultRequestConfig(requestConfig)
            .evictExpiredConnections()
            .evictIdleConnections(IDLE_CONNECTION_EVICT_TIME)
            .build();
    }

    public static String sendGetRequest(String url, Map<String, Object> head, Map<String, Object> params) {
        String result = null;
        String requestUrl = url;
        try {
            URIBuilder uriBuilder = new URIBuilder(url);
            if (params != null && !params.isEmpty()) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    uriBuilder.addParameter(key, value == null ? "" : String.valueOf(value));
                }
            }

            requestUrl = uriBuilder.build().toString();
            HttpGet httpGet = new HttpGet(requestUrl);
            applyHeaders(head, httpGet);

            result = HTTP_CLIENT.execute(httpGet, HttpUtils::parseResponse);
            return result;
        } catch (Exception e) {
            log.error("HttpUtils sendGetRequest error, url:{}", requestUrl, e);
            throw new RuntimeException("sendGetRequest failed", e);
        } finally {
            log.info(
                "HttpUtils sendGetRequest url:{}, head:{}, params:{}, result:{}",
                requestUrl,
                JsonUtils.toJson(head),
                JsonUtils.toJson(params),
                result
            );
        }
    }

    public static String sendPostRequest(String url, Map<String, Object> head, Map<String, Object> params) {
        String result = null;
        try {
            HttpPost httpPost = new HttpPost(url);
            applyHeaders(head, httpPost);
            applyDefaultJsonContentType(head, httpPost);

            String jsonParams = params == null ? "{}" : JsonUtils.toJson(params);
            StringEntity requestEntity = new StringEntity(
                jsonParams,
                ContentType.APPLICATION_JSON
            );
            httpPost.setEntity(requestEntity);

            result = HTTP_CLIENT.execute(httpPost, HttpUtils::parseResponse);
            return result;
        } catch (Exception e) {
            log.error("HttpUtils sendPostRequest error, url:{}", url, e);
            throw new RuntimeException("sendPostRequest failed", e);
        } finally {
            log.info(
                "HttpUtils sendPostRequest url:{}, head:{}, params:{}, result:{}",
                url,
                JsonUtils.toJson(head),
                JsonUtils.toJson(params),
                result
            );
        }
    }

    private static void applyHeaders(Map<String, Object> head, HttpUriRequestBase request) {
        if (head == null || head.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : head.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                request.setHeader(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
    }

    private static void applyDefaultJsonContentType(Map<String, Object> head, HttpUriRequestBase request) {
        if (!containsHeaderIgnoreCase(head, "Content-Type")) {
            request.setHeader("Content-Type", "application/json;charset=UTF-8");
        }
    }

    private static boolean containsHeaderIgnoreCase(Map<String, Object> head, String headerName) {
        if (head == null || head.isEmpty()) {
            return false;
        }
        for (String key : head.keySet()) {
            if (key != null && key.equalsIgnoreCase(headerName)) {
                return true;
            }
        }
        return false;
    }

    private static String parseResponse(final ClassicHttpResponse response) throws IOException {
        int statusCode = response.getCode();
        HttpEntity responseEntity = response.getEntity();
        String body;

        try {
            body = responseEntity == null
                ? null
                : EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
        } catch (ParseException e) {
            throw new IOException("parse response body failed", e);
        }

        if (statusCode < 200 || statusCode >= 300) {
            throw new RuntimeException(
                "http request failed, statusCode=" + statusCode + ", responseBody=" + body
            );
        }

        return body;
    }
}