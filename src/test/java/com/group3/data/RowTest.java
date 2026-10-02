package com.group3.data;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RowTest {

    @Test
    void createsRowWithValues() {
        List<Value> values = List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, "Billy"),
                new Value(DataType.FLOAT, 3.65),
                new Value(DataType.BOOLEAN, true)
        );

        Row row = new Row(values);

        assertEquals(4, row.size());
    }

    @Test
    void getReturnsCorrectIntegerValue() {
        Row row = createExampleRow();

        assertEquals(
                new Value(DataType.INTEGER, 1),
                row.get(0)
        );
    }

    @Test
    void getReturnsCorrectStringValue() {
        Row row = createExampleRow();

        assertEquals(
                new Value(DataType.STRING, "Billy"),
                row.get(1)
        );
    }

    @Test
    void getReturnsCorrectFloatValue() {
        Row row = createExampleRow();

        assertEquals(
                new Value(DataType.FLOAT, 3.65),
                row.get(2)
        );
    }

    @Test
    void getReturnsCorrectBooleanValue() {
        Row row = createExampleRow();

        assertEquals(
                new Value(DataType.BOOLEAN, true),
                row.get(3)
        );
    }

    @Test
    void preservesValueOrder() {
        Row row = createExampleRow();

        assertEquals(1, row.get(0).getValue());
        assertEquals("Billy", row.get(1).getValue());
        assertEquals(3.65, row.get(2).getValue());
        assertEquals(true, row.get(3).getValue());
    }

    @Test
    void createsEmptyRow() {
        Row row = new Row(List.of());

        assertEquals(0, row.size());
        assertTrue(row.getValues().isEmpty());
    }

    @Test
    void allowsNullValueObjects() {
        Row row = new Row(List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, null)
        ));

        assertEquals(2, row.size());
        assertTrue(row.get(1).isNull());
    }

    @Test
    void getValuesReturnsAllValues() {
        List<Value> values = List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, "Billy")
        );

        Row row = new Row(values);

        assertEquals(values, row.getValues());
    }


    // ---------------------------------------------------
    // DEFENSIVE COPYING
    // ---------------------------------------------------

    @Test
    void modifyingOriginalListDoesNotModifyRow() {
        List<Value> values = new ArrayList<>();

        values.add(new Value(DataType.INTEGER, 1));
        values.add(new Value(DataType.STRING, "Billy"));

        Row row = new Row(values);

        values.add(new Value(DataType.BOOLEAN, true));

        assertEquals(2, row.size());
    }

    @Test
    void clearingOriginalListDoesNotModifyRow() {
        List<Value> values = new ArrayList<>();

        values.add(new Value(DataType.INTEGER, 1));
        values.add(new Value(DataType.STRING, "Billy"));

        Row row = new Row(values);

        values.clear();

        assertEquals(2, row.size());
    }


    // ---------------------------------------------------
    // IMMUTABILITY
    // ---------------------------------------------------

    @Test
    void returnedValuesListCannotBeModified() {
        Row row = createExampleRow();

        assertThrows(
                UnsupportedOperationException.class,
                () -> row.getValues().add(
                        new Value(DataType.INTEGER, 5)
                )
        );
    }

    @Test
    void returnedValuesListCannotBeCleared() {
        Row row = createExampleRow();

        assertThrows(
                UnsupportedOperationException.class,
                () -> row.getValues().clear()
        );
    }


    // ---------------------------------------------------
    // INDEX ERRORS
    // ---------------------------------------------------

    @Test
    void negativeIndexThrowsException() {
        Row row = createExampleRow();

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> row.get(-1)
        );
    }

    @Test
    void indexEqualToSizeThrowsException() {
        Row row = createExampleRow();

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> row.get(row.size())
        );
    }

    @Test
    void indexGreaterThanSizeThrowsException() {
        Row row = createExampleRow();

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> row.get(100)
        );
    }


    // ---------------------------------------------------
    // TO STRING
    // ---------------------------------------------------

    @Test
    void toStringContainsRowValues() {
        Row row = createExampleRow();

        String result = row.toString();

        assertTrue(result.contains("1"));
        assertTrue(result.contains("Billy"));
        assertTrue(result.contains("3.65"));
        assertTrue(result.contains("true"));
    }


    // ---------------------------------------------------
    // HELPER
    // ---------------------------------------------------

    private Row createExampleRow() {
        return new Row(List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, "Billy"),
                new Value(DataType.FLOAT, 3.65),
                new Value(DataType.BOOLEAN, true)
        ));
    }
}