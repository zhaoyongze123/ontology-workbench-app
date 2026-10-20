package com.devin.ontology.common.utils;

import com.devin.ontology.common.constant.I18nConstant;
import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.common.utils.i18n.I18nUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2025/9/28 19:34.
 *
 * <p>
 *     字符串模版格式化工具
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
public class StringTemplateFormatUtils {

    /**
     * 匹配$关键字符.
     */
    private static final Pattern REGEX_TEMPLATE_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)(?=[^a-zA-Z0-9_]|$)");

    /**
     * 匹配模版参数.
     * @param template 模版
     * @param paramMap 参数
     * @return 格式化后的模版
     */
    public static String formatMatch(final String template, final Map<String, Object> paramMap) {
        // 判断模版是否为空
        if (template.isBlank()) {
            return "";
        }

        checkParamMatch(template, paramMap);

        // 采集匹配
        Matcher matcher = REGEX_TEMPLATE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String placeholder = matcher.group(1);

            Object value = paramMap.get(placeholder);
            String replacement = value.toString().trim();

            // 使用Matcher.quoteReplacement避免特殊字符问题
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 校验模版参数是否匹配.
     * @param template 模版
     * @param paramMap 参数
     */
    public static void checkParamMatch(final String template, final Map<String, Object> paramMap) {
        // 获取所有的Key
        Set<String> templateKeys = getTemplateKeys(template);
        Set<String> paramKeys = new HashSet<>(paramMap.keySet());
        // 判断参数是否匹配
        if (templateKeys.size() != paramKeys.size() || !paramKeys.containsAll(templateKeys)) {
            throw new ValidateException(I18nUtils.getMessage(I18nConstant.TEMPLATE_PARAM_NOT_MATCH));
        }
    }

    /**
     * 获取模版中的所有key.
     * @param template 模版
     * @return key列表
     */
    public static Set<String> getTemplateKeys(final String template) {
        Set<String> keySet = new LinkedHashSet<>();

        Matcher matcher = REGEX_TEMPLATE_PATTERN.matcher(template);

        while (matcher.find()) {
            keySet.add(matcher.group(1));
        }

        return keySet;
    }

    /**
     * 构建模版格式化工具.
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 构建模版格式化工具.
     */
    public static class Builder {
        /**
         * 模版.
         */
        private String template;

        /**
         * 参数.
         */
        private final Map<String, Object> paramMap = new HashMap<>();

        /**
         * build.
         * @return 格式化结果
         */
        public String build() {
            return formatMatch(template, paramMap);
        }

        /**
         * 设置模版.
         * @param template 模版
         * @return Builder
         */
        public Builder template(final String template) {
            this.template = template;
            return this;
        }

        /**
         * 设置参数.
         * @param key 参数key
         * @param value 参数值
         * @return Builder
         */
        public Builder param(final String key, final Object value) {
            this.paramMap.put(key, value);
            return this;
        }

        /**
         * 设置参数.
         * @param paramMap 参数
         * @return Builder
         */
        public Builder param(final Map<String, Object> paramMap) {
            this.paramMap.putAll(paramMap);
            return this;
        }
    }
}
