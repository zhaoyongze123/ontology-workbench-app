package com.devin.ontology.strategy.storage;

import com.devin.ontology.domain.dto.NodeDTO;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 2025/7/9 15:26.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
public class CypherUtils {

    /**
     * 生成标签.
     *
     * @param labels 标签列表
     * @return 标签字符串
     */
    public static String generateLabelStr(final List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        return labels.stream().map(CypherUtils::quoteIdentifier)
                .collect(Collectors.joining(":", ":", ""));
    }

    /**
     * 属性值转换.
     *
     * @param properties 属性列表
     * @return 属性字符串
     */
    public static String convertPropertyVal(final Map<String, Object> properties) {
        if (properties == null) {
            throw new IllegalArgumentException("属性 map 不能为空");
        }
        if (properties.isEmpty()) {
            return "{}";
        }

        return properties.entrySet().stream()
                .map(entry -> quoteIdentifier(entry.getKey()) + ": " + formatValue(entry.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * 类型转换.
     *
     * @param value 属性值
     * @return 属性值
     */
    public static String formatValue(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) return quoteString(string);
        if (value instanceof Boolean bool) return bool ? "true" : "false";
        if (value instanceof Number number) return formatNumber(number);
        if (value instanceof Map<?, ?> map) return convertPropertyMap(map);
        if (value instanceof Collection<?> collection) return convertPropertyCollection(collection);
        if (value.getClass().isArray()) return convertArray(value);
        throw new IllegalArgumentException("不支持的 Cypher 属性值类型: " + value.getClass().getName());
    }

    private static String quoteString(final String value) {
        StringBuilder result = new StringBuilder("'");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '\'' -> result.append("\\'");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                default -> {
                    if (Character.isISOControl(character)) {
                        result.append(String.format("\\u%04X", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('\'').toString();
    }

    private static String formatNumber(final Number number) {
        if (!(number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long
                || number instanceof Float || number instanceof Double || number instanceof BigInteger
                || number instanceof BigDecimal)) {
            throw new IllegalArgumentException("不支持的 Cypher 数字类型");
        }
        if (number instanceof Double doubleValue && !Double.isFinite(doubleValue)) {
            throw new IllegalArgumentException("Cypher 数字属性必须是有限值");
        }
        if (number instanceof Float floatValue && !Float.isFinite(floatValue)) {
            throw new IllegalArgumentException("Cypher 数字属性必须是有限值");
        }
        return number.toString();
    }

    /**
     * 属性集合转换.
     *
     * @param map 属性集合
     * @return 属性集合字符串
     */
    private static String convertPropertyMap(final Map<?, ?> map) {
        if (map.isEmpty()) {
            return "{}";
        }
        return map.entrySet().stream()
                .map(entry -> {
                    if (!(entry.getKey() instanceof String key)) {
                        throw new IllegalArgumentException("Cypher map key 必须是字符串");
                    }
                    return quoteIdentifier(key) + ": " + formatValue(entry.getValue());
                })
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * 属性集合转换.
     *
     * @param collection 属性集合
     * @return 属性集合字符串
     */
    private static String convertPropertyCollection(final Collection<?> collection) {
        if (collection.isEmpty()) {
            return "[]";
        }
        return collection.stream()
                .map(CypherUtils::formatValue)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String convertArray(final Object array) {
        int length = Array.getLength(array);
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < length; i++) {
            if (i > 0) result.append(", ");
            result.append(formatValue(Array.get(array, i)));
        }
        return result.append(']').toString();
    }

    /** 单个 Cypher symbolic identifier；反引号通过成对反引号编码。 */
    public static String quoteIdentifier(final String identifier) {
        if (identifier == null || identifier.isEmpty() || identifier.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Cypher 标识符不能为空或包含 NUL");
        }
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * 生成节点的标签签名，用于分组.
     *
     * @param nodeDTO 节点DTO
     * @return 标签签名
     */
    public static String getLabelSignature(final NodeDTO nodeDTO) {
        List<String> labels = nodeDTO.getLabels();
        if (labels == null || labels.isEmpty()) {
            return "NO_LABELS";
        }
        return labels.stream().sorted().collect(Collectors.joining(","));
    }

    /**
     * 生成创建节点的Cypher语句.
     *
     * @param nodeDTO 节点DTO
     * @return Cypher语句
     */
    public static String generateCreateNodeCypher(final NodeDTO nodeDTO) {
        String labelsCypherPart = generateLabelStr(nodeDTO.getLabels());
        return "CREATE (n" + labelsCypherPart + ") "
                + "SET n = " + convertPropertyVal(nodeDTO.getProperties())
                + ", n._id = " + requireId(nodeDTO.getId());
    }

    /**
     * 批量生成创建节点的Cypher语句.
     *
     * @param nodeDTOList 节点DTO列表
     * @return Cypher语句
     */
    public static String generateCreateNodeBatchCypher(final List<NodeDTO> nodeDTOList) {
        if (nodeDTOList == null || nodeDTOList.isEmpty()) {
            return "";
        }

        AtomicInteger counter = new AtomicInteger(0);
        return nodeDTOList.stream().map(nodeDTO -> {
            int index = counter.getAndIncrement();
            String alias = "n" + index;
            return "CREATE (" + alias + generateLabelStr(nodeDTO.getLabels()) + ") SET "
                    + alias + " = " + convertPropertyVal(nodeDTO.getProperties())
                    + ", " + alias + "._id = " + requireId(nodeDTO.getId());
        }).collect(Collectors.joining("\n"));
    }

    /**
     * 生成创建关系的Cypher语句.
     *
     * @param name 关系名称
     * @return Cypher语句
     */
    public static String generateCreateRelationshipCypher(
            final String name, final Long id, final Long sourceId, final Long targetId,
            final Map<String, Object> properties) {
        return "MATCH (sourceNode), (targetNode) WHERE sourceNode._id = " + requireId(sourceId)
                + " AND targetNode._id = " + requireId(targetId) + " "
                + "CREATE (sourceNode)-[r:" + quoteIdentifier(name) + "]->(targetNode) "
                + "SET r = " + convertPropertyVal(properties) + " "
                + "SET r._id = " + requireId(id) + " "
                + "RETURN r._id as id ";
    }

    /**
     * Neo4j 策略兼容模板：属性值仍通过 Bolt 参数绑定，仅关系类型需安全编码为 Cypher 标识符。
     * AGE 路径使用上面的完整值序列化重载，不依赖此模板。
     */
    public static String generateCreateRelationshipCypher(final String name) {
        return "MATCH (sourceNode), (targetNode) WHERE sourceNode._id = $sourceId "
                + "AND targetNode._id = $targetId "
                + "CREATE (sourceNode)-[r:" + quoteIdentifier(name) + "]->(targetNode) "
                + "SET r = $properties SET r._id = $id RETURN r._id as id";
    }

    private static long requireId(final Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Cypher ID 必须为正数");
        return id;
    }

}
