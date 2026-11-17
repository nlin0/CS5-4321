package com.group3.operator;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraceHashJoinOperatorTest {
    @TempDir
    Path temp;

    private Table table(String name, DataType keyType, Object... keys) {
        Schema schema = new Schema(List.of(new Column("key", keyType),
            new Column("text", DataType.STRING), new Column("flag", DataType.BOOLEAN),
            new Column("float", DataType.FLOAT)));
        List<Row> rows = new ArrayList<>();
        // long unicode text checks that disk storage does not truncate string values.
        String longText = "猫\n\"" + "x".repeat(70_000);
        for (Object key : keys) {
            Row row = new Row(List.of(
                new Value(keyType, key),
                new Value(DataType.STRING, longText),
                new Value(DataType.BOOLEAN, null),
                new Value(DataType.FLOAT, 1.25f)
            ));
            rows.add(row);
        }
        return new Table(name, schema, rows);
    }

    private Map<Row, Long> rowCounts(Table table) {
        Map<Row, Long> counts = new HashMap<>();
        for (Row row : table.getRows()) {
            counts.merge(row, 1L, Long::sum);
        }
        return counts;
    }

    private void assertClean() throws Exception {
        try (var files = Files.list(temp)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void matchesHashJoin() throws Exception {
        Table left = table("left", DataType.INTEGER, 1, 2, null, 2, 3, 4, -1);
        Table right = table("right", DataType.FLOAT, 2.0, 3.0, 2.0, null, -1.0);
        Table expected = new JoinOperator().innerJoin(left, right, "key", "key");
        Table actual = new GraceHashJoinOperator(8, 3, temp).innerJoin(left, right, "key", "key");
        assertEquals(rowCounts(expected), rowCounts(actual));
        assertEquals(expected.getSchema().getColumns().stream().map(Column::getName).toList(),
            actual.getSchema().getColumns().stream().map(Column::getName).toList());
        assertEquals(7, left.size());
        assertEquals(5, right.size());
        assertClean();
    }

    @Test
    void joinsStringsAndBooleans() throws Exception {
        for (DataType type : List.of(DataType.STRING, DataType.BOOLEAN)) {
            Object key;
            Object other;
            if (type == DataType.STRING) {
                key = "same";
                other = "SAME";
            } else {
                key = true;
                other = false;
            }
            Table table = table("t", type, key, key, other, null);
            assertEquals(rowCounts(new JoinOperator().innerJoin(table, "a", table, "b", "key", "key")),
                rowCounts(new GraceHashJoinOperator(4, 4, temp)
                    .innerJoin(table, "a", table, "b", "key", "key")));
        }
        assertClean();
    }

    @Test
    void rejectsSkew() throws Exception {
        Table left = table("a", DataType.INTEGER, 1, 1, 1);
        Table right = table("b", DataType.INTEGER, 1, 1, 1);
        GraceHashJoinOperator join = new GraceHashJoinOperator(4, 2, temp);
        assertThrows(IllegalStateException.class, () -> join.innerJoin(left, right, "key", "key"));
        assertClean();
    }

    @Test
    void buildsSmallerSide() throws Exception {
        GraceHashJoinOperator join = new GraceHashJoinOperator(4, 1, temp);
        Table small = table("a", DataType.INTEGER, 1);
        Table large = table("b", DataType.INTEGER, 1, 1, 1, 1);
        assertEquals(4, join.innerJoin(small, large, "key", "key").size());
        assertEquals(4, join.innerJoin(large, small, "key", "key").size());
        assertClean();
    }

    @Test
    void handlesEmptyResults() throws Exception {
        GraceHashJoinOperator join = new GraceHashJoinOperator(4, 1, temp);
        Table empty = table("a", DataType.INTEGER);
        Table other = table("b", DataType.INTEGER, 1, null);
        assertEquals(8, join.innerJoin(empty, other, "key", "key").getSchema().size());
        assertEquals(0, join.innerJoin(other, empty, "key", "key").size());
        assertEquals(0, join.innerJoin(table("a", DataType.INTEGER, 2), other, "key", "key").size());
        assertClean();
    }

    @Test
    void joinsLargeInputs() throws Exception {
        Schema schema = new Schema(List.of(new Column("key", DataType.INTEGER)));
        List<Row> rows = new ArrayList<>();
        for (int i = -500; i < 500; i++) {
            rows.add(new Row(List.of(new Value(DataType.INTEGER, i))));
        }
        Table left = new Table("left", schema, rows);
        Table right = new Table("right", schema, rows);
        Table result = new GraceHashJoinOperator(64, 100, temp)
            .innerJoin(left, right, "key", "key");
        assertEquals(rowCounts(new JoinOperator().innerJoin(left, right, "key", "key")), rowCounts(result));
        assertEquals(1000, result.size());
        assertClean();
    }

    @Test
    void rejectsInvalidInputs() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new GraceHashJoinOperator(1, 1, temp));
        assertThrows(IllegalArgumentException.class, () -> new GraceHashJoinOperator(4, 0, temp));
        GraceHashJoinOperator join = new GraceHashJoinOperator(4, 1, temp);
        Table a = table("a", DataType.INTEGER, 1);
        Table b = table("b", DataType.STRING, "1");
        assertThrows(IllegalArgumentException.class, () -> join.innerJoin(a, b, "key", "key"));
        assertThrows(IllegalArgumentException.class, () -> join.innerJoin(a, a, "key", "key"));
        assertThrows(IllegalArgumentException.class, () -> join.innerJoin(a, b, "missing", "key"));
        GraceHashJoinOperator badTemp = new GraceHashJoinOperator(4, 1, temp.resolve("missing-directory"));
        assertThrows(IllegalStateException.class, () -> badTemp.innerJoin(a, "a", a, "b", "key", "key"));
        assertClean();
    }

    private Table randomTable(String name, int rowCount, Random random) {
        Schema schema = new Schema(List.of(
            new Column("label", DataType.STRING, false),
            new Column("key", DataType.INTEGER)
        ));
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < rowCount; index++) {
            Integer key = random.nextInt(11) - 5;
            if (random.nextInt(5) == 0) {
                key = null;
            }
            rows.add(new Row(List.of(
                new Value(DataType.STRING, name + index),
                new Value(DataType.INTEGER, key)
            )));
        }
        return new Table(name, schema, rows);
    }

    private Map<Row, Long> nestedMatches(Table left, Table right) {
        Map<Row, Long> expected = new HashMap<>();
        for (Row leftRow : left.getRows()) {
            for (Row rightRow : right.getRows()) {
                Value leftKey = leftRow.get(1);
                Value rightKey = rightRow.get(1);
                if (!leftKey.isNull() && leftKey.equals(rightKey)) {
                    List<Value> values = new ArrayList<>(leftRow.getValues());
                    values.addAll(rightRow.getValues());
                    expected.merge(new Row(values), 1L, Long::sum);
                }
            }
        }
        return expected;
    }

    @Test
    void matchesNestedLoop() throws Exception {
        Random random = new Random(4321);
        int[] partitionCounts = {2, 3, 16};
        for (int iteration = 0; iteration < 30; iteration++) {
            Table left = randomTable("left", random.nextInt(30), random);
            Table right = randomTable("right", random.nextInt(30), random);
            List<Row> originalLeft = new ArrayList<>(left.getRows());
            List<Row> originalRight = new ArrayList<>(right.getRows());
            int partitionCount = partitionCounts[iteration % partitionCounts.length];
            GraceHashJoinOperator join = new GraceHashJoinOperator(partitionCount, 30, temp);
            Table actual = join.innerJoin(left, right, "key", "key");

            assertEquals(nestedMatches(left, right), rowCounts(actual));
            assertEquals(originalLeft, left.getRows());
            assertEquals(originalRight, right.getRows());
            assertClean();
        }
    }

    @Test
    void handlesHashCollisions() throws Exception {
        // these different strings have the same hash code.
        assertEquals("Aa".hashCode(), "BB".hashCode());
        Table left = table("left", DataType.STRING, "Aa", "BB");
        Table right = table("right", DataType.STRING, "Aa");
        Table result = new GraceHashJoinOperator(2, 1, temp)
            .innerJoin(left, right, "key", "key");

        List<Value> expectedValues = new ArrayList<>(left.getRows().get(0).getValues());
        expectedValues.addAll(right.getRows().get(0).getValues());
        assertEquals(List.of(new Row(expectedValues)), result.getRows());
        assertClean();
    }

    @Test
    void checksBuildLimit() throws Exception {
        GraceHashJoinOperator join = new GraceHashJoinOperator(3, 2, temp);
        Table left = table("left", DataType.INTEGER, 1, 1);
        Table right = table("right", DataType.INTEGER, 1, 1);
        assertEquals(4, join.innerJoin(left, right, "key", "key").size());

        left.addRow(left.getRows().get(0));
        right.addRow(right.getRows().get(0));
        assertThrows(IllegalStateException.class,
            () -> join.innerJoin(left, right, "key", "key"));
        assertClean();
    }

    @Test
    void skipsNullKeys() throws Exception {
        Table left = table("left", DataType.INTEGER, null, null);
        Table right = table("right", DataType.INTEGER, null, null);
        Table result = new GraceHashJoinOperator(2, 1, temp)
            .innerJoin(left, right, "key", "key");
        assertEquals(0, result.size());
        assertEquals(8, result.getSchema().size());
        assertClean();
    }

    @Test
    void reusesAfterFailure() throws Exception {
        GraceHashJoinOperator join = new GraceHashJoinOperator(2, 1, temp);
        Table left = table("left", DataType.INTEGER, 1, 1);
        Table right = table("right", DataType.INTEGER, 1, 1);
        assertThrows(IllegalStateException.class,
            () -> join.innerJoin(left, right, "key", "key"));
        assertClean();

        Table smallerRight = table("right", DataType.INTEGER, 1);
        Table firstResult = join.innerJoin(left, smallerRight, "key", "key");
        Table secondResult = join.innerJoin(left, smallerRight, "key", "key");
        assertEquals(2, firstResult.size());
        assertEquals(firstResult.getRows(), secondResult.getRows());
        assertClean();
    }

    @Test
    void checksConfiguration() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> new GraceHashJoinOperator(257, 1, temp));
        Table left = table("left", DataType.INTEGER, 1);
        Table right = table("right", DataType.INTEGER, 1);
        assertEquals(1, new GraceHashJoinOperator(256, 1, temp)
            .innerJoin(left, right, "key", "key").size());
        assertEquals(1, new GraceHashJoinOperator()
            .innerJoin(left, right, "key", "key").size());
        assertClean();
    }

}
