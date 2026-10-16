package com.group3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.group3.expression.ComparisonExpression;
import com.group3.expression.LogicalExpression;
import com.group3.expression.LogicalOperator;
import com.group3.parser.Query;
import com.group3.parser.SQLParser;

class WhereParserTest {
    private final SQLParser parser = new SQLParser();

    @ParameterizedTest
    @ValueSource(strings = {
        "id = 1", "id != 1", "id <> 1", "id < 1", "id <= 1", "id > 1", "id >= 1",
        "id = other_id", "1 < id", "active = true", "active = false", "name = 'O''Brien'",
        "id = NULL", "id IS NULL", "id IS NOT NULL", "id IN (1, 2, NULL)",
        "id IN (1)", "id NOT IN (1, 2)", "NOT (id = 1)", "((id = (1)))",
        "score >= -1.5e2", "score < +.5", "(id) = 1"
    })
    void translatesSupportedPredicates(String predicate) {
        assertNotNull(parser.parse("SELECT * FROM t WHERE " + predicate).getWhere());
    }

    @Test
    void retainsSqlPrecedenceAndParentheses() {
        LogicalExpression or = assertInstanceOf(LogicalExpression.class,
            parser.parse("SELECT * FROM t WHERE id = 1 OR id = 2 AND id = 3").getWhere());
        assertEquals(LogicalOperator.OR, or.getOperator());
        assertEquals(LogicalOperator.AND,
            assertInstanceOf(LogicalExpression.class, or.getChildren().get(1)).getOperator());

        LogicalExpression and = assertInstanceOf(LogicalExpression.class,
            parser.parse("SELECT * FROM t WHERE (id = 1 OR id = 2) AND id = 3").getWhere());
        assertEquals(LogicalOperator.AND, and.getOperator());
        assertEquals(LogicalOperator.OR,
            assertInstanceOf(LogicalExpression.class, and.getChildren().get(0)).getOperator());
    }

    @Test
    void handlesSignedIntegerBoundaries() {
        ComparisonExpression comparison = assertInstanceOf(ComparisonExpression.class,
            parser.parse("SELECT * FROM t WHERE id = -2147483648").getWhere());
        assertEquals(Integer.MIN_VALUE, comparison.getRight().getLiteral().getValue());
        assertThrows(IllegalArgumentException.class,
            () -> parser.parse("SELECT * FROM t WHERE id = 2147483648"));
        assertThrows(IllegalArgumentException.class,
            () -> parser.parse("SELECT * FROM t WHERE id = -2147483649"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"t.id", "T.id", "p.id", "P.id", "id"})
    void resolvesSourceTableAndAliasQualifiers(String column) {
        Query query = parser.parse("SELECT * FROM t AS p WHERE " + column + " = 1");
        ComparisonExpression comparison = assertInstanceOf(ComparisonExpression.class, query.getWhere());
        assertEquals("id", comparison.getLeft().getColumnName());
        assertEquals("p", query.getTableAlias());
    }

    @Test
    void rejectsUnrelatedQualifiers() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> parser.parse("SELECT * FROM t WHERE other.id = 1"));
        assertTrue(error.getMessage().contains("Unknown table qualifier in WHERE"));
        assertThrows(IllegalArgumentException.class,
            () -> parser.parse("SELECT * FROM t WHERE id = other.id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "id IN (SELECT id FROM other)", "EXISTS (SELECT id FROM other)",
        "id = (SELECT id FROM other)", "id + 1 = 2", "id = id + 1",
        "ABS(id) = 1", "id = ABS(1)", "name LIKE 'A%'", "id BETWEEN 1 AND 3",
        "id IN (other_id)", "id IN (1 + 1)", "id IN (ABS(1))", "(id, other_id) = (1, 2)"
    })
    void rejectsUnsupportedPredicatesRatherThanDroppingThem(String predicate) {
        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
            () -> parser.parse("SELECT * FROM t WHERE " + predicate));
        assertTrue(error.getMessage().contains("Unsupported WHERE expression"));
    }

    @Test
    void aliasIsPreservedWithoutIntroducingAWhereClause() {
        Query query = parser.parse("SELECT id AS identifier FROM t");
        assertNull(query.getWhere());
        assertEquals("identifier", query.getSelectList().get(0).outputName());
    }
}
