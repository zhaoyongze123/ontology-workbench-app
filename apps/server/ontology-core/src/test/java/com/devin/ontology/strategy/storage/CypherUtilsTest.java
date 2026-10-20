package com.devin.ontology.strategy.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CypherUtilsTest {

    @Test
    void quotesEachUnicodeLabelAndEscapesBackticks() {
        assertEquals(":`设备``类型`:`温度`", CypherUtils.generateLabelStr(List.of("设备`类型", "温度")));
    }

    @Test
    void recursivelySerializesPropertyValuesAndNestedMapKeys() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("key`\"", "quote' slash\\ line\n");
        nested.put("values", new Object[] {null, true, 7, List.of("中\u0001文")});
        Map<String, Object> properties = Map.of("nested", nested);

        assertEquals("{`nested`: {`key``\"`: 'quote\\' slash\\\\ line\\n', `values`: [null, true, 7, ['中\\u0001文']]}}",
                CypherUtils.convertPropertyVal(properties));
    }

    @Test
    void stringContainingJsonRemainsAStringAndNullIsSerializedExplicitly() {
        assertEquals("'{\"x\":1}'", CypherUtils.formatValue("{\"x\":1}"));
        assertEquals("null", CypherUtils.formatValue(null));
    }

    @Test
    void legacyNeo4jRelationshipTemplateQuotesTypeAndKeepsBoundParameters() {
        String cypher = CypherUtils.generateCreateRelationshipCypher("R`); MATCH (n) DELETE n //");

        assertTrue(cypher.contains("[r:`R``); MATCH (n) DELETE n //`]"));
        assertTrue(cypher.contains("sourceNode._id = $sourceId"));
        assertTrue(cypher.contains("SET r = $properties SET r._id = $id"));
    }

    @Test
    void rejectsUnsupportedNumbersAndNulIdentifiers() {
        Number hostile = new Number() {
            @Override public int intValue() { return 1; }
            @Override public long longValue() { return 1; }
            @Override public float floatValue() { return 1; }
            @Override public double doubleValue() { return 1; }
            @Override public String toString() { return "1) MATCH (n) DETACH DELETE n //"; }
        };
        assertThrows(IllegalArgumentException.class, () -> CypherUtils.formatValue(hostile));
        assertThrows(IllegalArgumentException.class, () -> CypherUtils.quoteIdentifier("bad\0label"));
    }
}
