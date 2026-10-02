package com.group3.data;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class DataTypeTest {

    @Test
    void containsAllSupportedDataTypes() {
        Set<DataType> expected = Set.of(
                DataType.INTEGER,
                DataType.FLOAT,
                DataType.STRING,
                DataType.BOOLEAN
        );

        Set<DataType> actual = Set.of(DataType.values());

        assertEquals(expected, actual);
    }

    @Test
    void valueOfReturnsCorrectIntegerType() {
        assertEquals(DataType.INTEGER, DataType.valueOf("INTEGER"));
    }

    @Test
    void valueOfReturnsCorrectFloatType() {
        assertEquals(DataType.FLOAT, DataType.valueOf("FLOAT"));
    }

    @Test
    void valueOfReturnsCorrectStringType() {
        assertEquals(DataType.STRING, DataType.valueOf("STRING"));
    }

    @Test
    void valueOfReturnsCorrectBooleanType() {
        assertEquals(DataType.BOOLEAN, DataType.valueOf("BOOLEAN"));
    }

    @Test
    void valueOfIsCaseSensitive() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DataType.valueOf("integer")
        );
    }

    @Test
    void valueOfRejectsInvalidType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DataType.valueOf("DATE")
        );
    }
}
