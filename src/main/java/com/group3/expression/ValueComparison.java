package com.group3.expression;

import com.group3.data.DataType;
import com.group3.data.Value;

final class ValueComparison {

    private ValueComparison() {
    }

    static boolean equals(Value left, Value right) {
        return compare(left, right) == 0;
    }

    static int compare(Value left, Value right) {
        if (isNumeric(left.getType()) && isNumeric(right.getType())) {
            return Double.compare(asDouble(left), asDouble(right));
        }

        if (left.getType() != right.getType()) {
            throw new IllegalArgumentException(
                "Cannot compare " + left.getType() + " and " + right.getType()
            );
        }

        return switch (left.getType()) {
            case STRING -> asString(left).compareTo(asString(right));
            case BOOLEAN -> Boolean.compare(asBoolean(left), asBoolean(right));
            case INTEGER, FLOAT -> throw new IllegalStateException(
                "Numeric values are compared above"
            );
        };
    }

    private static boolean isNumeric(DataType type) {
        return type == DataType.INTEGER || type == DataType.FLOAT;
    }

    private static double asDouble(Value value) {
        if (value.getValue() instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException(
            "Expected a number for type " + value.getType()
        );
    }

    private static String asString(Value value) {
        if (value.getValue() instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Expected a string");
    }

    private static boolean asBoolean(Value value) {
        if (value.getValue() instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("Expected a boolean");
    }
}
