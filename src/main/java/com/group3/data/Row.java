package com.group3.data;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Row {

    // A plain array rather than an ArrayList: one object less per row, which
    // matters when a table holds hundreds of thousands of rows.
    private final Value[] values;

    public Row(List<Value> values) {
        this.values = values.toArray(new Value[0]);
    }

    public Value get(int index) {
        return values[index];
    }

    public int size() {
        return values.length;
    }

    public List<Value> getValues() {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof Row other && Arrays.equals(values, other.values));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
