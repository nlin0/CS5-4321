package com.group3.operator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.engine.QueryResult;
import com.group3.expression.ComparisonExpression;
import com.group3.expression.LogicalExpression;

class AggregateOperatorTest {

    private final Table scores = new Table("scores", new Schema(List.of(
        new Column("team", DataType.STRING),
        new Column("score", DataType.INTEGER),
        new Column("rating", DataType.FLOAT)
    )), List.of(
        row("red", 10, 2.5), row("red", null, 3.5), row("blue", 20, null)
    ));

    @Test
    void aggregatesEntireTableAndFollowsNullRules() {
        QueryResult result = new AggregateOperator().execute(scores, List.of(), List.of(
            new AggregateExpression(AggregateFunction.COUNT, "*", "rows"),
            new AggregateExpression(AggregateFunction.COUNT, "score", "scored"),
            new AggregateExpression(AggregateFunction.SUM, "score", "total"),
            new AggregateExpression(AggregateFunction.AVG, "score", "average"),
            new AggregateExpression(AggregateFunction.MIN, "score", "lowest"),
            new AggregateExpression(AggregateFunction.MAX, "score", "highest")
        ));

        Row values = result.getRows().get(0);
        assertEquals(3, values.get(0).getValue());
        assertEquals(2, values.get(1).getValue());
        assertEquals(30, values.get(2).getValue());
        assertEquals(15.0, values.get(3).getValue());
        assertEquals(10, values.get(4).getValue());
        assertEquals(20, values.get(5).getValue());
    }

    @Test
    void groupsRowsInFirstSeenOrder() {
        QueryResult result = new AggregateOperator().execute(scores, List.of("team"), List.of(
            new AggregateExpression(AggregateFunction.COUNT, "*", null),
            new AggregateExpression(AggregateFunction.AVG, "score", null)
        ));

        assertEquals(2, result.getRowCount());
        assertEquals("red", result.getRows().get(0).get(0).getValue());
        assertEquals(2, result.getRows().get(0).get(1).getValue());
        assertEquals(10.0, result.getRows().get(0).get(2).getValue());
        assertEquals("blue", result.getRows().get(1).get(0).getValue());
    }

    @Test
    void emptyInputProducesOneUngroupedAggregateRow() {
        Table empty = new Table("empty", scores.getSchema());
        QueryResult result = new AggregateOperator().execute(empty, List.of(), List.of(
            new AggregateExpression(AggregateFunction.COUNT, "*", null),
            new AggregateExpression(AggregateFunction.SUM, "score", null)
        ));

        assertEquals(1, result.getRowCount());
        assertEquals(0, result.getRows().get(0).get(0).getValue());
        assertTrue(result.getRows().get(0).get(1).isNull());
    }

    @Test
    void rejectsNumericFunctionsOnNonNumericColumns() {
        assertThrows(IllegalArgumentException.class, () -> new AggregateOperator().execute(scores, List.of(), List.of(
            new AggregateExpression(AggregateFunction.SUM, "team", null)
        )));
    }

    @Test
    void aggregateResultCanBeFilteredAsAHavingClause() {
        QueryResult grouped = new AggregateOperator().execute(scores, List.of("team"), List.of(
            new AggregateExpression(AggregateFunction.COUNT, "*", "people")
        ));

        List<Row> rows = new FilterOperator().apply(grouped.getSchema(), grouped.getRows(),
            LogicalExpression.and(
                ComparisonExpression.of("people", ">=", new Value(DataType.INTEGER, 2)),
                ComparisonExpression.of("team", "=", new Value(DataType.STRING, "red"))));

        assertEquals(1, rows.size());
        assertEquals("red", rows.get(0).get(0).getValue());
    }

    private static Row row(String team, Integer score, Double rating) {
        return new Row(List.of(
            new Value(DataType.STRING, team),
            new Value(DataType.INTEGER, score),
            new Value(DataType.FLOAT, rating)
        ));
    }
}
