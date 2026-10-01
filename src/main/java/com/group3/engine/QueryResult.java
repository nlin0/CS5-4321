package com.group3.engine;

import java.util.List;

import com.group3.data.Row;
import com.group3.data.Schema;

public class QueryResult {
    private final Schema schema;
    private final List<Row> rows;

    public QueryResult(Schema schema, List<Row> rows) {
        this.schema = schema;
        this.rows = List.copyOf(rows);
    }

    public Schema getSchema() {
        return schema;
    }

    public List<Row> getRows() {
        return rows;
    }

    public int getRowCount() {
        return rows.size();
    }
}