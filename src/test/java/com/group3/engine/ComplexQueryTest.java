package com.group3.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.expression.ComparisonExpression;
import com.group3.operator.AggregateExpression;
import com.group3.operator.AggregateFunction;
import com.group3.parser.OrderByItem;
import com.group3.parser.Query;
import com.group3.parser.SQLParser;
import com.group3.parser.SelectColumn;

/** Exercises interactions between SQL parsing and the complete execution pipeline. */
class ComplexQueryTest {

    private final SQLParser parser = new SQLParser();
    private Table sales;
    private QueryEngine engine;

    @BeforeEach
    void setUp() {
        sales = new Table("sales", new Schema(List.of(
            new Column("region", DataType.STRING),
            new Column("product", DataType.STRING),
            new Column("units", DataType.INTEGER),
            new Column("price", DataType.FLOAT),
            new Column("active", DataType.BOOLEAN, false)
        )), List.of(
            row("north", "book", 2, 10.0, true),
            row("south", "book", 3, 20.0, true),
            row("north", "book", null, 10.0, true),
            row("north", "pen", 5, 2.0, false),
            row("south", "book", 1, null, true),
            row("north", "pen", 4, 3.0, true),
            row("west", "book", null, null, true),
            row("north", "book", 2, 10.0, true),
            row(null, "book", 8, 5.0, true),
            row("south", "pen", -2, -1.0, true),
            row("south", "pen", 2, 1.0, false)
        ));
        Database database = new Database();
        database.addTable(sales);
        database.addTable(new Table("empty", sales.getSchema()));
        engine = new QueryEngine(database);
    }

    @Test
    void combinesQualifiedWhereCompositeGroupingHavingAliasesSortingAndLimit() {
        QueryResult result = run("""
            SELECT s.region AS area, s.product, COUNT(*) AS records, COUNT(s.units) AS counted,
                   SUM(s.units) AS total, AVG(s.price) AS average
            FROM sales s
            WHERE s.active = true AND (s.units >= 0 OR s.units IS NULL)
            GROUP BY s.region, s.product
            HAVING total >= 4 AND (counted >= 2 OR product = 'pen')
            ORDER BY total DESC, average DESC, area
            LIMIT 2
            """);

        assertEquals(List.of("area", "product", "records", "counted", "total", "average"), names(result));
        assertEquals(List.of(
            List.of("south", "book", 2, 2, 4, 20.0),
            List.of("north", "book", 3, 2, 4, 10.0)
        ), payloads(result));
    }

    @Test
    void distinctCollapsesEqualAggregateProjectionsBeforeLimit() {
        // SQL parsing requires group keys in SELECT; the Query API permits this narrower projection.
        Query query = new Query("sales", null, List.of(SelectColumn.aggregate("n")),
            List.of("region", "product"), List.of(new AggregateExpression(AggregateFunction.COUNT, "*", "n")),
            ComparisonExpression.of("active", "=", new Value(DataType.BOOLEAN, true)),
            null, List.of(new OrderByItem("n", false)), 3, true);
        QueryResult result = engine.processQuery(query);

        assertEquals(List.of(List.of(3), List.of(2), List.of(1)), payloads(result));
    }

    @Test
    void havingIsNullFindsAnAllNullGroupWithoutConfusingItWithZero() {
        QueryResult result = run("""
            SELECT region, product, SUM(units) AS total, COUNT(units) AS counted FROM sales
            WHERE active = true GROUP BY region, product HAVING total IS NULL
            """);

        assertEquals(List.of(Arrays.asList("west", "book", null, 0)), payloads(result));
    }

