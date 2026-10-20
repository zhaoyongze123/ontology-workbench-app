package com.devin.ontology.query;

import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.strategy.storage.AgeSql;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 只接受受限的 Cypher 读表达式；字符串、标识符和注释按词法状态逐字符扫描。 */
@Component
public final class CypherReadOnlyValidator {
    public static final int MAX_QUERY_BYTES = 16 * 1024;
    public static final int MAX_COLUMNS = 64;

    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "CREATE", "MERGE", "SET", "DELETE", "REMOVE", "DROP", "LOAD", "CALL", "COPY", "UNION",
            "FOREACH", "USE", "ALTER", "GRANT", "DENY", "REVOKE", "START", "COMMIT", "ROLLBACK",
            "TRUNCATE", "INSERT", "UPDATE", "UPSERT", "EXECUTE", "DO", "TERMINATE", "SHOW", "EXPLAIN",
            "PROFILE", "CONSTRAINT", "INDEX", "RENAME", "VACUUM", "ANALYZE");

    private static final Set<String> GROUPING_WORDS = Set.of(
            "MATCH", "WHERE", "WITH", "RETURN", "AND", "OR", "XOR", "NOT", "IN", "IS",
            "BY", "SKIP", "LIMIT", "UNWIND", "YIELD");

    private static final Set<String> SAFE_FUNCTIONS = Set.of(
            "abs", "acos", "asin", "atan", "atan2", "ceil", "ceiling", "cos", "cot", "degrees", "e",
            "exp", "floor", "haversin", "log", "log10", "pi", "radians", "rand", "round", "sign",
            "sin", "sqrt", "tan", "timestamp", "toboolean", "tobool", "tofloat", "tointeger", "toint",
            "tostring", "size", "length", "left", "right", "substring", "trim", "ltrim", "rtrim",
            "replace", "split", "reverse", "lower", "tolower", "upper", "toupper", "coalesce", "head",
            "last", "tail", "range", "keys", "labels", "type", "id", "elementid", "properties", "nodes",
            "relationships", "startnode", "endnode", "point", "distance", "count", "sum", "avg", "min",
            "max", "collect", "stdev", "stdevp", "percentiledisc", "percentilecont", "all", "any", "none",
            "single", "reduce", "exists");

    public List<String> validateAndGetColumns(final String cypher) {
        if (cypher == null || cypher.isBlank()) {
            throw invalid("Cypher 不能为空");
        }
        if (cypher.length() > MAX_QUERY_BYTES
                || cypher.getBytes(StandardCharsets.UTF_8).length > MAX_QUERY_BYTES) {
            throw invalid("Cypher 超过 16 KiB 限制");
        }
        for (int i = 0; i < cypher.length(); i++) {
            char c = cypher.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                throw invalid("Cypher 包含控制字符");
            }
        }
        List<Token> tokens = scan(cypher);
        rejectUnsafeTokens(tokens);

        List<String> columns = AgeSql.parseReturnColumns(cypher);
        if (columns.size() > MAX_COLUMNS) {
            throw invalid("Cypher 最多支持 64 个返回列");
        }
        Set<String> aliases = new HashSet<>();
        for (String column : columns) {
            if (!aliases.add(column.toLowerCase(Locale.ROOT))) {
                throw invalid("Cypher 返回列别名不能重复");
            }
        }
        return columns;
    }

    private List<Token> scan(final String source) {
        List<Token> tokens = new ArrayList<>();
        Deque<Character> delimiters = new ArrayDeque<>();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                throw invalid("Cypher 包含控制字符");
            }
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '/' && has(source, i + 1, '/')) {
                i = skipLineComment(source, i + 2);
                continue;
            }
            if (c == '/' && has(source, i + 1, '*')) {
                i = skipBlockComment(source, i);
                continue;
            }
            if (c == ';') {
                throw invalid("不支持多语句 Cypher");
            }
            if (c == '\\' || c == '$') {
                // AGE 的 cstring 调用不支持外部参数；也阻止反斜杠 Unicode 转义绕过函数名检查。
                throw invalid("Cypher 不支持转义标识符或外部参数");
            }
            if (c == '\'' || c == '"') {
                int end = skipString(source, i, c);
                tokens.add(new Token(TokenType.STRING, source.substring(i, end), i));
                i = end;
                continue;
            }
            if (c == '`') {
                int end = skipBacktickIdentifier(source, i);
                tokens.add(new Token(TokenType.QUOTED_IDENTIFIER, source.substring(i, end), i));
                i = end;
                continue;
            }
            if (isIdentifierStart(c)) {
                int start = i++;
                while (i < source.length() && isIdentifierPart(source.charAt(i))) i++;
                tokens.add(new Token(TokenType.WORD, source.substring(start, i), start));
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                if (delimiters.size() >= 128) throw invalid("Cypher 嵌套层数超过限制");
                delimiters.push(c);
                tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c), i++));
                continue;
            }
            if (c == ')' || c == ']' || c == '}') {
                if (delimiters.isEmpty() || !matches(delimiters.pop(), c)) {
                    throw invalid("Cypher 括号不匹配");
                }
                tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c), i++));
                continue;
            }
            tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c), i++));
        }
        if (!delimiters.isEmpty()) throw invalid("Cypher 括号未闭合");
        return tokens;
    }

    private void rejectUnsafeTokens(final List<Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.type() == TokenType.WORD) {
                String word = token.text().toUpperCase(Locale.ROOT);
                if (FORBIDDEN_WORDS.contains(word)) {
                    throw invalid("Cypher 包含禁止的写入或管理关键字: " + word);
                }
                if (("AG_CATALOG".equals(word) || "PG_CATALOG".equals(word))
                        && isSymbol(tokens, i + 1, ".")) {
                    throw invalid("不支持 PostgreSQL schema-qualified 函数");
                }
            }
            if (token.type() == TokenType.SYMBOL && "(".equals(token.text()) && i > 0) {
                Token name = tokens.get(i - 1);
                if (name.type() == TokenType.QUOTED_IDENTIFIER) {
                    throw invalid("不支持带引号的函数名");
                }
                if (name.type() == TokenType.WORD) {
                    String function = name.text().toLowerCase(Locale.ROOT);
                    if (GROUPING_WORDS.contains(name.text().toUpperCase(Locale.ROOT))) continue;
                    if (isSymbol(tokens, i - 2, ".")) {
                        throw invalid("不支持 schema-qualified 函数");
                    }
                    if (!isAsciiFunctionName(name.text()) || !SAFE_FUNCTIONS.contains(function)) {
                        throw invalid("Cypher 函数不在只读白名单中");
                    }
                }
            }
        }
    }

    private boolean isAsciiFunctionName(final String value) {
        return value.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private boolean isSymbol(final List<Token> tokens, final int index, final String symbol) {
        return index >= 0 && index < tokens.size() && tokens.get(index).type() == TokenType.SYMBOL
                && symbol.equals(tokens.get(index).text());
    }

    private int skipLineComment(final String source, int index) {
        while (index < source.length() && source.charAt(index) != '\n' && source.charAt(index) != '\r') index++;
        return index;
    }

    private int skipBlockComment(final String source, final int start) {
        int depth = 1;
        int i = start + 2;
        while (i < source.length()) {
            if (source.charAt(i) == '/' && has(source, i + 1, '*')) {
                depth++;
                i += 2;
            } else if (source.charAt(i) == '*' && has(source, i + 1, '/')) {
                depth--;
                i += 2;
                if (depth == 0) return i;
            } else {
                i++;
            }
        }
        throw invalid("Cypher 块注释未闭合");
    }

    private int skipString(final String source, final int start, final char quote) {
        int i = start + 1;
        while (i < source.length()) {
            char c = source.charAt(i++);
            if (c == '\\') {
                if (i >= source.length()) throw invalid("Cypher 字符串转义未闭合");
                i++;
            } else if (c == quote) {
                if (i < source.length() && source.charAt(i) == quote) {
                    i++;
                } else {
                    return i;
                }
            }
        }
        throw invalid("Cypher 字符串未闭合");
    }

    private int skipBacktickIdentifier(final String source, final int start) {
        int i = start + 1;
        while (i < source.length()) {
            if (source.charAt(i++) == '`') {
                if (i < source.length() && source.charAt(i) == '`') {
                    i++;
                } else {
                    return i;
                }
            }
        }
        throw invalid("Cypher 反引号标识符未闭合");
    }

    private boolean isIdentifierStart(final char c) {
        return c == '_' || Character.isUnicodeIdentifierStart(c);
    }

    private boolean isIdentifierPart(final char c) {
        return c == '_' || Character.isUnicodeIdentifierPart(c);
    }

    private boolean has(final String source, final int index, final char expected) {
        return index < source.length() && source.charAt(index) == expected;
    }

    private boolean matches(final char opening, final char closing) {
        return (opening == '(' && closing == ')') || (opening == '[' && closing == ']')
                || (opening == '{' && closing == '}');
    }

    private ValidateException invalid(final String message) {
        return new ValidateException(message);
    }

    private enum TokenType { WORD, STRING, QUOTED_IDENTIFIER, SYMBOL }

    private record Token(TokenType type, String text, int offset) { }
}
