package com.devin.ontology.model.validate;

import com.devin.ontology.common.constant.I18nConstant;
import com.devin.ontology.common.enums.PropertyTypeEnum;
import com.devin.ontology.common.enums.RequiredEnum;
import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.common.utils.i18n.I18nUtils;
import com.devin.ontology.common.utils.r.ResultEnum;
import com.devin.ontology.model.PropertySchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 2025/11/20 19:45.
 *
 * <p>
 * JSON格式校验
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class JsonSchemaValidator {

    // CHECKSTYLE:OFF
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern VALID_PROPERTY_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$");
    // CHECKSTYLE:ON

    /**
     * 根据Schema定义（JSON字符串）校验JSON字符串.
     *
     * @param schemaJson Schema定义的JSON字符串
     * @param jsonData   待校验的JSON字符串
     * @throws ValidateException 校验失败时抛出
     */
    public static void validate(final String schemaJson, final String jsonData) {
        try {
            List<PropertySchema> schemas = parseSchemaFromJson(schemaJson);
            validatePropertyNames(schemas);
            validate(schemas, jsonData);
        } catch (ValidateException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析Schema JSON失败 exceptionType={}", e.getClass().getSimpleName());
            throw new ValidateException(I18nUtils.getMessage(I18nConstant.PARAM_PARSE_FAIL));
        }
    }

    /**
     * 校验JSON数据.
     *
     * @param schemas  属性列表
     * @param jsonData JSON数据
     * @throws ValidateException 校验失败时抛出
     */
    public static void validate(
            final List<PropertySchema> schemas,
            final String jsonData
    ) {
        try {
            validatePropertyNames(schemas);
            JsonNode dataNode = objectMapper.readTree(jsonData);
            validate(schemas, dataNode);
        } catch (ValidateException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析JSON失败 exceptionType={}", e.getClass().getSimpleName());
            throw new ValidateException(I18nUtils.getMessage(I18nConstant.PARAM_PARSE_FAIL));
        }
    }

    /**
     * 根据Schema定义校验JsonNode.
     *
     * @param schemas  属性模式列表
     * @param dataNode 待校验的JsonNode
     * @throws ValidateException 校验失败时抛出
     */
    public static void validate(
            final List<PropertySchema> schemas,
            final JsonNode dataNode
    ) {
        if (!dataNode.isObject()) {
            throw new ValidateException(I18nUtils.getMessage(I18nConstant.PARAM_PARSE_FAIL));
        }

        validatePropertyNames(schemas);

        // 校验每个schema定义的字段
        for (PropertySchema schema : schemas) {
            validateField("", dataNode, schema);
        }
    }

    /**
     * 根据Schema定义校验JSON字符串，校验通过后返回Map.
     * 注意：OBJECT类型的值会被转换为JSON字符串
     *
     * @param schemaJson 属性模式JSON字符串
     * @param jsonData   待校验的JSON字符串
     * @return Map 包含数据Map，OBJECT类型的值为JSON字符串
     * @throws ValidateException 校验失败时抛出
     */
    public static Map<String, Object> validateAndGetMap(
            final String schemaJson,
            final String jsonData
    ) {
        try {
            List<PropertySchema> schemas = parseSchemaFromJson(schemaJson);
            validatePropertyNames(schemas);
            return validateAndGetMap(schemas, jsonData);
        } catch (ValidateException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析Schema JSON失败 exceptionType={}", e.getClass().getSimpleName());
            throw new ValidateException(I18nUtils.getMessage(I18nConstant.PARAM_PARSE_FAIL));
        }
    }

    /**
     * 根据Schema定义校验JSON字符串，校验通过后返回Map.
     * 注意：OBJECT类型的值会被转换为JSON字符串
     *
     * @param schemas  属性模式列表
     * @param jsonData 待校验的JSON字符串
     * @return Map 包含数据Map，OBJECT类型的值为JSON字符串
     * @throws ValidateException 校验失败时抛出
     */
    public static Map<String, Object> validateAndGetMap(
            final List<PropertySchema> schemas,
            final String jsonData
    ) {
        try {
            validatePropertyNames(schemas);
            JsonNode dataNode = objectMapper.readTree(jsonData);
            // 进行校验
            validate(schemas, dataNode);

            // 校验通过，转换为Map（OBJECT类型转为JSON字符串）
            return convertToMap(schemas, dataNode);
        } catch (ValidateException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析JSON失败 exceptionType={}", e.getClass().getSimpleName());
            throw new ValidateException(I18nUtils.getMessage(I18nConstant.PARAM_PARSE_FAIL));
        }
    }

    /**
     * 校验属性名称是否合法（只允许字母、数字、下划线和中文）.
     *
     * @param schemas 属性模式列表
     * @throws ValidateException 如果属性名称包含特殊字符
     */
    private static void validatePropertyNames(final List<PropertySchema> schemas) {
        for (PropertySchema schema : schemas) {
            String propertyName = schema.getName();
            if (propertyName == null || propertyName.isEmpty()) {
                throw new ValidateException(
                        ResultEnum.VALIDATE_ERROR.getCode(),
                        "属性名称不能为空");
            }
            if (!VALID_PROPERTY_NAME_PATTERN.matcher(propertyName).matches()) {
                throw new ValidateException(
                        ResultEnum.VALIDATE_ERROR.getCode(),
                        String.format("属性名称 '%s' 包含特殊字符，只允许字母、数字、下划线和中文", propertyName));
            }
        }
    }

    /**
     * 将JsonNode转换为Map，OBJECT类型的字段转换为JSON字符串.
     *
     * @param schemas  属性模式列表
     * @param dataNode 数据节点
     * @return Map
     * @throws Exception 转换异常
     */
    private static Map<String, Object> convertToMap(
            final List<PropertySchema> schemas,
            final JsonNode dataNode
    ) throws Exception {
        Map<String, Object> result = new HashMap<>();

        for (PropertySchema schema : schemas) {
            String fieldName = schema.getName();
            JsonNode fieldValue = dataNode.get(fieldName);

            // 如果字段不存在，跳过
            if (fieldValue == null || fieldValue.isNull()) {
                continue;
            }

            // 如果是空字符串且不是STRING类型，跳过（视为空值）
            if (isEmptyStringForNonStringType(fieldValue, schema.getType())) {
                continue;
            }

            // 根据类型转换值
            Object value = convertValue(fieldValue, schema.getType());
            result.put(fieldName, value);
        }

        return result;
    }

    /**
     * 根据类型转换值.
     *
     * @param fieldValue JsonNode值
     * @param type       属性类型
     * @return 转换后的值
     * @throws Exception 转换异常
     */
    private static Object convertValue(final JsonNode fieldValue, final PropertyTypeEnum type) throws Exception {
        switch (type) {
            case NUMBER:
                if (fieldValue.isIntegralNumber()) {
                    return fieldValue.asLong();
                } else {
                    return fieldValue.asDouble();
                }
            case BOOLEAN:
                return fieldValue.asBoolean();
            case OBJECT:
                return objectMapper.writeValueAsString(fieldValue);
            case STRING:
            default:
                String textValue = fieldValue.asText();
                return textValue.replace("\\", "\\\\");
        }
    }

    /**
     * 校验JSON字符串是否为有效的JSON格式.
     *
     * @param jsonStr JSON字符串
     * @return 是否有效
     */
    public static boolean isValidJson(final String jsonStr) {
        try {
            objectMapper.readTree(jsonStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验单个字段.
     *
     * @param parentPath 父路径（用于错误提示）
     * @param dataNode   数据节点
     * @param schema     属性模式
     * @throws ValidateException 校验失败时抛出
     */
    private static void validateField(
            final String parentPath,
            final JsonNode dataNode,
            final PropertySchema schema
    ) {
        String fieldName = schema.getName();
        String fullPath = parentPath.isEmpty() ? fieldName : parentPath + "." + fieldName;
        JsonNode fieldValue = dataNode.get(fieldName);

        // 检查必填字段
        if (RequiredEnum.REQUIRED.equals(schema.getRequired())) {
            if (fieldValue == null || fieldValue.isNull()) {
                throw new ValidateException(
                        ResultEnum.VALIDATE_ERROR.getCode(),
                        String.format("字段 '%s' 是必填项，但未提供", fullPath));
            }
            // 必填字段不允许为空字符串（对于非STRING类型）
            if (isEmptyStringForNonStringType(fieldValue, schema.getType())) {
                throw new ValidateException(
                        ResultEnum.VALIDATE_ERROR.getCode(),
                        String.format("字段 '%s' 是必填项，不能为空值", fullPath));
            }
        }

        // 如果字段不存在且非必填，跳过
        if (fieldValue == null || fieldValue.isNull()) {
            return;
        }

        // 如果是非必填字段且为空字符串（针对NUMBER、BOOLEAN、OBJECT类型），跳过校验
        if (isEmptyStringForNonStringType(fieldValue, schema.getType())) {
            return;
        }

        // 校验字段类型
        validateFieldType(fullPath, fieldValue, schema);
    }

    /**
     * 判断是否为非STRING类型的空字符串.
     *
     * @param fieldValue 字段值
     * @param type       属性类型
     * @return 是否为空字符串且类型不是STRING
     */
    private static boolean isEmptyStringForNonStringType(
            final JsonNode fieldValue,
            final PropertyTypeEnum type
    ) {
        // 只有当字段是文本类型且值为空字符串，并且定义的类型不是STRING时，才返回true
        return fieldValue.isTextual()
                && fieldValue.asText().isEmpty()
                && type != PropertyTypeEnum.STRING;
    }

    /**
     * 校验字段类型.
     *
     * @param fullPath   字段完整路径
     * @param fieldValue 字段值
     * @param schema     属性模式
     * @throws ValidateException 校验失败时抛出
     */
    private static void validateFieldType(
            final String fullPath,
            final JsonNode fieldValue,
            final PropertySchema schema
    ) {
        PropertyTypeEnum type = schema.getType();

        switch (type) {
            case STRING:
                if (!fieldValue.isTextual()) {
                    throw new ValidateException(
                            ResultEnum.VALIDATE_ERROR.getCode(),
                            String.format("%s属性必须为字符串，当前类型: %s",
                                    fullPath, getActualType(fieldValue)));
                }
                break;

            case NUMBER:
                if (!fieldValue.isNumber()) {
                    throw new ValidateException(
                            ResultEnum.VALIDATE_ERROR.getCode(),
                            String.format("%s属性必须为数字，当前类型: %s",
                                    fullPath, getActualType(fieldValue)));
                }
                break;

            case BOOLEAN:
                if (!fieldValue.isBoolean()) {
                    throw new ValidateException(
                            ResultEnum.VALIDATE_ERROR.getCode(),
                            String.format("%s属性必须为布尔值，当前类型: %s",
                                    fullPath, getActualType(fieldValue)));
                }
                break;

            case OBJECT:
                // 数组也是对象类型，允许对象或数组
                if (!fieldValue.isObject() && !fieldValue.isArray()) {
                    throw new ValidateException(
                            ResultEnum.VALIDATE_ERROR.getCode(),
                            String.format("%s属性必须为对象或数组，当前类型: %s",
                                    fullPath, getActualType(fieldValue)));
                }
                break;

            default:
                throw new ValidateException(
                        ResultEnum.VALIDATE_ERROR.getCode(),
                        String.format("未知的类型定义: %s", type));
        }
    }

    /**
     * 从JSON字符串解析PropertySchema列表.
     *
     * @param schemaJson Schema JSON字符串
     * @return PropertySchema列表
     * @throws Exception 解析异常
     */
    public static List<PropertySchema> parseSchemaFromJson(final String schemaJson) throws Exception {
        return objectMapper.readValue(schemaJson,
                objectMapper.getTypeFactory().constructCollectionType(
                        List.class, PropertySchema.class));
    }

    /**
     * 获取实际的JSON类型.
     *
     * @param node JsonNode节点
     * @return 类型描述
     */
    private static String getActualType(final JsonNode node) {
        if (node.isTextual()) {
            return "字符串";
        }
        if (node.isNumber()) {
            return "数字";
        }
        if (node.isBoolean()) {
            return "布尔值";
        }
        if (node.isArray()) {
            return "数组";
        }
        if (node.isObject()) {
            return "对象";
        }
        if (node.isNull()) {
            return "null";
        }
        return "未知类型";
    }
}
