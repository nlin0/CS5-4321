package com.group3.data;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Table {

    private final String name;
    private final Schema schema;
    private final List<Row> rows;
    private final List<ForeignKey> foreignKeys = new ArrayList<>();
    private String primaryKeyColumn;
    private int primaryKeyIndex = -1;
    private Set<Value> primaryKeyValues;

    public Table(String name, Schema schema) {
        this.name = name;
        this.schema = schema;
        this.rows = new ArrayList<>();
    }

    public Table(String name, Schema schema, List<Row> rows) {
    this.name = name;
    this.schema = schema;
    this.rows = new ArrayList<>();

    for (Row row : rows) {
        validateRow(row);
        this.rows.add(row);
    }
    }

    public String getName() {
        return name;
    }

    public Schema getSchema() {
        return schema;
    }

    public List<Row> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public int size() {
        return rows.size();
    }

    /** Name of the primary-key column, or null when this table has none. */
    public String getPrimaryKeyColumn() {
        return primaryKeyColumn;
    }

    public List<ForeignKey> getForeignKeys() {
        return List.copyOf(foreignKeys);
    }

    /**
     * Declares a single-column primary key. Existing rows must already be
     * non-null and unique. Later rows are checked in addRow.
     */
    public void setPrimaryKey(String column) {
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("Primary key column is required");
        }
        if (primaryKeyColumn != null) {
            throw new IllegalArgumentException(
                "Table " + name + " already has a primary key"
            );
        }
        int index = schema.indexOf(column);
        if (index < 0) {
            throw new IllegalArgumentException("Column not found: " + column);
        }

        Set<Value> values = new HashSet<>();
        for (Row row : rows) {
            collectPrimaryKey(row.get(index), schema.getColumn(index).getName(), values);
        }
        primaryKeyColumn = schema.getColumn(index).getName();
        primaryKeyIndex = index;
        primaryKeyValues = values;
    }

    /** Attaches a foreign key that has already been checked against current rows. */
    void addForeignKey(ForeignKey foreignKey) {
        foreignKeys.add(foreignKey);
    }

    boolean containsPrimaryKey(Value value) {
        return primaryKeyValues != null && primaryKeyValues.contains(value);
    }

    public void addRow(Row row) {
        validateRow(row);
        if (primaryKeyValues != null) {
            requirePrimaryKey(row.get(primaryKeyIndex), primaryKeyColumn, primaryKeyValues);
        }
        for (ForeignKey foreignKey : foreignKeys) {
            foreignKey.check(row);
        }
        if (primaryKeyValues != null) {
            primaryKeyValues.add(row.get(primaryKeyIndex));
        }
        rows.add(row);
    }

    private void collectPrimaryKey(Value value, String column, Set<Value> values) {
        requirePrimaryKey(value, column, values);
        values.add(value);
    }

    private void requirePrimaryKey(Value value, String column, Set<Value> values) {
        if (value.isNull()) {
            throw new IllegalArgumentException("Primary key " + column + " cannot be NULL");
        }
        if (values.contains(value)) {
            throw new IllegalArgumentException("Duplicate primary key: " + value);
        }
    }

    private void validateRow(Row row) {

        if (row.size() != schema.size()) {
            throw new IllegalArgumentException(
                "Expected " + schema.size() +
                " values but received " + row.size()
            );
        }

        for (int i = 0; i < row.size(); i++) {

            Value value = row.get(i);
            Column column = schema.getColumn(i);

            if (value.isNull()) {
                if (!column.isNullable()) {
                    throw new IllegalArgumentException(
                        "Column " + column.getName()
                        + " cannot be NULL"
                    );
                }

                continue;
            }

            if (value.getType() != column.getType()) {
                throw new IllegalArgumentException(
                    "Expected " + column.getType() +
                    " for column " + column.getName() +
                    " but received " + value.getType()
                );
            }
        }
    }
}
