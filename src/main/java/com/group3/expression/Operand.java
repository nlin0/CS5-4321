package com.group3.expression;

import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Value;

public final class Operand {

    private final String columnName;
    private final Value literal;

    private Operand(String columnName, Value literal) {
        this.columnName = columnName;
        this.literal = literal;
    }

    public static Operand column(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Column name is required");
        }
        return new Operand(name, null);
    }

    public static Operand literal(Value value) {
        if (value == null) {
            throw new IllegalArgumentException(
                "Literal is required; use a Value with a null payload for SQL NULL"
            );
        }
        return new Operand(null, value);
    }

    public boolean isColumn() {
        return columnName != null;
    }

    public String getColumnName() {
        return columnName;
    }

    public Value getLiteral() {
        return literal;
    }

    public Value resolve(Schema schema, Row row) {
        if (!isColumn()) {
            return literal;
        }

        int index = schema.indexOf(columnName);
        if (index < 0) {
            throw new IllegalArgumentException("Column not found: " + columnName);
        }
        return row.get(index);
    }
}
