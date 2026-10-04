package com.group3.data;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Row {

    private final List<Value> values;

    public Row(List<Value> values) {
        this.values = new ArrayList<>(values);
    }

    public Value get(int index) {
        return values.get(index);
    }

    public int size() {
        return values.size();
    }

    public List<Value> getValues() {
        return Collections.unmodifiableList(values);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof Row other && values.equals(other.values));
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
