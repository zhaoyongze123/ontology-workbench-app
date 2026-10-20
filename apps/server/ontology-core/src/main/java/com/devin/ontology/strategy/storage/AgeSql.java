package com.devin.ontology.strategy.storage;

import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.exception.ValidateException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Deque;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

/** AGE/PostgreSQL 边界编码与 SQL 执行工具。 */
public final class AgeSql {
    private AgeSql() { }

    /** 只从固定图类型前缀和正数 ID 生成 AGE graph name。 */
    public static String graphName(final String prefix, final Long id) {
        if (prefix == null || id == null || id <= 0) {
            throw new IllegalArgumentException("AGE graph 必须使用有效图类型和正数 ID");
        }
        final GraphTypeEnum graphType;
        try {
            graphType = GraphTypeEnum.valueOf(prefix.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("无效的 AGE graph 类型", exception);
        }
        return graphType.getDbPrefix() + id;
    }

    /** PostgreSQL E-string literal；双写引号和反斜杠，并拒绝 PostgreSQL 不支持的 NUL。 */
    public static String sqlTextLiteral(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("SQL 文本字面量不能为空");
        }
        StringBuilder literal = new StringBuilder("E'");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\0') {
                throw new IllegalArgumentException("SQL 文本不能包含 NUL");
            }
            switch (character) {
                case '\'' -> literal.append("''");
                case '\\' -> literal.append("\\\\");
                case '\n' -> literal.append("\\n");
                case '\r' -> literal.append("\\r");
                case '\t' -> literal.append("\\t");
                case '\b' -> literal.append("\\b");
                case '\f' -> literal.append("\\f");
                default -> {
                    if (character < 0x20 || character == 0x7f) {
                        literal.append("\\x").append(String.format("%02X", (int) character));
                    } else {
                        literal.append(character);
                    }
                }
            }
        }
        return literal.append('\'').toString();
    }

    /** 对外稳定的文本字面量 helper 名称，供只读查询模块复用。 */
    public static String literal(final String value) {
        return sqlTextLiteral(value);
    }

    /** SQL 标识符独立编码；数据类型由调用点使用可信常量提供。 */
    public static String sqlIdentifier(final String value) {
        if (value == null || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("SQL 标识符不能为空或包含 NUL");
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** 对外稳定的 SQL 标识符 helper 名称。 */
    public static String identifier(final String value) {
        return sqlIdentifier(value);
    }

    /** 使用 PostgreSQL 参数执行普通 SQL 函数，不把值拼进 SQL 文本。 */
    public static void executePrepared(final JdbcTemplate jdbcTemplate, final String sql, final Object... values) {
        jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) statement -> {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.execute();
            return null;
        });
    }

    /** 为 AGE 的 cstring 查询常量生成安全 SQL，并独立编码返回列标识符。 */
    public static String cypherSql(final String graph, final String cypher, final List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("AGE 返回列不能为空");
        }
        String definitions = columns.stream()
                .map(column -> sqlIdentifier(column) + " ag_catalog.agtype")
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
        return cypher(graph, cypher, definitions);
    }

    /**
     * AGE 查询使用经过正文碰撞检查的 dollar-quote；列定义来自常量或 identifier() 编码。
     */
    public static String cypher(
            final String graph, final String text, final String trustedColumnDefinitions) {
        validateGraphName(graph);
        if (text == null) {
            throw new IllegalArgumentException("Cypher 文本不能为空");
        }
        if (trustedColumnDefinitions == null || trustedColumnDefinitions.isBlank()) {
            throw new IllegalArgumentException("AGE 返回列定义不能为空");
        }
        return "SELECT * FROM ag_catalog.cypher(" + sqlTextLiteral(graph) + ", "
                + cypherLiteral(text) + ") AS (" + trustedColumnDefinitions + ")";
    }

    /**
     * AGE 的解析器要求 query_string 使用 dollar-quote，普通 E-string 会被拒绝。
     * 分隔符必须确认不在正文中出现；安全性来自完整碰撞检查，而不是依赖散列不可预测。
     */
    public static String cypherLiteral(final String text) {
        if (text == null || text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Cypher 文本不能为空或包含 NUL");
        }
        String suffix = Integer.toUnsignedString(text.hashCode(), 36);
        String delimiter = "$ontology_" + suffix + "$";
        int attempt = 0;
        while (text.contains(delimiter)) {
            delimiter = "$ontology_" + suffix + "_" + (++attempt) + "$";
        }
        return delimiter + text + delimiter;
    }

    private static void validateGraphName(final String graph) {
        if (graph == null || !graph.matches("(?:ontology|entity)[1-9][0-9]*")) {
            throw new IllegalArgumentException("AGE graph 名称必须由固定类型前缀和正数 ID 组成");
        }
        try {
            Long.parseLong(graph.replaceFirst("^(?:ontology|entity)", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("AGE graph ID 超出范围", exception);
        }
    }

    /** 仅为已用 Long 类型承载的图业务 ID 生成 Cypher 数值字面量。 */
    public static long cypherInteger(final Long value) {
        if (value == null || value <= 0) throw new IllegalArgumentException("Cypher ID 必须为正数");
        return value;
    }

    /** 安全生成 Long ID 列表的 Cypher 数组。 */
    public static String cypherIntegerList(final List<Long> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("Cypher ID 列表不能为空");
        return values.stream().map(AgeSql::cypherInteger).map(String::valueOf)
                .reduce((left, right) -> left + ", " + right)
                .map(ids -> "[" + ids + "]")
                .orElseThrow();
    }

    /**
     * 对 RETURN 投影做词法级切分，忽略字符串、注释、转义标识符及嵌套表达式中的逗号/关键字。
     * 无法完整扫描或缺少投影时拒绝，避免把不完整解析结果拿去构造 SQL record 描述符。
     */
    public static List<String> parseReturnColumns(final String cypher) {
        List<Token> tokens = tokenize(cypher);
        int returnToken = -1;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.depth() == 0 && token.word("UNION")) {
                throw new ValidateException("Cypher UNION 必须由调用方显式处理返回列");
            }
            if (token.depth() == 0 && token.word("RETURN")) {
                if (returnToken >= 0) {
                    throw new ValidateException("Cypher 必须只有一个顶层 RETURN 投影");
                }
                returnToken = i;
            }
        }
        if (returnToken < 0) {
            throw new ValidateException("Cypher 必须包含顶层 RETURN 投影");
        }

        int start = tokens.get(returnToken).end();
        if (returnToken + 1 < tokens.size() && tokens.get(returnToken + 1).depth() == 0
                && tokens.get(returnToken + 1).word("DISTINCT")) {
            start = tokens.get(returnToken + 1).end();
        }
        int end = cypher.length();
        for (int i = returnToken + 1; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.depth() != 0) continue;
            if (token.word("SKIP") || token.word("LIMIT")) {
                end = token.start();
                break;
            }
            if (token.word("ORDER") && i + 1 < tokens.size() && tokens.get(i + 1).depth() == 0
                    && tokens.get(i + 1).word("BY")) {
                end = token.start();
                break;
            }
        }
        String projection = cypher.substring(start, end).trim();
        if (projection.isEmpty()) {
            throw new ValidateException("Cypher RETURN 投影不能为空");
        }
        List<String> items = splitTopLevel(projection, ',');
        List<String> result = new ArrayList<>(items.size());
        for (String item : items) {
            String normalized = item.trim();
            if (normalized.isEmpty()) throw new ValidateException("Cypher RETURN 投影无效");
            String alias = findAlias(normalized);
            result.add(alias == null ? defaultColumnName(normalized) : decodeIdentifier(alias));
        }
        return List.copyOf(result);
    }

    private static String defaultColumnName(final String expression) {
        String value = expression.trim();
        if (value.startsWith("*") && value.equals("*")) {
            throw new ValidateException("RETURN * 不受支持；请显式列出返回项");
        }
        List<String> path = splitTopLevel(value, '.');
        return path.size() > 1 ? decodeIdentifier(path.get(path.size() - 1).trim()) : value;
    }

    private static String findAlias(final String expression) {
        List<Token> tokens = tokenize(expression);
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.depth() == 0 && token.word("AS")) {
                String alias = expression.substring(token.end()).trim();
                if (alias.isEmpty() || !isSingleIdentifier(alias)) {
                    throw new ValidateException("Cypher AS 别名必须是单个标识符");
                }
                return alias;
            }
        }
        return null;
    }

    private static boolean isSingleIdentifier(final String value) {
        if (value.length() >= 2 && value.charAt(0) == '`' && value.charAt(value.length() - 1) == '`') {
            return true;
        }
        return value.matches("[\\p{L}_][\\p{L}\\p{N}_]*");
    }

    private static String decodeIdentifier(final String value) {
        String name = value.trim();
        if (name.length() >= 2 && name.charAt(0) == '`' && name.charAt(name.length() - 1) == '`') {
            return name.substring(1, name.length() - 1).replace("``", "`");
        }
        if (!isSingleIdentifier(name)) {
            throw new ValidateException("Cypher 输出列名必须是简单标识符或显式别名");
        }
        return name;
    }

    private static List<String> splitTopLevel(final String source, final char delimiter) {
        List<String> parts = new ArrayList<>();
        List<Token> tokens = tokenize(source);
        int start = 0;
        for (Token token : tokens) {
            if (token.depth() == 0 && token.symbol() == delimiter) {
                parts.add(source.substring(start, token.start()));
                start = token.end();
            }
        }
        parts.add(source.substring(start));
        return parts;
    }

    private static List<Token> tokenize(final String source) {
        if (source == null || source.isBlank()) throw new ValidateException("Cypher 不能为空");
        List<Token> tokens = new ArrayList<>();
        Deque<Character> delimiters = new ArrayDeque<>();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == ';') throw new ValidateException("不支持多语句 Cypher");
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                i += 2;
                while (i < source.length() && source.charAt(i) != '\n' && source.charAt(i) != '\r') i++;
                continue;
            }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                i = skipBlockComment(source, i);
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                i = skipQuoted(source, i, c);
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                tokens.add(new Token("", i, i + 1, delimiters.size(), c));
                delimiters.push(c);
                i++;
                continue;
            }
            if (c == ')' || c == ']' || c == '}') {
                if (delimiters.isEmpty() || !matches(delimiters.pop(), c)) {
                    throw new ValidateException("Cypher 括号不匹配");
                }
                tokens.add(new Token("", i, i + 1, delimiters.size(), c));
                i++;
                continue;
            }
            int start = i;
            if (Character.isLetter(c) || c == '_') {
                i++;
                while (i < source.length() && (Character.isLetterOrDigit(source.charAt(i))
                        || source.charAt(i) == '_')) i++;
                tokens.add(new Token(source.substring(start, i), start, i, delimiters.size(), '\0'));
            } else {
                tokens.add(new Token("", i, i + 1, delimiters.size(), c));
                i++;
            }
        }
        if (!delimiters.isEmpty()) throw new ValidateException("Cypher 括号不匹配");
        return tokens;
    }

    private static boolean matches(final char opening, final char closing) {
        return (opening == '(' && closing == ')') || (opening == '[' && closing == ']')
                || (opening == '{' && closing == '}');
    }

    private static int skipQuoted(final String source, final int start, final char quote) {
        int i = start + 1;
        while (i < source.length()) {
            char c = source.charAt(i++);
            if (c == '\\') {
                if (i >= source.length()) throw new ValidateException("Cypher 转义序列未结束");
                i++;
            } else if (c == quote) {
                if (quote == '`' && i < source.length() && source.charAt(i) == '`') {
                    i++;
                } else if (quote != '`' && i < source.length() && source.charAt(i) == quote) {
                    i++;
                } else {
                    return i;
                }
            }
        }
        throw new ValidateException("Cypher 字符串或转义标识符未结束");
    }

    private static int skipBlockComment(final String source, final int start) {
        int depth = 1;
        int i = start + 2;
        while (i + 1 < source.length()) {
            if (source.charAt(i) == '/' && source.charAt(i + 1) == '*') { depth++; i += 2; }
            else if (source.charAt(i) == '*' && source.charAt(i + 1) == '/') {
                depth--;
                i += 2;
                if (depth == 0) return i;
            } else i++;
        }
        throw new ValidateException("Cypher 块注释未结束");
    }

    private record Token(String text, int start, int end, int depth, char symbol) {
        boolean word(final String word) { return text.equalsIgnoreCase(word); }
    }
}
