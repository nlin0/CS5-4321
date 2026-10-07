package com.group3.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.group3.parser.SQLParser;

/** End-to-end tests for ORDER BY, LIMIT and DISTINCT: SQL text through parser and engine. */
class ResultOperationsTest {

    private final SQLParser parser = new SQLParser();
    private QueryEngine engine;

    @BeforeEach
    void setUp() {
        Table t = new Table("t", new Schema(List.of(
                new Column("name", DataType.STRING),
                new Column("age", DataType.INTEGER),
                new Column("dept", DataType.STRING))));
        t.addRow(row("Bob", 20, "eng"));
        t.addRow(row("Ada", 30, "math"));
        t.addRow(row("Cy", 20, "eng"));
        t.addRow(row("Dee", null, "math"));
        t.addRow(row("Eve", 30, "eng"));
        t.addRow(row("Ada", 30, "math"));
        t.addRow(row("Fay", 25, "eng"));
        Database db = new Database();
        db.addTable(t);
        engine = new QueryEngine(db);
    }

    private static Row row(String name, Integer age, String dept) {
        return new Row(List.of(
                new Value(DataType.STRING, name),
                new Value(DataType.INTEGER, age),
                new Value(DataType.STRING, dept)));
    }

    private QueryResult run(String sql) {
        return engine.processQuery(parser.parse(sql));
    }

    private static List<Object> column(QueryResult result, int index) {
        return result.getRows().stream().map(r -> r.get(index).getValue()).toList();
    }

    // ---------- ORDER BY ----------

    @Test
    void ordersByColumnThatIsNotSelected() {
        QueryResult r = run("SELECT name FROM t ORDER BY age DESC, name");

        assertEquals(1, r.getSchema().size());
        assertEquals(List.of("Ada", "Ada", "Eve", "Fay", "Bob", "Cy", "Dee"), column(r, 0));
    }

    @Test
    void explicitNullsLast() {
        assertEquals(List.of("Bob", "Cy", "Fay", "Ada", "Ada", "Eve", "Dee"),
                column(run("SELECT name FROM t ORDER BY age NULLS LAST, name"), 0));
    }

    @Test
    void explicitNullsFirstWithDescending() {
        assertEquals(List.of("Dee", "Ada"),
                column(run("SELECT name FROM t ORDER BY age DESC NULLS FIRST, name LIMIT 2"), 0));
    }

    @Test
    void ordersBySelectListPosition() {
        assertEquals(List.of("Dee", "Bob", "Cy"),
                column(run("SELECT name, age FROM t ORDER BY 2, 1 LIMIT 3"), 0));
    }

    @Test
    void ordersByColumnAlias() {
        QueryResult r = run("SELECT age AS years, name FROM t ORDER BY years DESC, name LIMIT 1");

        assertEquals(30, r.getRows().get(0).get(0).getValue());
        assertEquals("Ada", r.getRows().get(0).get(1).getValue());
    }

    @Test
    void unknownOrderByColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> run("SELECT name FROM t ORDER BY nope"));
    }

    // ---------- LIMIT ----------

    @Test
    void limitLargerThanRowCountReturnsEverything() {
        assertEquals(7, run("SELECT * FROM t LIMIT 100").getRowCount());
    }

    @Test
    void limitZeroReturnsNoRowsButKeepsSchema() {
        QueryResult r = run("SELECT name, age FROM t LIMIT 0");

        assertEquals(0, r.getRowCount());
        assertEquals(2, r.getSchema().size());
    }

    @Test
    void limitWithoutOrderByKeepsTableOrder() {
        assertEquals(List.of("Bob", "Ada"), column(run("SELECT name FROM t LIMIT 2"), 0));
    }

    // ---------- DISTINCT ----------

    @Test
    void distinctRemovesDuplicateRows() {
        assertEquals(6, run("SELECT DISTINCT name, age FROM t").getRowCount());
        assertEquals(List.of("eng", "math"), column(run("SELECT DISTINCT dept FROM t"), 0));
    }

    @Test
    void distinctThenOrderThenLimit() {
        assertEquals(List.of("math", "eng"), column(run("SELECT DISTINCT dept FROM t ORDER BY dept DESC"), 0));
        assertEquals(List.of("Ada", "Bob", "Cy"),
                column(run("SELECT DISTINCT name FROM t ORDER BY name LIMIT 3"), 0));
    }

    @Test
    void distinctKeepsOneNull() {
        List<Object> ages = column(run("SELECT DISTINCT age FROM t ORDER BY age"), 0);

        assertEquals(4, ages.size());
        assertEquals(null, ages.get(0));
    }

    // ---------- with GROUP BY ----------

    @Test
    void ordersByAggregateFunctionText() {
        QueryResult r = run("SELECT dept, COUNT(*) FROM t GROUP BY dept ORDER BY COUNT(*) DESC");

        assertEquals("eng", r.getRows().get(0).get(0).getValue());
        assertEquals(4, r.getRows().get(0).get(1).getValue());
    }

    @Test
    void ordersByAggregatePosition() {
        assertEquals(List.of("math"),
                column(run("SELECT dept, COUNT(*) AS n FROM t GROUP BY dept ORDER BY 2 LIMIT 1"), 0));
    }

    @Test
    void tiedAggregatesFallBackToSecondKey() {
        assertEquals(List.of("math", "eng"),
                column(run("SELECT dept, MAX(age) AS oldest FROM t GROUP BY dept ORDER BY oldest DESC, dept DESC"), 0));
    }
}
