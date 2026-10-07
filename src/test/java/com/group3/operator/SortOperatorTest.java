package com.group3.operator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Value;
import com.group3.parser.OrderByItem;

class SortOperatorTest {

    private final SortOperator sort = new SortOperator();

    private final Schema schema = new Schema(List.of(
            new Column("name", DataType.STRING),
            new Column("age", DataType.INTEGER),
            new Column("gpa", DataType.FLOAT),
            new Column("active", DataType.BOOLEAN)));

    private static Row row(String name, Integer age, Double gpa, Boolean active) {
        return new Row(List.of(
                new Value(DataType.STRING, name),
                new Value(DataType.INTEGER, age),
                new Value(DataType.FLOAT, gpa),
                new Value(DataType.BOOLEAN, active)));
    }

    private static List<Object> names(List<Row> rows) {
        return rows.stream().map(r -> r.get(0).getValue()).toList();
    }

    private static List<OrderByItem> by(OrderByItem... items) {
        return Arrays.asList(items);
    }

    private final List<Row> rows = List.of(
            row("Cy", 20, 3.1, true),
            row("Ada", 30, 3.9, false),
            row("Bob", 20, 2.5, true),
            row("Dee", null, null, null),
            row("Eve", 25, 3.9, false));

    @Test
    void emptyOrderByKeepsInputOrder() {
        assertEquals(names(rows), names(sort.apply(schema, rows, List.of())));
        assertEquals(names(rows), names(sort.apply(schema, rows, null)));
    }

    @Test
    void sortsAscendingWithNullsFirstByDefault() {
        List<Row> sorted = sort.apply(schema, rows, by(new OrderByItem("age", true)));
        assertEquals(List.of("Dee", "Cy", "Bob", "Eve", "Ada"), names(sorted));
    }

    @Test
    void sortsDescendingWithNullsLastByDefault() {
        List<Row> sorted = sort.apply(schema, rows, by(new OrderByItem("age", false)));
        assertEquals(List.of("Ada", "Eve", "Cy", "Bob", "Dee"), names(sorted));
    }

    @Test
    void explicitNullsLastInAscendingOrder() {
        List<Row> sorted = sort.apply(schema, rows, by(new OrderByItem("age", true, false)));
        assertEquals("Dee", names(sorted).get(4));
        assertEquals("Cy", names(sorted).get(0));
    }

    @Test
    void explicitNullsFirstInDescendingOrder() {
        List<Row> sorted = sort.apply(schema, rows, by(new OrderByItem("age", false, true)));
        assertEquals(List.of("Dee", "Ada", "Eve", "Cy", "Bob"), names(sorted));
    }

    @Test
    void secondKeyBreaksTies() {
        List<Row> sorted = sort.apply(schema, rows,
                by(new OrderByItem("age", true, false), new OrderByItem("name", true)));
        assertEquals(List.of("Bob", "Cy", "Eve", "Ada", "Dee"), names(sorted));
    }

    @Test
    void keysCanHaveDifferentDirections() {
        List<Row> sorted = sort.apply(schema, rows,
                by(new OrderByItem("gpa", false), new OrderByItem("name", false)));
        assertEquals(List.of("Eve", "Ada", "Cy", "Bob", "Dee"), names(sorted));
    }

    @Test
    void sortIsStableForEqualKeys() {
        // Cy comes before Bob in the input and both have age 20.
        List<Row> sorted = sort.apply(schema, rows, by(new OrderByItem("age", true)));
        assertTrue(names(sorted).indexOf("Cy") < names(sorted).indexOf("Bob"));
    }

    @Test
    void sortsStringsAndBooleans() {
        assertEquals(List.of("Ada", "Bob", "Cy", "Dee", "Eve"),
                names(sort.apply(schema, rows, by(new OrderByItem("name", true)))));
        // false < true; NULL first.
        assertEquals(List.of("Dee", "Ada", "Eve", "Cy", "Bob"),
                names(sort.apply(schema, rows, by(new OrderByItem("active", true)))));
    }

    @Test
    void sortsNegativeAndFractionalFloats() {
        List<Row> floats = List.of(
                row("a", 1, 0.5, true), row("b", 1, -2.25, true), row("c", 1, 0.0, true));
        assertEquals(List.of("b", "c", "a"),
                names(sort.apply(schema, floats, by(new OrderByItem("gpa", true)))));
    }

    @Test
    void floatAndDoubleStorageCompareNumerically() {
        Schema s = new Schema(List.of(new Column("x", DataType.FLOAT)));
        List<Row> mixed = List.of(
                new Row(List.of(new Value(DataType.FLOAT, 2.0))),
                new Row(List.of(new Value(DataType.FLOAT, 1.5f))));
        List<Row> sorted = sort.apply(s, mixed, by(new OrderByItem("x", true)));
        assertEquals(1.5f, sorted.get(0).get(0).getValue());
    }

    @Test
    void integerAndFloatCompareNumerically() {
        assertTrue(SortOperator.compareValues(
                new Value(DataType.INTEGER, 2), new Value(DataType.FLOAT, 2.5)) < 0);
        assertEquals(0, SortOperator.compareValues(
                new Value(DataType.INTEGER, 3), new Value(DataType.FLOAT, 3.0)));
    }

    @Test
    void incomparableTypesThrow() {
        assertThrows(IllegalArgumentException.class, () -> SortOperator.compareValues(
                new Value(DataType.STRING, "a"), new Value(DataType.INTEGER, 1)));
    }

    @Test
    void columnLookupIsCaseInsensitive() {
        List<Row> sorted = sort.apply(schema, rows, by(new OrderByItem("NAME", true)));
        assertEquals("Ada", names(sorted).get(0));
    }

    @Test
    void unknownColumnThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> sort.apply(schema, rows, by(new OrderByItem("nope", true))));
        assertTrue(e.getMessage().contains("Column not found"));
    }

    @Test
    void nullArgumentsThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> sort.apply(null, rows, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> sort.apply(schema, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> sort.apply(schema, rows, by((OrderByItem) null)));
    }

    @Test
    void blankColumnRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OrderByItem(" ", true));
        assertThrows(IllegalArgumentException.class, () -> new OrderByItem(null, true));
    }

    @Test
    void emptyInputGivesEmptyOutput() {
        assertTrue(sort.apply(schema, List.of(), by(new OrderByItem("age", true))).isEmpty());
    }

    @Test
    void doesNotModifyInputAndReturnsImmutableList() {
        List<Row> input = new ArrayList<>(rows);
        List<Row> sorted = sort.apply(schema, input, by(new OrderByItem("name", true)));

        assertEquals(names(rows), names(input));
        assertThrows(UnsupportedOperationException.class, () -> sorted.add(rows.get(0)));
    }
}
