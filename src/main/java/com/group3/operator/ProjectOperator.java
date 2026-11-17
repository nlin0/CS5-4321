package com.group3.operator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.group3.data.Row;
import com.group3.data.Value;

/** Keeps only the selected columns of each row, one row at a time. */
public class ProjectOperator {

    public Iterator<Row> iterator(Iterator<Row> rows, List<Integer> indexes) {
        if (rows == null || indexes == null) {
            throw new IllegalArgumentException("Rows and indexes are required");
        }
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return rows.hasNext();
            }

            @Override
            public Row next() {
                Row row = rows.next();
                List<Value> values = new ArrayList<>(indexes.size());
                for (int index : indexes) {
                    values.add(row.get(index));
                }
                return new Row(values);
            }
        };
    }
}
