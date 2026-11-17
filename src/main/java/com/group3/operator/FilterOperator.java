package com.group3.operator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

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

    /** Lazily yields only the rows that satisfy the predicate; no intermediate list is built. */
    public Iterator<Row> iterator(Schema schema, Iterator<Row> rows, Expression predicate) {
        if (schema == null || rows == null) {
            throw new IllegalArgumentException("Schema and rows are required");
        }
        if (predicate == null) {
            return rows;
        }
        return new Iterator<>() {
            private Row next;

            @Override
            public boolean hasNext() {
                while (next == null && rows.hasNext()) {
                    Row candidate = rows.next();
                    if (predicate.evaluate(schema, candidate)) {
                        next = candidate;
                    }
                }
                return next != null;
            }

            @Override
            public Row next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Row result = next;
                next = null;
                return result;
            }
        };
    }
}
