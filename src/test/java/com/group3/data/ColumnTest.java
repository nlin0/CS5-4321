package com.group3.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ColumnTest {

    @Test
    void createsIntegerColumn() {
        Column column = new Column("id", DataType.INTEGER);

        assertEquals("id", column.getName());
        assertEquals(DataType.INTEGER, column.getType());
    }

    @Test
    void createsFloatColumn() {
        Column column = new Column("gpa", DataType.FLOAT);

        assertEquals("gpa", column.getName());
        assertEquals(DataType.FLOAT, column.getType());
    }

    @Test
    void createsStringColumn() {
        Column column = new Column("name", DataType.STRING);

        assertEquals("name", column.getName());
        assertEquals(DataType.STRING, column.getType());
    }

    @Test
    void createsBooleanColumn() {
        Column column = new Column("active", DataType.BOOLEAN);

        assertEquals("active", column.getName());
        assertEquals(DataType.BOOLEAN, column.getType());
    }

    @Test
    void columnIsNullableByDefault() {
        Column column = new Column("name", DataType.STRING);

        assertTrue(column.isNullable());
    }

    @Test
    void createsNullableColumn() {
        Column column = new Column(
                "name",
                DataType.STRING,
                true
        );

        assertTrue(column.isNullable());
    }

    @Test
    void createsNonNullableColumn() {
        Column column = new Column(
                "id",
                DataType.INTEGER,
                false
        );

        assertFalse(column.isNullable());
    }

    @Test
    void toStringContainsColumnName() {
        Column column = new Column("gpa", DataType.FLOAT);

        assertTrue(column.toString().contains("gpa"));
    }

    @Test
    void toStringContainsColumnType() {
        Column column = new Column("gpa", DataType.FLOAT);

        assertTrue(column.toString().contains("FLOAT"));
    }
}