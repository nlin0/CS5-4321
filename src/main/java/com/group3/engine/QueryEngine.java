package com.group3.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.group3.data.Column;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.expression.Expression;
import com.group3.index.PartialIndex;
import com.group3.parser.Query;
import com.group3.parser.JoinSpec;
import com.group3.operator.JoinOperator;
import com.group3.operator.GraceHashJoinOperator;
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
    private String joinMode = "auto";
    private int partitionCount = 16;
    private int maxBuildRows = 100_000;
    private String lastJoin;
    private final Map<String, PartialIndex> indexes = new LinkedHashMap<>();
    private String lastIndex;

    public void setJoin(String mode, int partitions, int maxRows) {
        if (!List.of("auto", "hash", "grace").contains(mode)) {
            throw new IllegalArgumentException("Join mode must be auto, hash, or grace");
        }
        new GraceHashJoinOperator(partitions, maxRows, null);
        joinMode = mode;
        partitionCount = partitions;
        maxBuildRows = maxRows;
    }

    public String getLastJoin() {
        return lastJoin;
    }

    /**
     * Creates an index on table.column. With a predicate it is a partial index holding only
     * matching rows; queries use it only when their WHERE implies that predicate.
     * Indexes are opt-in: with none created, queries run exactly as before.
     */
    public PartialIndex createIndex(String name, String tableName, String column,
                                    Expression predicate, String predicateText) {
        String key = name == null ? null : name.toLowerCase(Locale.ROOT);
        if (key != null && indexes.containsKey(key)) {
            throw new IllegalArgumentException("Index already exists: " + name);
        }
        PartialIndex index = new PartialIndex(name, database.getTable(tableName), column, predicate, predicateText);
        indexes.put(key, index);
        return index;
    }

    public void dropIndex(String name) {
        if (name == null || indexes.remove(name.toLowerCase(Locale.ROOT)) == null) {
            throw new IllegalArgumentException("Index not found: " + name);
        }
    }

    public List<PartialIndex> getIndexes() {
        return List.copyOf(indexes.values());
    }

    /** Index used by the last query and how many rows it scanned, or null when none was used. */
    public String getLastIndex() {
        return lastIndex;
    }

    public QueryEngine(Database database) {
        if (database == null) {
            throw new IllegalArgumentException("Database is required");
        }
        this.database = database;
    }

    public QueryResult processQuery(Query query) {
        // FROM
        lastJoin = null;
        lastIndex = null;
        Table table = source(query);
        Schema schema = table.getSchema();

        // WHERE, against the source columns. Lazy: rows are pulled through the pipeline one at a time.
        // A matching index narrows the rows scanned; the full WHERE still runs on every one of them.
        Iterator<Row> scan = table.getRows().iterator();
        if (query.getJoin() == null) {
            scan = indexScan(table, query.getWhere(), scan);
        }
        FilterOperator filter = new FilterOperator();
        Iterator<Row> rows = filter.iterator(schema, scan, query.getWhere());

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
     * Picks the index on this table that yields the fewest candidate rows for the WHERE,
     * or returns the full scan unchanged when none applies.
     */
    private Iterator<Row> indexScan(Table table, Expression where, Iterator<Row> fullScan) {
        if (where == null || indexes.isEmpty() || !PartialIndex.safeToNarrow(where, table.getSchema())) {
            return fullScan;
        }
        PartialIndex best = null;
        int[] bestPositions = null;
        for (PartialIndex index : indexes.values()) {
            if (index.getTable() != table) {
                continue;
            }
            int[] positions = index.candidates(where);
            if (positions != null && (bestPositions == null || positions.length < bestPositions.length)) {
                best = index;
                bestPositions = positions;
            }
        }
        if (best == null) {
            return fullScan;
        }
        lastIndex = best.getName() + " (scanned " + bestPositions.length + " of " + table.size() + " rows)";
        return positionsIterator(table.getRows(), bestPositions);
    }

    private static Iterator<Row> positionsIterator(List<Row> rows, int[] positions) {
        return new Iterator<>() {
            private int next;

            @Override
            public boolean hasNext() {
                return next < positions.length;
            }

            @Override
            public Row next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return rows.get(positions[next++]);
            }
        };
    }

    private Table source(Query query) {
        Table left = database.getTable(query.getTableName());
        JoinSpec join = query.getJoin();
        if (join == null) {
            return left;
        }
        Table right = database.getTable(join.tableName());
        boolean useGrace = joinMode.equals("grace")
            || (joinMode.equals("auto") && right.size() > maxBuildRows);
        JoinOperator operator;
        if (useGrace) {
            operator = new GraceHashJoinOperator(partitionCount, maxBuildRows, null);
        } else {
            operator = new JoinOperator();
        }
        String leftAlias = left.getName();
        if (query.getTableAlias() != null) {
            leftAlias = query.getTableAlias();
        }
        Table result = operator.innerJoin(left, leftAlias, right, join.alias(),
            join.leftColumn(), join.rightColumn());
        if (useGrace) {
            lastJoin = "grace";
        } else {
            lastJoin = "hash";
        }
        return result;
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
