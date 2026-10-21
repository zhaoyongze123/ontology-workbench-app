package com.devin.ontology.common.exception;

import com.devin.ontology.common.constant.I18nConstant;
import com.devin.ontology.common.utils.i18n.I18nUtils;
import com.devin.ontology.common.utils.r.ApiResult;
import com.devin.ontology.common.utils.r.ResultEnum;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.NonTransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

/**
 * 2025/6/12 16:16.
 *
 * <p>
 *     全局异常处理
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
@Profile("!multiuser")
public class GlobalExceptionHandler {
    private static final String REQUEST_ID_ATTRIBUTE = "ontology.requestId";

    /**
     * 处理业务异常.
     * @param e 业务异常
     * @return 响应结果集
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleBusinessException(final HttpServletRequest request, final BusinessException e) {
        logSafeFailure(request, e, e.getErrCode());
        return ApiResult.fail(e.getErrCode(), ResultEnum.BUSINESS_ERROR.getMessage());
    }

    /**
     * 处理客户端异常.
     * @param e 客户端异常
     * @return 响应结果集
     */
    @ExceptionHandler(ClientException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleClientException(final HttpServletRequest request, final ClientException e) {
        logSafeFailure(request, e, e.getErrCode());
        return ApiResult.fail(e.getErrCode(), ResultEnum.CLIENT_ERROR.getMessage());
    }

    /**
     * 处理参数校验异常.
     * @param e 参数校验异常
     * @return 响应结果集
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleMethodArgumentNotValidException(final BindException e) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fieldError -> map.putIfAbsent(
                fieldError.getField(), fieldError.getDefaultMessage()));
        return ApiResult.fail(ResultEnum.PARAM_IS_INVALID, map);
    }

    /**
     * Neo4j异常处理.
     * @param e Neo4j异常
     * @return ApiResult
     */
    @ExceptionHandler(NonTransientDataAccessResourceException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleFatalDiscoveryException(final HttpServletRequest request,
            final NonTransientDataAccessResourceException e) {
        logSafeFailure(request, e, null);
        // 数据库异常原文可能包含 SQL、schema、连接信息或用户输入，不能作为 API 消息返回。
        return ApiResult.fail(ResultEnum.GRAPH_ERROR, I18nUtils.getMessage(I18nConstant.ERROR));
    }

    /**
     * 处理图谱报错信息.
     * @param e 图谱报错信息
     * @return ApiResult
     */
    @ExceptionHandler(InvalidDataAccessResourceUsageException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleInvalidDataAccessResourceUsageException(final HttpServletRequest request,
            final InvalidDataAccessResourceUsageException e) {
        logSafeFailure(request, e, null);
        String errType = "42601".equals(sqlState(e)) ? "Cypher语法出错，请检查语法信息" :
                I18nUtils.getMessage(I18nConstant.ERROR);
        return ApiResult.fail(ResultEnum.GRAPH_ERROR.getCode(), errType);
    }

    /**
     * 数据库重复Key异常.
     * @param e 数据库重复Key异常
     * @return 响应结果集
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleDuplicateKeyException(final HttpServletRequest request, final DuplicateKeyException e) {
        logSafeFailure(request, e, ResultEnum.DUPLICATE_KEY.getCode());
        return ApiResult.fail(ResultEnum.DUPLICATE_KEY);
    }

    /**
     * 处理校验异常.
     * @param e 校验异常
     * @return 响应结果集
     */
    @ExceptionHandler(ValidateException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ApiResult<?> handleDuplicateKeyException(final HttpServletRequest request, final ValidateException e) {
        logSafeFailure(request, e, e.getErrCode());
        return ApiResult.fail(e.getErrCode(), ResultEnum.VALIDATE_ERROR.getMessage());
    }

    /**
     * 处理异常.
     * @param request 请求
     * @param e 异常
     * @return 响应结果集
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleException(final HttpServletRequest request, final Exception e) {
        logSafeFailure(request, e, ResultEnum.ERROR.getCode());
        return ApiResult.fail(ResultEnum.ERROR, "请求处理失败，请稍后重试");
    }

    private void logSafeFailure(final HttpServletRequest request, final Throwable failure, final Integer errorCode) {
        // 异常消息和 SQL 细节可能含用户输入，只保留排障所需的白名单元数据。
        log.error("request failed requestId={} method={} path={} exceptionType={} errorCode={}",
                safeMetadata(request.getAttribute(REQUEST_ID_ATTRIBUTE), 80), safeMetadata(request.getMethod(), 16),
                safeMetadata(request.getRequestURI(), 512), failure.getClass().getSimpleName(),
                errorCode == null ? "-" : errorCode);
    }

    private String safeMetadata(final Object value, final int maxLength) {
        if (value == null) return "-";
        String raw = value.toString();
        StringBuilder sanitized = new StringBuilder(Math.min(raw.length(), maxLength));
        for (int index = 0; index < raw.length() && sanitized.length() < maxLength; index++) {
            char character = raw.charAt(index);
            sanitized.append(Character.isISOControl(character) ? '_' : character);
        }
        return sanitized.toString();
    }

    private String sqlState(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }
}
