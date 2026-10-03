package com.group3.engine;

import java.util.ArrayList;
import java.util.List;

import com.group3.data.Column;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.parser.Query;
import com.group3.operator.AggregateOperator;
import com.group3.operator.FilterOperator;

public class QueryEngine {

    private final Database database;

    public QueryEngine(Database database) {
        if (database == null) {
            throw new IllegalArgumentException("Database is required");
        }
        this.database = database;
    }

    public QueryResult processQuery(Query query) {
        Table table = database.getTable(query.getTableName());
        if (query.hasAggregates()) {
            QueryResult aggregateResult = new AggregateOperator().execute(
                table, query.getGroupByColumns(), query.getAggregates());
            List<Row> filteredRows = new FilterOperator().apply(
                aggregateResult.getSchema(), aggregateResult.getRows(), query.getHaving());
            return new QueryResult(aggregateResult.getSchema(), filteredRows);
        }
        Schema source = table.getSchema();

        List<Integer> indexes = new ArrayList<>();
        for (String name : query.getColumns()) {
            if (name.equals("*")) {
                for (int i = 0; i < source.size(); i++) {
                    indexes.add(i);
                }
            } else {
                int index = source.indexOf(name);
                if (index == -1) {
                    throw new IllegalArgumentException("Column not found: " + name);
                }
                indexes.add(index);
            }
        }

        List<Column> columns = new ArrayList<>();
        for (int index : indexes) {
            columns.add(source.getColumn(index));
        }

        List<Row> rows = new ArrayList<>();
        for (Row row : table.getRows()) {
            rows.add(project(row, indexes));
        }

        return new QueryResult(new Schema(columns), rows);
    }

    private Row project(Row row, List<Integer> indexes) {
        List<com.group3.data.Value> values = new ArrayList<>();
        for (int index : indexes) {
            values.add(row.get(index));
        }
        return new Row(values);
    }
}
