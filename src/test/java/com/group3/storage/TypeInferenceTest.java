package com.group3.storage;

import com.group3.data.DataType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TypeInferenceTest {

  @Test
  void infersInteger() {
    assertEquals(
        DataType.INTEGER,
        TypeInference.inferValue("123"));

    assertEquals(
        DataType.INTEGER,
        TypeInference.inferValue("-42"));

    assertEquals(
        DataType.INTEGER,
        TypeInference.inferValue("0"));
  }

  @Test
  void infersFloat() {
    assertEquals(
        DataType.FLOAT,
        TypeInference.inferValue("3.14"));

    assertEquals(
        DataType.FLOAT,
        TypeInference.inferValue("-0.5"));

    assertEquals(
        DataType.FLOAT,
        TypeInference.inferValue("1.0"));
  }

  @Test
  void infersBoolean() {
    assertEquals(
        DataType.BOOLEAN,
        TypeInference.inferValue("true"));

    assertEquals(
        DataType.BOOLEAN,
        TypeInference.inferValue("false"));

    assertEquals(
        DataType.BOOLEAN,
        TypeInference.inferValue("TRUE"));
  }

  @Test
  void infersString() {
    assertEquals(
        DataType.STRING,
        TypeInference.inferValue("Nicole"));

    assertEquals(
        DataType.STRING,
        TypeInference.inferValue("Computer Science"));

    assertEquals(
        DataType.STRING,
        TypeInference.inferValue("123abc"));
  }

  @Test
  void trimsWhitespaceBeforeInference() {
    assertEquals(
        DataType.INTEGER,
        TypeInference.inferValue("  123  "));

    assertEquals(
        DataType.BOOLEAN,
        TypeInference.inferValue(" TRUE "));
  }

  @Test
  void rejectsBlankSingleValue() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TypeInference.inferValue(""));

    assertThrows(
        IllegalArgumentException.class,
        () -> TypeInference.inferValue("   "));
  }

  @Test
  void infersIntegerColumn() {
    List<String> values = List.of("1", "2", "3", "4");

    assertEquals(
        DataType.INTEGER,
        TypeInference.inferColumn(values));
  }

  @Test
  void infersFloatColumn() {
    List<String> values = List.of("1.5", "2.7", "3.2");

    assertEquals(
        DataType.FLOAT,
        TypeInference.inferColumn(values));
  }

  @Test
  void promotesIntegerColumnToFloat() {
    List<String> values = List.of("1", "2", "3.5", "4");

    assertEquals(
        DataType.FLOAT,
        TypeInference.inferColumn(values));
  }

  @Test
  void infersBooleanColumn() {
    List<String> values = List.of("true", "false", "TRUE");

    assertEquals(
        DataType.BOOLEAN,
        TypeInference.inferColumn(values));
  }

  @Test
  void mixedTypesBecomeString() {
    List<String> values = List.of("1", "Nicole", "3");

    assertEquals(
        DataType.STRING,
        TypeInference.inferColumn(values));
  }

  @Test
  void ignoresBlankValuesDuringInference() {
    List<String> values = Arrays.asList("1", "", null, "3");

    assertEquals(
        DataType.INTEGER,
        TypeInference.inferColumn(values));
  }

  @Test
  void allBlankColumnDefaultsToString() {
    List<String> values = Arrays.asList("", " ", null);

    assertEquals(
        DataType.STRING,
        TypeInference.inferColumn(values));
  }

  @Test
  void rejectsNullColumnList() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TypeInference.inferColumn(null));
  }
}