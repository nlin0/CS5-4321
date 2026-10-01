package com.group3.expression;

public enum ComparisonOperator {
    EQUAL,
    NOT_EQUAL,
    LESS_THAN,
    GREATER_THAN,
    LESS_OR_EQUAL,
    GREATER_OR_EQUAL;

    public static ComparisonOperator fromSymbol(String symbol) {
        return switch (symbol) {
            case "=" -> EQUAL;
            case "!=", "<>" -> NOT_EQUAL;
            case "<" -> LESS_THAN;
            case ">" -> GREATER_THAN;
            case "<=" -> LESS_OR_EQUAL;
            case ">=" -> GREATER_OR_EQUAL;
            default -> throw new IllegalArgumentException(
                "Unsupported comparison operator: " + symbol
            );
        };
    }

    public boolean isOrdering() {
        return this != EQUAL && this != NOT_EQUAL;
    }
}
