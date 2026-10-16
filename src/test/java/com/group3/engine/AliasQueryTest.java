package com.group3.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.parser.SQLParser;

/** Alias regressions must start with SQL: manually constructed SELECT items already supported aliases. */
class AliasQueryTest {
    private final SQLParser parser = new SQLParser();
    private QueryEngine engine;
    private Table people;

    @BeforeEach
    void setUp() {
        Schema schema = new Schema(List.of(
            new Column("id", DataType.INTEGER, false), new Column("name", DataType.STRING),
            new Column("age", DataType.INTEGER), new Column("score", DataType.FLOAT),
            new Column("active", DataType.BOOLEAN, false), new Column("dept", DataType.STRING, false)));
        people = new Table("people", schema);
        people.addRow(row(1, "Bob", 20, 2.0, false, "eng"));
        people.addRow(row(2, "Ada", 30, 3.5, true, "math"));
        people.addRow(row(3, "Cy", 20, 2.0, true, "eng"));
        people.addRow(row(4, "Dee", null, null, false, "math"));
        people.addRow(row(5, "Eve", 30, 4.0, true, "eng"));
        Database database = new Database();
        database.addTable(people);
        database.addTable(new Table("empty", schema));
        engine = new QueryEngine(database);
    }

    private static Row row(int id, String name, Integer age, Double score, boolean active, String dept) {
        return new Row(List.of(new Value(DataType.INTEGER, id), new Value(DataType.STRING, name),
            new Value(DataType.INTEGER, age), new Value(DataType.FLOAT, score),
            new Value(DataType.BOOLEAN, active), new Value(DataType.STRING, dept)));
    }

    private QueryResult run(String sql) {
        return engine.processQuery(parser.parse(sql));
    }

    private static List<String> headers(QueryResult result) {
        return result.getSchema().getColumns().stream().map(Column::getName).toList();
    }

