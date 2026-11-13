package com.group3.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.group3.data.Column;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.parser.Query;
import com.group3.parser.SelectColumn;
import com.group3.operator.AggregateOperator;
import com.group3.operator.FilterOperator;
import com.group3.operator.ProjectOperator;
import com.group3.operator.SortOperator;

/**
 * Runs a Query as a pipeline:
 * FROM -> WHERE -> GROUP BY/aggregates -> HAVING -> ORDER BY -> SELECT -> DISTINCT -> LIMIT.
 */
public class QueryEngine {

    /**
     * Supported dataset size: tables of up to this many rows are expected to work within default JVM memory.
     * Initial target; to be confirmed with Person 4. Informational only, it is not enforced.
     */
    public static final int SUPPORTED_MAX_ROWS_PER_TABLE = 100_000;

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

        // WHERE, against the source columns. Lazy: rows are pulled through the pipeline one at a time.
        FilterOperator filter = new FilterOperator();
        Iterator<Row> rows = filter.iterator(schema, table.getRows().iterator(), query.getWhere());

        // GROUP BY / aggregates (consumes the filtered rows directly), then HAVING against the grouped rows
        if (query.hasAggregates()) {
            QueryResult grouped = new AggregateOperator().execute(
                schema, rows, query.getGroupByColumns(), query.getAggregates());
            schema = grouped.getSchema();
            rows = filter.iterator(schema, grouped.getRows().iterator(), query.getHaving());
        }

        // Resolve the SELECT list up front so an unknown column is reported before any work is done.
        List<Integer> indexes = new ArrayList<>();
        List<Column> columns = new ArrayList<>();
        resolveSelectList(query.getSelectList(), schema, indexes, columns);
        Schema outputSchema = new Schema(columns);

        // ORDER BY on the pre-projection schema, so it may use unselected columns and source names behind aliases.
        // Sorting needs every row, so this is the one place the stream is materialized.
        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty()) {
            List<Row> buffered = new ArrayList<>();
            rows.forEachRemaining(buffered::add);
            rows = new SortOperator().apply(schema, buffered, query.getOrderBy()).iterator();
        }

        // SELECT
        rows = new ProjectOperator().iterator(rows, indexes);

        return distinctAndLimit(query, outputSchema, rows);
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

    /** Applies DISTINCT then LIMIT while draining the stream, stopping as soon as LIMIT rows are collected. */
    private QueryResult distinctAndLimit(Query query, Schema schema, Iterator<Row> rows) {
        Integer limit = query.getLimit();
        Set<Row> seen = query.isDistinct() ? new HashSet<>() : null;
        List<Row> result = new ArrayList<>();
        while ((limit == null || result.size() < limit) && rows.hasNext()) {
            Row row = rows.next();
            if (seen == null || seen.add(row)) {
                result.add(row);
            }
        }
        return new QueryResult(schema, result);
    }
}
