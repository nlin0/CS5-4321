package com.group3.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Function;
import com.group3.operator.AggregateExpression;
import com.group3.operator.AggregateFunction;
import com.group3.expression.ComparisonExpression;
import com.group3.expression.Expression;
import com.group3.expression.IsNullExpression;
import com.group3.expression.LogicalExpression;
import com.group3.data.DataType;
import com.group3.data.Value;

public class SQLParser {

    private static final Pattern HAVING_COMPARISON = Pattern.compile(
        "(?is)^\\s*(.+?)\\s*(<=|>=|<>|!=|=|<|>)\\s*(.+?)\\s*$");

    public Query parse(String sql) {
        final Statement statement;

        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw new IllegalArgumentException("Invalid SQL syntax", e);
        }

        if (!(statement instanceof PlainSelect)) {
            throw new UnsupportedOperationException(
                "Only SELECT queries are supported"
            );
        }

        PlainSelect select = (PlainSelect) statement;

        if (!(select.getFromItem() instanceof Table)) {
            throw new UnsupportedOperationException(
                "FROM must specify a table"
            );
        }

        Table table = (Table) select.getFromItem();
        List<String> columns = new ArrayList<>();
        List<AggregateExpression> aggregates = new ArrayList<>();

        for (SelectItem<?> item : select.getSelectItems()) {
            if (item.getExpression() instanceof AllColumns) {
                columns.add("*");
            } else if (item.getExpression() instanceof Column) {
                Column column = (Column) item.getExpression();
                columns.add(column.getColumnName());
            } else if (item.getExpression() instanceof Function function) {
                AggregateFunction aggregateFunction;
                try {
                    aggregateFunction = AggregateFunction.valueOf(function.getName().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new UnsupportedOperationException("Unsupported SELECT function: " + function.getName());
                }
                String argument = aggregateArgument(function);
                String alias = item.getAlias() == null ? null : item.getAlias().getName();
                aggregates.add(new AggregateExpression(aggregateFunction, argument, alias));
            } else {
                throw new UnsupportedOperationException(
                    "SELECT supports only column names or *"
                );
            }
        }

        List<String> groupByColumns = new ArrayList<>();
        if (select.getGroupBy() != null) {
            for (net.sf.jsqlparser.expression.Expression expression : select.getGroupBy().getGroupByExpressionList().getExpressions()) {
                if (!(expression instanceof Column column)) {
                    throw new UnsupportedOperationException("GROUP BY supports only column names");
                }
                groupByColumns.add(column.getColumnName());
            }
        }
        if (!aggregates.isEmpty() && !columns.equals(groupByColumns)) {
            throw new UnsupportedOperationException("Selected non-aggregate columns must match GROUP BY columns");
        }
        Expression having = parseHaving(select.getHaving(), aggregates, groupByColumns);
        List<OrderByItem> orderBy = parseOrderBy(select, aggregates, groupByColumns);
        Integer limit = parseLimit(select);
        boolean distinct = select.getDistinct() != null;
        return new Query(table.getFullyQualifiedName(), columns, groupByColumns, aggregates,
            having, orderBy, limit, distinct);
    }

    private List<OrderByItem> parseOrderBy(PlainSelect select, List<AggregateExpression> aggregates,
                                           List<String> groupByColumns) {
        List<OrderByItem> items = new ArrayList<>();
        if (select.getOrderByElements() == null) return items;
        for (OrderByElement element : select.getOrderByElements()) {
            String name;
            if (element.getExpression() instanceof Column column) {
                name = column.getColumnName();
            } else if (!aggregates.isEmpty() && element.getExpression() instanceof Function) {
                name = element.getExpression().toString();
            } else {
                throw new UnsupportedOperationException("ORDER BY supports only column names");
            }
            if (!aggregates.isEmpty()) {
                name = resolveHavingColumn(name, aggregates, groupByColumns);
            }
            items.add(new OrderByItem(name, element.isAsc()));
        }
        return items;
    }

    private Integer parseLimit(PlainSelect select) {
        if (select.getOffset() != null) {
            throw new UnsupportedOperationException("OFFSET is not supported");
        }
        Limit limit = select.getLimit();
        if (limit == null) return null;
        if (limit.getOffset() != null
                || !(limit.getRowCount() instanceof LongValue count)
                || count.getValue() < 0 || count.getValue() > Integer.MAX_VALUE) {
            throw new UnsupportedOperationException("LIMIT requires a non-negative integer");
        }
        return (int) count.getValue();
    }

    private Expression parseHaving(net.sf.jsqlparser.expression.Expression having,
                                   List<AggregateExpression> aggregates,
                                   List<String> groupByColumns) {
        if (having == null) return null;
        if (aggregates.isEmpty()) {
            throw new UnsupportedOperationException("HAVING requires an aggregate query");
        }
        return parseHavingText(having.toString(), aggregates, groupByColumns);
    }

