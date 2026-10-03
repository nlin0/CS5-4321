package com.group3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {

    private String run(String script) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Cli cli = new Cli(new PrintStream(buffer));
        cli.run(new BufferedReader(new StringReader(script)), false);
        return buffer.toString();
    }

    @Test
    void loadsCsvAndRunsQuery(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("students.csv");
        Files.writeString(csv, "name,age\nAda,30\nBob,25\n");

        String output = run(
            ".load " + csv + "\n" +
            ".tables\n" +
            "SELECT name FROM students;\n" +
            "SELECT * FROM students;\n");

        assertTrue(output.contains("Loaded 2 rows into students"));
        assertTrue(output.contains("students (2 rows)"));
        assertTrue(output.contains("Ada"));
        assertTrue(output.contains("(2 rows)"));
        assertTrue(output.contains("age"));
    }

    @Test
    void reportsErrorsWithoutExiting() throws Exception {
        String output = run(
            "SELECT * FROM missing;\n" +
            ".bogus\n" +
            ".tables\n");

        assertTrue(output.contains("Error: Table not found: missing"));
        assertTrue(output.contains("Unknown command: .bogus"));
        assertTrue(output.contains("(no tables loaded)"));
    }

    @Test
    void readsScriptFile(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("t.csv");
        Files.writeString(csv, "id\n1\n2\n3\n");
        Path script = dir.resolve("s.sql");
        Files.writeString(script, "-- comment\n.load " + csv + "\nSELECT id\nFROM t;\n");

        String output = run(".read " + script + "\n");

        assertTrue(output.contains("(3 rows)"));
    }

    @Test
    void quitStopsProcessing() throws Exception {
        String output = run(".quit\n.tables\n");
        assertEquals("", output);
    }

    @Test
    void schemaListsColumnsWithInferredTypes(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("t.csv");
        Files.writeString(csv, "name,age,active\nAda,30,true\n");

        String output = run(".load " + csv + "\n.schema t\n");

        assertTrue(output.contains("name STRING"));
        assertTrue(output.contains("age INTEGER"));
        assertTrue(output.contains("active BOOLEAN"));
    }

    @Test
    void schemaOfMissingTableReportsError() throws Exception {
        assertTrue(run(".schema nope\n").contains("Error: Table not found: nope"));
    }

    @Test
    void helpListsCommands() throws Exception {
        String output = run(".help\n");

        for (String command : new String[] {".load", ".save", ".tables", ".schema", ".read", ".quit"}) {
            assertTrue(output.contains(command), command);
        }
    }

    @Test
    void saveWritesTableBackToCsv(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("t.csv");
        Path out = dir.resolve("copy.csv");
        Files.writeString(csv, "id,name\n1,Ada\n2,Bob\n");

        String output = run(".load " + csv + "\n.save t " + out + "\n");

        assertTrue(output.contains("Saved t to"));
        assertTrue(Files.readString(out).contains("2,Bob"));
    }

    @Test
    void saveOfMissingTableReportsError(@TempDir Path dir) throws Exception {
        String output = run(".save nope " + dir.resolve("x.csv") + "\n");

        assertTrue(output.contains("Error: Table not found: nope"));
    }

    @Test
    void loadingSameTableTwiceIsRejected(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("t.csv");
        Files.writeString(csv, "id\n1\n");

        String output = run(".load " + csv + "\n.load " + csv + "\n");

        assertTrue(output.contains("Error: Table already exists: t"));
    }

    @Test
    void loadAcceptsExplicitTableName(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("data.csv");
        Files.writeString(csv, "id\n1\n");

        String output = run(".load " + csv + " custom\n.tables\n");

        assertTrue(output.contains("custom (1 rows)"));
    }

    @Test
    void loadOfMissingFileReportsError() throws Exception {
        assertTrue(run(".load /no/such/file.csv\n").contains("Error:"));
    }

    @Test
    void loadWithWrongArgumentCountShowsUsage() throws Exception {
        assertTrue(run(".load\n").contains("Usage: .load"));
    }

    @Test
    void statementWithoutTrailingSemicolonRunsAtEndOfInput(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("t.csv");
        Files.writeString(csv, "id\n1\n");

        String output = run(".load " + csv + "\nSELECT id FROM t");

        assertTrue(output.contains("(1 row)"));
    }

    @Test
    void multipleStatementsOnSeparateLinesAllRun(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("t.csv");
        Files.writeString(csv, "id\n1\n2\n");

        String output = run(".load " + csv + "\nSELECT id FROM t;\nSELECT * FROM t;\n");

        assertEquals(2, output.split("\\(2 rows\\)", -1).length - 1);
    }

    @Test
    void invalidSqlReportsErrorAndContinues() throws Exception {
        String output = run("SELEC nonsense;\n.tables\n");

        assertTrue(output.contains("Error: Invalid SQL syntax"));
        assertTrue(output.contains("(no tables loaded)"));
    }

    @Test
    void missingScriptReportsError() throws Exception {
        assertTrue(run(".read /no/such/script.sql\n").contains("Error: Script not found"));
    }
}
