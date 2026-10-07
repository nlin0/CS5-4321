package com.group3.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.group3.expression.ComparisonExpression;
import com.group3.expression.Expression;
import com.group3.expression.InExpression;
import com.group3.expression.IsNullExpression;
import com.group3.expression.LogicalExpression;
import com.group3.operator.AggregateExpression;
import com.group3.operator.AggregateFunction;
import com.group3.parser.OrderByItem;
import com.group3.parser.Query;
import com.group3.parser.SelectColumn;

/** Task 4: FROM, WHERE, SELECT projection and aliases, built by hand with the full Query constructor. */
class QueryEngineTask4Test {

    private QueryEngine engine;

    @BeforeEach
    void setUp() {
        Table people = new Table("people", new Schema(List.of(
            new Column("name", DataType.STRING),
            new Column("dept", DataType.STRING),
            new Column("age", DataType.INTEGER),
            new Column("salary", DataType.FLOAT)
        )));
        people.addRow(person("Ada", "eng", 36, 120000.0));
        people.addRow(person("Bob", "eng", 28, 95000.0));
        people.addRow(person("Cy", "math", null, 70000.0));
        people.addRow(person("Dee", "math", 45, null));
        people.addRow(person("Eve", "eng", 31, 105000.0));

        Table solo = new Table("solo", people.getSchema());
        solo.addRow(person("Zed", "ops", 50, 1000.0));

        Database database = new Database();
        database.addTable(people);
        database.addTable(new Table("empty", people.getSchema()));
        database.addTable(solo);
        engine = new QueryEngine(database);
    }

    // --- projection and aliases ---

    @Test
    void selectStarKeepsAllColumnsInOrder() {
        QueryResult r = run(select(null, SelectColumn.star()));

        assertEquals(List.of("name", "dept", "age", "salary"), columnNames(r));
        assertEquals(5, r.getRowCount());
        assertEquals(List.of("Ada", "Bob", "Cy", "Dee", "Eve"), column(r, 0));
    }

    @Test
    void selectListReordersColumns() {
        QueryResult r = run(select(null, SelectColumn.column("age", null), SelectColumn.column("name", null)));

        assertEquals(List.of("age", "name"), columnNames(r));
        assertEquals(36, r.getRows().get(0).get(0).getValue());
        assertEquals("Ada", r.getRows().get(0).get(1).getValue());
    }

    @Test
    void columnAliasRenamesOutputButKeepsValuesAndType() {
        QueryResult r = run(select(null, SelectColumn.column("name", "who")));

        Column who = r.getSchema().getColumn(0);
        assertEquals("who", who.getName());
        assertEquals(DataType.STRING, who.getType());
        assertTrue(who.isNullable());
        assertEquals(List.of("Ada", "Bob", "Cy", "Dee", "Eve"), column(r, 0));
    }

    @Test
    void nullsPassThroughProjectionUnchanged() {
        QueryResult r = run(select(null, SelectColumn.column("age", null), SelectColumn.column("salary", null)));

        assertTrue(r.getRows().get(2).get(0).isNull());   // Cy's age
        assertTrue(r.getRows().get(3).get(1).isNull());   // Dee's salary
        assertEquals(DataType.INTEGER, r.getRows().get(2).get(0).getType());
    }

    // --- WHERE ---

    @Test
    void whereFiltersRows() {
        QueryResult r = run(select(gt("age", 30), SelectColumn.column("name", null)));

        // Cy's NULL age never satisfies a comparison.
        assertEquals(List.of("Ada", "Dee", "Eve"), column(r, 0));
    }

    @Test
    void whereWithAndAndOr() {
        Expression and = LogicalExpression.and(eq("dept", "eng"), gt("age", 30));
        assertEquals(List.of("Ada", "Eve"), column(run(select(and, SelectColumn.column("name", null))), 0));

        Expression or = LogicalExpression.or(eq("dept", "math"),
            ComparisonExpression.of("age", "<", new Value(DataType.INTEGER, 30)));
        assertEquals(List.of("Bob", "Cy", "Dee"), column(run(select(or, SelectColumn.column("name", null))), 0));
    }

