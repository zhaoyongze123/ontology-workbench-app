package com.devin.ontology.common.exception;

import com.devin.ontology.common.utils.r.ApiResult;
import java.util.Map;

/** 旧错误结构的集中序列化器；不判断业务原因，也不从异常文案推断错误类型。 */
public final class LegacyErrorAdapter {
    public enum Shape { API_RESULT, ERROR_FIELD }

    private LegacyErrorAdapter() { }

    public static Object adapt(final Shape shape, final ApiErrorCode code, final int numericCode,
            final String safeDetail, final String legacyError, final Map<String, String> fieldErrors) {
        if (shape == Shape.ERROR_FIELD) {
            return Map.of("error", legacyError == null ? "request_failed" : legacyError);
        }
        ApiResult<Object> result = ApiResult.fail(numericCode, safeDetail);
        if (fieldErrors != null && !fieldErrors.isEmpty()) result.setData(fieldErrors);
        return result;
    }
}
