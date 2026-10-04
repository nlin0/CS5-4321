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

    @Test
    void orderByLimitAndDistinct() {
        com.group3.parser.SQLParser parser = new com.group3.parser.SQLParser();
        Table t = new Table("t", new Schema(List.of(
            new Column("name", DataType.STRING), new Column("age", DataType.INTEGER))));
        t.addRow(row("Bob", 20));
        t.addRow(row("Ada", 30));
        t.addRow(row("Cy", 20));
        t.addRow(row("Dee", null));
        t.addRow(row("Eve", 30));
        Database db = new Database();
        db.addTable(t);
        QueryEngine e = new QueryEngine(db);

        QueryResult r = e.processQuery(parser.parse("SELECT name FROM t ORDER BY age DESC, name ASC"));
        assertEquals(List.of("Ada", "Eve", "Bob", "Cy", "Dee"), names(r));

        r = e.processQuery(parser.parse("SELECT name FROM t ORDER BY age, name DESC LIMIT 2"));
        assertEquals(List.of("Dee", "Cy"), names(r));

        r = e.processQuery(parser.parse("SELECT DISTINCT age FROM t ORDER BY age DESC"));
        assertEquals(3, r.getRowCount());
        assertEquals(30, r.getRows().get(0).get(0).getValue());

        r = e.processQuery(parser.parse("SELECT age, COUNT(*) AS n FROM t GROUP BY age ORDER BY n DESC, age LIMIT 1"));
        assertEquals(20, r.getRows().get(0).get(0).getValue());

        assertEquals(0, e.processQuery(parser.parse("SELECT * FROM t LIMIT 0")).getRowCount());
        assertThrows(IllegalArgumentException.class,
            () -> e.processQuery(parser.parse("SELECT * FROM t ORDER BY nope")));
        assertThrows(UnsupportedOperationException.class, () -> parser.parse("SELECT * FROM t LIMIT 1 OFFSET 1"));
    }

    private static List<Object> names(QueryResult r) {
        return r.getRows().stream().map(x -> x.get(0).getValue()).toList();
    }
}
