package com.group3.data;
import java.util.Objects;

public class Value {

    private final DataType type;
    private final Object value;

    public Value(DataType type, Object value) {
        if (type == null) {
            throw new IllegalArgumentException("Data type cannot be null");
        }
        
        this.type = type;
        this.value = value;
        validateType();
    }

    public DataType getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public boolean isNull() {
        return value == null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Value other)) return false;

        return type == other.type &&
               Objects.equals(value, other.value);
    }

    private void validateType() {
    if (value == null) {
        return;
    }

    switch (type) {
        case INTEGER -> {
            if (!(value instanceof Integer)) {
                throw new IllegalArgumentException("Expected Integer");
            }
        }

        case FLOAT -> {
            if (!(value instanceof Double) &&
                !(value instanceof Float)) {
                throw new IllegalArgumentException("Expected Float/Double");
            }
        }

        case STRING -> {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Expected String");
            }
        }

        case BOOLEAN -> {
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException("Expected Boolean");
            }
        }
    }
}

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return isNull() ? "NULL" : value.toString();
    }
}
