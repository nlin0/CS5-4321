package com.group3.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Value;

class CSVResultWriterTest {
    @TempDir Path directory;
    private final CSVWriter writer = new CSVWriter();

    private List<List<String>> records(Path file) throws Exception {
        try (var reader = Files.newBufferedReader(file); var parser = CSVFormat.DEFAULT.parse(reader)) {
            return parser.getRecords().stream().map(record -> record.toList()).toList();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"City, State", "A \"quote\"", "first\nsecond", "first\r\nsecond", "Zoë 東京"})
    void roundTripsSpecialHeadersAndValues(String text) throws Exception {
        Path destination = directory.resolve("result.csv");
        Schema schema = new Schema(List.of(new Column(text, DataType.STRING)));
        writer.write(schema, List.of(new Row(List.of(new Value(DataType.STRING, text)))), destination.toString());
        assertEquals(List.of(List.of(text), List.of(text)), records(destination));
    }

    @Test
    void preservesColumnOrderTypesAndNullCells() throws Exception {
        Schema schema = new Schema(List.of(new Column("person", DataType.STRING),
            new Column("age", DataType.INTEGER), new Column("score", DataType.FLOAT),
            new Column("active", DataType.BOOLEAN)));
        List<Row> rows = List.of(
            new Row(List.of(new Value(DataType.STRING, "Ada"), new Value(DataType.INTEGER, 30),
                new Value(DataType.FLOAT, 3.5), new Value(DataType.BOOLEAN, true))),
            new Row(List.of(new Value(DataType.STRING, "Bob"), new Value(DataType.INTEGER, null),
                new Value(DataType.FLOAT, null), new Value(DataType.BOOLEAN, false))));
        Path destination = directory.resolve("result.csv");

        writer.write(schema, rows, destination.toString());

        assertEquals(List.of(List.of("person", "age", "score", "active"),
            List.of("Ada", "30", "3.5", "true"), List.of("Bob", "", "", "false")), records(destination));
    }

    @Test
    void emptyResultsWriteOnlyTheHeader() throws Exception {
        Path destination = directory.resolve("result.csv");
        writer.write(new Schema(List.of(new Column("person", DataType.STRING))), List.of(), destination.toString());
        assertEquals(List.of(List.of("person")), records(destination));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null schema", "null rows", "short row", "long row", "null row", "null value", "null path", "blank path"})
    void rejectsInvalidInputBeforeTruncatingDestination(String invalid) throws Exception {
        Path destination = directory.resolve("result.csv");
        Files.writeString(destination, "keep this file");
        Schema schema = new Schema(List.of(new Column("id", DataType.INTEGER)));
        Value value = new Value(DataType.INTEGER, 1);
        List<Row> rows = List.of(new Row(List.of(value)));
        String path = destination.toString();
        switch (invalid) {
            case "null schema" -> schema = null;
            case "null rows" -> rows = null;
            case "short row" -> rows = List.of(new Row(List.of()));
            case "long row" -> rows = List.of(new Row(List.of(value, value)));
            case "null row" -> rows = Arrays.asList((Row) null);
            case "null value" -> rows = List.of(new Row(Arrays.asList((Value) null)));
            case "null path" -> path = null;
            case "blank path" -> path = " ";
            default -> throw new AssertionError(invalid);
        }
        Schema invalidSchema = schema;
        List<Row> invalidRows = rows;
        String invalidPath = path;

        assertThrows(IllegalArgumentException.class, () -> writer.write(invalidSchema, invalidRows, invalidPath));
        assertEquals("keep this file", Files.readString(destination));
    }
}
