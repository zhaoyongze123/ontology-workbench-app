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
import org.springframework.web.filter.OncePerRequestFilter;

/** 令刷新令牌的读取、轮换及重用检测在同一数据库事务中串行化。 */
public final class OAuthTokenTransactionFilter extends OncePerRequestFilter {
    private final TransactionTemplate transactions;

    public OAuthTokenTransactionFilter(final PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().endsWith("/oidc/app/token");
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        try {
            transactions.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(final TransactionStatus status) {
                    try {
                        filterChain.doFilter(request, response);
                    } catch (IOException | ServletException failure) {
                        status.setRollbackOnly();
                        throw new TokenEndpointFailure(failure);
                    }
                }
            });
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
