package com.group3.storage;

import com.group3.data.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CSVWriterTest {

  @TempDir
  Path tempDir;

  @Test
  void writesBasicTable() throws IOException {

    Table table = createBasicTable();

    Path file = tempDir.resolve("output.csv");

    new CSVWriter().write(
        table,
        file.toString());

    String contents = Files.readString(file);

    assertTrue(
        contents.contains("name,age,gpa,active"));

    assertTrue(
        contents.contains("StudentA,20,3.5,true"));

    assertTrue(
        contents.contains("StudentB,21,3.8,false"));
  }

  @Test
  void writesHeader() throws IOException {

    Table table = createBasicTable();

    Path file = tempDir.resolve("output.csv");

    new CSVWriter().write(
        table,
        file.toString());

    List<String> lines = Files.readAllLines(file);

    assertEquals(
        "name,age,gpa,active",
        lines.get(0));
  }

  @Test
  void writesCorrectNumberOfRows()
      throws IOException {

    Table table = createBasicTable();

    Path file = tempDir.resolve("output.csv");

    new CSVWriter().write(
        table,
        file.toString());

    List<String> lines = Files.readAllLines(file);

    // One header + two data rows
    assertEquals(3, lines.size());
  }

  @Test
  void writesNullAsEmptyField() throws IOException {

    Schema schema = new Schema(
        List.of(
            new Column("name", DataType.STRING),
            new Column("age", DataType.INTEGER)));

    Table table = new Table("students", schema);

    table.addRow(
        new Row(
            List.of(
                new Value(
                    DataType.STRING,
                    "StudentA"),
                new Value(
                    DataType.INTEGER,
                    null))));

    Path file = tempDir.resolve("output.csv");

    new CSVWriter().write(
        table,
        file.toString());

    List<String> lines = Files.readAllLines(file);

    assertEquals(
        "StudentA,",
        lines.get(1));
  }

  @Test
  void quotesValuesContainingCommas()
      throws IOException {

    Schema schema = new Schema(
        List.of(
            new Column("name", DataType.STRING),
            new Column("location", DataType.STRING)));

    Table table = new Table("people", schema);

    table.addRow(
        new Row(
            List.of(
                new Value(
                    DataType.STRING,
                    "PersonA"),
                new Value(
                    DataType.STRING,
                    "City A, Region A"))));

    Path file = tempDir.resolve("output.csv");

    new CSVWriter().write(
        table,
        file.toString());

    List<String> lines = Files.readAllLines(file);

    assertEquals(
        "PersonA,\"City A, Region A\"",
        lines.get(1));
  }

  @Test
  void writesHeaderOnlyForEmptyTable()
      throws IOException {

    Schema schema = new Schema(
        List.of(
            new Column("name", DataType.STRING),
            new Column("age", DataType.INTEGER)));

    Table table = new Table("students", schema);

    Path file = tempDir.resolve("output.csv");

    new CSVWriter().write(
        table,
        file.toString());

    List<String> lines = Files.readAllLines(file);

    assertEquals(1, lines.size());
    assertEquals("name,age", lines.get(0));
  }

  @Test
  void rejectsNullTable() {

    Path file = tempDir.resolve("output.csv");

    assertThrows(
        IllegalArgumentException.class,
        () -> new CSVWriter().write(
            null,
            file.toString()));
  }

  @Test
  void rejectsBlankPath() {

    Table table = createBasicTable();

    assertThrows(
        IllegalArgumentException.class,
        () -> new CSVWriter().write(
            table,
            " "));
  }

  private Table createBasicTable() {

    Schema schema = new Schema(
        List.of(
            new Column(
                "name",
                DataType.STRING),
            new Column(
                "age",
                DataType.INTEGER),
            new Column(
                "gpa",
                DataType.FLOAT),
            new Column(
                "active",
                DataType.BOOLEAN)));

    Table table = new Table("students", schema);

    table.addRow(
        new Row(
            List.of(
                new Value(
                    DataType.STRING,
                    "StudentA"),
                new Value(
                    DataType.INTEGER,
                    20),
                new Value(
                    DataType.FLOAT,
                    3.5f),
                new Value(
                    DataType.BOOLEAN,
                    true))));

    table.addRow(
        new Row(
            List.of(
                new Value(
                    DataType.STRING,
                    "StudentB"),
                new Value(
                    DataType.INTEGER,
                    21),
                new Value(
                    DataType.FLOAT,
                    3.8f),
                new Value(
                    DataType.BOOLEAN,
                    false))));

    return table;
  }
}