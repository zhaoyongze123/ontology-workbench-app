package com.devin.ontology.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedClientAddressResolverTest {
    @Test
    void ignoresForwardedAddressWhenSocketPeerIsUntrusted() {
        TrustedClientAddressResolver resolver = new TrustedClientAddressResolver(Set.of("127.0.0.1/32"));
        MockHttpServletRequest request = request("198.51.100.20");
        request.addHeader("X-Forwarded-For", "203.0.113.99");

        assertEquals("198.51.100.20", resolver.resolve(request));
    }

    @Test
    void walksProxyChainFromTrustedPeerTowardFirstUntrustedAddress() {
        TrustedClientAddressResolver resolver = new TrustedClientAddressResolver(
                Set.of("127.0.0.1/32", "10.0.0.0/8"));
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.23, 10.1.2.3");

        assertEquals("198.51.100.23", resolver.resolve(request));
    }

    @Test
    void fallsBackToSocketPeerForMalformedForwardedChain() {
        TrustedClientAddressResolver resolver = new TrustedClientAddressResolver(Set.of("127.0.0.1/32"));
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.23, attacker.invalid");

        assertEquals("127.0.0.1", resolver.resolve(request));
    }

    private MockHttpServletRequest request(final String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
