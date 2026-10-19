package com.group3.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Database;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.engine.QueryEngine;
import com.group3.engine.QueryResult;
import com.group3.parser.SQLParser;

class CSVEdgeCaseTest {

    @TempDir Path directory;
    private final CSVReader reader = new CSVReader();

    @Test
    void readsCrLfRecordsWithEmbeddedNewlinesEscapedQuotesAndUnicode() throws IOException {
        Table table = read("name,notes,score\r\n"
            + "\"Zoë 東京\",\"first line\r\n\"\"quoted, second\"\"\",-3.5\r\n");

        assertEquals(1, table.size());
        assertEquals(List.of("Zoë 東京", "first line\r\n\"quoted, second\"", -3.5f),
            payloads(table).get(0));
    }

    @Test
    void lateStringPromotionPreservesEarlierNumericAndBooleanText() throws IOException {
        Table table = read("id,value\n1,001\n2,2.5\n3,false\n4,text\n");

        assertEquals(DataType.STRING, table.getSchema().getColumn("value").getType());
        assertEquals(List.of("001", "2.5", "false", "text"),
            table.getRows().stream().map(row -> row.get(1).getValue()).toList());
    }

    @Test
    void floatPromotionHandlesScientificNotationNegativeValuesAndBlanks() throws IOException {
        Table table = read("label,value\nmissing,\nnegative,-2\nfraction,1.25e1\nzero,0\nblank,   \n");

        assertEquals(DataType.FLOAT, table.getSchema().getColumn("value").getType());
        assertEquals(Arrays.asList(null, -2.0f, 12.5f, 0.0f, null),
            table.getRows().stream().map(row -> row.get(1).getValue()).toList());
        assertTrue(table.getRows().stream().allMatch(row -> row.get(1).getType() == DataType.FLOAT));
    }

    @Test
    void allBlankColumnsDefaultToStringAndDoNotDropRecords() throws IOException {
        Table table = read("id,first,second\n1,,\n2,\"   \", \n3,,\"\"\n");

        assertEquals(List.of(DataType.INTEGER, DataType.STRING, DataType.STRING),
            table.getSchema().getColumns().stream().map(Column::getType).toList());
        assertEquals(List.of(Arrays.asList(1, null, null), Arrays.asList(2, null, null),
            Arrays.asList(3, null, null)), payloads(table));
    }

    @Test
    void preservesExactIntegerBoundaries() throws IOException {
        Table table = read("value\n-2147483648\n2147483647\n0\n");

        assertEquals(DataType.INTEGER, table.getSchema().getColumn(0).getType());
        assertEquals(List.of(List.of(Integer.MIN_VALUE), List.of(Integer.MAX_VALUE), List.of(0)), payloads(table));
    }

    @Test
    void booleanInferenceIsCaseInsensitiveAndSkipsBlankCells() throws IOException {
        Table table = read("id,active\n1, TRUE \n2,fAlSe\n3,\n4,true\n");

        assertEquals(DataType.BOOLEAN, table.getSchema().getColumn("active").getType());
        assertEquals(Arrays.asList(true, false, null, true),
            table.getRows().stream().map(row -> row.get(1).getValue()).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\n\n", "name,,score\n", "name,   ,score\n", "\" \",score\n", " name ,NAME\n"})
    void rejectsMissingBlankAndWhitespaceNormalizedDuplicateHeaders(String contents) {
        assertThrows(IllegalArgumentException.class, () -> read(contents));
    }

    @Test
    void countsMultilineFieldsAsOneRecordWhenValidatingRowWidths() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> read("id,note,score\n1,\"first\nsecond\",2\n2,missing-score\n"));

        assertTrue(exception.getMessage().contains("has 2 values but expected 3"));
    }

    @Test
    void rejectsDirectoryPaths() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> reader.read(directory.toString(), "input"));

        assertTrue(exception.getMessage().contains("CSV path is not a file"));
    }

    @Test
    void tableRoundTripPreservesMultilineUnicodeQuotesNullsAndInferredTypes() throws IOException {
        Table original = workflowInput();
        Path output = directory.resolve("round-trip.csv");

        new CSVWriter().write(original, output.toString());
        Table reloaded = reader.read(output.toString(), "reloaded");

        assertEquals(List.of(
            List.of("Office, East", 2.5f, true, "first\nsecond"),
            Arrays.asList("Office, East", null, true, "said \"hi\""),
            List.of("R&D", 4.0f, false, "東京"),
            List.of("R&D", 1.25f, true, "Zoë")
        ), payloads(reloaded));
        assertEquals(original.getRows(), reloaded.getRows());
        assertEquals(original.getSchema().getColumns().stream().map(Column::getType).toList(),
            reloaded.getSchema().getColumns().stream().map(Column::getType).toList());
    }

    @Test
    void importedDataCanBeFilteredGroupedExportedAndReloaded() throws IOException {
        Database database = new Database();
        database.addTable(workflowInput());
        QueryResult result = new QueryEngine(database).processQuery(new SQLParser().parse("""
            SELECT category, COUNT(*) AS records, COUNT(amount) AS counted, SUM(amount) AS total
            FROM input WHERE active = true GROUP BY category
            HAVING total >= 1 ORDER BY total DESC
            """));
        Path output = directory.resolve("query-result.csv");

        new CSVWriter().write(result.getSchema(), result.getRows(), output.toString());
        Table reloaded = reader.read(output.toString(), "report");

        assertEquals(List.of("category", "records", "counted", "total"),
            reloaded.getSchema().getColumns().stream().map(Column::getName).toList());
        assertEquals(List.of(DataType.STRING, DataType.INTEGER, DataType.INTEGER, DataType.FLOAT),
            reloaded.getSchema().getColumns().stream().map(Column::getType).toList());
        assertEquals(List.of(List.of("Office, East", 2, 1, 2.5f), List.of("R&D", 1, 1, 1.25f)),
            payloads(reloaded));
    }

    private Table workflowInput() throws IOException {
        return read("category,amount,active,note\n"
            + "\"Office, East\",2.5,true,\"first\nsecond\"\n"
            + "\"Office, East\",,TRUE,\"said \"\"hi\"\"\"\n"
            + "R&D,4,false,東京\nR&D,1.25,true,Zoë\n");
    }

    private Table read(String contents) throws IOException {
        Path file = directory.resolve("input.csv");
        Files.writeString(file, contents);
        return reader.read(file.toString(), "input");
    }

    private static List<List<Object>> payloads(Table table) {
        return table.getRows().stream()
            .map(row -> row.getValues().stream().map(Value::getValue).toList()).toList();
    }
}
