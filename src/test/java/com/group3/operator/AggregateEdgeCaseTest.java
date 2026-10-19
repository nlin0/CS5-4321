package com.group3.operator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.engine.QueryResult;

class AggregateEdgeCaseTest {

    private final AggregateOperator operator = new AggregateOperator();
    private final Schema schema = new Schema(List.of(
        new Column("team", DataType.STRING),
        new Column("venue", DataType.STRING),
        new Column("score", DataType.INTEGER),
        new Column("rating", DataType.FLOAT)
    ));
    private final Table scores = new Table("scores", schema, List.of(
        row("red", "home", 10, 1.25),
        row(null, "home", null, null),
        row("red", "away", -4, -0.25),
        row("red", "home", null, 2.0),
        row(null, "home", null, null),
        row("red", null, 0, 0.0),
        row("blue", "home", 20, 4.5),
        row("red", "away", 4, 0.25),
        row("red", null, 0, null),
        row(null, null, null, null)
    ));

    @Test
    void compositeKeysKeepNullComponentsDistinctAndPreserveFirstSeenOrder() {
        QueryResult result = operator.execute(scores, List.of("TEAM", "venue"), List.of(
            aggregate(AggregateFunction.COUNT, "*", "rows"),
            aggregate(AggregateFunction.COUNT, "score", "scored"),
            aggregate(AggregateFunction.SUM, "score", "total")
        ));

        assertEquals(List.of(
            Arrays.asList("red", "home", 2, 1, 10),
            Arrays.asList(null, "home", 2, 0, null),
            Arrays.asList("red", "away", 2, 2, 0),
            Arrays.asList("red", null, 2, 2, 0),
            Arrays.asList("blue", "home", 1, 1, 20),
            Arrays.asList(null, null, 1, 0, null)
        ), payloads(result));
    }

    @ParameterizedTest
    @EnumSource(value = AggregateFunction.class, names = {"SUM", "AVG", "MIN", "MAX"})
    void emptyAndAllNullInputsReturnTypedNulls(AggregateFunction function) {
        for (Table input : List.of(new Table("empty", schema), new Table("nulls", schema, List.of(
                row("red", "home", null, null), row("blue", "away", null, null))))) {
            QueryResult result = operator.execute(input, List.of(), List.of(
                aggregate(function, "score", "answer")
            ));

            DataType expectedType = function == AggregateFunction.AVG ? DataType.FLOAT : DataType.INTEGER;
            assertEquals(1, result.getRowCount(), input.getName());
            assertEquals(expectedType, result.getSchema().getColumn("answer").getType());
            assertEquals(new Value(expectedType, null), result.getRows().get(0).get(0));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "score"})
    void countOnEmptyInputReturnsIntegerZero(String column) {
        QueryResult result = operator.execute(new Table("empty", schema), List.of(), List.of(
            aggregate(AggregateFunction.COUNT, column, "n")
        ));

        assertEquals(DataType.INTEGER, result.getSchema().getColumn("n").getType());
        assertEquals(List.of(List.of(0)), payloads(result));
    }

    @Test
    void emptyGroupedInputHasNoGroupsButKeepsOutputSchema() {
        QueryResult result = operator.execute(new Table("empty", schema), List.of("venue", "team"), List.of(
            aggregate(AggregateFunction.COUNT, "*", "n"),
            aggregate(AggregateFunction.AVG, "score", "average")
        ));

        assertTrue(result.getRows().isEmpty());
        assertEquals(List.of("venue", "team", "n", "average"),
            result.getSchema().getColumns().stream().map(Column::getName).toList());
        assertEquals(List.of(DataType.STRING, DataType.STRING, DataType.INTEGER, DataType.FLOAT),
            result.getSchema().getColumns().stream().map(Column::getType).toList());
    }

