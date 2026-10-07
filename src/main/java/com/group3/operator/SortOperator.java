package com.group3.operator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Value;
import com.group3.parser.OrderByItem;

/**
 * Stable multi-column sort for ORDER BY.
 *
 * <p>Each key is compared in turn; later keys only break ties left by earlier
 * ones. Rows whose keys are all equal keep their input order. NULL placement
 * follows {@link OrderByItem#nullsFirstEffective()} and does not depend on the
 * direction once NULLS FIRST / NULLS LAST is given explicitly.
 */
public class SortOperator {

    public List<Row> apply(Schema schema, List<Row> rows, List<OrderByItem> orderBy) {
        if (schema == null || rows == null) {
            throw new IllegalArgumentException("Schema and rows are required");
        }
        if (orderBy == null || orderBy.isEmpty()) {
            return List.copyOf(rows);
        }

        Comparator<Row> comparator = null;
        for (OrderByItem item : orderBy) {
            if (item == null) {
                throw new IllegalArgumentException("ORDER BY item cannot be null");
            }
            int index = schema.indexOf(item.column());
            if (index == -1) {
                throw new IllegalArgumentException("Column not found: " + item.column());
            }
            Comparator<Row> next = keyComparator(index, item.ascending(), item.nullsFirstEffective());
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }

        // List.sort is a stable merge sort, so ties keep their input order.
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(comparator);
        return List.copyOf(sorted);
    }

    private static Comparator<Row> keyComparator(int index, boolean ascending, boolean nullsFirst) {
        return (left, right) -> {
            Value a = left.get(index);
            Value b = right.get(index);
            if (a.isNull() || b.isNull()) {
                if (a.isNull() && b.isNull()) {
                    return 0;
                }
                // NULL placement is applied before (not through) the direction.
                return a.isNull() == nullsFirst ? -1 : 1;
            }
            int result = compareValues(a, b);
            return ascending ? result : Integer.compare(0, result);
        };
    }

    /**
     * Compares two non-NULL values. INTEGER and FLOAT values are compared
     * numerically with each other (including Float vs Double storage);
     * strings compare lexicographically and booleans as false &lt; true.
     *
     * @throws IllegalArgumentException if the values cannot be compared
     */
    public static int compareValues(Value a, Value b) {
        Object x = a.getValue();
        Object y = b.getValue();
        if (x instanceof Integer i && y instanceof Integer j) {
            return Integer.compare(i, j);
        }
        if (x instanceof Number m && y instanceof Number n) {
            return Double.compare(m.doubleValue(), n.doubleValue());
        }
        if (x instanceof String s && y instanceof String t) {
            return s.compareTo(t);
        }
        if (x instanceof Boolean p && y instanceof Boolean q) {
            return Boolean.compare(p, q);
        }
        throw new IllegalArgumentException(
                "Cannot compare " + a.getType() + " with " + b.getType() + " in ORDER BY");
    }
}
