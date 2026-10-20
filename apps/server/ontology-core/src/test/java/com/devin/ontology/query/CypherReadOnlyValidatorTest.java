package com.devin.ontology.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.devin.ontology.common.exception.ValidateException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CypherReadOnlyValidatorTest {
    private final CypherReadOnlyValidator validator = new CypherReadOnlyValidator();

    @Test
    void acceptsCommonReadQueryWithNestedCommentsAndQuotedData() {
        String cypher = "MATCH (n:Person) /* outer /* inner */ done */ "
                + "WHERE (n.name = 'CREATE; $$') RETURN toLower(n.name) AS name, count(n) AS total "
                + "ORDER BY total LIMIT 10 // CREATE is comment text";

        assertEquals(List.of("name", "total"), validator.validateAndGetColumns(cypher));
    }

    @Test
    void allowsWriteWordsAndSemicolonsOnlyAsStringData() {
        assertEquals(List.of("value"), validator.validateAndGetColumns(
                "RETURN 'CREATE; MERGE; DELETE; $$' AS value"));
    }

    @Test
    void rejectsWriteClausesAndMultipleStatements() {
        for (String query : List.of(
                "MATCH (n) SET n.x = 1 RETURN n",
                "MATCH (n) DELETE n RETURN n",
                "CALL db.labels() YIELD label RETURN label",
                "MATCH (n) RETURN n; MATCH (m) RETURN m")) {
            assertThrows(ValidateException.class, () -> validator.validateAndGetColumns(query), query);
        }
    }

    @Test
    void rejectsSchemaQualifiedAndQuotedFunctions() {
        for (String query : List.of(
                "RETURN ag_catalog.cypher('graph', 'RETURN 1') AS value",
                "RETURN pg_catalog.pg_sleep(1) AS value",
                "MATCH (n) RETURN `count`(n) AS value",
                "MATCH (n) RETURN apoc.load.json('url') AS value",
                "MATCH (n) RETURN c\\u006funt(n) AS value")) {
            assertThrows(ValidateException.class, () -> validator.validateAndGetColumns(query), query);
        }
    }

    @Test
    void rejectsDuplicateAliasesAndMoreThan64Columns() {
        assertThrows(ValidateException.class,
                () -> validator.validateAndGetColumns("MATCH (n) RETURN n.a AS value, n.b AS VALUE"));
        String manyColumns = "RETURN " + java.util.stream.IntStream.range(0, 65)
                .mapToObj(index -> index + " AS c" + index).collect(java.util.stream.Collectors.joining(", "));
        assertThrows(ValidateException.class, () -> validator.validateAndGetColumns(manyColumns));
    }

    @Test
    void rejectsControlsMalformedTokensAndOversizedQueries() {
        assertThrows(ValidateException.class,
                () -> validator.validateAndGetColumns("RETURN 'safe\u0001data' AS value"));
        assertThrows(ValidateException.class, () -> validator.validateAndGetColumns("MATCH (n RETURN n"));
        assertThrows(ValidateException.class, () -> validator.validateAndGetColumns("RETURN 'unterminated AS x"));
        assertThrows(ValidateException.class,
                () -> validator.validateAndGetColumns("MATCH (n) RETURN n.name AS name " + " ".repeat(16_400)));
    }
}