    @Test
    void floatAggregatesIncludeNegativeAndZeroValuesAndSkipNulls() {
        QueryResult result = operator.execute(scores, List.of(), List.of(
            aggregate(AggregateFunction.SUM, "rating", "total"),
            aggregate(AggregateFunction.AVG, "rating", "average"),
            aggregate(AggregateFunction.MIN, "rating", "lowest"),
            aggregate(AggregateFunction.MAX, "rating", "highest")
        ));

        Row values = result.getRows().get(0);
        assertEquals(7.75, (Double) values.get(0).getValue(), 1e-12);
        assertEquals(7.75 / 6, (Double) values.get(1).getValue(), 1e-12);
        assertEquals(-0.25, values.get(2).getValue());
        assertEquals(4.5, values.get(3).getValue());
        assertTrue(result.getSchema().getColumns().stream().allMatch(c -> c.getType() == DataType.FLOAT));
    }

    @Test
    void stringMinAndMaxIgnoreNullsAndCompareLexicographically() {
        QueryResult result = operator.execute(scores, List.of(), List.of(
            aggregate(AggregateFunction.MIN, "team", "first"),
            aggregate(AggregateFunction.MAX, "team", "last")
        ));

        assertEquals(List.of(List.of("blue", "red")), payloads(result));
        assertEquals(DataType.STRING, result.getSchema().getColumn("first").getType());
        assertEquals(DataType.STRING, result.getSchema().getColumn("last").getType());
    }

    @ParameterizedTest
    @EnumSource(value = AggregateFunction.class, names = {"SUM", "AVG"})
    void numericFunctionsRejectStringColumnsEvenWithoutRows(AggregateFunction function) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> operator.execute(new Table("empty", schema), List.of(), List.of(
                aggregate(function, "team", "answer")
            )));

        assertTrue(exception.getMessage().contains("requires a numeric column"));
    }

    @Test
    void duplicateGroupColumnsAreRejectedIgnoringCase() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> operator.execute(scores, List.of("team", "TEAM"), List.of(
                aggregate(AggregateFunction.COUNT, "*", "n")
            )));

        assertTrue(exception.getMessage().contains("Duplicate GROUP BY column"));
    }

    @Test
    void unknownGroupColumnIsRejectedEvenOnEmptyInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> operator.execute(new Table("empty", schema), List.of("missing"), List.of(
                aggregate(AggregateFunction.COUNT, "*", "n")
            )));

        assertEquals("Column not found: missing", exception.getMessage());
    }

    @ParameterizedTest
    @EnumSource(AggregateFunction.class)
    void unknownAggregateColumnIsRejectedEvenOnEmptyInput(AggregateFunction function) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> operator.execute(new Table("empty", schema), List.of(), List.of(
                aggregate(function, "missing", "answer")
            )));

        assertEquals("Column not found: missing", exception.getMessage());
    }

    @Test
    void aggregateAliasesCannotCollideWithGroupColumnsOrEachOther() {
        assertThrows(IllegalArgumentException.class, () -> operator.execute(scores, List.of("team"), List.of(
            aggregate(AggregateFunction.COUNT, "*", "TEAM")
        )));
        assertThrows(IllegalArgumentException.class, () -> operator.execute(scores, List.of(), List.of(
            aggregate(AggregateFunction.SUM, "score", "total"),
            aggregate(AggregateFunction.AVG, "score", "TOTAL")
        )));
    }

    private static AggregateExpression aggregate(AggregateFunction function, String column, String alias) {
        return new AggregateExpression(function, column, alias);
    }

    private static Row row(String team, String venue, Integer score, Double rating) {
        return new Row(List.of(new Value(DataType.STRING, team), new Value(DataType.STRING, venue),
            new Value(DataType.INTEGER, score), new Value(DataType.FLOAT, rating)));
    }

    private static List<List<Object>> payloads(QueryResult result) {
        return result.getRows().stream()
            .map(row -> row.getValues().stream().map(Value::getValue).toList()).toList();
    }
}
