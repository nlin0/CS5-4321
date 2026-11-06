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

/**
 * Inner equi-join implemented with sort-merge join.
 *
 * <p>Each input is sorted on its join key, then the two sorted streams are
 * advanced together. When a key appears more than once on either side, every
 * row in the left key group is paired with every row in the right key group.
 * Input tables are never modified.</p>
 */
public class SortMergeJoinOperator {

    /** Joins two tables, using each table name as its output-column qualifier. */
    public Table innerJoin(Table left, Table right, String leftColumn, String rightColumn) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Both tables are required for a join");
        }
        return innerJoin(left, left.getName(), right, right.getName(), leftColumn, rightColumn);
    }

    /** Joins two tables under explicit aliases, which supports self-joins. */
    public Table innerJoin(
            Table left, String leftAlias,
            Table right, String rightAlias,
            String leftColumn, String rightColumn) {

        JoinPlan plan = validate(left, leftAlias, right, rightAlias, leftColumn, rightColumn);
        List<Row> leftRows = sortedNonNullRows(left.getRows(), plan.leftIndex());
        List<Row> rightRows = sortedNonNullRows(right.getRows(), plan.rightIndex());
        List<Row> joined = merge(leftRows, plan.leftIndex(), rightRows, plan.rightIndex());

        return new Table(
            leftAlias + "_" + rightAlias,
            combinedSchema(leftAlias, left.getSchema(), rightAlias, right.getSchema()),
            joined
        );
    }

    private static JoinPlan validate(
            Table left, String leftAlias,
            Table right, String rightAlias,
            String leftColumn, String rightColumn) {
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
            List<Row> leftRows, int leftIndex, List<Row> rightRows, int rightIndex) {
        List<Row> joined = new ArrayList<>();
        int left = 0;
        int right = 0;

        while (left < leftRows.size() && right < rightRows.size()) {
            int comparison = compareKeys(leftRows.get(left).get(leftIndex), rightRows.get(right).get(rightIndex));
            if (comparison < 0) {
                left++;
            } else if (comparison > 0) {
                right++;
            } else {
                int leftEnd = endOfKeyGroup(leftRows, left, leftIndex);
                int rightEnd = endOfKeyGroup(rightRows, right, rightIndex);
                for (int l = left; l < leftEnd; l++) {
                    for (int r = right; r < rightEnd; r++) {
                        joined.add(combine(leftRows.get(l), rightRows.get(r)));
                    }
                }
                left = leftEnd;
                right = rightEnd;
            }
        }
        return joined;
    }

    private static int endOfKeyGroup(List<Row> rows, int start, int keyIndex) {
        Value key = rows.get(start).get(keyIndex);
        int end = start + 1;
        while (end < rows.size() && compareKeys(key, rows.get(end).get(keyIndex)) == 0) {
            end++;
        }
        return end;
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
