package com.group3.data;
import java.util.Collection;
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
        return tables.values();
    }
}