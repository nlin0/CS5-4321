package com.group3.engine;

import java.util.ArrayList;
import java.util.List;

import com.group3.data.Column;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.parser.Query;
import com.group3.parser.SelectColumn;
import com.group3.operator.AggregateOperator;
import com.group3.operator.DistinctOperator;
import com.group3.operator.FilterOperator;
import com.group3.operator.SortOperator;

/**
 * Runs a Query as a pipeline:
 * FROM -> WHERE -> GROUP BY/aggregates -> HAVING -> ORDER BY -> SELECT -> DISTINCT -> LIMIT.
 */
public class QueryEngine {

    private final Database database;

    public QueryEngine(Database database) {
        if (database == null) {
            throw new IllegalArgumentException("Database is required");
        }
        this.database = database;
    }

    public QueryResult processQuery(Query query) {
        // FROM
        Table table = database.getTable(query.getTableName());
        Schema schema = table.getSchema();

        // WHERE, against the source columns
        List<Row> rows = new FilterOperator().apply(schema, table.getRows(), query.getWhere());

        // GROUP BY / aggregates, then HAVING against the grouped rows
        if (query.hasAggregates()) {
            Table filtered = new Table(table.getName(), schema, rows);
            QueryResult grouped = new AggregateOperator().execute(
                filtered, query.getGroupByColumns(), query.getAggregates());
            schema = grouped.getSchema();
            rows = new FilterOperator().apply(schema, grouped.getRows(), query.getHaving());
        }

        // Resolve the SELECT list up front so an unknown column is reported before any work is done.
        List<Integer> indexes = new ArrayList<>();
        List<Column> columns = new ArrayList<>();
        resolveSelectList(query.getSelectList(), schema, indexes, columns);
        Schema outputSchema = new Schema(columns);

        // ORDER BY on the pre-projection schema, so it may use unselected columns and source names behind aliases.
        List<Row> sorted = new SortOperator().apply(schema, rows, query.getOrderBy());

        // SELECT
        List<Row> projected = new ArrayList<>();
        for (Row row : sorted) {
            projected.add(project(row, indexes));
        }

        return distinctAndLimit(query, outputSchema, projected);
    }

    /**
     * Maps each SELECT item to the index of its input column and builds the output column.
     * "*" expands to every input column; an alias renames the column but keeps its type and nullability.
     */
    private void resolveSelectList(List<SelectColumn> selectList, Schema input,
                                   List<Integer> indexes, List<Column> columns) {
        for (SelectColumn item : selectList) {
            if (item.isStar()) {
                for (int i = 0; i < input.size(); i++) {
                    indexes.add(i);
                    columns.add(input.getColumn(i));
                }
                continue;
            }
            int index = input.indexOf(item.source());
            if (index == -1) {
                throw new IllegalArgumentException("Column not found: " + item.source());
            }
            Column column = input.getColumn(index);
            indexes.add(index);
            columns.add(item.alias() == null
                ? column
                : new Column(item.outputName(), column.getType(), column.isNullable()));
        }
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
        List<Value> values = new ArrayList<>();
        for (int index : indexes) {
            values.add(row.get(index));
        }
        return new Row(values);
    }
}
