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
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        return ApiResult.fail(e.getErrCode(), e.getErrMsg());
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
        return ApiResult.fail(e.getErrCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常.
     * @param e 参数校验异常
     * @return 响应结果集
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleMethodArgumentNotValidException(final BindException e) {
        String errorField = Objects.requireNonNull(e.getFieldError()).getField();
        String errorMsg = Objects.requireNonNull(e.getBindingResult().getFieldError()).getDefaultMessage();
        Map<String, String> map = new HashMap<>();
        map.put(errorField, errorMsg);
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
        String errMsg = e.getMessage();
        String errType = "";
        String errData = "";
        // 使用正则表达式提取数据库名称
        if (errMsg != null) {
            Matcher matcher = Pattern.compile("Database name: '([^']+)'")
                    .matcher(errMsg);
            if (matcher.find()) {
                errType = I18nUtils.getMessage(I18nConstant.GRAPH_NOT_EXIST);
                errData = matcher.group(1);
            } else {
                errType = I18nUtils.getMessage(I18nConstant.ERROR);
                errData = errMsg;
            }
        }
        return ApiResult.fail(ResultEnum.GRAPH_ERROR, errType + ": " + errData);
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
        String errMsg = e.getMessage();
        String errType = "";
        if (errMsg.contains("Neo.ClientError.Statement.SyntaxError")) {
            errType = "Cypher语法出错，请检查语法信息";
        }
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
        Map<String, String> errMap = extractDuplicateKeyAndValue(e.getMessage());
        logSafeFailure(request, e, ResultEnum.DUPLICATE_KEY.getCode());
        if (!errMap.isEmpty()) {
            return ApiResult.fail(ResultEnum.DUPLICATE_KEY, errMap.get("key"));
        }
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
        return ApiResult.fail(e.getErrCode(), e.getMessage());
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

    /**
     * 提取重复键和值.
     * @param errorMessage 错误信息
     * @return 键和值
     */
    private static Map<String, String> extractDuplicateKeyAndValue(final String errorMessage) {
        Map<String, String> result = new HashMap<>();

        // 正则表达式与上面相同
        final Pattern pattern = Pattern.compile("Key \\((.*)\\)=\\((.*)\\) already exists");
        Matcher matcher = pattern.matcher(errorMessage);

        if (matcher.find() && matcher.groupCount() >= 2) {
            // group(1) 捕获第一个括号的内容 (key)
            String key = matcher.group(1);
            // group(2) 捕获第二个括号的内容 (value)
            String value = matcher.group(2);

            result.put("key", key);
            result.put("value", value);
        }

        return result;
    }
}
