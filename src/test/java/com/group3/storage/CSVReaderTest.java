package com.group3.storage;

import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Table;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CSVReaderTest {

  @TempDir
  Path tempDir;

  @Test
  void readsBasicCSV() throws IOException {

    Path file = createCSV(
        "students.csv",
        """
            name,age,gpa,active
            StudentA,20,3.65,true
            StudentB,21,3.90,false
            """);

    CSVReader reader = new CSVReader();

    Table table = reader.read(file.toString(), "students");

    assertEquals("students", table.getName());
    assertEquals(2, table.size());
    assertEquals(4, table.getSchema().size());
  }

  @Test
  void infersColumnTypes() throws IOException {

    Path file = createCSV(
        "students.csv",
        """
            name,age,gpa,active
            StudentA,20,3.65,true
            StudentB,21,3.90,false
            """);

    Table table = new CSVReader().read(
        file.toString(),
        "students");

    assertEquals(
        DataType.STRING,
        table.getSchema().getColumn("name").getType());

    assertEquals(
        DataType.INTEGER,
        table.getSchema().getColumn("age").getType());

    assertEquals(
        DataType.FLOAT,
        table.getSchema().getColumn("gpa").getType());

    assertEquals(
        DataType.BOOLEAN,
        table.getSchema().getColumn("active").getType());
  }

  @Test
  void parsesValuesIntoCorrectJavaTypes()
      throws IOException {

    Path file = createCSV(
        "students.csv",
        """
            name,age,gpa,active
            StudentA,20,3.65,true
            """);

    Table table = new CSVReader().read(
        file.toString(),
        "students");

    Row row = table.getRows().get(0);

    assertEquals("StudentA", row.get(0).getValue());
    assertEquals(20, row.get(1).getValue());
    assertEquals(3.65f, row.get(2).getValue());
    assertEquals(true, row.get(3).getValue());
  }

  @Test
  void blankValuesBecomeNull() throws IOException {

    Path file = createCSV(
        "students.csv",
        """
            name,age
            StudentA,20
            StudentB,
            StudentC,21
            """);

    Table table = new CSVReader().read(
        file.toString(),
        "students");

    assertEquals(
        DataType.INTEGER,
        table.getSchema()
            .getColumn("age")
            .getType());

    assertTrue(
        table.getRows()
            .get(1)
            .get(1)
            .isNull());
  }

  @Test
  void promotesIntegersToFloat() throws IOException {

    Path file = createCSV(
        "numbers.csv",
        """
            value
            1
            2.5
            3
            """);

    Table table = new CSVReader().read(
        file.toString(),
        "numbers");

    assertEquals(
        DataType.FLOAT,
        table.getSchema()
            .getColumn("value")
            .getType());

    assertEquals(
        1.0f,
        table.getRows().get(0).get(0).getValue());

    assertEquals(
        2.5f,
        table.getRows().get(1).get(0).getValue());
  }

  @Test
  void handlesQuotedCommas() throws IOException {

    Path file = createCSV(
        "people.csv",
        """
            name,location
            PersonA,"City A, Region A"
            PersonB,"City B, Region B"
            """);

    Table table = new CSVReader().read(
        file.toString(),
        "people");

    assertEquals(
        "City A, Region A",
        table.getRows()
            .get(0)
            .get(1)
            .getValue());
  }

  @Test
  void headerOnlyCSVCreatesEmptyTable()
      throws IOException {

    Path file = createCSV(
        "empty.csv",
        """
            name,age,gpa
            """);

    Table table = new CSVReader().read(
        file.toString(),
        "students");

    assertEquals(0, table.size());
    assertEquals(3, table.getSchema().size());

    // No data exists to infer a more specific type.
    assertEquals(
        DataType.STRING,
        table.getSchema()
            .getColumn("age")
            .getType());
  }

  @Test
  void rejectsRowWithTooFewValues()
      throws IOException {

    Path file = createCSV(
        "bad.csv",
        """
            name,age,gpa
            StudentA,20,3.65
            StudentB,21
            """);

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> new CSVReader().read(
            file.toString(),
            "students"));

    assertTrue(
        exception.getMessage().contains("expected 3"));
  }

  @Test
  void rejectsRowWithTooManyValues()
      throws IOException {

    Path file = createCSV(
        "bad.csv",
        """
            name,age
            StudentA,20,extra
            """);

    assertThrows(
        IllegalArgumentException.class,
        () -> new CSVReader().read(
            file.toString(),
            "students"));
  }

  @Test
  void rejectsDuplicateHeadersIgnoringCase()
      throws IOException {

    Path file = createCSV(
        "bad.csv",
        """
            name,NAME
            ValueA,ValueB
            """);

    assertThrows(
        IllegalArgumentException.class,
        () -> new CSVReader().read(
            file.toString(),
            "students"));
  }

  @Test
  void rejectsBlankTableName() throws IOException {

    Path file = createCSV(
        "students.csv",
        """
            name
            StudentA
            """);

    assertThrows(
        IllegalArgumentException.class,
        () -> new CSVReader().read(
            file.toString(),
            " "));
  }

  @Test
  void rejectsMissingFile() {

    Path missing = tempDir.resolve("does-not-exist.csv");

    assertThrows(
        IllegalArgumentException.class,
        () -> new CSVReader().read(
            missing.toString(),
            "students"));
  }

  private Path createCSV(
      String fileName,
      String contents) throws IOException {

    Path file = tempDir.resolve(fileName);

    Files.writeString(file, contents);

    return file;
  }
}
