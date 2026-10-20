package com.devin.ontology.common.utils.validator;

import com.devin.ontology.common.annocation.Size;
import com.devin.ontology.common.constant.I18nConstant;
import com.devin.ontology.common.enums.TextTypeEnum;
import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.common.utils.i18n.I18nUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.io.UnsupportedEncodingException;
import java.util.Objects;

/**
 * 2025/12/1 18:58.
 *
 * <p>
 *     自定义长度校验器
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
public class SizeValidator implements ConstraintValidator<Size, String> {

    private int min;

    private int max;

    private TextTypeEnum textType;

    private String charset;

    @Override
    public void initialize(final Size constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
        this.textType = constraintAnnotation.type();
        this.charset = constraintAnnotation.charset();
    }

    @Override
    public boolean isValid(final String param, final ConstraintValidatorContext constraintValidatorContext) {
        if (Objects.isNull(param)) {
            return true;
        }
        constraintValidatorContext.disableDefaultConstraintViolation();
        switch (textType) {
            // 文本长度
            case TEXT -> {
                if (param.length() < min || param.length() > max) {
                    constraintValidatorContext.buildConstraintViolationWithTemplate(
                            I18nUtils.getMessage(I18nConstant.PARAMETER_LENGTH_ERROR, param, min + "-" + max + "(char)", param.length())
                    ).addConstraintViolation();
                    return false;
                }
                return true;
            }
            // 字节长度
            case BYTE -> {
                try {
                    int lengthByte = param.getBytes(charset).length;
                    if (lengthByte < min || lengthByte > max) {
                        constraintValidatorContext.buildConstraintViolationWithTemplate(
                                I18nUtils.getMessage(I18nConstant.PARAMETER_LENGTH_ERROR, param, min + "-" + max + "(byte)", lengthByte)
                        ).addConstraintViolation();
                        return false;
                    }
                    return true;
                } catch (UnsupportedEncodingException e) {
                    throw new ValidateException(I18nUtils.getMessage(I18nConstant.PARAM_IS_INVALID));
                }
            }
            default -> throw new ValidateException(I18nUtils.getMessage(I18nConstant.PARAM_IS_INVALID));
        }
    }
}
