package com.devin.ontology.common.aop;

import com.devin.ontology.common.annocation.Logger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Controller 日志只记录请求元数据，不序列化请求参数和业务响应。 */
@Slf4j
@Aspect
@Component
public class LoggerAspect {
    private static final String REQUEST_ID_ATTRIBUTE = "ontology.requestId";

    @Pointcut("execution (* com.devin..controller..*.*(..)) || @annotation(com.devin.ontology.common.annocation.Logger)")
    public void pointCut() {
    }

    @Around("pointCut()")
    public Object aroundAdvice(final ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            recordLog(joinPoint, result, null, elapsedMillis(startedAt));
            return result;
        } catch (Throwable failure) {
            // 异常文本和栈可能包含认证材料或用户输入，只记录异常类型。
            recordLog(joinPoint, null, failure, elapsedMillis(startedAt));
            throw failure;
        }
    }

    private void recordLog(final ProceedingJoinPoint joinPoint, final Object result,
            final Throwable failure, final long elapsedMillis) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Logger annotation = method.getAnnotation(Logger.class);
        String module = Optional.ofNullable(annotation).map(Logger::module).orElse("");
        String description = Optional.ofNullable(annotation)
                .map(value -> Objects.equals(value.value(), "") ? value.desc() : value.value())
                .orElse("");

        ServletRequestAttributes attributes = requestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();
        HttpServletResponse response = attributes == null ? null : attributes.getResponse();
        String requestId = request == null ? "-"
                : safeMetadata(request.getAttribute(REQUEST_ID_ATTRIBUTE));
        String actor = request == null || request.getUserPrincipal() == null ? "anonymous"
                : safeMetadata(request.getUserPrincipal().getName());
        Object resolvedAddress = request == null ? null : request.getAttribute("ontology.clientAddress");
        String peerAddress = request == null ? "-"
                : safeMetadata(resolvedAddress == null ? request.getRemoteAddr() : resolvedAddress);
        String path = request == null ? "-" : safeMetadata(request.getRequestURI());
        String httpMethod = request == null ? "-" : safeMetadata(request.getMethod());
        int status = response == null ? (failure == null ? 200 : 500) : response.getStatus();
        String errorType = failure == null ? "-" : failure.getClass().getSimpleName();
        String resultType = result == null ? "void" : result.getClass().getSimpleName();

        // 只记录参数个数、类型和响应状态；绝不记录参数值、响应正文或异常消息。
        log.info("request completed requestId={} actor={} peer={} method={} path={} handler={} "
                        + "status={} argCount={} resultType={} errorType={} module={} description={} elapsedMs={}",
                requestId, actor, peerAddress, httpMethod, path,
                signature.getDeclaringType().getSimpleName() + "." + method.getName(),
                status, joinPoint.getArgs().length, resultType, errorType,
                safeMetadata(module), safeMetadata(description), elapsedMillis);
    }

    private ServletRequestAttributes requestAttributes() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes;
        }
        return null;
    }

    private String safeMetadata(final Object value) {
        if (value == null) return "-";
        String text = value.toString().replace('\r', '_').replace('\n', '_');
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

    private long elapsedMillis(final long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
