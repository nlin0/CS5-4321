package com.group3.data;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Database {

    private final Map<String, Table> tables;

    public Database() {
        this.tables = new HashMap<>();
    }

    public void addTable(Table table) {

        String key = table.getName().toLowerCase();

        if (tables.containsKey(key)) {
            throw new IllegalArgumentException(
                "Table already exists: " + table.getName()
            );
        }

        tables.put(key, table);
    }

    public Table getTable(String name) {

        Table table = tables.get(name.toLowerCase());

        if (table == null) {
            throw new IllegalArgumentException(
                "Table not found: " + name
            );
        }

        return table;
    }

    public boolean containsTable(String name) {
        return tables.containsKey(name.toLowerCase());
    }

    public Collection<Table> getTables() {
        return Collections.unmodifiableCollection(
                tables.values()
        );
    }

    /**
     * Requires every non-null child value to match the parent primary key.
     * A null child value is allowed. Later rows are checked in Table.addRow.
     */
    public void addForeignKey(
            String childTable, String childColumn,
            String parentTable, String parentColumn) {
        Table child = getTable(childTable);
        Table parent = getTable(parentTable);
        if (parent.getPrimaryKeyColumn() == null
                || !parent.getPrimaryKeyColumn().equalsIgnoreCase(parentColumn)) {
            throw new IllegalArgumentException(
                "Foreign key must reference the primary key of " + parent.getName()
            );
        }
        int childIndex = child.getSchema().indexOf(childColumn);
        if (childIndex < 0) {
            throw new IllegalArgumentException("Column not found: " + childColumn);
        }
        Column childKey = child.getSchema().getColumn(childIndex);
        Column parentKey = parent.getSchema().getColumn(parent.getPrimaryKeyColumn());
        if (childKey.getType() != parentKey.getType()) {
            throw new IllegalArgumentException(
                "Foreign key type " + childKey.getType()
                    + " does not match primary key type " + parentKey.getType()
            );
        }
        for (ForeignKey existing : child.getForeignKeys()) {
            if (existing.getChildColumn().equalsIgnoreCase(childKey.getName())) {
                throw new IllegalArgumentException(
                    "Column " + childKey.getName() + " already has a foreign key"
                );
            }
        }

        ForeignKey foreignKey = new ForeignKey(
            childKey.getName(), childIndex, parent, parent.getPrimaryKeyColumn()
        );
        for (Row row : child.getRows()) {
            foreignKey.check(row);
        }
        child.addForeignKey(foreignKey);
    }
}