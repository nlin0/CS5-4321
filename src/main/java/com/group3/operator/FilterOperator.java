package com.group3.operator;

import java.util.ArrayList;
import java.util.List;

import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.expression.Expression;

public class FilterOperator {

    public List<Row> apply(Schema schema, List<Row> rows, Expression predicate) {
        if (schema == null || rows == null) {
            throw new IllegalArgumentException("Schema and rows are required");
        }
        if (predicate == null) {
            return List.copyOf(rows);
        }

        List<Row> kept = new ArrayList<>();
        for (Row row : rows) {
            if (predicate.evaluate(schema, row)) {
                kept.add(row);
            }
        }
        return List.copyOf(kept);
    }
}
