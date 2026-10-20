package com.devin.ontology.common.utils.i18n;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import java.util.Locale;

/**
 * 国际化工具类.
 *
 * @author joey
 */
@Slf4j
@Component
public class I18nUtils {

    private static MessageSource messageSource;

    /**
     * 注入MessageSource.
     *
     * @param messageSource MessageSource
     */
    @Autowired
    public void setMessageSource(final MessageSource messageSource) {
        I18nUtils.messageSource = messageSource;
    }

    /**
     * 获取国际化.
     *
     * @param code code
     * @return 信息
     */
    public static String getMessage(final String code) {
        return getMessage(code, null);
    }

    /**
     * 获取国际化.
     *
     * @param code code
     * @param args 占位符
     * @return 信息
     */
    public static String getMessage(final String code, final Object... args) {
        // 如果 messageSource 还没有初始化，返回默认消息
        if (messageSource == null) {
            log.warn("MessageSource not initialized yet, returning default message for code: {}", code);
            return getDefaultMessage(code);
        }
        
        Locale locale = LocaleContextHolder.getLocale();
        String content;
        try {
            content = messageSource.getMessage(code, args, locale);
        } catch (NoSuchMessageException e) {
            content = getChinaLocalMessage(code, args);
        }
        return content;
    }

    /**
     * 获取默认消息（当 messageSource 未初始化时使用）.
     *
     * @param code code
     * @return 默认消息
     */
    private static String getDefaultMessage(final String code) {
        // 根据常用的 code 返回默认消息
        switch (code) {
            case "success":
                return "成功";
            case "error":
                return "未知错误";
            default:
                return "未知信息";
        }
    }

    /**
     * 获取中国语言.
     *
     * @param code code
     * @return 信息
     */
    public static String getChinaLocalMessage(final String code) {
        Locale locale = Locale.CHINA;
        String content;
        try {
            content = messageSource.getMessage(code, null, locale);
        } catch (NoSuchMessageException e) {
            log.info("获取提示消息失败： ->", e);
            content = "未知信息";
        }
        return content;
    }

    /**
     * 获取中国语言.
     *
     * @param code code
     * @param args 占位符
     * @return 信息
     */
    public static String getChinaLocalMessage(final String code, final Object... args) {
        Locale locale = Locale.CHINA;
        String content;
        try {
            content = messageSource.getMessage(code, args, locale);
        } catch (NoSuchMessageException e) {
            log.info("获取提示消息失败： ->", e);
            content = "未知信息";
        }
        return content;
    }

    /**
     * 获取当前语言.
     *
     * @return 当前语言
     */
    public static String getLanguage() {
        String language = LocaleContextHolder.getLocale().getLanguage();
        String[] strings = language.split("_");
        if (strings.length > 1) {
            strings[1] = strings[1].toUpperCase();
        }
        return StringUtils.join(strings, "_");
    }
}
