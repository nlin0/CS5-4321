package com.group3.parser;

import java.util.ArrayList;
import java.util.List;

import com.group3.operator.AggregateExpression;
import com.group3.expression.Expression;

public class Query {
    private final String tableName;
    private final String tableAlias;
    private final List<SelectColumn> selectList;
    private final Expression where;
    private final List<String> columns;
    private final List<String> groupByColumns;
    private final List<AggregateExpression> aggregates;
    private final Expression having;
    private final List<OrderByItem> orderBy;
    private final Integer limit;
    private final boolean distinct;

    public Query(String tableName, List<String> columns) {
        this(tableName, columns, List.of(), List.of(), null);
    }

    public Query(String tableName, List<String> columns, List<String> groupByColumns,
                 List<AggregateExpression> aggregates) {
        this(tableName, columns, groupByColumns, aggregates, null);
    }

    public Query(String tableName, List<String> columns, List<String> groupByColumns,
                 List<AggregateExpression> aggregates, Expression having) {
        this(tableName, columns, groupByColumns, aggregates, having, List.of(), null, false);
    }

    public Query(String tableName, List<String> columns, List<String> groupByColumns,
                 List<AggregateExpression> aggregates, Expression having,
                 List<OrderByItem> orderBy, Integer limit, boolean distinct) {
        this(tableName, null, selectListOf(columns, aggregates), groupByColumns, aggregates,
            null, having, orderBy, limit, distinct);
    }

    /**
     * Full constructor. selectList holds the SELECT items in the order written;
     * where is null when there is no WHERE clause, tableAlias null when the
     * table has no alias.
     */
    public Query(String tableName, String tableAlias, List<SelectColumn> selectList,
                 List<String> groupByColumns, List<AggregateExpression> aggregates,
                 Expression where, Expression having,
                 List<OrderByItem> orderBy, Integer limit, boolean distinct) {
        if (limit != null && limit < 0) {
            throw new IllegalArgumentException("LIMIT must be non-negative: " + limit);
        }
        this.tableName = tableName;
        this.tableAlias = tableAlias;
        this.selectList = List.copyOf(selectList);
        this.columns = columnsOf(this.selectList);
        this.groupByColumns = List.copyOf(groupByColumns);
        this.aggregates = List.copyOf(aggregates);
        this.where = where;
        this.having = having;
        this.orderBy = List.copyOf(orderBy);
        this.limit = limit;
        this.distinct = distinct;
    }

    /** Select list for the older constructors: the plain columns, then the aggregates. */
    private static List<SelectColumn> selectListOf(List<String> columns,
                                                   List<AggregateExpression> aggregates) {
        List<SelectColumn> items = new ArrayList<>();
        for (String name : columns) {
            items.add(name.equals("*") ? SelectColumn.star() : SelectColumn.column(name, null));
        }
        for (AggregateExpression aggregate : aggregates) {
            items.add(SelectColumn.aggregate(aggregate.outputName()));
        }
        return items;
    }

    /** Source names of the non-aggregate SELECT items ("*" included), as getColumns() has always returned. */
    private static List<String> columnsOf(List<SelectColumn> selectList) {
        List<String> names = new ArrayList<>();
        for (SelectColumn item : selectList) {
            if (!item.aggregate()) {
                names.add(item.source());
            }
        }
        return List.copyOf(names);
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

    public List<OrderByItem> getOrderBy() { return orderBy; }

    /** Maximum number of rows to return, or null when there is no LIMIT. */
    public Integer getLimit() { return limit; }

    public boolean isDistinct() { return distinct; }

    /** Row predicate evaluated against the source table, before GROUP BY; null when there is no WHERE. */
    public Expression getWhere() { return where; }

    /** Alias given to the table in FROM, or null when there is none. */
    public String getTableAlias() { return tableAlias; }

    /** SELECT items in the order written, including aggregates. */
    public List<SelectColumn> getSelectList() { return selectList; }
}
