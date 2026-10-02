package com.group3.data;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TableTest {

    // ---------------------------------------------------
    // CONSTRUCTION
    // ---------------------------------------------------

    @Test
    void createsEmptyTable() {
        Schema schema = createExampleSchema();

        Table table = new Table("students", schema);

        assertEquals("students", table.getName());
        assertSame(schema, table.getSchema());
        assertEquals(0, table.size());
        assertTrue(table.getRows().isEmpty());
    }

    @Test
    void createsTableWithInitialRows() {
        Schema schema = createExampleSchema();

        List<Row> rows = List.of(
                createBilly(),
                createAlice()
        );

        Table table = new Table(
                "students",
                schema,
                rows
        );

        assertEquals(2, table.size());
    }

    @Test
    void preservesInitialRowOrder() {
        Schema schema = createExampleSchema();

        Table table = new Table(
                "students",
                schema,
                List.of(
                        createBilly(),
                        createAlice()
                )
        );

        assertEquals("Billy", table.getRows().get(0).get(1).getValue());
        assertEquals("Alice", table.getRows().get(1).get(1).getValue());
    }


    // ---------------------------------------------------
    // ADD ROW
    // ---------------------------------------------------

    @Test
    void addsValidRow() {
        Table table = createEmptyStudentTable();

        table.addRow(createBilly());

        assertEquals(1, table.size());
    }

    @Test
    void addedRowCanBeRetrieved() {
        Table table = createEmptyStudentTable();

        Row billy = createBilly();

        table.addRow(billy);

        assertSame(billy, table.getRows().get(0));
    }

    @Test
    void addsMultipleRows() {
        Table table = createEmptyStudentTable();

        table.addRow(createBilly());
        table.addRow(createAlice());

        assertEquals(2, table.size());
    }


    // ---------------------------------------------------
    // ROW LENGTH VALIDATION
    // ---------------------------------------------------

    @Test
    void rejectsRowWithTooFewValues() {
        Table table = createEmptyStudentTable();

        Row invalidRow = new Row(List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, "Billy")
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> table.addRow(invalidRow)
        );
    }

    @Test
    void rejectsRowWithTooManyValues() {
        Table table = createEmptyStudentTable();

        Row invalidRow = new Row(List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, "Billy"),
                new Value(DataType.FLOAT, 3.65),
                new Value(DataType.BOOLEAN, true),
                new Value(DataType.STRING, "extra")
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> table.addRow(invalidRow)
        );
    }


    // ---------------------------------------------------
    // TYPE VALIDATION
    // ---------------------------------------------------

    @Test
    void rejectsWrongIntegerType() {
        Table table = createEmptyStudentTable();

        Row invalidRow = new Row(List.of(
                new Value(DataType.STRING, "1"),
                new Value(DataType.STRING, "Billy"),
                new Value(DataType.FLOAT, 3.65),
                new Value(DataType.BOOLEAN, true)
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> table.addRow(invalidRow)
        );
    }

    @Test
    void rejectsWrongStringType() {
        Table table = createEmptyStudentTable();

        Row invalidRow = new Row(List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.INTEGER, 100),
                new Value(DataType.FLOAT, 3.65),
                new Value(DataType.BOOLEAN, true)
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> table.addRow(invalidRow)
        );
    }

    @Test
    void rejectsWrongFloatType() {
        Table table = createEmptyStudentTable();

        Row invalidRow = new Row(List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, "Billy"),
                new Value(DataType.INTEGER, 3),
                new Value(DataType.BOOLEAN, true)
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> table.addRow(invalidRow)
        );
    }

    @Test
    void rejectsWrongBooleanType() {
        Table table = createEmptyStudentTable();

        Row invalidRow = new Row(List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, "Billy"),
                new Value(DataType.FLOAT, 3.65),
                new Value(DataType.STRING, "true")
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> table.addRow(invalidRow)
        );
    }


    // ---------------------------------------------------
    // NULL VALUES
    // ---------------------------------------------------

    @Test
    void allowsNullInNullableColumn() {
        Table table = createEmptyStudentTable();

        Row row = new Row(List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, null),
                new Value(DataType.FLOAT, 3.65),
                new Value(DataType.BOOLEAN, true)
        ));

        assertDoesNotThrow(
                () -> table.addRow(row)
        );

        assertEquals(1, table.size());
    }

    @Test
    void rejectsNullInNonNullableColumn() {
        Table table = createEmptyStudentTable();

        Row row = new Row(List.of(
                new Value(DataType.INTEGER, null),
                new Value(DataType.STRING, "Billy"),
                new Value(DataType.FLOAT, 3.65),
                new Value(DataType.BOOLEAN, true)
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> table.addRow(row)
        );
    }


    // ---------------------------------------------------
    // CONSTRUCTOR VALIDATION
    // ---------------------------------------------------

    @Test
    void constructorRejectsInvalidInitialRow() {
        Schema schema = createExampleSchema();

        Row invalidRow = new Row(List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, "Billy")
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Table(
                        "students",
                        schema,
                        List.of(invalidRow)
                )
        );
    }

    @Test
    void constructorRejectsWrongTypeInInitialRow() {
        Schema schema = createExampleSchema();

        Row invalidRow = new Row(List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, "Billy"),
                new Value(DataType.FLOAT, 3.65),
                new Value(DataType.STRING, "true")
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Table(
                        "students",
                        schema,
                        List.of(invalidRow)
                )
        );
    }


    // ---------------------------------------------------
    // DEFENSIVE COPYING
    // ---------------------------------------------------

    @Test
    void modifyingOriginalRowListDoesNotModifyTable() {
        Schema schema = createExampleSchema();

        List<Row> rows = new ArrayList<>();
        rows.add(createBilly());

        Table table = new Table(
                "students",
                schema,
                rows
        );

        rows.add(createAlice());

        assertEquals(1, table.size());
    }


    // ---------------------------------------------------
    // IMMUTABILITY
    // ---------------------------------------------------

    @Test
    void returnedRowListCannotBeModified() {
        Table table = createEmptyStudentTable();

        table.addRow(createBilly());

        assertThrows(
                UnsupportedOperationException.class,
                () -> table.getRows().add(createAlice())
        );
    }

    @Test
    void returnedRowListCannotBeCleared() {
        Table table = createEmptyStudentTable();

        table.addRow(createBilly());

        assertThrows(
                UnsupportedOperationException.class,
                () -> table.getRows().clear()
        );
    }


    // ---------------------------------------------------
    // HELPERS
    // ---------------------------------------------------

    private Schema createExampleSchema() {
        return new Schema(List.of(
                new Column("id", DataType.INTEGER, false),
                new Column("name", DataType.STRING),
                new Column("gpa", DataType.FLOAT),
                new Column("active", DataType.BOOLEAN)
        ));
    }

    private Table createEmptyStudentTable() {
        return new Table(
                "students",
                createExampleSchema()
        );
    }

    private Row createBilly() {
        return new Row(List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, "Billy"),
                new Value(DataType.FLOAT, 3.65),
                new Value(DataType.BOOLEAN, true)
        ));
    }

    private Row createAlice() {
        return new Row(List.of(
                new Value(DataType.INTEGER, 2),
                new Value(DataType.STRING, "Alice"),
                new Value(DataType.FLOAT, 3.91),
                new Value(DataType.BOOLEAN, true)
        ));
    }
}