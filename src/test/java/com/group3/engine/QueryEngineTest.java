package com.group3.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.parser.Query;

class QueryEngineTest {

    private Database database;
    private QueryEngine engine;

    @BeforeEach
    void setUp() {
        Schema schema = new Schema(List.of(
            new Column("name", DataType.STRING),
            new Column("age", DataType.INTEGER)
        ));
        Table students = new Table("students", schema);
        students.addRow(row("Ada", 30));
        students.addRow(row("Bob", null));

        database = new Database();
        database.addTable(students);
        database.addTable(new Table("empty", schema));
        engine = new QueryEngine(database);
    }

    private static Row row(String name, Integer age) {
        return new Row(List.of(
            new Value(DataType.STRING, name),
            new Value(DataType.INTEGER, age)
        ));
    }

    @Test
    void selectStarReturnsAllColumnsAndRows() {
        QueryResult result = engine.processQuery(new Query("students", List.of("*")));

        assertEquals(2, result.getSchema().size());
        assertEquals(2, result.getRowCount());
        assertEquals("Ada", result.getRows().get(0).get(0).getValue());
    }

    @Test
    void projectsRequestedColumnsInRequestedOrder() {
        QueryResult result = engine.processQuery(new Query("students", List.of("age", "name")));

        assertEquals("age", result.getSchema().getColumn(0).getName());
        assertEquals("name", result.getSchema().getColumn(1).getName());
        assertEquals(30, result.getRows().get(0).get(0).getValue());
        assertEquals("Ada", result.getRows().get(0).get(1).getValue());
    }

    @Test
    void columnLookupIsCaseInsensitive() {
        QueryResult result = engine.processQuery(new Query("STUDENTS", List.of("NAME")));

        assertEquals(1, result.getSchema().size());
        assertEquals(2, result.getRowCount());
    }

    @Test
    void preservesNullValues() {
        QueryResult result = engine.processQuery(new Query("students", List.of("age")));

        assertTrue(result.getRows().get(1).get(0).isNull());
    }

    @Test
    void emptyTableReturnsNoRowsButKeepsSchema() {
        QueryResult result = engine.processQuery(new Query("empty", List.of("*")));

        assertEquals(0, result.getRowCount());
        assertEquals(2, result.getSchema().size());
    }

    @Test
    void starCombinedWithColumnWouldDuplicateColumn() {
        assertThrows(IllegalArgumentException.class,
            () -> engine.processQuery(new Query("students", List.of("*", "name"))));
    }

    @Test
    void unknownTableThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> engine.processQuery(new Query("missing", List.of("*"))));
        assertTrue(e.getMessage().contains("Table not found"));
    }

    @Test
    void unknownColumnThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> engine.processQuery(new Query("students", List.of("nope"))));
        assertTrue(e.getMessage().contains("Column not found"));
    }

    @Test
    void nullDatabaseRejected() {
        assertThrows(IllegalArgumentException.class, () -> new QueryEngine(null));
    }

    @Test
    void queryResultIsImmutableCopy() {
        QueryResult result = engine.processQuery(new Query("students", List.of("*")));

        assertThrows(UnsupportedOperationException.class,
            () -> result.getRows().add(row("Eve", 1)));
    }
}
