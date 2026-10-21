package com.devin.ontology.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class OAuthTokenTransactionFilterTest {
    @Test
    void doesNotPublishTokenBodyWhenTransactionCommitFails() throws Exception {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        MockHttpServletResponse response = new MockHttpServletResponse();
        doAnswer(invocation -> {
            // commit 调用时，缓存中的令牌尚未写入真实响应。
            assertThat(response.isCommitted()).isFalse();
            assertThat(response.getContentAsByteArray()).isEmpty();
            throw new IllegalStateException("simulated commit failure");
        }).when(transactions).commit(any(TransactionStatus.class));

        assertThatThrownBy(() -> filter(transactions).doFilter(request(), response,
                (request, buffered) -> buffered.getWriter().write("access-token-secret")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated commit failure");

        assertThat(response.isCommitted()).isFalse();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void publishesProtocolErrorOnlyAfterSuccessfulCommit() throws Exception {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        MockHttpServletResponse response = new MockHttpServletResponse();
        doAnswer(invocation -> {
            assertThat(response.isCommitted()).isFalse();
            assertThat(response.getContentAsByteArray()).isEmpty();
            return null;
        }).when(transactions).commit(any(TransactionStatus.class));

        filter(transactions).doFilter(request(), response, (request, buffered) -> {
            ((HttpServletResponse) buffered).setStatus(400);
            buffered.getWriter().write("{\"error\":\"invalid_grant\"}");
            buffered.flushBuffer();
        });

        verify(transactions).commit(any(TransactionStatus.class));
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"invalid_grant\"}");
    }

    @Test
    void doesNotPublishBodyWhenFilterChainThrows() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter(transactions).doFilter(request(), response, (request, buffered) -> {
            buffered.getWriter().write("access-token-secret");
            throw new IllegalStateException("simulated endpoint failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("simulated endpoint failure");

        verify(transactions).rollback(any(TransactionStatus.class));
        assertThat(response.isCommitted()).isFalse();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    private OAuthTokenTransactionFilter filter(final PlatformTransactionManager transactions) {
        return new OAuthTokenTransactionFilter(transactions);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/oidc/app/token");
        return request;
    }
}
