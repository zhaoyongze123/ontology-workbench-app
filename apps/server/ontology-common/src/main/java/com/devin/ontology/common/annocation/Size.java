package com.devin.ontology.common.annocation;

import com.devin.ontology.common.enums.TextTypeEnum;
import com.devin.ontology.common.utils.validator.SizeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 2025/12/1 18:57.
 *
 * <p>
 *     自定义长度校验器
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SizeValidator.class)
public @interface Size {

    /**
     * 错误信息.
     * @return 错误信息
     */
    String message() default "parameter size is error";

    /**
     * 最短长度.
     * @return 最短长度
     */
    int min() default 0;

    /**
     * 最长长度.
     * @return 最长长度
     */
    int max() default Integer.MAX_VALUE;

    /**
     * 文本类型.
     * @return 文本类型
     */
    TextTypeEnum type() default TextTypeEnum.TEXT;

    /**
     * 字符集.
     * @return 字符集
     */
    String charset() default "UTF-8";

    /**
     * 默认分组.
     * @return 默认分组
     */
    Class<?>[] groups() default {};

    /**
     * 负载.
     * @return 负载
     */
    Class<? extends Payload>[] payload() default {};
}
