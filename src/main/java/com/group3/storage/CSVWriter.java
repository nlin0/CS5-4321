package com.group3.storage;

import com.group3.data.*;
import org.apache.commons.csv.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CSVWriter {

  /**
   * Writes a Table to a CSV file.
   */
  public void write(Table table, String path) throws IOException {

    if (table == null) {
      throw new IllegalArgumentException(
          "Table cannot be null");
    }

    write(table.getSchema(), table.getRows(), path);
  }

  /** Writes a schema and its result rows without requiring a catalog table. */
  public void write(Schema schema, List<Row> rows, String path) throws IOException {
    if (schema == null || rows == null) {
      throw new IllegalArgumentException("Schema and rows are required");
    }

    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException(
          "CSV path cannot be null or blank");
    }

    // Validate before opening the file, which would truncate an existing destination.
    for (Row row : rows) {
      if (row == null || row.size() != schema.size()) {
        throw new IllegalArgumentException("Every row must match the schema's column count");
      }
      for (Value value : row.getValues()) {
        if (value == null) {
          throw new IllegalArgumentException("Use a Value with a null payload for a NULL cell");
        }
      }
    }

    Path csvPath = Path.of(path);

    CSVFormat format = CSVFormat.DEFAULT.builder()
        .setHeader(getHeaders(schema))
        .get();

    try (
        BufferedWriter writer = Files.newBufferedWriter(csvPath);

        CSVPrinter printer = new CSVPrinter(writer, format)) {
      for (Row row : rows) {
        writeRow(printer, row);
      }
    }
  }

  /**
   * Gets the column names from the table schema.
   */
  private String[] getHeaders(Schema schema) {

    String[] headers = new String[schema.size()];

    for (int i = 0; i < schema.size(); i++) {
      headers[i] = schema.getColumn(i).getName();
    }

    return headers;
  }

  /**
   * Writes one database row to the CSV.
   */
  private void writeRow(
      CSVPrinter printer,
      Row row) throws IOException {

    for (Value value : row.getValues()) {

      if (value.isNull()) {
        printer.print("");
      } else {
        printer.print(value.getValue());
      }
    }

    printer.println();
  }
}
