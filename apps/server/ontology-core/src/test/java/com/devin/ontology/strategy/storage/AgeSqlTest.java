package com.devin.ontology.strategy.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devin.ontology.common.exception.ValidateException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgeSqlTest {

    @Test
    void encodesPostgresTextLiteralIncludingQuotesSlashesAndDollarQuotes() {
        assertEquals("E'''\\\\$$\\n'", AgeSql.literal("'\\$$\n"));
        assertThrows(IllegalArgumentException.class, () -> AgeSql.literal("bad\0value"));
    }

    @Test
    void quotesSqlIdentifiersAndRejectsNul() {
        assertEquals("\"x\"\"; DROP TABLE users --\"", AgeSql.identifier("x\"; DROP TABLE users --"));
        assertThrows(IllegalArgumentException.class, () -> AgeSql.identifier("bad\0name"));
    }

    @Test
    void buildsCypherSqlWithSafeTextAndQuotedProjectionNames() {
        String sql = AgeSql.cypherSql("ontology42", "RETURN '$$\\\\' AS value", List.of("v\"; DROP TABLE x --"));
        assertTrue(sql.startsWith("SELECT * FROM ag_catalog.cypher(E'ontology42', $ontology_"));
        assertTrue(sql.contains("$$"));
        assertTrue(sql.contains("E'"));
        assertTrue(sql.endsWith(" AS (\"v\"\"; DROP TABLE x --\" ag_catalog.agtype)"));
        assertFalse(sql.contains("$$) AS"));
    }

    @Test
    void usesACompleteDollarDelimiterAbsentFromTheWholeQueryBody() {
        String query = "RETURN '$$); DROP TABLE users; -- $ontology_example$' AS value";
        String literal = AgeSql.cypherLiteral(query);
        int end = literal.indexOf('$', 1);
        String delimiter = literal.substring(0, end + 1);
        assertFalse(query.contains(delimiter));
        assertEquals(delimiter + query + delimiter, literal);
        assertThrows(IllegalArgumentException.class, () -> AgeSql.cypherLiteral("bad\0text"));
    }

    @Test
    void parsesTopLevelReturnProjectionAndEscapedAliases() {
        assertEquals(List.of("name", "x`); DROP TABLE users --", "count(n)"),
                AgeSql.parseReturnColumns("MATCH (n) RETURN n.name, n AS `x``); DROP TABLE users --`, count(n) ORDER BY n.name LIMIT 2"));
    }

    @Test
    void rejectsAmbiguousOrMalformedProjection() {
        assertThrows(ValidateException.class, () -> AgeSql.parseReturnColumns("MATCH (n) RETURN *"));
        assertThrows(ValidateException.class, () -> AgeSql.parseReturnColumns("RETURN n UNION RETURN m"));
        assertThrows(ValidateException.class, () -> AgeSql.parseReturnColumns("RETURN (n]"));
        assertThrows(ValidateException.class, () -> AgeSql.parseReturnColumns("RETURN n; DROP TABLE x"));
    }
}
