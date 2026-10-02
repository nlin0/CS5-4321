package com.group3.storage;

import com.group3.data.*;
import org.apache.commons.csv.*;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CSVReader {

    /**
     * Reads a CSV file and converts it into a Table.
     *
     * The first row of the CSV is treated as the header.
     * Column types are automatically inferred from the data.
     */
    public Table read(String path, String tableName) throws IOException {

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "CSV path cannot be null or blank");
        }

        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException(
                    "Table name cannot be null or blank");
        }

        Path csvPath = Path.of(path);

        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException(
                    "CSV file does not exist: " + path);
        }

        if (!Files.isRegularFile(csvPath)) {
            throw new IllegalArgumentException(
                    "CSV path is not a file: " + path);
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(false)
                .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
                .setIgnoreSurroundingSpaces(true)
                .get();

        try (
                Reader reader = Files.newBufferedReader(csvPath);
                CSVParser parser = format.parse(reader)) {
            List<String> headers = new ArrayList<>(parser.getHeaderNames());

            validateHeaders(headers);

            List<List<String>> rawRows = readRawRows(parser, headers.size());

            Schema schema = buildSchema(headers, rawRows);

            List<Row> rows = buildRows(rawRows, schema);

            return new Table(tableName, schema, rows);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid CSV file: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Validates that the CSV contains a usable header.
     */
    private void validateHeaders(List<String> headers) {

        if (headers.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV file must contain a header");
        }

        Set<String> seen = new HashSet<>();

        for (String header : headers) {

            if (header == null || header.isBlank()) {
                throw new IllegalArgumentException(
                        "CSV header cannot be blank");
            }

            String normalized = header.trim().toLowerCase();

            if (!seen.add(normalized)) {
                throw new IllegalArgumentException(
                        "Duplicate CSV header: " + header);
            }
        }
    }

    /**
     * Reads all CSV records as raw strings and validates
     * that every row has the same number of fields as the header.
     */
    private List<List<String>> readRawRows(
            CSVParser parser,
            int expectedColumns) {

        List<List<String>> rows = new ArrayList<>();

        for (CSVRecord record : parser) {

            if (record.size() != expectedColumns) {
                throw new IllegalArgumentException(
                        "CSV row " + record.getRecordNumber()
                                + " has " + record.size()
                                + " values but expected "
                                + expectedColumns);
            }

            List<String> row = new ArrayList<>();

            for (String value : record) {
                row.add(value);
            }

            rows.add(row);
        }

        return rows;
    }

    /**
     * Infers the type of each column and creates the table schema.
     */
    private Schema buildSchema(
            List<String> headers,
            List<List<String>> rawRows) {

        List<Column> columns = new ArrayList<>();

        for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {

            List<String> columnValues = new ArrayList<>();

            for (List<String> row : rawRows) {
                columnValues.add(row.get(columnIndex));
            }

            DataType type = TypeInference.inferColumn(columnValues);

            columns.add(
                    new Column(headers.get(columnIndex), type));
        }

        return new Schema(columns);
    }

    /**
     * Converts all raw CSV rows into typed database Rows.
     */
    private List<Row> buildRows(
            List<List<String>> rawRows,
            Schema schema) {

        List<Row> rows = new ArrayList<>();

        for (List<String> rawRow : rawRows) {

            List<Value> values = new ArrayList<>();

            for (int i = 0; i < rawRow.size(); i++) {

                DataType type = schema.getColumn(i).getType();

                values.add(
                        parseValue(rawRow.get(i), type));
            }

            rows.add(new Row(values));
        }

        return rows;
    }

    /**
     * Converts one raw CSV string into a typed Value.
     *
     * Blank CSV fields are represented as NULL values.
     */
    private Value parseValue(
            String rawValue,
            DataType type) {

        if (rawValue == null || rawValue.isBlank()) {
            return new Value(type, null);
        }

        String value = rawValue.trim();

        try {
            return switch (type) {
                case INTEGER ->
                    new Value(
                            DataType.INTEGER,
                            Integer.parseInt(value));

                case FLOAT ->
                    new Value(
                            DataType.FLOAT,
                            Float.parseFloat(value));

                case BOOLEAN ->
                    new Value(
                            DataType.BOOLEAN,
                            parseBoolean(value));

                case STRING ->
                    new Value(
                            DataType.STRING,
                            value);
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Could not parse value '"
                            + rawValue
                            + "' as "
                            + type,
                    e);
        }
    }

    /**
     * Parses a boolean without silently converting invalid
     * strings to false.
     */
    private boolean parseBoolean(String value) {

        if (value.equalsIgnoreCase("true")) {
            return true;
        }

        if (value.equalsIgnoreCase("false")) {
            return false;
        }

        throw new IllegalArgumentException(
                "Could not parse value '"
                        + value
                        + "' as BOOLEAN");
    }
}