    @Test
    void whereWithInAndNot() {
        Expression notMath = LogicalExpression.not(
            InExpression.of("dept", List.of(new Value(DataType.STRING, "math"))));

        assertEquals(List.of("Ada", "Bob", "Eve"), column(run(select(notMath, SelectColumn.column("name", null))), 0));
    }

    @Test
    void whereIsNullAndIsNotNull() {
        assertEquals(List.of("Cy"),
            column(run(select(IsNullExpression.isNull("age"), SelectColumn.column("name", null))), 0));
        assertEquals(List.of("Ada", "Bob", "Cy", "Eve"),
            column(run(select(IsNullExpression.isNotNull("salary"), SelectColumn.column("name", null))), 0));
    }

    @Test
    void whereCanUseColumnThatIsNotSelected() {
        QueryResult r = run(select(eq("dept", "math"), SelectColumn.column("name", "who")));

        assertEquals(List.of("who"), columnNames(r));
        assertEquals(List.of("Cy", "Dee"), column(r, 0));
    }

    // --- aggregates ---

    @Test
    void whereRunsBeforeAggregation() {
        AggregateExpression count = new AggregateExpression(AggregateFunction.COUNT, "*", "n");
        Query query = new Query("people", null, List.of(SelectColumn.aggregate(count.outputName())),
            List.of(), List.of(count), eq("dept", "eng"), null, List.of(), null, false);

        QueryResult r = run(query);
        assertEquals(1, r.getRowCount());
        assertEquals(3, r.getRows().get(0).get(0).getValue());
    }

    @Test
    void aggregateOutputFollowsSelectListOrder() {
        AggregateExpression count = new AggregateExpression(AggregateFunction.COUNT, "*", null);
        Query query = new Query("people", null,
            List.of(SelectColumn.aggregate(count.outputName()), SelectColumn.column("dept", null)),
            List.of("dept"), List.of(count), null, null, List.of(), null, false);

        QueryResult r = run(query);
        assertEquals(List.of("count(*)", "dept"), columnNames(r));
        assertEquals(3, r.getRows().get(0).get(0).getValue());
        assertEquals("eng", r.getRows().get(0).get(1).getValue());
        assertEquals(2, r.getRows().get(1).get(0).getValue());
        assertEquals("math", r.getRows().get(1).get(1).getValue());
    }

    @Test
    void groupColumnAliasAndWhereWithGroupBy() {
        AggregateExpression count = new AggregateExpression(AggregateFunction.COUNT, "*", "n");
        Query query = new Query("people", null,
            List.of(SelectColumn.column("dept", "team"), SelectColumn.aggregate(count.outputName())),
            List.of("dept"), List.of(count), gt("age", 30), null, List.of(), null, false);

        QueryResult r = run(query);
        assertEquals(List.of("team", "n"), columnNames(r));
        assertEquals(List.of("eng", "math"), column(r, 0));
        assertEquals(List.of(2, 1), column(r, 1));
    }

    // --- ORDER BY with projection ---

    @Test
    void orderByColumnThatIsNotSelected() {
        Query query = new Query("people", null, List.of(SelectColumn.column("name", null)),
            List.of(), List.of(), null, null, List.of(new OrderByItem("age", false)), null, false);

        // DESC puts NULLs last by default.
        assertEquals(List.of("Dee", "Ada", "Eve", "Bob", "Cy"), column(run(query), 0));
    }

    @Test
    void aliasedColumnIsSortedBySourceName() {
        Query query = new Query("people", null,
            List.of(SelectColumn.column("age", "years"), SelectColumn.column("name", null)),
            List.of(), List.of(), null, null, List.of(new OrderByItem("age", true)), null, false);

        QueryResult r = run(query);
        assertEquals(List.of("years", "name"), columnNames(r));
        assertEquals(List.of("Cy", "Bob", "Eve", "Ada", "Dee"), column(r, 1));
        assertTrue(r.getRows().get(0).get(0).isNull());
        assertEquals(28, r.getRows().get(1).get(0).getValue());
    }

