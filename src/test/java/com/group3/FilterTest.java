package com.group3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.expression.ComparisonExpression;
import com.group3.expression.ComparisonOperator;
import com.group3.expression.Expression;
import com.group3.expression.InExpression;
import com.group3.expression.IsNullExpression;
import com.group3.expression.LogicalExpression;
import com.group3.expression.Operand;
import com.group3.operator.FilterOperator;

class FilterTest {

    private Schema schema;
    private List<Row> rows;
    private FilterOperator filter;

    @BeforeEach
    void setUp() {
        schema = new Schema(List.of(
            new Column("id", DataType.INTEGER),
            new Column("name", DataType.STRING),
            new Column("gpa", DataType.FLOAT),
            new Column("active", DataType.BOOLEAN)
        ));
        Table table = new Table("students", schema, List.of(
            row(1, "Billy", 3.65, true),
            row(2, "Alice", 3.91, true),
            row(3, "Bob", 2.85, false),
            row(4, "Cara", null, true)
        ));
        rows = table.getRows();
        filter = new FilterOperator();
    }

    @Test
    void greaterThanDropsNulls() {
        Expression gpaAbove = ComparisonExpression.of(
            "gpa", ">", new Value(DataType.FLOAT, 3.5)
        );

        assertEquals(List.of("Billy", "Alice"), names(filter.apply(schema, rows, gpaAbove)));
    }

    @Test
    void equalityMatchesOneString() {
        Expression nameIsAlice = ComparisonExpression.of(
            "name", "=", new Value(DataType.STRING, "Alice")
        );

        assertEquals(List.of("Alice"), names(filter.apply(schema, rows, nameIsAlice)));
    }

    @Test
    void nullComparisonIsFalse() {
        Expression gpaIsNullLiteral = ComparisonExpression.of(
            "gpa", "=", new Value(DataType.FLOAT, null)
        );

        assertTrue(filter.apply(schema, rows, gpaIsNullLiteral).isEmpty());
    }

    @Test
    void andOrAndNot() {
        Expression highGpa = ComparisonExpression.of(
            "gpa", ">", new Value(DataType.FLOAT, 3.5)
        );
        Expression active = ComparisonExpression.of(
            "active", "=", new Value(DataType.BOOLEAN, true)
        );
        Expression nameIsBob = ComparisonExpression.of(
            "name", "=", new Value(DataType.STRING, "Bob")
        );

        assertEquals(
            List.of("Billy", "Alice"),
            names(filter.apply(schema, rows, LogicalExpression.and(highGpa, active)))
        );
        assertEquals(
            List.of("Billy", "Alice", "Bob"),
            names(filter.apply(schema, rows, LogicalExpression.or(highGpa, nameIsBob)))
        );
        assertEquals(
            List.of("Bob"),
            names(filter.apply(schema, rows, LogicalExpression.not(active)))
        );
    }

    @Test
    void isNullAndIsNotNull() {
        assertEquals(
            List.of("Cara"),
            names(filter.apply(schema, rows, IsNullExpression.isNull("gpa")))
        );
        assertEquals(
            List.of("Billy", "Alice", "Bob"),
            names(filter.apply(schema, rows, IsNullExpression.isNotNull("gpa")))
        );
    }

    @Test
    void inListSkipsNullColumn() {
        Expression names = InExpression.of("name", List.of(
            new Value(DataType.STRING, "Alice"),
            new Value(DataType.STRING, "Bob")
        ));
        assertEquals(List.of("Alice", "Bob"), names(filter.apply(schema, rows, names)));

        Row missingName = new Row(List.of(
            new Value(DataType.INTEGER, 5),
            new Value(DataType.STRING, null),
            new Value(DataType.FLOAT, 4.0),
            new Value(DataType.BOOLEAN, true)
        ));
        Expression inList = InExpression.of("name", List.of(
            new Value(DataType.STRING, "Alice"),
            new Value(DataType.STRING, null)
        ));
        assertTrue(filter.apply(schema, List.of(missingName), inList).isEmpty());
    }

    @Test
    void comparesIntegerToFloatAndColumnToColumn() {
        Expression idAbove = ComparisonExpression.of(
            "id", ">", new Value(DataType.FLOAT, 2.5)
        );
        assertEquals(List.of("Bob", "Cara"), names(filter.apply(schema, rows, idAbove)));

        Expression sameName = new ComparisonExpression(
            Operand.column("name"),
            ComparisonOperator.EQUAL,
            Operand.column("name")
        );
        assertEquals(4, filter.apply(schema, rows, sameName).size());
    }

    @Test
    void missingPredicateKeepsEveryRow() {
        assertEquals(4, filter.apply(schema, rows, null).size());
    }

    @Test
    void unknownColumnAndBooleanOrderingFail() {
        Expression missing = ComparisonExpression.of(
            "major", "=", new Value(DataType.STRING, "CS")
        );
        assertThrows(IllegalArgumentException.class, () -> filter.apply(schema, rows, missing));

        Expression activeOrdered = ComparisonExpression.of(
            "active", ">", new Value(DataType.BOOLEAN, false)
        );
        assertThrows(IllegalArgumentException.class, () -> filter.apply(schema, rows, activeOrdered));
    }

    private static Row row(int id, String name, Double gpa, boolean active) {
        return new Row(List.of(
            new Value(DataType.INTEGER, id),
            new Value(DataType.STRING, name),
            new Value(DataType.FLOAT, gpa),
            new Value(DataType.BOOLEAN, active)
        ));
    }

    private static List<String> names(List<Row> matched) {
        return matched.stream()
            .map(row -> (String) row.get(1).getValue())
            .toList();
    }
}
