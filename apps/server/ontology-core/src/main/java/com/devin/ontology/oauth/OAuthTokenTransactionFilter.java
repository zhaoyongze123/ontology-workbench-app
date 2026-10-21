package com.devin.ontology.oauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

/** 令 Token 轮换、重用检测及 RFC 7009 撤销在同一数据库事务中串行化。 */
public final class OAuthTokenTransactionFilter extends OncePerRequestFilter {
    private final TransactionTemplate transactions;

    public OAuthTokenTransactionFilter(final PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        String path = request.getRequestURI();
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !(path.endsWith("/oidc/app/token") || path.endsWith("/oidc/app/revoke"));
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        // Token 响应必须等事务真正提交后才能写到客户端，避免提交失败时泄露已失效的令牌。
        ContentCachingResponseWrapper bufferedResponse = new ContentCachingResponseWrapper(response);
        try {
            transactions.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(final TransactionStatus status) {
                    try {
                        filterChain.doFilter(request, bufferedResponse);
                    } catch (IOException | ServletException failure) {
                        status.setRollbackOnly();
                        throw new TokenEndpointFailure(failure);
                    }
                }
            });
            // 正常协议响应（包括 invalid_grant 等 4xx）仍会提交并发布；只有提交成功才复制响应体。
            bufferedResponse.copyBodyToResponse();
        } catch (TokenEndpointFailure failure) {
            if (failure.getCause() instanceof IOException io) throw io;
            if (failure.getCause() instanceof ServletException servlet) throw servlet;
            throw failure;
        }
    }

    private static final class TokenEndpointFailure extends RuntimeException {
        private TokenEndpointFailure(final Exception cause) { super(cause); }
    }
}