    /** Parses the intentionally small but useful HAVING language after JSqlParser validates SQL syntax. */
    private Expression parseHavingText(String text, List<AggregateExpression> aggregates,
                                       List<String> groupByColumns) {
        String expression = removeOuterParentheses(text.trim());
        int orIndex = findTopLevelKeyword(expression, "OR");
        if (orIndex >= 0) {
            return LogicalExpression.or(
                parseHavingText(expression.substring(0, orIndex), aggregates, groupByColumns),
                parseHavingText(expression.substring(orIndex + 2), aggregates, groupByColumns));
        }
        int andIndex = findTopLevelKeyword(expression, "AND");
        if (andIndex >= 0) {
            return LogicalExpression.and(
                parseHavingText(expression.substring(0, andIndex), aggregates, groupByColumns),
                parseHavingText(expression.substring(andIndex + 3), aggregates, groupByColumns));
        }
        if (expression.regionMatches(true, 0, "NOT ", 0, 4)) {
            return LogicalExpression.not(
                parseHavingText(expression.substring(4), aggregates, groupByColumns));
        }

        Matcher nullMatcher = Pattern.compile("(?is)^\\s*(.+?)\\s+IS\\s+(NOT\\s+)?NULL\\s*$")
            .matcher(expression);
        if (nullMatcher.matches()) {
            String outputColumn = resolveHavingColumn(nullMatcher.group(1), aggregates, groupByColumns);
            return nullMatcher.group(2) == null
                ? IsNullExpression.isNull(outputColumn)
                : IsNullExpression.isNotNull(outputColumn);
        }

        Matcher matcher = HAVING_COMPARISON.matcher(expression);
        if (!matcher.matches()) {
            throw new UnsupportedOperationException("Unsupported HAVING condition");
        }
        String outputColumn = resolveHavingColumn(matcher.group(1), aggregates, groupByColumns);
        return ComparisonExpression.of(outputColumn, matcher.group(2), parseLiteral(matcher.group(3)));
    }

    private String resolveHavingColumn(String rawColumn, List<AggregateExpression> aggregates,
                                       List<String> groupByColumns) {
        String candidate = rawColumn.trim();
        for (AggregateExpression aggregate : aggregates) {
            if (aggregate.outputName().equalsIgnoreCase(candidate)
                    || aggregateText(aggregate).equalsIgnoreCase(candidate)) {
                return aggregate.outputName();
            }
        }
        for (String groupByColumn : groupByColumns) {
            if (groupByColumn.equalsIgnoreCase(candidate)) return groupByColumn;
        }
        throw new UnsupportedOperationException("HAVING must reference a selected aggregate, alias, or GROUP BY column: " + candidate);
    }

    private String aggregateText(AggregateExpression aggregate) {
        return aggregate.function().name().toLowerCase(Locale.ROOT) + "(" + aggregate.column() + ")";
    }

    /**
     * JSqlParser 5 represents aggregate parameters differently for different
     * function forms. Its normalized function text is stable for our restricted
     * syntax, so extract and validate the one supported argument from it here.
     */
    private String aggregateArgument(Function function) {
        String text = function.toString().trim();
        int openParen = text.indexOf('(');
        int closeParen = text.lastIndexOf(')');
        if (openParen < 1 || closeParen != text.length() - 1) {
            throw new UnsupportedOperationException("Aggregate functions require one column or *");
        }

        String argument = text.substring(openParen + 1, closeParen).trim();
        if (argument.equals("*")) return argument;

        // Permit an optional table qualifier (for example staff.salary), but
        // retain the unqualified column name used by this project's Schema.
        if (!argument.matches("(?i)([a-z_][a-z0-9_]*\\.)?[a-z_][a-z0-9_]*")) {
            throw new UnsupportedOperationException("Aggregate functions require one column or *");
        }
        int qualifier = argument.lastIndexOf('.');
        return qualifier < 0 ? argument : argument.substring(qualifier + 1);
    }

    private Value parseLiteral(String rawValue) {
        String value = rawValue.trim();
        if (value.matches("[-+]?\\d+")) return new Value(DataType.INTEGER, Integer.parseInt(value));
        if (value.matches("[-+]?(\\d+\\.\\d*|\\d*\\.\\d+)([eE][-+]?\\d+)?")) {
            return new Value(DataType.FLOAT, Double.parseDouble(value));
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return new Value(DataType.BOOLEAN, Boolean.parseBoolean(value));
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return new Value(DataType.STRING, value.substring(1, value.length() - 1).replace("''", "'"));
        }
        throw new UnsupportedOperationException("HAVING supports numeric, boolean, or quoted string literals");
    }

    private int findTopLevelKeyword(String text, String keyword) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i <= text.length() - keyword.length(); i++) {
            char current = text.charAt(i);
            if (current == '\'') {
                if (inString && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inString = !inString;
            } else if (!inString && current == '(') {
                depth++;
            } else if (!inString && current == ')') {
                depth--;
            }
            if (!inString && depth == 0 && i > 0
                    && text.regionMatches(true, i, keyword, 0, keyword.length())
                    && Character.isWhitespace(text.charAt(i - 1))
                    && i + keyword.length() < text.length()
                    && Character.isWhitespace(text.charAt(i + keyword.length()))) {
                return i;
            }
        }
        return -1;
    }

    private String removeOuterParentheses(String text) {
        while (text.startsWith("(") && text.endsWith(")") && outerParenthesesEnclose(text)) {
            text = text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    private boolean outerParenthesesEnclose(String text) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\'') inString = !inString;
            if (inString) continue;
            if (current == '(') depth++;
            if (current == ')' && --depth == 0 && i < text.length() - 1) return false;
        }
        return depth == 0;
    }
}
