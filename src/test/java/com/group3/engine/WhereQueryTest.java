package com.group3.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.parser.SQLParser;

/** SQL text through parser and engine; hand-built Query objects cannot catch missing WHERE wiring. */
class WhereQueryTest {
    private final SQLParser parser = new SQLParser();
    private QueryEngine engine;

    @BeforeEach
    void setUp() {
        Schema schema = new Schema(List.of(
            new Column("id", DataType.INTEGER), new Column("name", DataType.STRING),
            new Column("age", DataType.INTEGER), new Column("threshold", DataType.INTEGER),
            new Column("score", DataType.FLOAT), new Column("active", DataType.BOOLEAN),
            new Column("dept", DataType.STRING)));
        Table people = new Table("people", schema);
        people.addRow(row(1, "Ada", 30, 25, 3.5, true, "eng"));
        people.addRow(row(2, "Bob", 20, 25, 2.0, false, "eng"));
        people.addRow(row(3, "Cy", null, 30, null, true, "math"));
        people.addRow(row(4, "Dee", 40, 40, 4.0, false, "math"));
        people.addRow(row(5, "O'Brien", -5, 0, -0.5, true, "eng"));
        Database database = new Database();
        database.addTable(people);
        database.addTable(new Table("empty", schema));
        engine = new QueryEngine(database);
    }

    private static Row row(int id, String name, Integer age, int threshold,
                           Double score, boolean active, String dept) {
        return new Row(List.of(new Value(DataType.INTEGER, id), new Value(DataType.STRING, name),
            new Value(DataType.INTEGER, age), new Value(DataType.INTEGER, threshold),
            new Value(DataType.FLOAT, score), new Value(DataType.BOOLEAN, active),
            new Value(DataType.STRING, dept)));
    }

    private QueryResult run(String sql) {
        return engine.processQuery(parser.parse(sql));
    }

    private static List<Object> column(QueryResult result, int index) {
        return result.getRows().stream().map(row -> row.get(index).getValue()).toList();
    }

    static Stream<Arguments> predicates() {
        return Stream.of(
            Arguments.of("id = 1", List.of(1)),
            Arguments.of("id != 1", List.of(2, 3, 4, 5)),
            Arguments.of("id <> 1", List.of(2, 3, 4, 5)),
            Arguments.of("id < 3", List.of(1, 2)),
            Arguments.of("id <= 3", List.of(1, 2, 3)),
            Arguments.of("id > 3", List.of(4, 5)),
            Arguments.of("id >= 3", List.of(3, 4, 5)),
            Arguments.of("age >= threshold", List.of(1, 4)),
            Arguments.of("25 < age", List.of(1, 4)),
            Arguments.of("age >= 20.0", List.of(1, 2, 4)),
            Arguments.of("age = -5", List.of(5)),
            Arguments.of("score = -5e-1", List.of(5)),
            Arguments.of("score = +3.5", List.of(1)),
            Arguments.of("name = 'O''Brien'", List.of(5)),
            Arguments.of("active = true", List.of(1, 3, 5)),
            Arguments.of("active = false", List.of(2, 4)),
            Arguments.of("id = 1 OR id = 2 AND active = false", List.of(1, 2)),
            Arguments.of("(id = 1 OR id = 2) AND active = false", List.of(2)),
            Arguments.of("NOT (id = 1 OR id = 2)", List.of(3, 4, 5)),
            Arguments.of("age IS NULL", List.of(3)),
            Arguments.of("age IS NOT NULL", List.of(1, 2, 4, 5)),
            Arguments.of("age = NULL", List.of()),
            Arguments.of("age <> NULL", List.of()),
            Arguments.of("NOT (age > 25)", List.of(2, 3, 5)),
            Arguments.of("age IN (20, NULL, 30.0)", List.of(1, 2)),
            Arguments.of("age IN (30)", List.of(1)),
            Arguments.of("age NOT IN (20, NULL, 30.0)", List.of(3, 4, 5)),
            Arguments.of("name IN ('Ada', 'O''Brien')", List.of(1, 5)),
            Arguments.of("active IN (true)", List.of(1, 3, 5)),
            Arguments.of("NOT (age IS NULL) AND age >= +20", List.of(1, 2, 4)),
            Arguments.of("1 = 1", List.of(1, 2, 3, 4, 5)),
            Arguments.of("NULL IS NULL", List.of(1, 2, 3, 4, 5))
        );
    }

    @ParameterizedTest
    @MethodSource("predicates")
    void returnsExactlyTheMatchingRows(String predicate, List<Integer> expectedIds) {
        assertEquals(expectedIds, column(run("SELECT id FROM people WHERE " + predicate), 0));
    }

    @Test
    void filtersOnUnselectedColumnsAndSupportsTableAliases() {
        assertEquals(List.of("Ada", "Dee"),
            column(run("SELECT name FROM people p WHERE p.age >= people.threshold"), 0));
    }

    @Test
    void filtersBeforeGroupingAndHaving() {
        QueryResult result = run("SELECT dept, COUNT(*) AS n FROM people WHERE age >= 20 "
            + "GROUP BY dept HAVING n >= 2");
        assertEquals(List.of("eng"), column(result, 0));
        assertEquals(List.of(2), column(result, 1));
    }

    @Test
    void combinesFilteringSortingDistinctAndLimit() {
        assertEquals(List.of("math"), column(run("SELECT DISTINCT dept FROM people "
            + "WHERE age >= 20 ORDER BY dept DESC LIMIT 1"), 0));
        assertEquals(List.of("Dee", "Ada"), column(run("SELECT name FROM people "
            + "WHERE age >= 20 ORDER BY age DESC LIMIT 2"), 0));
    }

    @Test
    void emptyAndUnmatchedResultsKeepProjectedSchema() {
        for (String sql : List.of("SELECT name FROM people WHERE id = 99", "SELECT name FROM empty WHERE id = 1")) {
            QueryResult result = run(sql);
            assertEquals(0, result.getRowCount());
            assertEquals("name", result.getSchema().getColumn(0).getName());
        }
    }

    @Test
    void reportsUnknownColumnsAndInvalidBooleanOrdering() {
        assertThrows(IllegalArgumentException.class, () -> run("SELECT id FROM people WHERE missing = 1"));
        assertThrows(IllegalArgumentException.class, () -> run("SELECT id FROM people WHERE active > false"));
    }

    @Test
    void queriesWithoutWhereKeepAllRows() {
        assertEquals(List.of(1, 2, 3, 4, 5), column(run("SELECT id FROM people"), 0));
    }
}
