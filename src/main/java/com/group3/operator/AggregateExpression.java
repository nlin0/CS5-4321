package com.group3.operator;

import java.util.Locale;

/** A single aggregate in a SELECT list, for example COUNT(*) or AVG(gpa). */
public record AggregateExpression(AggregateFunction function, String column, String alias) {

    public AggregateExpression {
        if (function == null) {
            throw new IllegalArgumentException("Aggregate function is required");
        }
        if (function != AggregateFunction.COUNT && (column == null || column.isBlank() || column.equals("*"))) {
            throw new IllegalArgumentException(function + " requires a column name");
        }
    }

    public String outputName() {
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        return function.name().toLowerCase(Locale.ROOT) + "(" + (column == null ? "*" : column) + ")";
    }
}
