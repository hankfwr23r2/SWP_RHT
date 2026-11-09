package com.example.racehorse_transport.entity;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.Set;

/** Strict conversion keeps unsupported legacy payloads intact instead of discarding fields. */
final class FinancialJsonSupport {
    static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private FinancialJsonSupport() {}

    static void requireFields(JsonNode node, Set<String> fields) {
        if (!node.isObject()) throw new IllegalArgumentException("Expected a financial record object");
        node.fieldNames().forEachRemaining(name -> {
            if (!fields.contains(name)) throw new IllegalArgumentException("Unsupported financial field: " + name);
        });
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("Expected text: " + field);
        return value.textValue();
    }

    static BigDecimal amount(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isNumber()) throw new IllegalArgumentException("Expected a number: " + field);
        return value.decimalValue();
    }

    static Long integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException("Expected an epoch millisecond integer: " + field);
        }
        return value.longValue();
    }

    static int presentFields(JsonNode node, String... names) {
        int mask = 0;
        for (int i = 0; i < names.length; i++) {
            if (node.has(names[i])) mask |= 1 << i;
        }
        return mask;
    }

    static void put(ObjectNode node, int mask, int bit, String field, String value) {
        if ((mask & (1 << bit)) == 0) return;
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    static void put(ObjectNode node, int mask, int bit, String field, BigDecimal value) {
        if ((mask & (1 << bit)) == 0) return;
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    static void put(ObjectNode node, int mask, int bit, String field, Long value) {
        if ((mask & (1 << bit)) == 0) return;
        if (value == null) node.putNull(field); else node.put(field, value);
    }
}
