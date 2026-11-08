package com.group3.operator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.expression.ComparisonOperator;

/**
 * Inner join implemented with sort-merge join, for equality and for
 * inequality conditions (=, !=, <, <=, >, >=) between one column of
 * each table.
 *
 * Each input is sorted on its join key (rows with a null key are dropped,
 * because a comparison with null is never true).
 *
 */
public class SortMergeJoinOperator {

    /** Equi-join of two tables, using each table name as its output-column qualifier. */
    public Table innerJoin(Table left, Table right, String leftColumn, String rightColumn) {
        return innerJoin(left, right, leftColumn, ComparisonOperator.EQUAL, rightColumn);
    }

    /** Equi-join under explicit aliases, which supports self-joins. */
    public Table innerJoin(
            Table left, String leftAlias,
            Table right, String rightAlias,
            String leftColumn, String rightColumn) {
        return innerJoin(left, leftAlias, right, rightAlias,
            leftColumn, ComparisonOperator.EQUAL, rightColumn);
    }


    public Table innerJoin(
            Table left, Table right,
            String leftColumn, ComparisonOperator operator, String rightColumn) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Both tables are required for a join");
        }
        return innerJoin(left, left.getName(), right, right.getName(),
            leftColumn, operator, rightColumn);
    }


    public Table innerJoin(
            Table left, String leftAlias,
            Table right, String rightAlias,
            String leftColumn, ComparisonOperator operator, String rightColumn) {

        JoinPlan plan = validate(left, leftAlias, right, rightAlias, leftColumn, operator, rightColumn);
        List<Row> leftRows = sortedNonNullRows(left.getRows(), plan.leftIndex());
        List<Row> rightRows = sortedNonNullRows(right.getRows(), plan.rightIndex());
        List<Row> joined = merge(leftRows, plan.leftIndex(), rightRows, plan.rightIndex(), operator);

        return new Table(
            leftAlias + "_" + rightAlias,
            combinedSchema(leftAlias, left.getSchema(), rightAlias, right.getSchema()),
            joined
        );
    }

    private static JoinPlan validate(
            Table left, String leftAlias,
            Table right, String rightAlias,
            String leftColumn, ComparisonOperator operator, String rightColumn) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Both tables are required for a join");
        }
        if (isBlank(leftAlias) || isBlank(rightAlias)) {
            throw new IllegalArgumentException("Both sides of a join need a name");
        }
        if (leftAlias.equalsIgnoreCase(rightAlias)) {
            throw new IllegalArgumentException(
                "Both sides of the join are named '" + leftAlias + "'; give one of them an alias"
            );
        }
        if (isBlank(leftColumn) || isBlank(rightColumn)) {
            throw new IllegalArgumentException("Both join columns are required");
        }
        if (operator == null) {
            throw new IllegalArgumentException("A join comparison operator is required");
        }

        int leftIndex = left.getSchema().indexOf(leftColumn);
        if (leftIndex < 0) {
            throw new IllegalArgumentException("Column not found: " + leftAlias + "." + leftColumn);
        }
        int rightIndex = right.getSchema().indexOf(rightColumn);
        if (rightIndex < 0) {
            throw new IllegalArgumentException("Column not found: " + rightAlias + "." + rightColumn);
        }

        DataType leftType = left.getSchema().getColumn(leftIndex).getType();
        DataType rightType = right.getSchema().getColumn(rightIndex).getType();
        if (!comparable(leftType, rightType)) {
            throw new IllegalArgumentException(
                "Cannot join " + leftAlias + "." + leftColumn + " (" + leftType + ") with "
                    + rightAlias + "." + rightColumn + " (" + rightType + ")"
            );
        }
        // Same rule as WHERE comparisons: booleans only support = and !=.
        if (operator.isOrdering() && leftType == DataType.BOOLEAN) {
            throw new IllegalArgumentException("Boolean columns only support = and != in a join");
        }
        return new JoinPlan(leftIndex, rightIndex);
    }

    private static List<Row> sortedNonNullRows(List<Row> rows, int keyIndex) {
        List<Row> sorted = new ArrayList<>();
        for (Row row : rows) {
            if (!row.get(keyIndex).isNull()) {
                sorted.add(row);
            }
        }
        // List.sort is stable, preserving input order inside an equal-key group.
        sorted.sort(Comparator.comparing(
            (Row row) -> row.get(keyIndex), SortMergeJoinOperator::compareKeys));
        return sorted;
    }

    private static List<Row> merge(
            List<Row> leftRows, int leftIndex,
            List<Row> rightRows, int rightIndex,
            ComparisonOperator operator) {
        List<Row> joined = new ArrayList<>();
        int size = rightRows.size();
        int lo = 0; // first right row with key >= the current left key
        int hi = 0; // first right row with key >  the current left key

        for (Row leftRow : leftRows) {
            Value leftKey = leftRow.get(leftIndex);

            while (lo < size && compareKeys(rightRows.get(lo).get(rightIndex), leftKey) < 0) {
                lo++;
            }
            if (hi < lo) {
                hi = lo;
            }
            while (hi < size && compareKeys(rightRows.get(hi).get(rightIndex), leftKey) <= 0) {
                hi++;
            }

            switch (operator) {
                case EQUAL -> emit(joined, leftRow, rightRows, lo, hi);
                case NOT_EQUAL -> {
                    emit(joined, leftRow, rightRows, 0, lo);
                    emit(joined, leftRow, rightRows, hi, size);
                }
                case LESS_THAN -> emit(joined, leftRow, rightRows, hi, size);
                case LESS_OR_EQUAL -> emit(joined, leftRow, rightRows, lo, size);
                case GREATER_THAN -> emit(joined, leftRow, rightRows, 0, lo);
                case GREATER_OR_EQUAL -> emit(joined, leftRow, rightRows, 0, hi);
            }
        }
        return joined;
    }

    // Pairs one left row with right rows [from, to).
    private static void emit(List<Row> joined, Row leftRow, List<Row> rightRows, int from, int to) {
        for (int r = from; r < to; r++) {
            joined.add(combine(leftRow, rightRows.get(r)));
        }
    }

    private static int compareKeys(Value left, Value right) {
        return SortOperator.compareValues(left, right);
    }

    private static Schema combinedSchema(
            String leftAlias, Schema leftSchema, String rightAlias, Schema rightSchema) {
        List<Column> columns = new ArrayList<>();
        for (Column column : leftSchema.getColumns()) {
            columns.add(new Column(leftAlias + "." + column.getName(), column.getType(), column.isNullable()));
        }
        for (Column column : rightSchema.getColumns()) {
            columns.add(new Column(rightAlias + "." + column.getName(), column.getType(), column.isNullable()));
        }
        return new Schema(columns);
    }

    private static Row combine(Row left, Row right) {
        List<Value> values = new ArrayList<>(left.size() + right.size());
        values.addAll(left.getValues());
        values.addAll(right.getValues());
        return new Row(values);
    }

    private static boolean comparable(DataType left, DataType right) {
        return left == right || (isNumeric(left) && isNumeric(right));
    }

    private static boolean isNumeric(DataType type) {
        return type == DataType.INTEGER || type == DataType.FLOAT;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private record JoinPlan(int leftIndex, int rightIndex) { }
}