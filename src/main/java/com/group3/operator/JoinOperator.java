package com.group3.operator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;

/**
 * Inner-joins two tables on one column from each side (an equi-join).
 *
 * Implemented as a hash join: the right table is loaded into a hash map by
 * key, then each left row probes it. Output order is deterministic: left rows
 * in their original order, and for each left row its matches in right-table
 * order.
 *
 * Output columns are qualified as "alias.column" (for example "students.id"),
 * so both sides can have a column with the same name without ambiguity. To
 * join a table to itself, give the two sides different aliases.
 *
 * The inputs are assumed to be valid tables: the Table constructor already
 * checks that every row matches its schema.
 */
public class JoinOperator {

    /** Joins two tables, using each table's own name as its alias. */
    public Table innerJoin(Table left, Table right, String leftColumn, String rightColumn) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Both tables are required for a join");
        }
        return innerJoin(left, left.getName(), right, right.getName(), leftColumn, rightColumn);
    }

    /** Joins two tables under explicit aliases (needed for self-joins or SQL aliases). */
    public Table innerJoin(
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

        Schema leftSchema = left.getSchema();
        Schema rightSchema = right.getSchema();

        int leftIndex = leftSchema.indexOf(leftColumn);
        if (leftIndex < 0) {
            throw new IllegalArgumentException("Column not found: " + leftAlias + "." + leftColumn);
        }
        int rightIndex = rightSchema.indexOf(rightColumn);
        if (rightIndex < 0) {
            throw new IllegalArgumentException("Column not found: " + rightAlias + "." + rightColumn);
        }

        DataType leftType = leftSchema.getColumn(leftIndex).getType();
        DataType rightType = rightSchema.getColumn(rightIndex).getType();
        if (!comparable(leftType, rightType)) {
            throw new IllegalArgumentException(
                "Cannot join " + leftAlias + "." + leftColumn + " (" + leftType + ") with "
                    + rightAlias + "." + rightColumn + " (" + rightType + ")"
            );
        }

        Schema outputSchema = combinedSchema(leftAlias, leftSchema, rightAlias, rightSchema);

        // Build: right rows grouped by join key. A null key can never match, so skip it.
        Map<Object, List<Row>> rightByKey = new HashMap<>();
        for (Row rightRow : right.getRows()) {
            Value key = rightRow.get(rightIndex);
            if (key.isNull()) {
                continue;
            }
            rightByKey.computeIfAbsent(keyOf(key), k -> new ArrayList<>()).add(rightRow);
        }

        // Probe: for each left row, emit one output row per matching right row.
        List<Row> joined = new ArrayList<>();
        for (Row leftRow : left.getRows()) {
            Value key = leftRow.get(leftIndex);
            if (key.isNull()) {
                continue;
            }
            List<Row> matches = rightByKey.get(keyOf(key));
            if (matches == null) {
                continue;
            }
            for (Row rightRow : matches) {
                joined.add(combine(leftRow, rightRow));
            }
        }

        return new Table(leftAlias + "_" + rightAlias, outputSchema, joined);
    }

    private static Schema combinedSchema(
            String leftAlias, Schema leftSchema, String rightAlias, Schema rightSchema) {
        List<Column> columns = new ArrayList<>();
        for (Column column : leftSchema.getColumns()) {
            columns.add(qualified(leftAlias, column));
        }
        for (Column column : rightSchema.getColumns()) {
            columns.add(qualified(rightAlias, column));
        }
        return new Schema(columns);
    }

    // An inner join never introduces nulls, so each column keeps its nullability.
    private static Column qualified(String alias, Column column) {
        return new Column(alias + "." + column.getName(), column.getType(), column.isNullable());
    }

    private static Row combine(Row left, Row right) {
        List<Value> values = new ArrayList<>(left.size() + right.size());
        values.addAll(left.getValues());
        values.addAll(right.getValues());
        return new Row(values);
    }

    // Numeric keys are normalized to double so that 3 (INTEGER) matches 3.0 (FLOAT),
    // the same way WHERE comparisons treat them. (Value.equals can't be used for this:
    // it also compares the type, so Value(INTEGER, 3) != Value(FLOAT, 3.0).)
    private static Object keyOf(Value value) {
        Object payload = value.getValue();
        if (payload instanceof Number number) {
            return number.doubleValue();
        }
        return payload;
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
}