package com.group3.parser;

import java.util.List;

public class Query {
    private final String tableName;
    private final List<String> columns;

    public Query(String tableName, List<String> columns) {
        this.tableName = tableName;
        this.columns = List.copyOf(columns);
    }

    public List<String> getColumns() {
        return columns;
    }

    public String getTableName() {
        return tableName;
    }
}