    @Test
    void aggregateOrdinalsHonorExplicitNullPlacementAndTieBreakers() {
        QueryResult result = run("""
            SELECT region, product, SUM(units) AS total, AVG(price) AS average FROM sales
            WHERE active = true GROUP BY region, product
            ORDER BY 4 DESC NULLS FIRST, 3 DESC, region ASC NULLS LAST LIMIT 4
            """);

        assertEquals(List.of(
            Arrays.asList("west", "book", null, null),
            List.of("south", "book", 4, 20.0),
            List.of("north", "book", 4, 10.0),
            Arrays.asList(null, "book", 8, 5.0)
        ), payloads(result));
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "sales WHERE units > 100"})
    void emptyGlobalAggregateProducesOneRowWithZerosAndTypedNulls(String source) {
        QueryResult result = run("SELECT COUNT(*) AS records, COUNT(units) AS counted, SUM(units) AS total, "
            + "AVG(price) AS average, MIN(units) AS low, MAX(price) AS high FROM " + source);

        assertEquals(List.of(Arrays.asList(0, 0, null, null, null, null)), payloads(result));
        assertEquals(List.of(DataType.INTEGER, DataType.INTEGER, DataType.INTEGER,
            DataType.FLOAT, DataType.INTEGER, DataType.FLOAT),
            result.getSchema().getColumns().stream().map(Column::getType).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "sales WHERE units > 100"})
    void emptyGroupedAggregateHasNoSyntheticGroup(String source) {
        QueryResult result = run("SELECT region AS area, COUNT(*) AS n FROM " + source
            + " GROUP BY region HAVING n >= 0 ORDER BY n DESC LIMIT 2");

        assertTrue(result.getRows().isEmpty());
        assertEquals(List.of("area", "n"), names(result));
    }

    @Test
    void havingCanRemoveTheSyntheticEmptyGlobalAggregateRow() {
        QueryResult result = run("SELECT COUNT(*) AS n FROM sales WHERE units > 100 HAVING n > 0");

        assertTrue(result.getRows().isEmpty());
        assertEquals(List.of("n"), names(result));
    }

    @Test
    void zeroLimitKeepsAggregateAliasesAndTypesAfterHavingAndDistinct() {
        QueryResult result = run("""
            SELECT DISTINCT region AS area, AVG(price) AS average FROM sales
            GROUP BY region HAVING average IS NOT NULL ORDER BY average DESC LIMIT 0
            """);

        assertTrue(result.getRows().isEmpty());
        assertEquals(List.of("area", "average"), names(result));
        assertEquals(DataType.FLOAT, result.getSchema().getColumn("average").getType());
    }

    @Test
    void distinctCompositeProjectionRetainsDifferentProductsAndOneNullRegion() {
        QueryResult result = run("""
            SELECT DISTINCT region, product FROM sales
            ORDER BY region ASC NULLS LAST, product DESC LIMIT 6
            """);

        assertEquals(List.of(
            List.of("north", "pen"), List.of("north", "book"),
            List.of("south", "pen"), List.of("south", "book"),
            List.of("west", "book"), Arrays.asList(null, "book")
        ), payloads(result));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT missing FROM empty LIMIT 0",
        "SELECT region FROM empty ORDER BY missing LIMIT 0",
        "SELECT missing FROM sales WHERE units > 100",
        "SELECT region FROM sales WHERE units > 100 ORDER BY missing"
    })
    void invalidColumnsAreStillReportedWhenThereAreNoResultRows(String sql) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> run(sql));

        assertEquals("Column not found: missing", exception.getMessage());
    }

    @Test
    void repeatedComplexQueriesDoNotChangeSourceRowsOrEarlierResults() {
        List<Row> original = List.copyOf(sales.getRows());
        String sql = "SELECT region, SUM(units) AS total FROM sales WHERE active = true "
            + "GROUP BY region ORDER BY total DESC NULLS LAST";
        QueryResult first = run(sql);
        List<List<Object>> expected = List.of(
            List.of("north", 8), Arrays.asList(null, 8), List.of("south", 2), Arrays.asList("west", null));

        run("SELECT DISTINCT product FROM sales ORDER BY product DESC LIMIT 1");
        QueryResult second = run(sql);

        assertEquals(expected, payloads(first));
        assertEquals(expected, payloads(second));
        assertEquals(original, sales.getRows());
    }

    private QueryResult run(String sql) {
        return engine.processQuery(parser.parse(sql));
    }

    private static Row row(String region, String product, Integer units, Double price, boolean active) {
        return new Row(List.of(new Value(DataType.STRING, region), new Value(DataType.STRING, product),
            new Value(DataType.INTEGER, units), new Value(DataType.FLOAT, price),
            new Value(DataType.BOOLEAN, active)));
    }

    private static List<String> names(QueryResult result) {
        return result.getSchema().getColumns().stream().map(Column::getName).toList();
    }

    private static List<List<Object>> payloads(QueryResult result) {
        return result.getRows().stream()
            .map(row -> row.getValues().stream().map(Value::getValue).toList()).toList();
    }
}
