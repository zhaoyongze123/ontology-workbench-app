package com.devin.ontology.common.exception;

/** API 响应、请求关联日志与审计共用的服务端关联号契约。 */
public final class ApiRequestContext {
    public static final String REQUEST_ID_ATTRIBUTE = "ontology.requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    private ApiRequestContext() { }
}
