package com.group3.data;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Schema {

    private final List<Column> columns;

    public Schema(List<Column> columns) {
        this.columns = new ArrayList<>(columns);
    }

    public List<Column> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public int size() {
        return columns.size();
    }

    public Column getColumn(int index) {
        return columns.get(index);
    }

    public Column getColumn(String name) {
        int index = indexOf(name);

        if (index == -1) {
            throw new IllegalArgumentException(
                "Column not found: " + name
            );
        }

        return columns.get(index);
    }

    public int indexOf(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(name)) {
                return i;
            }
        }

        return -1;
    }

    public boolean hasColumn(String name) {
        return indexOf(name) != -1;
    }
}