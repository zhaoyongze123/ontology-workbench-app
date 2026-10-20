package com.devin.ontology.common.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Bean属性拷贝工具.
 *
 * <p>功能特性：
 * <ul>
 *   <li>基础类型拷贝</li>
 *   <li>集合批量拷贝</li>
 *   <li>字段映射</li>
 *   <li>忽略字段</li>
 *   <li>自动JSON转换（String ↔ List/Map）✨新增</li>
 *   <li>自定义转换器✨新增</li>
 * </ul>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 2.0
 * @since 1.0
 */
// CHECKSTYLE:OFF
public class BeanCopyUtils {

    /**
     * 缓存类的字段信息，提高性能
     */
    private static final Map<Class<?>, Map<String, Field>> CLASS_FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * 缓存类的方法信息，提高性能
     */
    private static final Map<Class<?>, Map<String, Method>> CLASS_METHOD_CACHE = new ConcurrentHashMap<>();

    /**
     * 基础类型集合
     */
    private static final Set<Class<?>> PRIMITIVE_TYPES = new HashSet<>(Arrays.asList(
            String.class, Integer.class, Long.class, Double.class, Float.class,
            Boolean.class, Byte.class, Short.class, Character.class,
            int.class, long.class, double.class, float.class,
            boolean.class, byte.class, short.class, char.class,
            BigDecimal.class, Date.class
    ));

    /**
     * 自定义类型转换器注册表
     * key: 源类型#目标类型
     * value: 转换函数
     */
    private static final Map<String, BiFunction<Object, Type, Object>> CUSTOM_CONVERTERS = new ConcurrentHashMap<>();

    /**
     * 是否启用JSON自动转换（默认启用）
     */
    private static volatile boolean jsonAutoConvertEnabled = true;

    // ==================== 公共API ====================

