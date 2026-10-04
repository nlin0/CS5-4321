package com.group3.operator;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.group3.data.Row;

/** Removes duplicate rows, keeping the first occurrence so any prior ordering is preserved. */
public class DistinctOperator {

    public List<Row> apply(List<Row> rows) {
        if (rows == null) {
            throw new IllegalArgumentException("Rows are required");
        }
        Set<Row> unique = new LinkedHashSet<>(rows);
        return List.copyOf(unique);
    }
}