    private static List<Object> column(QueryResult result, int index) {
        return result.getRows().stream().map(row -> row.get(index).getValue()).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "name", "age", "score", "active"})
    void renamingPreservesValuesTypesNullabilityAndInputTable(String source) {
        QueryResult result = run("SELECT " + source + " AS renamed FROM people");
        Column original = people.getSchema().getColumn(source);
        Column renamed = result.getSchema().getColumn(0);
        int index = people.getSchema().indexOf(source);
        assertEquals(List.of("renamed"), headers(result));
        assertEquals(original.getType(), renamed.getType());
        assertEquals(original.isNullable(), renamed.isNullable());
        assertEquals(people.getRows().stream().map(row -> row.get(index).getValue()).toList(), column(result, 0));
        assertEquals(source, people.getSchema().getColumn(index).getName());
    }

    static Stream<Arguments> sorts() {
        return Stream.of(
            Arguments.of("age AS years, name AS person", "years, person", List.of("Dee", "Bob", "Cy", "Ada", "Eve")),
            Arguments.of("age AS years, name AS person", "years DESC, person", List.of("Ada", "Eve", "Bob", "Cy", "Dee")),
            Arguments.of("age AS Years, name AS person", "yEaRs DESC, PERSON", List.of("Ada", "Eve", "Bob", "Cy", "Dee")),
            Arguments.of("age AS years, name AS person", "age DESC, name", List.of("Ada", "Eve", "Bob", "Cy", "Dee")),
            Arguments.of("age AS years, name AS person", "years ASC NULLS LAST, person", List.of("Bob", "Cy", "Ada", "Eve", "Dee")),
            Arguments.of("age AS years, name AS person", "years DESC NULLS FIRST, person", List.of("Dee", "Ada", "Eve", "Bob", "Cy")),
            Arguments.of("age AS \"Years Old\", name AS person", "\"Years Old\" DESC, person", List.of("Ada", "Eve", "Bob", "Cy", "Dee")),
            Arguments.of("age AS \"Years \"\"Old\"\"\", name AS person", "\"Years \"\"Old\"\"\" DESC, person", List.of("Ada", "Eve", "Bob", "Cy", "Dee")),
            Arguments.of("age AS name, name AS person", "name DESC, person", List.of("Ada", "Eve", "Bob", "Cy", "Dee")),
            Arguments.of("age AS name, name AS person", "2", List.of("Ada", "Bob", "Cy", "Dee", "Eve")),
            Arguments.of("age AS name, name AS person", "people.name", List.of("Ada", "Bob", "Cy", "Dee", "Eve")),
            Arguments.of("age AS name, name AS person", "1 DESC, 2", List.of("Ada", "Eve", "Bob", "Cy", "Dee"))
        );
    }

    @ParameterizedTest
    @MethodSource("sorts")
    void sortsByAliasesSourcesOrPositions(String projection, String orderBy, List<String> names) {
        assertEquals(names, column(run("SELECT " + projection + " FROM people ORDER BY " + orderBy), 1));
    }

    @Test
    void projectsOneSourceUnderMultipleAliases() {
        QueryResult result = run("SELECT age AS original_age, age AS copied_age FROM people");
        assertEquals(List.of("original_age", "copied_age"), headers(result));
        assertEquals(column(result, 0), column(result, 1));
    }

    @Test
    void quotedAliasSpellingIsPreserved() {
        assertEquals(List.of("Person \"Name\""), headers(run("SELECT name AS \"Person \"\"Name\"\"\" FROM people")));
    }

    @Test
    void wildcardAndAliasedColumnKeepTheirWrittenPositions() {
        QueryResult result = run("SELECT name AS person, *, age AS years FROM people LIMIT 1");
        assertEquals(List.of("person", "id", "name", "age", "score", "active", "dept", "years"), headers(result));
        assertEquals(List.of("Bob", 1, "Bob", 20, 2.0, false, "eng", 20),
            result.getRows().get(0).getValues().stream().map(Value::getValue).toList());
    }

    @Test
    void filtersBySourceThenProjectsAndLimits() {
        QueryResult result = run("SELECT name person FROM people WHERE age >= 30 ORDER BY person DESC LIMIT 1");
        assertEquals(List.of("person"), headers(result));
        assertEquals(List.of("Eve"), column(result, 0));
    }

    @Test
    void distinctUsesValuesAndResolvesAliasedSorting() {
        QueryResult result = run("SELECT DISTINCT age AS years FROM people ORDER BY years NULLS LAST LIMIT 3");
        assertEquals(List.of("years"), headers(result));
        assertEquals(Arrays.asList(20, 30, null), column(result, 0));
    }

    @Test
    void groupColumnAliasAndAggregateAliasWorkWithExistingHaving() {
        QueryResult result = run("SELECT dept AS team, COUNT(*) AS members FROM people "
            + "WHERE age >= 20 GROUP BY dept HAVING members >= 2 ORDER BY team");
        assertEquals(List.of("team", "members"), headers(result));
        assertEquals(List.of("eng"), column(result, 0));
        assertEquals(List.of(3), column(result, 1));
    }

    @Test
    void aggregateFirstUsesWrittenOrderForProjectionAndSorting() {
        QueryResult result = run("SELECT COUNT(*) AS n, dept AS team FROM people GROUP BY dept ORDER BY 1 DESC");
        assertEquals(List.of("n", "team"), headers(result));
        assertEquals(List.of(3, 2), column(result, 0));
        assertEquals(List.of("eng", "math"), column(result, 1));
    }

    @Test
    void interleavesMultipleAggregatesAndGroupColumn() {
        QueryResult result = run("SELECT MAX(age) AS oldest, dept AS team, COUNT(*) AS n "
            + "FROM people GROUP BY dept ORDER BY oldest DESC, team DESC");
        assertEquals(List.of("oldest", "team", "n"), headers(result));
        assertEquals(List.of(30, "math", 2), result.getRows().get(0).getValues().stream().map(Value::getValue).toList());
        assertEquals(List.of(30, "eng", 3), result.getRows().get(1).getValues().stream().map(Value::getValue).toList());
    }

    @Test
    void quotedAggregateAliasCanBeUsedForSorting() {
        QueryResult result = run("SELECT COUNT(*) AS \"Row Count\", dept AS team "
            + "FROM people GROUP BY dept ORDER BY \"Row Count\" DESC");
        assertEquals(List.of("Row Count", "team"), headers(result));
        assertEquals(List.of(3, 2), column(result, 0));
    }

    @Test
    void quotedAggregateAliasStillWorksInHaving() {
        QueryResult result = run("SELECT COUNT(*) AS \"Row \"\"Count\"\"\", dept AS team "
            + "FROM people GROUP BY dept HAVING \"Row \"\"Count\"\"\" > 2 "
            + "ORDER BY \"Row \"\"Count\"\"\" DESC");
        assertEquals(List.of("Row \"Count\"", "team"), headers(result));
        assertEquals(List.of(3), column(result, 0));
        assertEquals(List.of("eng"), column(result, 1));
    }

    @Test
    void spacesInsideQuotedAggregateAliasArePreservedDuringResolution() {
        QueryResult result = run("SELECT COUNT(*) AS \" Row Count \", dept AS team "
            + "FROM people GROUP BY dept HAVING \" Row Count \" > 2 ORDER BY \" Row Count \"");
        assertEquals(List.of(" Row Count ", "team"), headers(result));
        assertEquals(List.of(3), column(result, 0));
    }

    @Test
    void aggregateSortingByFunctionTextStillWorks() {
        QueryResult result = run("SELECT COUNT(*) AS n, dept AS team FROM people GROUP BY dept ORDER BY COUNT(*)");
        assertEquals(List.of("math", "eng"), column(result, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT age AS years FROM empty",
        "SELECT age AS years FROM people WHERE id = 99",
        "SELECT age AS years FROM people LIMIT 0"
    })
    void emptyResultsRetainAliasedSchema(String sql) {
        QueryResult result = run(sql);
        assertEquals(List.of("years"), headers(result));
        assertEquals(DataType.INTEGER, result.getSchema().getColumn(0).getType());
        assertTrue(result.getSchema().getColumn(0).isNullable());
        assertEquals(0, result.getRowCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT id AS duplicate, name AS DUPLICATE FROM people",
        "SELECT id AS name, name FROM people",
        "SELECT *, id AS name FROM people",
        "SELECT name AS NAME, * FROM people",
        "SELECT COUNT(*) AS n, MAX(age) AS N FROM people",
        "SELECT dept AS n, COUNT(*) AS n FROM people GROUP BY dept"
    })
    void rejectsDuplicateOutputNames(String sql) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> run(sql));
        assertTrue(error.getMessage().contains("Duplicate column"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT missing AS person FROM people",
        "SELECT name AS person FROM people ORDER BY missing",
        "SELECT name AS person FROM people WHERE person = 'Ada'"
    })
    void aliasesDoNotHideMissingSourcesOrBecomeWhereColumns(String sql) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> run(sql));
        assertTrue(error.getMessage().contains("Column not found"));
    }
}
