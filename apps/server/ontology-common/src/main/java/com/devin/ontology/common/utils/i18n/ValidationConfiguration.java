package com.devin.ontology.common.utils.i18n;

import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * 不合法配置.
 * @author joey
 */
@Configuration
public class ValidationConfiguration {

    private MessageSource messageSource;

    /**
     * Set MessageSource.
     * @param messageSource MessageSource
     */
    @Autowired
    public void setMessageSource(final MessageSource messageSource) {
        this.messageSource = messageSource;
    }
    
    /**
     * Create LocalValidatorFactoryBean Instance.
     * @return LocalValidatorFactoryBean Instance
     */
    @NotNull(message = "{required.parameter.error}")
    @Bean
    public LocalValidatorFactoryBean getValidator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource);
        return bean;
    }

}
