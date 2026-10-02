package com.group3.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ValueTest {

    // ---------------------------------------------------
    // INTEGER
    // ---------------------------------------------------

    @Test
    void createsIntegerValue() {
        Value value = new Value(DataType.INTEGER, 5);

        assertEquals(DataType.INTEGER, value.getType());
        assertEquals(5, value.getValue());
        assertFalse(value.isNull());
    }

    @Test
    void rejectsStringAsInteger() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Value(DataType.INTEGER, "5")
        );
    }

    @Test
    void rejectsDoubleAsInteger() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Value(DataType.INTEGER, 5.0)
        );
    }


    // ---------------------------------------------------
    // FLOAT
    // ---------------------------------------------------

    @Test
    void createsFloatValueUsingDouble() {
        Value value = new Value(DataType.FLOAT, 3.65);

        assertEquals(DataType.FLOAT, value.getType());
        assertEquals(3.65, value.getValue());
        assertFalse(value.isNull());
    }

    @Test
    void createsFloatValueUsingFloat() {
        Float input = 3.65f;

        Value value = new Value(DataType.FLOAT, input);

        assertEquals(DataType.FLOAT, value.getType());
        assertEquals(input, value.getValue());
    }

    @Test
    void rejectsIntegerAsFloat() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Value(DataType.FLOAT, 3)
        );
    }

    @Test
    void rejectsStringAsFloat() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Value(DataType.FLOAT, "3.65")
        );
    }


    // ---------------------------------------------------
    // STRING
    // ---------------------------------------------------

    @Test
    void createsStringValue() {
        Value value = new Value(DataType.STRING, "Billy");

        assertEquals(DataType.STRING, value.getType());
        assertEquals("Billy", value.getValue());
        assertFalse(value.isNull());
    }

    @Test
    void createsEmptyStringValue() {
        Value value = new Value(DataType.STRING, "");

        assertEquals("", value.getValue());
        assertFalse(value.isNull());
    }

    @Test
    void rejectsIntegerAsString() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Value(DataType.STRING, 5)
        );
    }

    @Test
    void rejectsBooleanAsString() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Value(DataType.STRING, true)
        );
    }


    // ---------------------------------------------------
    // BOOLEAN
    // ---------------------------------------------------

    @Test
    void createsTrueBooleanValue() {
        Value value = new Value(DataType.BOOLEAN, true);

        assertEquals(DataType.BOOLEAN, value.getType());
        assertEquals(true, value.getValue());
        assertFalse(value.isNull());
    }

    @Test
    void createsFalseBooleanValue() {
        Value value = new Value(DataType.BOOLEAN, false);

        assertEquals(DataType.BOOLEAN, value.getType());
        assertEquals(false, value.getValue());
    }

    @Test
    void rejectsStringAsBoolean() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Value(DataType.BOOLEAN, "true")
        );
    }

    @Test
    void rejectsIntegerAsBoolean() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Value(DataType.BOOLEAN, 1)
        );
    }


    // ---------------------------------------------------
    // NULL
    // ---------------------------------------------------

    @Test
    void allowsNullIntegerValue() {
        Value value = new Value(DataType.INTEGER, null);

        assertTrue(value.isNull());
        assertNull(value.getValue());
        assertEquals(DataType.INTEGER, value.getType());
    }

    @Test
    void allowsNullFloatValue() {
        Value value = new Value(DataType.FLOAT, null);

        assertTrue(value.isNull());
    }

    @Test
    void allowsNullStringValue() {
        Value value = new Value(DataType.STRING, null);

        assertTrue(value.isNull());
    }

    @Test
    void allowsNullBooleanValue() {
        Value value = new Value(DataType.BOOLEAN, null);

        assertTrue(value.isNull());
    }


    // ---------------------------------------------------
    // EQUALITY
    // ---------------------------------------------------

    @Test
    void equalValuesAreEqual() {
        Value value1 = new Value(DataType.INTEGER, 5);
        Value value2 = new Value(DataType.INTEGER, 5);

        assertEquals(value1, value2);
    }

    @Test
    void differentValuesAreNotEqual() {
        Value value1 = new Value(DataType.INTEGER, 5);
        Value value2 = new Value(DataType.INTEGER, 6);

        assertNotEquals(value1, value2);
    }

    @Test
    void differentTypesAreNotEqual() {
        Value value1 = new Value(DataType.INTEGER, null);
        Value value2 = new Value(DataType.STRING, null);

        assertNotEquals(value1, value2);
    }

    @Test
    void valueIsEqualToItself() {
        Value value = new Value(DataType.STRING, "hello");

        assertEquals(value, value);
    }

    @Test
    void valueIsNotEqualToNull() {
        Value value = new Value(DataType.INTEGER, 5);

        assertNotEquals(null, value);
    }

    @Test
    void valueIsNotEqualToDifferentObjectType() {
        Value value = new Value(DataType.INTEGER, 5);

        assertNotEquals(5, value);
    }


    // ---------------------------------------------------
    // HASH CODE
    // ---------------------------------------------------

    @Test
    void equalValuesHaveEqualHashCodes() {
        Value value1 = new Value(DataType.STRING, "Billy");
        Value value2 = new Value(DataType.STRING, "Billy");

        assertEquals(value1.hashCode(), value2.hashCode());
    }


    // ---------------------------------------------------
    // TO STRING
    // ---------------------------------------------------

    @Test
    void integerToStringReturnsValue() {
        Value value = new Value(DataType.INTEGER, 42);

        assertEquals("42", value.toString());
    }

    @Test
    void stringToStringReturnsString() {
        Value value = new Value(DataType.STRING, "Billy");

        assertEquals("Billy", value.toString());
    }

    @Test
    void booleanToStringReturnsBoolean() {
        Value value = new Value(DataType.BOOLEAN, true);

        assertEquals("true", value.toString());
    }

    @Test
    void nullToStringReturnsNullKeyword() {
        Value value = new Value(DataType.STRING, null);

        assertEquals("NULL", value.toString());
    }
    @Test
    void rejectsNullDataType() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Value(null, 5)
    );
    }
}