    /**
     * 单个对象拷贝
     *
     * @param source 源对象
     * @param targetClass 目标类
     * @param <T> 目标类型
     * @return 目标对象
     */
    public static <T> T copy(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }

        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            copyProperties(source, target);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("Bean copy failed", e);
        }
    }

    /**
     * 集合拷贝
     *
     * @param sources 源集合
     * @param targetClass 目标类
     * @param <T> 目标类型
     * @return 目标集合
     */
    public static <T> List<T> copy(List<?> sources, Class<T> targetClass) {
        if (sources == null || sources.isEmpty()) {
            return new ArrayList<>();
        }

        return sources.stream()
                .filter(Objects::nonNull)
                .map(source -> copy(source, targetClass))
                .collect(Collectors.toList());
    }

    /**
     * 带字段映射的对象拷贝
     *
     * @param source 源对象
     * @param targetClass 目标类
     * @param fieldMapping 字段映射 (源字段名 -> 目标字段名)
     * @param <T> 目标类型
     * @return 目标对象
     */
    public static <T> T copy(Object source, Class<T> targetClass, Map<String, String> fieldMapping) {
        if (source == null) {
            return null;
        }

        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            copyProperties(source, target, fieldMapping, null);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("Bean copy failed", e);
        }
    }

    /**
     * 带忽略字段的对象拷贝
     *
     * @param source 源对象
     * @param targetClass 目标类
     * @param ignoreFields 忽略的字段名
     * @param <T> 目标类型
     * @return 目标对象
     */
    public static <T> T copy(Object source, Class<T> targetClass, String... ignoreFields) {
        if (source == null) {
            return null;
        }

        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            Set<String> ignoreSet = ignoreFields != null ? new HashSet<>(Arrays.asList(ignoreFields)) : new HashSet<>();
            copyProperties(source, target, null, ignoreSet);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("Bean copy failed", e);
        }
    }

    /**
     * 属性拷贝
     *
     * @param source 源对象
     * @param target 目标对象
     */
    public static void copyProperties(Object source, Object target) {
        copyProperties(source, target, null, null);
    }

    /**
     * 创建对象拷贝构建器
     *
     * @param source 源对象
     * @param targetClass 目标类
     * @param <T> 目标类型
     * @return 拷贝构建器
     */
    public static <T> CopyBuilder<T> from(Object source, Class<T> targetClass) {
        return new CopyBuilder<>(source, targetClass);
    }

    // ==================== 配置API ====================

    /**
     * 注册自定义类型转换器
     *
     * @param sourceType 源类型
     * @param targetType 目标类型
     * @param converter 转换函数
     */
    public static void registerConverter(Class<?> sourceType, Class<?> targetType,
                                         BiFunction<Object, Type, Object> converter) {
        String key = getConverterKey(sourceType, targetType);
        CUSTOM_CONVERTERS.put(key, converter);
    }

    /**
     * 清空自定义转换器
     */
    public static void clearCustomConverters() {
        CUSTOM_CONVERTERS.clear();
    }

    // ==================== 核心逻辑 ====================

    /**
     * 属性拷贝核心方法
     *
     * @param source 源对象
     * @param target 目标对象
     * @param fieldMapping 字段映射
     * @param ignoreFields 忽略字段
     */
    private static void copyProperties(Object source, Object target,
                                       Map<String, String> fieldMapping,
                                       Set<String> ignoreFields) {
        if (source == null || target == null) {
            return;
        }

        Class<?> sourceClass = source.getClass();
        Class<?> targetClass = target.getClass();

        Map<String, Field> sourceFields = getFieldMap(sourceClass);
        Map<String, Field> targetFields = getFieldMap(targetClass);

        for (Map.Entry<String, Field> sourceEntry : sourceFields.entrySet()) {
            String sourceFieldName = sourceEntry.getKey();
            Field sourceField = sourceEntry.getValue();

            // 检查是否忽略此字段
            if (ignoreFields != null && ignoreFields.contains(sourceFieldName)) {
                continue;
            }

            // 获取目标字段名
            String targetFieldName = sourceFieldName;
            if (fieldMapping != null && fieldMapping.containsKey(sourceFieldName)) {
                targetFieldName = fieldMapping.get(sourceFieldName);
            }

            Field targetField = targetFields.get(targetFieldName);
            if (targetField == null) {
                continue;
            }

            try {
                sourceField.setAccessible(true);
                targetField.setAccessible(true);

                Object sourceValue = sourceField.get(source);
                if (sourceValue == null) {
                    continue;
                }

                Object targetValue = convertValue(sourceValue, sourceField, targetField);
                if (targetValue != null) {
                    targetField.set(target, targetValue);
                }

            } catch (Exception e) {
                // 忽略无法拷贝的字段，继续拷贝其他字段
            }
        }
    }

    /**
     * 值类型转换（增强版）
     *
     * @param value 值
     * @param sourceField 源字段
     * @param targetField 目标字段
     * @return 转换后的值
     */
    private static Object convertValue(Object value, Field sourceField, Field targetField) {
        if (value == null) {
            return null;
        }

        Class<?> sourceType = sourceField.getType();
        Class<?> targetType = targetField.getType();
        Type targetGenericType = targetField.getGenericType();

        // 1. 优先使用自定义转换器
        String converterKey = getConverterKey(sourceType, targetType);
        BiFunction<Object, Type, Object> customConverter = CUSTOM_CONVERTERS.get(converterKey);
        if (customConverter != null) {
            return customConverter.apply(value, targetGenericType);
        }

        // 2. 类型相同或兼容，直接返回
        if (targetType.isAssignableFrom(sourceType)) {
            return value;
        }

        // 3. JSON自动转换（核心增强）✨
        if (jsonAutoConvertEnabled) {
            Object jsonConverted = tryJsonConvert(value, sourceType, targetType, targetGenericType);
            if (jsonConverted != null) {
                return jsonConverted;
            }
        }

        // 4. 基础类型转换
        if (PRIMITIVE_TYPES.contains(sourceType) && PRIMITIVE_TYPES.contains(targetType)) {
            return convertPrimitiveType(value, targetType);
        }

        // 5. 复杂对象拷贝
        if (!PRIMITIVE_TYPES.contains(targetType) && !targetType.isInterface() && !targetType.isArray()) {
            return copy(value, targetType);
        }

        return value;
    }

    /**
     * 尝试JSON转换
     *
     * <p>支持的转换类型：
     * <ul>
     *   <li>String → List/Map (JSON反序列化)</li>
     *   <li>List/Map → String (JSON序列化)</li>
     *   <li>JSONObject → Map</li>
     *   <li>JSONArray → List</li>
     * </ul>
     *
     * @param value 值
     * @param sourceType 源类型
     * @param targetType 目标类型
     * @param targetGenericType 目标泛型类型
     * @return 转换后的值，失败返回null
     */
    private static Object tryJsonConvert(Object value, Class<?> sourceType,
                                         Class<?> targetType, Type targetGenericType) {
        try {
            // String → List/Map (JSON反序列化)
            if (sourceType == String.class) {
                String jsonStr = (String) value;

                // 检查是否是JSON格式
                if (!isJsonString(jsonStr)) {
                    return null;
                }

                // String → List
                if (List.class.isAssignableFrom(targetType)) {
                    if (targetGenericType instanceof ParameterizedType) {
                        ParameterizedType pt = (ParameterizedType) targetGenericType;
                        Type actualType = pt.getActualTypeArguments()[0];
                        if (actualType instanceof Class) {
                            return JSON.parseArray(jsonStr, (Class<?>) actualType);
                        }
                    }
                    return JSON.parseArray(jsonStr);
                }

                // String → Map
                if (Map.class.isAssignableFrom(targetType)) {
                    return JSON.parseObject(jsonStr, Map.class);
                }

                // String → 具体对象
                if (!PRIMITIVE_TYPES.contains(targetType)) {
                    return JSON.parseObject(jsonStr, targetType);
                }
            }

            // List/Map → String (JSON序列化)
            if (targetType == String.class) {
                if (value instanceof List || value instanceof Map) {
                    return JSON.toJSONString(value);
                }
            }

            // JSONObject → Map
            if (value instanceof JSONObject && Map.class.isAssignableFrom(targetType)) {
                return ((JSONObject) value).toJavaObject(Map.class);
            }

            // JSONArray → List
            if (value instanceof JSONArray && List.class.isAssignableFrom(targetType)) {
                JSONArray array = (JSONArray) value;
                if (targetGenericType instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) targetGenericType;
                    Type actualType = pt.getActualTypeArguments()[0];
                    if (actualType instanceof Class) {
                        return array.toJavaList((Class<?>) actualType);
                    }
                }
                return array.toJavaList(Object.class);
            }

        } catch (Exception e) {
            // JSON转换失败，返回null继续其他转换策略
        }

        return null;
    }

    /**
     * 检查字符串是否是JSON格式
     *
     * @param str 字符串
     * @return 是否是JSON
     */
    private static boolean isJsonString(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }

        str = str.trim();
        return (str.startsWith("{") && str.endsWith("}"))
                || (str.startsWith("[") && str.endsWith("]"));
    }

    /**
     * 基础类型转换
     *
     * @param value 值
     * @param targetType 目标类型
     * @return 转换后的值
     */
    private static Object convertPrimitiveType(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        String valueStr = value.toString();

        try {
            if (targetType == String.class) {
                return valueStr;
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(valueStr);
            } else if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(valueStr);
            } else if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(valueStr);
            } else if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(valueStr);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(valueStr);
            } else if (targetType == BigDecimal.class) {
                return new BigDecimal(valueStr);
            } else if (targetType == Date.class && value instanceof String) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                return sdf.parse(valueStr);
            }
        } catch (Exception e) {
            // 转换失败，返回原值
            return value;
        }

        return value;
    }

    /**
     * 获取类的字段映射
     *
     * @param clazz 类
     * @return 字段映射
     */
    private static Map<String, Field> getFieldMap(Class<?> clazz) {
        return CLASS_FIELD_CACHE.computeIfAbsent(clazz, k -> {
            Map<String, Field> fieldMap = new HashMap<>();
            getAllFields(clazz, fieldMap);
            return fieldMap;
        });
    }

    /**
     * 递归获取所有字段（包括父类）
     *
     * @param clazz 类
     * @param fieldMap 字段映射
     */
    private static void getAllFields(Class<?> clazz, Map<String, Field> fieldMap) {
        if (clazz == null || clazz == Object.class) {
            return;
        }

        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            // 跳过静态字段和final字段
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            fieldMap.put(field.getName(), field);
        }

        // 递归处理父类
        getAllFields(clazz.getSuperclass(), fieldMap);
    }

    /**
     * 获取转换器Key
     */
    private static String getConverterKey(Class<?> sourceType, Class<?> targetType) {
        return sourceType.getName() + "#" + targetType.getName();
    }

    // ==================== 构建器 ====================

    /**
     * 拷贝构建器，支持链式调用
     */
    public static class CopyBuilder<T> {
        private final Object source;
        private final Class<T> targetClass;
        private final Map<String, String> fieldMapping;
        private final Set<String> ignoreFields;

        public CopyBuilder(Object source, Class<T> targetClass) {
            this.source = source;
            this.targetClass = targetClass;
            this.fieldMapping = new HashMap<>();
            this.ignoreFields = new HashSet<>();
        }

        /**
         * 添加字段映射
         *
         * @param sourceField 源字段名
         * @param targetField 目标字段名
         * @return 构建器
         */
        public CopyBuilder<T> mapping(String sourceField, String targetField) {
            this.fieldMapping.put(sourceField, targetField);
            return this;
        }

        /**
         * 忽略字段
         *
         * @param fieldNames 字段名
         * @return 构建器
         */
        public CopyBuilder<T> ignore(String... fieldNames) {
            if (fieldNames != null) {
                this.ignoreFields.addAll(Arrays.asList(fieldNames));
            }
            return this;
        }

        /**
         * 执行拷贝
         *
         * @return 目标对象
         */
        public T build() {
            if (source == null) {
                return null;
            }

            try {
                T target = targetClass.getDeclaredConstructor().newInstance();
                copyProperties(source, target, fieldMapping, ignoreFields);
                return target;
            } catch (Exception e) {
                throw new RuntimeException("Bean copy failed", e);
            }
        }
    }
}
// CHECKSTYLE:ON