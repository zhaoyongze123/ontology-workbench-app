package com.devin.ontology.common.utils.i18n;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import java.util.Locale;

/**
 * 国际化配置.
 * @author joey
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.messages", value = "basename", havingValue = "i18n/messages")
@ComponentScan(basePackages = "com.devin.utils")
public class MarsI18nAutoConfiguration {

    @Value("${spring.messages.basename}")
    private String baseName;
    
    /**
     * Create MessageSource Instance.
     * @return MessageSource Instance
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename(baseName);
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }
    
    /**
     * 默认解析器 其中locale表示默认语言.
     * @return LocaleResolver
     */
    @Bean
    public LocaleResolver localeResolver() {
        DataVizLocalResolver localeResolver = new DataVizLocalResolver();
        localeResolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        return localeResolver;
    }
}
