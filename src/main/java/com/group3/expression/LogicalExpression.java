package com.group3.expression;

import java.util.List;

import com.group3.data.Row;
import com.group3.data.Schema;

public final class LogicalExpression implements Expression {

    private final LogicalOperator operator;
    private final List<Expression> children;

    public LogicalExpression(LogicalOperator operator, List<Expression> children) {
        if (operator == null || children == null) {
            throw new IllegalArgumentException("Logical expression requires an operator and children");
        }
        if (operator == LogicalOperator.NOT && children.size() != 1) {
            throw new IllegalArgumentException("NOT requires one expression");
        }
        if (operator != LogicalOperator.NOT && children.size() < 2) {
            throw new IllegalArgumentException(operator + " requires at least two expressions");
        }
        for (Expression child : children) {
            if (child == null) {
                throw new IllegalArgumentException("Logical expression cannot contain a null child");
            }
        }
        this.operator = operator;
        this.children = List.copyOf(children);
    }

    public static LogicalExpression and(Expression left, Expression right) {
        return new LogicalExpression(LogicalOperator.AND, List.of(left, right));
    }

    public static LogicalExpression or(Expression left, Expression right) {
        return new LogicalExpression(LogicalOperator.OR, List.of(left, right));
    }

    public static LogicalExpression not(Expression expression) {
        return new LogicalExpression(LogicalOperator.NOT, List.of(expression));
    }

    public LogicalOperator getOperator() {
        return operator;
    }

    public List<Expression> getChildren() {
        return children;
    }

    @Override
    public boolean evaluate(Schema schema, Row row) {
        return switch (operator) {
            case AND -> evaluateAnd(schema, row);
            case OR -> evaluateOr(schema, row);
            case NOT -> !children.get(0).evaluate(schema, row);
        };
    }

    private boolean evaluateAnd(Schema schema, Row row) {
        for (Expression child : children) {
            if (!child.evaluate(schema, row)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateOr(Schema schema, Row row) {
        for (Expression child : children) {
            if (child.evaluate(schema, row)) {
                return true;
            }
        }
        return false;
    }
}
