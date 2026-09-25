package com.group3.data;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Table {

    private final String name;
    private final Schema schema;
    private final List<Row> rows;

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

    public void addRow(Row row) {
        validateRow(row);
        rows.add(row);
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
