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
        if (value.indexOf(':') < 0) return normalizeIpv4(value);
        try {
            InetAddress address = InetAddress.getByName(value);
            return address.getHostAddress();
        } catch (UnknownHostException invalid) {
            return null;
        }
    }

    private String normalizeIpv4(final String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) return null;
        StringBuilder normalized = new StringBuilder();
        for (String octet : octets) {
            // 严格接受四段十进制 IPv4，避免 Java 把 127.1 或前导零写法解释成其他地址。
            if (octet.isEmpty() || octet.length() > 3 || (octet.length() > 1 && octet.charAt(0) == '0')) {
                return null;
            }
            int number = 0;
            for (int index = 0; index < octet.length(); index++) {
                char digit = octet.charAt(index);
                if (digit < '0' || digit > '9') return null;
                number = number * 10 + digit - '0';
            }
            if (number > 255) return null;
            if (!normalized.isEmpty()) normalized.append('.');
            normalized.append(number);
        }
        return normalized.toString();
    }
}
