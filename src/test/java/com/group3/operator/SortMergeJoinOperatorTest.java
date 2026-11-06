package com.group3.operator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;

class SortMergeJoinOperatorTest {

    private final SortMergeJoinOperator join = new SortMergeJoinOperator();

    @Test
    void joinsUnsortedInputsAndProducesEveryDuplicateKeyCombination() {
        Table left = table("left", DataType.INTEGER,
            pair(DataType.INTEGER, 2, "left-2"),
            pair(DataType.INTEGER, 1, "left-1a"),
            pair(DataType.INTEGER, 1, "left-1b"),
            pair(DataType.INTEGER, 3, "left-3"));
        Table right = table("right", DataType.INTEGER,
            pair(DataType.INTEGER, 1, "right-1a"),
            pair(DataType.INTEGER, 2, "right-2"),
            pair(DataType.INTEGER, 1, "right-1b"));

        Table result = join.innerJoin(left, right, "key", "key");

        assertEquals(List.of(
            List.of(1, "left-1a", 1, "right-1a"),
            List.of(1, "left-1a", 1, "right-1b"),
            List.of(1, "left-1b", 1, "right-1a"),
            List.of(1, "left-1b", 1, "right-1b"),
            List.of(2, "left-2", 2, "right-2")
        ), payloads(result));
    }

    @Test
    void matchesIntegerAndFloatKeysAndSkipsNullKeys() {
        Table integers = table("integers", DataType.INTEGER,
            pair(DataType.INTEGER, null, "no-match"),
            pair(DataType.INTEGER, 3, "three"));
        Table floats = table("floats", DataType.FLOAT,
            pair(DataType.FLOAT, null, "also-no-match"),
            pair(DataType.FLOAT, 3.0, "three-point-zero"));

        Table result = join.innerJoin(integers, floats, "key", "key");

        assertEquals(List.of(List.of(3, "three", 3.0, "three-point-zero")), payloads(result));
    }

    @Test
    void usesAliasesInTheOutputSchema() {
        Table left = table("same", DataType.INTEGER, pair(DataType.INTEGER, 1, "a"));
        Table right = table("same", DataType.INTEGER, pair(DataType.INTEGER, 1, "b"));

        Table result = join.innerJoin(left, "l", right, "r", "key", "key");

        assertEquals(List.of("l.key", "l.value", "r.key", "r.value"),
            result.getSchema().getColumns().stream().map(Column::getName).toList());
    }

    @Test
    void rejectsIncompatibleJoinColumns() {
        Table numbers = table("numbers", DataType.INTEGER, pair(DataType.INTEGER, 1, "one"));
        Table words = table("words", DataType.STRING, pair(DataType.STRING, "one", "one"));

        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(numbers, words, "key", "key"));
    }

    private static Table table(String name, DataType keyType, Row... rows) {
        return new Table(name, new Schema(List.of(
            new Column("key", keyType),
            new Column("value", DataType.STRING)
        )), List.of(rows));
    }

    private static Row pair(DataType keyType, Object key, String value) {
        return new Row(List.of(new Value(keyType, key), new Value(DataType.STRING, value)));
    }

    private static List<List<Object>> payloads(Table table) {
        List<List<Object>> rows = new ArrayList<>();
        for (Row row : table.getRows()) {
            rows.add(row.getValues().stream().map(Value::getValue).toList());
        }
        return rows;
    }
}
