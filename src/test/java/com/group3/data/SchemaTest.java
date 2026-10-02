package com.group3.data;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SchemaTest {

    // ---------------------------------------------------
    // BASIC CONSTRUCTION
    // ---------------------------------------------------

    @Test
    void createsSchemaWithColumns() {
        Schema schema = createExampleSchema();

        assertEquals(4, schema.size());
    }

    @Test
    void preservesColumnOrder() {
        Schema schema = createExampleSchema();

        assertEquals("id", schema.getColumn(0).getName());
        assertEquals("name", schema.getColumn(1).getName());
        assertEquals("gpa", schema.getColumn(2).getName());
        assertEquals("active", schema.getColumn(3).getName());
    }

    @Test
    void createsEmptySchema() {
        Schema schema = new Schema(List.of());

        assertEquals(0, schema.size());
        assertTrue(schema.getColumns().isEmpty());
    }


    // ---------------------------------------------------
    // GET COLUMN BY INDEX
    // ---------------------------------------------------

    @Test
    void getColumnByIndexReturnsCorrectColumn() {
        Schema schema = createExampleSchema();

        Column column = schema.getColumn(2);

        assertEquals("gpa", column.getName());
        assertEquals(DataType.FLOAT, column.getType());
    }

    @Test
    void getColumnWithNegativeIndexThrowsException() {
        Schema schema = createExampleSchema();

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> schema.getColumn(-1)
        );
    }

    @Test
    void getColumnWithIndexEqualToSizeThrowsException() {
        Schema schema = createExampleSchema();

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> schema.getColumn(schema.size())
        );
    }


    // ---------------------------------------------------
    // FIND COLUMN BY NAME
    // ---------------------------------------------------

    @Test
    void indexOfReturnsCorrectIndex() {
        Schema schema = createExampleSchema();

        assertEquals(2, schema.indexOf("gpa"));
    }

    @Test
    void indexOfFirstColumnReturnsZero() {
        Schema schema = createExampleSchema();

        assertEquals(0, schema.indexOf("id"));
    }

    @Test
    void indexOfIsCaseInsensitive() {
        Schema schema = createExampleSchema();

        assertEquals(2, schema.indexOf("GPA"));
        assertEquals(2, schema.indexOf("Gpa"));
        assertEquals(2, schema.indexOf("gpa"));
    }

    @Test
    void indexOfMissingColumnReturnsNegativeOne() {
        Schema schema = createExampleSchema();

        assertEquals(-1, schema.indexOf("age"));
    }


    // ---------------------------------------------------
    // HAS COLUMN
    // ---------------------------------------------------

    @Test
    void hasColumnReturnsTrueForExistingColumn() {
        Schema schema = createExampleSchema();

        assertTrue(schema.hasColumn("name"));
    }

    @Test
    void hasColumnIsCaseInsensitive() {
        Schema schema = createExampleSchema();

        assertTrue(schema.hasColumn("NAME"));
    }

    @Test
    void hasColumnReturnsFalseForMissingColumn() {
        Schema schema = createExampleSchema();

        assertFalse(schema.hasColumn("age"));
    }


    // ---------------------------------------------------
    // GET COLUMN BY NAME
    // ---------------------------------------------------

    @Test
    void getColumnByNameReturnsCorrectColumn() {
        Schema schema = createExampleSchema();

        Column column = schema.getColumn("name");

        assertEquals("name", column.getName());
        assertEquals(DataType.STRING, column.getType());
    }

    @Test
    void getColumnByNameIsCaseInsensitive() {
        Schema schema = createExampleSchema();

        Column column = schema.getColumn("GPA");

        assertEquals("gpa", column.getName());
    }

    @Test
    void getMissingColumnThrowsException() {
        Schema schema = createExampleSchema();

        assertThrows(
                IllegalArgumentException.class,
                () -> schema.getColumn("age")
        );
    }


    // ---------------------------------------------------
    // DUPLICATE COLUMNS
    // ---------------------------------------------------

    @Test
    void duplicateColumnNamesAreRejected() {
        List<Column> columns = List.of(
                new Column("id", DataType.INTEGER),
                new Column("id", DataType.STRING)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new Schema(columns)
        );
    }

    @Test
    void duplicateColumnNamesAreCaseInsensitive() {
        List<Column> columns = List.of(
                new Column("id", DataType.INTEGER),
                new Column("ID", DataType.INTEGER)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new Schema(columns)
        );
    }


    // ---------------------------------------------------
    // DEFENSIVE COPYING / IMMUTABILITY
    // ---------------------------------------------------

    @Test
    void modifyingOriginalListDoesNotModifySchema() {
        List<Column> columns = new ArrayList<>();

        columns.add(new Column("id", DataType.INTEGER));
        columns.add(new Column("name", DataType.STRING));

        Schema schema = new Schema(columns);

        columns.add(new Column("gpa", DataType.FLOAT));

        assertEquals(2, schema.size());
    }

    @Test
    void clearingOriginalListDoesNotModifySchema() {
        List<Column> columns = new ArrayList<>();

        columns.add(new Column("id", DataType.INTEGER));
        columns.add(new Column("name", DataType.STRING));

        Schema schema = new Schema(columns);

        columns.clear();

        assertEquals(2, schema.size());
    }

    @Test
    void returnedColumnListCannotBeModified() {
        Schema schema = createExampleSchema();

        assertThrows(
                UnsupportedOperationException.class,
                () -> schema.getColumns().add(
                        new Column("age", DataType.INTEGER)
                )
        );
    }

    @Test
    void returnedColumnListCannotBeCleared() {
        Schema schema = createExampleSchema();

        assertThrows(
                UnsupportedOperationException.class,
                () -> schema.getColumns().clear()
        );
    }


    // ---------------------------------------------------
    // HELPER
    // ---------------------------------------------------

    private Schema createExampleSchema() {
        return new Schema(List.of(
                new Column("id", DataType.INTEGER, false),
                new Column("name", DataType.STRING),
                new Column("gpa", DataType.FLOAT),
                new Column("active", DataType.BOOLEAN)
        ));
    }
}
