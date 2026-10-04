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
import com.group3.operator.DistinctOperator;
import com.group3.operator.FilterOperator;
import com.group3.operator.SortOperator;

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
            return finish(query, aggregateResult.getSchema(), filteredRows);
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

        // Sort on the source schema so ORDER BY may use columns that are not selected.
        List<Row> sorted = new SortOperator().apply(source, table.getRows(), query.getOrderBy());
        List<Row> rows = new ArrayList<>();
        for (Row row : sorted) {
            rows.add(project(row, indexes));
        }

        return distinctAndLimit(query, new Schema(columns), rows);
    }

    private QueryResult finish(Query query, Schema schema, List<Row> rows) {
        List<Row> sorted = new SortOperator().apply(schema, rows, query.getOrderBy());
        return distinctAndLimit(query, schema, sorted);
    }

    /** Applies DISTINCT then LIMIT to rows that are already sorted. */
    private QueryResult distinctAndLimit(Query query, Schema schema, List<Row> rows) {
        if (query.isDistinct()) {
            rows = new DistinctOperator().apply(rows);
        }
        if (query.getLimit() != null && query.getLimit() < rows.size()) {
            rows = rows.subList(0, query.getLimit());
        }
        return new QueryResult(schema, rows);
    }

    private Row project(Row row, List<Integer> indexes) {
        List<com.group3.data.Value> values = new ArrayList<>();
        for (int index : indexes) {
            values.add(row.get(index));
        }
        return new Row(values);
    }
}
