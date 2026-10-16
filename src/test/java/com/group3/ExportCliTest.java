package com.group3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExportCliTest {
    @TempDir Path directory;
    private Path source;
    private Path destination;
    private ByteArrayOutputStream output;
    private Cli cli;

    @BeforeEach
    void setUp() throws Exception {
        source = directory.resolve("people.csv");
        destination = directory.resolve("result.csv");
        Files.writeString(source, "id,name,age,dept\n1,Ada,30,eng\n2,Bob,20,eng\n3,Cy,40,math\n");
        output = new ByteArrayOutputStream();
        cli = new Cli(new PrintStream(output));
    }

    private void loadAndQuery(String sql) {
        cli.execute(".load " + source + " people");
        cli.execute(sql);
    }

    private List<List<String>> records(Path file) throws Exception {
        try (var reader = Files.newBufferedReader(file); var parser = CSVFormat.DEFAULT.parse(reader)) {
            return parser.getRecords().stream().map(record -> record.toList()).toList();
        }
    }

    @Test
    void exportsFilteredProjectedAndSortedRowsWithAliases() throws Exception {
        loadAndQuery("SELECT name AS person, age FROM people WHERE age >= 30 ORDER BY age DESC;");
        cli.execute(".export " + destination);

        assertEquals(List.of(List.of("person", "age"), List.of("Cy", "40"), List.of("Ada", "30")), records(destination));
        assertTrue(output.toString().contains("Exported 2 rows to " + destination));
        assertTrue(output.toString().contains("(2 rows)"));
    }

    @Test
    void newestQueryReplacesTheStoredResult() throws Exception {
        loadAndQuery("SELECT name FROM people;");
        cli.execute("SELECT id AS identifier FROM people WHERE id = 2;");
        cli.execute(".export " + destination);
        assertEquals(List.of(List.of("identifier"), List.of("2")), records(destination));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exportBeforeQueryDoesNotCreateOrOverwriteAFile(boolean existingFile) throws Exception {
        if (existingFile) Files.writeString(destination, "keep me");
        cli.execute(".export " + destination);
        assertTrue(output.toString().contains("Error: No query result to export"));
        if (existingFile) assertEquals("keep me", Files.readString(destination));
        else assertFalse(Files.exists(destination));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELEC name FROM people;", "SELECT * FROM missing;", "SELECT missing FROM people;"})
    void failedSqlClearsTheOldResultWithoutTouchingDestination(String invalidSql) throws Exception {
        loadAndQuery("SELECT name FROM people;");
        Files.writeString(destination, "keep me");
        cli.execute(invalidSql);
        cli.execute(".export " + destination);
        assertTrue(output.toString().contains("Error: No query result to export"));
        assertEquals("keep me", Files.readString(destination));
        cli.execute("SELECT id FROM people WHERE id = 1;");
        cli.execute(".export " + destination);
        assertEquals(List.of(List.of("id"), List.of("1")), records(destination));
    }

    @Test
    void dotCommandsPreserveResultAndSaveStillWritesTheWholeTable() throws Exception {
        loadAndQuery("SELECT name FROM people WHERE id = 1;");
        Path savedTable = directory.resolve("table.csv");
        cli.execute(".tables");
        cli.execute(".schema people");
        cli.execute(".help");
        cli.execute(".save people " + savedTable);
        cli.execute(".schema missing");
        cli.execute(".export " + destination);

        assertEquals(4, records(savedTable).size());
        assertEquals(List.of("id", "name", "age", "dept"), records(savedTable).get(0));
        assertEquals(List.of(List.of("name"), List.of("Ada")), records(destination));
        assertTrue(output.toString().contains(".export <file.csv>"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing parent", "directory"})
    void writeErrorsAllowRetryWithoutLosingTheResult(String failure) throws Exception {
        loadAndQuery("SELECT name FROM people WHERE id = 1;");
        Path badPath = failure.equals("directory") ? directory : directory.resolve("missing/result.csv");
        cli.execute(".export \"" + badPath + "\"");
        assertTrue(output.toString().contains("Error:"));
        assertFalse(output.toString().contains("Exported"));
        assertFalse(Files.exists(directory.resolve("missing")));

        cli.execute(".export " + destination);
        assertEquals(List.of(List.of("name"), List.of("Ada")), records(destination));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"\"", "\"unterminated", "\"result.csv\" extra", "one.csv two.csv"})
    void invalidExportArgumentsReportUsageAndKeepTheResult(String arguments) throws Exception {
        loadAndQuery("SELECT name FROM people WHERE id = 1;");
        cli.execute(".export " + arguments);
        assertTrue(output.toString().contains("Error: Usage: .export <file.csv>"));
        cli.execute(".export " + destination);
        assertEquals(List.of(List.of("name"), List.of("Ada")), records(destination));
    }

    @Test
    void acceptsDoubleQuotedPathsContainingSpaces() throws Exception {
        loadAndQuery("SELECT name FROM people WHERE id = 1;");
        Path spacedPath = directory.resolve("query results.csv");
        cli.execute(".export \"" + spacedPath + "\"");
        assertEquals(List.of(List.of("name"), List.of("Ada")), records(spacedPath));
        assertTrue(output.toString().contains("Exported 1 row to " + spacedPath));
    }

    @Test
    void resolvesRelativeDestinationFromTheProcessWorkingDirectory() throws Exception {
        loadAndQuery("SELECT name FROM people WHERE id = 1;");
        Path relative = Path.of("").toAbsolutePath().relativize(destination.toAbsolutePath());
        cli.execute(".export " + relative);
        assertEquals(List.of(List.of("name"), List.of("Ada")), records(destination));
    }

    @Test
    void repeatedExportsOverwriteExistingFilesAndKeepTheStoredResult() throws Exception {
        loadAndQuery("SELECT name FROM people WHERE id = 1;");
        Files.writeString(destination, "old contents that must be completely removed");
        cli.execute(".export " + destination);
        Path second = directory.resolve("second.csv");
        cli.execute(".export " + second);
        assertEquals(List.of(List.of("name"), List.of("Ada")), records(destination));
        assertEquals(Files.readString(destination), Files.readString(second));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WHERE id = 99", "LIMIT 0"})
    void emptyQueriesExportHeaderOnlyRatherThanAnOlderResult(String emptyClause) throws Exception {
        loadAndQuery("SELECT * FROM people;");
        cli.execute("SELECT name AS person FROM people " + emptyClause + ";");
        cli.execute(".export " + destination);
        assertEquals(List.of(List.of("person")), records(destination));
        assertTrue(output.toString().contains("Exported 0 rows to " + destination));
    }

    @Test
    void exportsGroupedAggregatesHavingAndWrittenSelectOrder() throws Exception {
        loadAndQuery("SELECT COUNT(*) AS n, dept AS team FROM people GROUP BY dept HAVING n >= 2 ORDER BY n DESC;");
        cli.execute(".export " + destination);
        assertEquals(List.of(List.of("n", "team"), List.of("2", "eng")), records(destination));
    }

    @Test
    void exportsDistinctAndLimitedResults() throws Exception {
        loadAndQuery("SELECT DISTINCT dept AS team FROM people ORDER BY team DESC LIMIT 1;");
        cli.execute(".export " + destination);
        assertEquals(List.of(List.of("team"), List.of("math")), records(destination));
    }

    @Test
    void exportsFromReadScriptAndKeepsResultInTheSameSession() throws Exception {
        Path script = directory.resolve("demo.sql");
        Files.writeString(script, ".load " + source + " people\nSELECT name AS person\n"
            + "FROM people WHERE id = 1;\n.export " + destination + "\n");
        cli.run(new BufferedReader(new StringReader(".read " + script + "\n")), false);
        assertEquals(List.of(List.of("person"), List.of("Ada")), records(destination));
        Path second = directory.resolve("second.csv");
        cli.execute(".export " + second);
        assertEquals(Files.readString(destination), Files.readString(second));
    }
}
