package com.group3.operator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Value;
import com.group3.parser.OrderByItem;

/** Stable multi-column sort. NULLs sort before every non-NULL value in ascending order. */
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
            int index = schema.indexOf(item.column());
            if (index == -1) {
                throw new IllegalArgumentException("Column not found: " + item.column());
            }
            Comparator<Row> next = (a, b) -> compare(a.get(index), b.get(index));
            if (!item.ascending()) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }

        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(comparator);
        return List.copyOf(sorted);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Value a, Value b) {
        if (a.isNull() || b.isNull()) {
            return Boolean.compare(!a.isNull(), !b.isNull());
        }
        return ((Comparable) a.getValue()).compareTo(b.getValue());
    }
}