    @Test
    void whereOrderByDistinctAndLimitTogether() {
        Query query = new Query("people", null, List.of(SelectColumn.column("dept", "d")),
            List.of(), List.of(), IsNullExpression.isNotNull("age"), null,
            List.of(new OrderByItem("dept", false)), 1, true);

        QueryResult r = run(query);
        assertEquals(List.of("d"), columnNames(r));
        assertEquals(List.of("math"), column(r, 0));
    }

    // --- empty results and errors ---

    @Test
    void emptyResultKeepsSchema() {
        QueryResult r = run(select(gt("age", 100), SelectColumn.column("name", null), SelectColumn.column("age", "years")));

        assertEquals(0, r.getRowCount());
        assertEquals(List.of("name", "years"), columnNames(r));
    }

    @Test
    void unknownSelectColumnThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> run(select(null, SelectColumn.column("nope", "x"))));
        assertEquals("Column not found: nope", e.getMessage());
    }

    @Test
    void unknownWhereColumnThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> run(select(gt("nope", 1), SelectColumn.star())));
        assertEquals("Column not found: nope", e.getMessage());
    }

    @Test
    void unknownTableThrows() {
        Query query = new Query("missing", null, List.of(SelectColumn.star()),
            List.of(), List.of(), null, null, List.of(), null, false);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> run(query));
        assertTrue(e.getMessage().contains("Table not found"));
    }

    @Test
    void duplicateOutputNameThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> run(select(null, SelectColumn.star(), SelectColumn.column("salary", "name"))));
        assertTrue(e.getMessage().contains("Duplicate column"));
    }

    // --- Query construction ---

    @Test
    void oldConstructorBuildsSelectListAndDefaults() {
        AggregateExpression count = new AggregateExpression(AggregateFunction.COUNT, "*", "n");
        Query query = new Query("people", List.of("dept"), List.of("dept"), List.of(count));

        assertEquals(List.of(SelectColumn.column("dept", null), SelectColumn.aggregate("n")), query.getSelectList());
        assertEquals(List.of("dept"), query.getColumns());
        assertNull(query.getWhere());
        assertNull(query.getTableAlias());
        assertEquals(List.of(SelectColumn.star()), new Query("people", List.of("*")).getSelectList());
    }

    @Test
    void newConstructorDerivesColumnsAndKeepsAlias() {
        AggregateExpression count = new AggregateExpression(AggregateFunction.COUNT, "*", null);
        Query query = new Query("people", "p",
            List.of(SelectColumn.star(), SelectColumn.aggregate(count.outputName()), SelectColumn.column("age", "years")),
            List.of(), List.of(count), null, null, List.of(), null, false);

        assertEquals(List.of("*", "age"), query.getColumns());
        assertEquals("p", query.getTableAlias());
        assertEquals("years", query.getSelectList().get(2).outputName());
        assertThrows(UnsupportedOperationException.class, () -> query.getSelectList().add(SelectColumn.star()));
    }

    @Test
    void newConstructorRejectsNegativeLimit() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new Query("people", null, List.of(SelectColumn.star()),
                List.of(), List.of(), null, null, List.of(), -1, false));
        assertEquals("LIMIT must be non-negative: -1", e.getMessage());
    }

    @Test
    void selectColumnHelpers() {
        assertTrue(SelectColumn.star().isStar());
        assertEquals("name", SelectColumn.column("name", null).outputName());
        assertEquals("name", SelectColumn.column("name", " ").outputName());
        assertEquals("n", SelectColumn.column("name", "n").outputName());
        assertTrue(SelectColumn.aggregate("count(*)").aggregate());
        assertThrows(IllegalArgumentException.class, () -> SelectColumn.column("", null));
    }

    // --- gap-check additions: projection ---

    @Test
    void singleColumnProjection() {
        // SELECT dept FROM people
        QueryResult r = run(select(null, SelectColumn.column("dept", null)));

        assertEquals(List.of("dept"), columnNames(r));
        assertEquals(List.of("eng", "eng", "math", "math", "eng"), column(r, 0));
    }

    @Test
    void allColumnsListedExplicitlyMatchesStar() {
        // SELECT name, dept, age, salary FROM people
        QueryResult explicit = run(select(null, SelectColumn.column("name", null), SelectColumn.column("dept", null),
            SelectColumn.column("age", null), SelectColumn.column("salary", null)));
        QueryResult star = run(select(null, SelectColumn.star()));

        assertEquals(columnNames(star), columnNames(explicit));
        assertEquals(star.getRows(), explicit.getRows());
    }

    @Test
    void oneAliasAmongSeveralColumns() {
        // SELECT name, age AS years, dept FROM people
        QueryResult r = run(select(null, SelectColumn.column("name", null),
            SelectColumn.column("age", "years"), SelectColumn.column("dept", null)));

        assertEquals(List.of("name", "years", "dept"), columnNames(r));
        assertEquals(DataType.INTEGER, r.getSchema().getColumn(1).getType());
        assertEquals(List.of("Ada", "Bob", "Cy", "Dee", "Eve"), column(r, 0));
        assertEquals(36, r.getRows().get(0).get(1).getValue());
        assertEquals(List.of("eng", "eng", "math", "math", "eng"), column(r, 2));
    }

    // --- gap-check additions: WHERE operators ---

    @Test
    void whereNotEqualExcludesNull() {
        // SELECT name FROM people WHERE age <> 36
        QueryResult r = run(select(cmp("age", "<>", 36), SelectColumn.column("name", null)));

        assertEquals(List.of("Bob", "Dee", "Eve"), column(r, 0));
    }

    @Test
    void whereLessOrEqualIncludesBoundary() {
        // SELECT name FROM people WHERE age <= 31
        QueryResult r = run(select(cmp("age", "<=", 31), SelectColumn.column("name", null)));

        assertEquals(List.of("Bob", "Eve"), column(r, 0));
    }

    @Test
    void whereGreaterOrEqualIncludesBoundary() {
        // SELECT name FROM people WHERE age >= 36
        QueryResult r = run(select(cmp("age", ">=", 36), SelectColumn.column("name", null)));

        assertEquals(List.of("Ada", "Dee"), column(r, 0));
    }

    @Test
    void whereIn() {
        // SELECT name FROM people WHERE age IN (28, 45)
        Expression in = InExpression.of("age",
            List.of(new Value(DataType.INTEGER, 28), new Value(DataType.INTEGER, 45)));

        assertEquals(List.of("Bob", "Dee"), column(run(select(in, SelectColumn.column("name", null))), 0));
    }

    @Test
    void whereMatchingEveryRowKeepsAllRowsInOrder() {
        // SELECT name FROM people WHERE dept IN ('eng', 'math')
        Expression in = InExpression.of("dept",
            List.of(new Value(DataType.STRING, "eng"), new Value(DataType.STRING, "math")));

        assertEquals(List.of("Ada", "Bob", "Cy", "Dee", "Eve"),
            column(run(select(in, SelectColumn.column("name", null))), 0));
    }

    // --- gap-check additions: NULLs ---

    @Test
    void nullsInsideAggregatesAfterWhere() {
        // SELECT COUNT(*), COUNT(salary), SUM(salary), AVG(age) FROM people WHERE dept = 'math'
        AggregateExpression countAll = new AggregateExpression(AggregateFunction.COUNT, "*", null);
        AggregateExpression countSalary = new AggregateExpression(AggregateFunction.COUNT, "salary", null);
        AggregateExpression sumSalary = new AggregateExpression(AggregateFunction.SUM, "salary", null);
        AggregateExpression avgAge = new AggregateExpression(AggregateFunction.AVG, "age", null);
        List<AggregateExpression> aggregates = List.of(countAll, countSalary, sumSalary, avgAge);
        Query query = new Query("people", null, aggregateItems(aggregates), List.of(), aggregates,
            eq("dept", "math"), null, List.of(), null, false);

        Row row = run(query).getRows().get(0);
        assertEquals(2, row.get(0).getValue());        // Cy and Dee
        assertEquals(1, row.get(1).getValue());        // Dee's NULL salary is not counted
        assertEquals(70000.0, row.get(2).getValue());  // and not summed
        assertEquals(45.0, row.get(3).getValue());     // Cy's NULL age is not averaged
    }

    @Test
    void notOverComparisonReturnsRowWithNullAge() {
        // SELECT name FROM people WHERE NOT (age > 30)
        // Differs from standard SQL: there NOT (NULL > 30) is UNKNOWN and Cy is excluded (result: Bob).
        // The team's NOT simply inverts false, so Cy is returned. Known gap, documented here.
        QueryResult r = run(select(LogicalExpression.not(gt("age", 30)), SelectColumn.column("name", null)));

        assertEquals(List.of("Bob", "Cy"), column(r, 0));
    }

    // --- gap-check additions: pipeline order ---

    @Test
    void whereGroupByAndHavingTogether() {
        // SELECT dept, COUNT(*) AS n FROM people WHERE age IS NOT NULL GROUP BY dept HAVING n >= 2
        AggregateExpression count = new AggregateExpression(AggregateFunction.COUNT, "*", "n");
        Query query = new Query("people", null,
            List.of(SelectColumn.column("dept", null), SelectColumn.aggregate(count.outputName())),
            List.of("dept"), List.of(count), IsNullExpression.isNotNull("age"),
            ComparisonExpression.of("n", ">=", new Value(DataType.INTEGER, 2)), List.of(), null, false);

        // Without the WHERE, math would also have 2 rows and pass HAVING.
        QueryResult r = run(query);
        assertEquals(1, r.getRowCount());
        assertEquals("eng", r.getRows().get(0).get(0).getValue());
        assertEquals(3, r.getRows().get(0).get(1).getValue());
    }

    @Test
    void orderByThenLimit() {
        // SELECT name FROM people ORDER BY salary DESC LIMIT 2
        Query query = new Query("people", null, List.of(SelectColumn.column("name", null)),
            List.of(), List.of(), null, null, List.of(new OrderByItem("salary", false)), 2, false);

        assertEquals(List.of("Ada", "Eve"), column(run(query), 0));
    }

    @Test
    void distinctThenLimit() {
        // SELECT DISTINCT dept FROM people ORDER BY dept LIMIT 2
        Query query = new Query("people", null, List.of(SelectColumn.column("dept", null)),
            List.of(), List.of(), null, null, List.of(new OrderByItem("dept", true)), 2, true);

        // LIMIT before DISTINCT would give only [eng].
        assertEquals(List.of("eng", "math"), column(run(query), 0));
    }

    @Test
    void limitZeroKeepsSchema() {
        // SELECT name, age FROM people LIMIT 0
        Query query = new Query("people", null,
            List.of(SelectColumn.column("name", null), SelectColumn.column("age", null)),
            List.of(), List.of(), null, null, List.of(), 0, false);

        QueryResult r = run(query);
        assertEquals(0, r.getRowCount());
        assertEquals(List.of("name", "age"), columnNames(r));
    }

    // --- gap-check additions: aggregates in output ---

    @Test
    void severalAggregatesMixedWithGroupColumn() {
        // SELECT SUM(salary) AS total, dept, COUNT(*) AS n, MAX(age) AS oldest FROM people GROUP BY dept
        AggregateExpression total = new AggregateExpression(AggregateFunction.SUM, "salary", "total");
        AggregateExpression count = new AggregateExpression(AggregateFunction.COUNT, "*", "n");
        AggregateExpression oldest = new AggregateExpression(AggregateFunction.MAX, "age", "oldest");
        Query query = new Query("people", null,
            List.of(SelectColumn.aggregate(total.outputName()), SelectColumn.column("dept", null),
                SelectColumn.aggregate(count.outputName()), SelectColumn.aggregate(oldest.outputName())),
            List.of("dept"), List.of(total, count, oldest), null, null, List.of(), null, false);

        QueryResult r = run(query);
        assertEquals(List.of("total", "dept", "n", "oldest"), columnNames(r));
        assertEquals(List.of(DataType.FLOAT, DataType.STRING, DataType.INTEGER, DataType.INTEGER),
            r.getSchema().getColumns().stream().map(Column::getType).toList());
        assertEquals(List.of(320000.0, "eng", 3, 36), values(r.getRows().get(0)));
        assertEquals(List.of(70000.0, "math", 2, 45), values(r.getRows().get(1)));
    }

    // --- gap-check additions: edge cases ---

    @Test
    void emptyTableKeepsSchema() {
        // SELECT * FROM empty
        Query query = new Query("empty", null, List.of(SelectColumn.star()),
            List.of(), List.of(), null, null, List.of(), null, false);

        QueryResult r = run(query);
        assertEquals(0, r.getRowCount());
        assertEquals(List.of("name", "dept", "age", "salary"), columnNames(r));
    }

    @Test
    void countStarOnEmptyTableReturnsOneRowWithZero() {
        // SELECT COUNT(*) FROM empty
        AggregateExpression count = new AggregateExpression(AggregateFunction.COUNT, "*", null);
        Query query = new Query("empty", null, List.of(SelectColumn.aggregate(count.outputName())),
            List.of(), List.of(count), null, null, List.of(), null, false);

        QueryResult r = run(query);
        assertEquals(1, r.getRowCount());
        assertEquals(0, r.getRows().get(0).get(0).getValue());
    }

    @Test
    void oneRowTable() {
        // SELECT name, age AS years FROM solo WHERE age > 0
        Query query = new Query("solo", null,
            List.of(SelectColumn.column("name", null), SelectColumn.column("age", "years")),
            List.of(), List.of(), gt("age", 0), null, List.of(), null, false);

        QueryResult r = run(query);
        assertEquals(List.of("name", "years"), columnNames(r));
        assertEquals(1, r.getRowCount());
        assertEquals(List.of("Zed", 50), values(r.getRows().get(0)));
    }

    // --- gap-check additions: errors ---

    @Test
    void duplicatePlainColumnThrows() {
        // SELECT name, name FROM people
        // Differs from standard SQL, which allows duplicate output names; the team's Schema rejects them.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> run(select(null, SelectColumn.column("name", null), SelectColumn.column("name", null))));
        assertEquals("Duplicate column: name", e.getMessage());
    }

    // --- helpers ---

    private QueryResult run(Query query) {
        return engine.processQuery(query);
    }

    /** SELECT items FROM people [WHERE where], with no grouping, ordering or limit. */
    private static Query select(Expression where, SelectColumn... items) {
        return new Query("people", null, List.of(items), List.of(), List.of(),
            where, null, List.of(), null, false);
    }

    private static Expression gt(String column, int value) {
        return ComparisonExpression.of(column, ">", new Value(DataType.INTEGER, value));
    }

    private static Expression cmp(String column, String symbol, int value) {
        return ComparisonExpression.of(column, symbol, new Value(DataType.INTEGER, value));
    }

    private static List<SelectColumn> aggregateItems(List<AggregateExpression> aggregates) {
        return aggregates.stream().map(a -> SelectColumn.aggregate(a.outputName())).toList();
    }

    private static List<Object> values(Row row) {
        return row.getValues().stream().map(Value::getValue).toList();
    }

    private static Expression eq(String column, String value) {
        return ComparisonExpression.of(column, "=", new Value(DataType.STRING, value));
    }

    private static Row person(String name, String dept, Integer age, Double salary) {
        return new Row(List.of(
            new Value(DataType.STRING, name),
            new Value(DataType.STRING, dept),
            new Value(DataType.INTEGER, age),
            new Value(DataType.FLOAT, salary)
        ));
    }

    private static List<String> columnNames(QueryResult r) {
        return r.getSchema().getColumns().stream().map(Column::getName).toList();
    }

    private static List<Object> column(QueryResult r, int index) {
        return r.getRows().stream().map(row -> row.get(index).getValue()).toList();
    }
}
