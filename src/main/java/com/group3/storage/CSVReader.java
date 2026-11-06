package com.group3.storage;

import com.group3.data.*;
import org.apache.commons.csv.*;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CSVReader {

    /**
     * Most distinct values a column remembers for sharing during one load.
     * Low-cardinality columns (departments, years, booleans) are shared in
     * full; a unique column such as an id stops growing the map at this size,
     * which keeps the extra memory used while loading small and bounded.
     */
    static final int MAX_SHARED_VALUES_PER_COLUMN = 4096;

    /** One immutable NULL Value per type, shared by every table. */
    private static final Map<DataType, Value> NULLS = new EnumMap<>(DataType.class);

    static {
        for (DataType type : DataType.values()) {
            NULLS.put(type, new Value(type, null));
        }
    }

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setAllowMissingColumnNames(false)
            .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
            .setIgnoreSurroundingSpaces(true)
            .get();

    /**
     * Reads a CSV file and converts it into a Table.
     *
     * The first row of the CSV is treated as the header.
     * Column types are automatically inferred from the data.
     *
     * The file is read twice, one record at a time, so the whole file is
     * never held in memory as text:
     * 1. the first pass infers each column's type and checks row widths;
     * 2. the second pass parses each record straight into a typed Row.
     * Peak memory is therefore about the size of the finished table.
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

        try {
            Schema schema = inferSchema(csvPath);

            Table table = new Table(tableName, schema);

            loadRows(csvPath, table);

            return table;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid CSV file: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Pass 1: reads the header and every record, keeping only the type
     * inferred so far for each column. Each record is discarded as soon as
     * it has been looked at.
     */
    private Schema inferSchema(Path csvPath) throws IOException {

        try (
                Reader reader = Files.newBufferedReader(csvPath);
                CSVParser parser = FORMAT.parse(reader)) {

            List<String> headers = new ArrayList<>(parser.getHeaderNames());

            validateHeaders(headers);

            // null means no non-blank value has been seen yet.
            DataType[] types = new DataType[headers.size()];

            for (CSVRecord record : parser) {

                checkWidth(record, headers.size());

                for (int i = 0; i < types.length; i++) {
                    types[i] = updateType(types[i], record.get(i));
                }
            }

            List<Column> columns = new ArrayList<>();

            for (int i = 0; i < headers.size(); i++) {

                // Same default as TypeInference.inferColumn for an
                // all-blank (or empty) column.
                DataType type = types[i] == null
                        ? DataType.STRING
                        : types[i];

                columns.add(new Column(headers.get(i), type));
            }

            return new Schema(columns);
        }
    }

    /**
     * Folds one more raw value into a column's inferred type, with the same
     * rules as TypeInference.inferColumn: blanks are ignored, INTEGER and
     * FLOAT merge to FLOAT, anything else mixed becomes STRING.
     */
    private DataType updateType(DataType current, String rawValue) {

        if (rawValue == null || rawValue.isBlank()
                || current == DataType.STRING) {
            return current;
        }

        DataType next = TypeInference.inferValue(rawValue);

        return current == null
                ? next
                : TypeInference.mergeTypes(current, next);
    }

    /**
     * Pass 2: reads the file again and adds each record to the table as a
     * typed Row. Repeated values in a column share one Value object.
     */
    private void loadRows(Path csvPath, Table table) throws IOException {

        Schema schema = table.getSchema();

        List<SharedValues> columns = new ArrayList<>();

        for (int i = 0; i < schema.size(); i++) {
            columns.add(new SharedValues(schema.getColumn(i).getType()));
        }

        try (
                Reader reader = Files.newBufferedReader(csvPath);
                CSVParser parser = FORMAT.parse(reader)) {

            for (CSVRecord record : parser) {

                // Already checked in pass 1; repeated in case the file
                // changed between the two passes.
                checkWidth(record, schema.size());

                List<Value> values = new ArrayList<>(schema.size());

                for (int i = 0; i < schema.size(); i++) {
                    values.add(columns.get(i).parse(record.get(i)));
                }

                table.addRow(new Row(values));
            }
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
     * Checks that a record has the same number of fields as the header.
     */
    private void checkWidth(CSVRecord record, int expectedColumns) {

        if (record.size() != expectedColumns) {
            throw new IllegalArgumentException(
                    "CSV row " + record.getRecordNumber()
                            + " has " + record.size()
                            + " values but expected "
                            + expectedColumns);
        }
    }

    /**
     * Converts the raw strings of one column into Values, reusing one Value
     * object for each repeated value. Value is immutable and compares by
     * type and content, so sharing does not change any result.
     */
    static final class SharedValues {

        private final DataType type;
        private final Map<Object, Value> seen = new HashMap<>();

        SharedValues(DataType type) {
            this.type = type;
        }

        /**
         * Converts one raw CSV string into a typed Value.
         *
         * Blank CSV fields are represented as NULL values.
         */
        Value parse(String rawValue) {

            if (rawValue == null || rawValue.isBlank()) {
                return NULLS.get(type);
            }

            Object parsed = parseObject(rawValue.trim(), rawValue);

            Value shared = seen.get(parsed);

            if (shared != null) {
                return shared;
            }

            Value value = new Value(type, parsed);

            if (seen.size() < MAX_SHARED_VALUES_PER_COLUMN) {
                seen.put(parsed, value);
            }

            return value;
        }

        private Object parseObject(String value, String rawValue) {

            try {
                return switch (type) {
                    case INTEGER -> Integer.parseInt(value);
                    case FLOAT -> Float.parseFloat(value);
                    case BOOLEAN -> parseBoolean(value);
                    case STRING -> value;
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
    }

    /**
     * Parses a boolean without silently converting invalid
     * strings to false.
     */
    private static boolean parseBoolean(String value) {

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
