package com.group3.storage;

import com.group3.data.*;
import org.apache.commons.csv.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CSVWriter {

  /**
   * Writes a Table to a CSV file.
   */
  public void write(Table table, String path) throws IOException {

    if (table == null) {
      throw new IllegalArgumentException(
          "Table cannot be null");
    }

    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException(
          "CSV path cannot be null or blank");
    }

    Path csvPath = Path.of(path);

    CSVFormat format = CSVFormat.DEFAULT.builder()
        .setHeader(getHeaders(table))
        .get();

    try (
        BufferedWriter writer = Files.newBufferedWriter(csvPath);

        CSVPrinter printer = new CSVPrinter(writer, format)) {
      for (Row row : table.getRows()) {
        writeRow(printer, row);
      }
    }
  }

  /**
   * Gets the column names from the table schema.
   */
  private String[] getHeaders(Table table) {

    Schema schema = table.getSchema();

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