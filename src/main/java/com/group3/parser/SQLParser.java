package com.group3.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.Distinct;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.BooleanValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import com.group3.operator.AggregateExpression;
import com.group3.operator.AggregateFunction;
import com.group3.expression.ComparisonExpression;
import com.group3.expression.ComparisonOperator;
import com.group3.expression.Expression;
import com.group3.expression.InExpression;
import com.group3.expression.IsNullExpression;
import com.group3.expression.LogicalExpression;
import com.group3.expression.Operand;
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
        List<SelectColumn> selectList = new ArrayList<>();
        // Lower-case output alias -> column in the pre-projection schema used by ORDER BY.
        Map<String, String> columnAliases = new HashMap<>();

        for (SelectItem<?> item : select.getSelectItems()) {
            if (item.getExpression() instanceof AllColumns) {
                columns.add("*");
                selectList.add(SelectColumn.star());
            } else if (item.getExpression() instanceof Column) {
                Column column = (Column) item.getExpression();
                columns.add(column.getColumnName());
                String alias = item.getAlias() == null ? null : unquoteIdentifier(item.getAlias().getName());
                selectList.add(SelectColumn.column(column.getColumnName(), alias));
                if (alias != null) {
                    columnAliases.put(alias.toLowerCase(Locale.ROOT), column.getColumnName());
                }
            } else if (item.getExpression() instanceof Function function) {
                AggregateFunction aggregateFunction;
                try {
                    aggregateFunction = AggregateFunction.valueOf(function.getName().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new UnsupportedOperationException("Unsupported SELECT function: " + function.getName());
                }
                String argument = aggregateArgument(function);
                String alias = item.getAlias() == null ? null : unquoteIdentifier(item.getAlias().getName());
                AggregateExpression aggregate = new AggregateExpression(aggregateFunction, argument, alias);
                aggregates.add(aggregate);
                selectList.add(SelectColumn.aggregate(aggregate.outputName()));
                if (alias != null) {
                    columnAliases.put(alias.toLowerCase(Locale.ROOT), aggregate.outputName());
                }
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
        List<OrderByItem> orderBy = parseOrderBy(select, aggregates, groupByColumns, selectList, columnAliases);
        Integer limit = parseLimit(select);
        boolean distinct = parseDistinct(select);
        if (distinct && aggregates.isEmpty() && !columns.contains("*")) {
            requireOrderByInSelectList(orderBy, columns);
        }
        Expression where = select.getWhere() == null ? null : parseWhere(select.getWhere(), table);
        return new Query(table.getFullyQualifiedName(),
            table.getAlias() == null ? null : table.getAlias().getName(), selectList,
            groupByColumns, aggregates, where, having, orderBy, limit, distinct);
    }

    /** Translate the validated AST directly so SQL precedence and parentheses are preserved. */
    private Expression parseWhere(net.sf.jsqlparser.expression.Expression expression, Table table) {
        expression = unwrapWhereParentheses(expression);
        if (expression instanceof AndExpression and) {
            return LogicalExpression.and(parseWhere(and.getLeftExpression(), table),
                parseWhere(and.getRightExpression(), table));
        }
        if (expression instanceof OrExpression or) {
            return LogicalExpression.or(parseWhere(or.getLeftExpression(), table),
                parseWhere(or.getRightExpression(), table));
        }
        if (expression instanceof NotExpression not) {
            return LogicalExpression.not(parseWhere(not.getExpression(), table));
        }
        if (expression instanceof EqualsTo || expression instanceof NotEqualsTo
                || expression instanceof GreaterThan || expression instanceof GreaterThanEquals
                || expression instanceof MinorThan || expression instanceof MinorThanEquals) {
            var comparison = (net.sf.jsqlparser.expression.operators.relational.ComparisonOperator) expression;
            if (comparison.getOldOracleJoinSyntax() != 0 || comparison.getOraclePriorPosition() != 0) {
                throw unsupportedWhere(expression);
            }
            return new ComparisonExpression(parseWhereOperand(comparison.getLeftExpression(), table),
                ComparisonOperator.fromSymbol(comparison.getStringExpression()),
                parseWhereOperand(comparison.getRightExpression(), table));
        }
        if (expression instanceof net.sf.jsqlparser.expression.operators.relational.IsNullExpression isNull) {
            if (isNull.getOldOracleJoinSyntax() != 0 || isNull.getOraclePriorPosition() != 0) {
                throw unsupportedWhere(expression);
            }
            return new IsNullExpression(parseWhereOperand(isNull.getLeftExpression(), table), isNull.isNot());
        }
        if (expression instanceof net.sf.jsqlparser.expression.operators.relational.InExpression in) {
            if (in.isGlobal() || in.getOldOracleJoinSyntax() != 0 || in.getOraclePriorPosition() != 0
                    || !(in.getRightExpression() instanceof ParenthesedExpressionList<?> list)
                    || list.isEmpty()) {
                throw unsupportedWhere(expression);
            }
            List<Value> values = new ArrayList<>();
            for (net.sf.jsqlparser.expression.Expression item : list) {
                values.add(parseWhereLiteral(item));
            }
            Expression predicate = new InExpression(parseWhereOperand(in.getLeftExpression(), table), values);
            return in.isNot() ? LogicalExpression.not(predicate) : predicate;
        }
        throw unsupportedWhere(expression);
    }

    private net.sf.jsqlparser.expression.Expression unwrapWhereParentheses(
            net.sf.jsqlparser.expression.Expression expression) {
        while (expression instanceof ParenthesedExpressionList<?> list && list.size() == 1) {
            expression = list.get(0);
        }
        return expression;
    }

    private Operand parseWhereOperand(net.sf.jsqlparser.expression.Expression expression, Table table) {
        expression = unwrapWhereParentheses(expression);
        if (expression instanceof Column column) {
            String qualifier = column.getTable() == null ? null : column.getTable().getFullyQualifiedName();
            if (qualifier != null && !qualifier.isEmpty()
                    && !qualifier.equalsIgnoreCase(table.getName())
                    && !qualifier.equalsIgnoreCase(table.getFullyQualifiedName())
                    && !(table.getAlias() != null && qualifier.equalsIgnoreCase(table.getAlias().getName()))) {
                throw new IllegalArgumentException("Unknown table qualifier in WHERE: " + qualifier);
            }
            return Operand.column(column.getColumnName());
        }
        return Operand.literal(parseWhereLiteral(expression));
    }

    private Value parseWhereLiteral(net.sf.jsqlparser.expression.Expression expression) {
        expression = unwrapWhereParentheses(expression);
        int sign = 1;
        boolean signed = false;
        while (expression instanceof SignedExpression unary) {
            signed = true;
            if (unary.getSign() == '-') {
                sign = -sign;
            } else if (unary.getSign() != '+') {
                throw unsupportedWhere(expression);
            }
            expression = unwrapWhereParentheses(unary.getExpression());
        }
        if (expression instanceof LongValue integer) {
            try {
                int value = integer.getBigIntegerValue().multiply(java.math.BigInteger.valueOf(sign)).intValueExact();
                return new Value(DataType.INTEGER, value);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("WHERE integer literal is outside the supported int range", e);
            }
        }
        if (expression instanceof DoubleValue decimal) {
            double value = sign * decimal.getValue();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("WHERE float literal must be finite");
            }
            return new Value(DataType.FLOAT, value);
        }
        if (!signed) {
            if (expression instanceof StringValue string) {
                return new Value(DataType.STRING, string.getValue().replace("''", "'"));
            }
            if (expression instanceof BooleanValue bool) {
                return new Value(DataType.BOOLEAN, bool.getValue());
            }
            if (expression instanceof NullValue) {
                // NULL's placeholder type is immaterial: expression evaluation checks null before types.
                return new Value(DataType.STRING, null);
            }
        }
        throw unsupportedWhere(expression);
    }

    private UnsupportedOperationException unsupportedWhere(net.sf.jsqlparser.expression.Expression expression) {
        return new UnsupportedOperationException("Unsupported WHERE expression: " + expression);
    }

    private List<OrderByItem> parseOrderBy(PlainSelect select, List<AggregateExpression> aggregates,
                                           List<String> groupByColumns, List<SelectColumn> selectList,
                                           Map<String, String> columnAliases) {
        List<OrderByItem> items = new ArrayList<>();
        if (select.getOrderByElements() == null) return items;
        for (OrderByElement element : select.getOrderByElements()) {
            net.sf.jsqlparser.expression.Expression expression = element.getExpression();
            String name;
            if (expression instanceof LongValue position) {
                name = resolveOrderByPosition(position.getValue(), selectList);
            } else if (expression instanceof Column column) {
                name = unquoteIdentifier(column.getColumnName());
                // Only unqualified names can refer to SELECT aliases.
                if (column.getTable() == null || column.getTable().getFullyQualifiedName().isEmpty()) {
                    String aliased = columnAliases.get(name.toLowerCase(Locale.ROOT));
                    if (aliased != null) {
                        name = aliased;
                    }
                }
            } else if (!aggregates.isEmpty() && expression instanceof Function) {
                name = expression.toString();
            } else {
                throw new UnsupportedOperationException(
                    "ORDER BY supports only column names, aliases, positions, or selected aggregates");
            }
            if (!aggregates.isEmpty()) {
                name = resolveOrderByColumn(name, aggregates, groupByColumns);
            }
            items.add(new OrderByItem(name, element.isAsc(), nullsFirst(element)));
        }
        return items;
    }

    /**
     * Resolves an ORDER BY name in an aggregate query to a column of the aggregate
     * output: an aggregate alias, aggregate text such as COUNT(*), or a GROUP BY column.
     * Same matching rules as resolveHavingColumn, but with an ORDER BY error message.
     */
    private String resolveOrderByColumn(String rawColumn, List<AggregateExpression> aggregates,
                                        List<String> groupByColumns) {
        // Names have already been decoded; spaces inside a quoted alias are significant.
        String candidate = rawColumn;
        for (AggregateExpression aggregate : aggregates) {
            if (aggregate.outputName().equalsIgnoreCase(candidate)
                    || aggregateText(aggregate).equalsIgnoreCase(candidate)) {
                return aggregate.outputName();
            }
        }
        for (String groupByColumn : groupByColumns) {
            if (groupByColumn.equalsIgnoreCase(candidate)) return groupByColumn;
        }
        throw new UnsupportedOperationException(
            "ORDER BY must reference a selected aggregate, alias, or GROUP BY column: " + candidate);
    }

    /** Maps an ordinal directly to its pre-projection column, without resolving aliases again. */
    private String resolveOrderByPosition(long position, List<SelectColumn> selectList) {
        if (selectList.stream().anyMatch(SelectColumn::isStar)) {
            throw new UnsupportedOperationException("ORDER BY position cannot be used with SELECT *");
        }
        if (position < 1 || position > selectList.size()) {
            throw new IllegalArgumentException("ORDER BY position " + position
                + " is out of range (1-" + selectList.size() + ")");
        }
        return selectList.get((int) position - 1).source();
    }

    private String unquoteIdentifier(String name) {
        if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
            return name.substring(1, name.length() - 1).replace("\"\"", "\"");
        }
        return name;
    }

    /** TRUE for NULLS FIRST, FALSE for NULLS LAST, null when not specified. */
    private Boolean nullsFirst(OrderByElement element) {
        if (element.getNullOrdering() == null) return null;
        return element.getNullOrdering() == OrderByElement.NullOrdering.NULLS_FIRST;
    }

    private boolean parseDistinct(PlainSelect select) {
        Distinct distinct = select.getDistinct();
        if (distinct == null) return false;
        if (distinct.getOnSelectItems() != null) {
            throw new UnsupportedOperationException("DISTINCT ON is not supported");
        }
        return true;
    }

    /**
     * With SELECT DISTINCT, sorting by a column that is not returned is ambiguous
     * (which duplicate's value decides the position?), so standard SQL rejects it.
     */
    private void requireOrderByInSelectList(List<OrderByItem> orderBy, List<String> columns) {
        for (OrderByItem item : orderBy) {
            boolean selected = columns.stream().anyMatch(c -> c.equalsIgnoreCase(item.column()));
            if (!selected) {
                throw new UnsupportedOperationException(
                    "With SELECT DISTINCT, ORDER BY columns must appear in the select list: " + item.column());
            }
        }
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
        String candidate = unquoteIdentifier(rawColumn.trim());
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
