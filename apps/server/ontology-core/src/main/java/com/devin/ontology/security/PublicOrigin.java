package com.devin.ontology.security;

import java.net.URI;
import java.net.URISyntaxException;

/** 对外链接与 OAuth issuer 共用的可信公开 origin 解析规则。 */
public final class PublicOrigin {
    private PublicOrigin() {}

    public static String parse(final String configuredOrigin) {
        if (configuredOrigin == null || configuredOrigin.isEmpty()
                || configuredOrigin.chars().anyMatch(Character::isWhitespace)
                || configuredOrigin.chars().anyMatch(Character::isISOControl)) {
            throw invalidOrigin();
        }

        // 根路径斜杠不属于 origin；先规范化，避免调用方对 https://host 与 https://host/ 处理不一致。
        String normalized = configuredOrigin.replaceAll("/+$", "");
        final URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException invalid) {
            throw invalidOrigin();
        }

        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.isOpaque() || uri.getHost() == null || uri.getHost().isEmpty()
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
            throw invalidOrigin();
        }
        return uri.toASCIIString();
    }

    private static IllegalArgumentException invalidOrigin() {
        return new IllegalArgumentException("ontology.security.public-origin 必须是纯 HTTP(S) origin");
    }
}
