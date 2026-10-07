package com.group3.operator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Value;

class DistinctOperatorTest {

    private final DistinctOperator distinct = new DistinctOperator();

    private static Row row(String name, Integer age) {
        return new Row(List.of(
                new Value(DataType.STRING, name),
                new Value(DataType.INTEGER, age)));
    }

    @Test
    void removesDuplicatesKeepingFirstOccurrenceOrder() {
        List<Row> input = List.of(row("Cy", 20), row("Ada", 30), row("Cy", 20), row("Bob", 1), row("Ada", 30));

        assertEquals(List.of(row("Cy", 20), row("Ada", 30), row("Bob", 1)), distinct.apply(input));
    }

    @Test
    void rowsMustMatchOnEveryColumn() {
        List<Row> input = List.of(row("Ada", 30), row("Ada", 31));

        assertEquals(2, distinct.apply(input).size());
    }

    @Test
    void nullsAreTreatedAsEqualToEachOther() {
        List<Row> input = List.of(row("Dee", null), row("Dee", null), row(null, null), row(null, null));

        assertEquals(List.of(row("Dee", null), row(null, null)), distinct.apply(input));
    }

    @Test
    void sameValueWithDifferentTypeIsNotADuplicate() {
        List<Row> input = List.of(
                new Row(List.of(new Value(DataType.INTEGER, null))),
                new Row(List.of(new Value(DataType.STRING, null))));

        assertEquals(2, distinct.apply(input).size());
    }

    @Test
    void emptyInputGivesEmptyOutput() {
        assertTrue(distinct.apply(List.of()).isEmpty());
    }

    @Test
    void nullInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> distinct.apply(null));
    }

    @Test
    void resultIsImmutable() {
        List<Row> result = distinct.apply(List.of(row("Ada", 30)));

        assertThrows(UnsupportedOperationException.class, () -> result.add(row("Bob", 1)));
    }
}
