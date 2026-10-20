package com.devin.ontology.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/** 仅在直连 Peer 属于明确配置的代理网段时，才解析 X-Forwarded-For。 */
public final class TrustedClientAddressResolver {
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]+");
    private final List<IpAddressMatcher> trustedProxies;

    public TrustedClientAddressResolver(final Set<String> trustedProxyCidrs) {
        this.trustedProxies = trustedProxyCidrs.stream().filter(value -> !value.isBlank())
                .map(IpAddressMatcher::new).toList();
    }

    public String resolve(final HttpServletRequest request) {
        String peer = normalizeIp(request.getRemoteAddr());
        if (peer == null) return "unknown";
        if (!isTrusted(peer)) return peer;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || forwarded.length() > 2048) return peer;
        String[] entries = forwarded.split(",", -1);
        if (entries.length > 20) return peer;
        List<String> chain = new ArrayList<>(entries.length);
        for (String entry : entries) {
            String ip = normalizeIp(entry.trim());
            // 转发链任何一段无法解析时都回退到 socket peer，不猜测可信来源。
            if (ip == null) return peer;
            chain.add(ip);
        }

        String current = peer;
        for (int index = chain.size() - 1; index >= 0 && isTrusted(current); index--) {
            current = chain.get(index);
        }
        return current;
    }

    private boolean isTrusted(final String address) {
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private String normalizeIp(final String value) {
        if (value == null || value.isBlank() || value.length() > 45 || !IP_LITERAL.matcher(value).matches()) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            // 拒绝非规范数字 IP（例如只有一段的 legacy IPv4 表示法）。
            if (value.indexOf(':') < 0 && address.getAddress().length != 4) return null;
            return address.getHostAddress();
        } catch (UnknownHostException invalid) {
            return null;
        }
    }
}
