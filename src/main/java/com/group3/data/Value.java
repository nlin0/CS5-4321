package com.group3.data;
import java.util.Objects;

public class Value {

    private final DataType type;
    private final Object value;

    public Value(DataType type, Object value) {
        this.type = type;
        this.value = value;
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

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return isNull() ? "NULL" : value.toString();
    }
}
