package com.group3.parser;

import java.util.List;

import com.group3.operator.AggregateExpression;
import com.group3.expression.Expression;

public class Query {
    private final String tableName;
    private final List<String> columns;
    private final List<String> groupByColumns;
    private final List<AggregateExpression> aggregates;
    private final Expression having;

    public Query(String tableName, List<String> columns) {
        this(tableName, columns, List.of(), List.of(), null);
    }

    public Query(String tableName, List<String> columns, List<String> groupByColumns,
                 List<AggregateExpression> aggregates) {
        this(tableName, columns, groupByColumns, aggregates, null);
    }

    public Query(String tableName, List<String> columns, List<String> groupByColumns,
                 List<AggregateExpression> aggregates, Expression having) {
        this.tableName = tableName;
        this.columns = List.copyOf(columns);
        this.groupByColumns = List.copyOf(groupByColumns);
        this.aggregates = List.copyOf(aggregates);
        this.having = having;
    }

    public List<String> getColumns() {
        return columns;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getGroupByColumns() { return groupByColumns; }

    public List<AggregateExpression> getAggregates() { return aggregates; }

    public boolean hasAggregates() { return !aggregates.isEmpty(); }

    /** Predicate evaluated against aggregate result rows, after GROUP BY. */
    public Expression getHaving() { return having; }
}
