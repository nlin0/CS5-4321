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
    void sqlWhereFiltersLoadedCsv(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("people.csv");
        Files.writeString(csv, "id,name\n1,Ada\n2,Bob\n3,Cy\n");

        String output = run(".load " + csv + "\nSELECT name FROM people WHERE id = 1;\n");

        assertTrue(output.contains("Ada"));
        assertTrue(output.contains("(1 row)"));
        assertFalse(output.contains("Bob"));
        assertFalse(output.contains("Cy"));
    }

    @Test
    void printsColumnAliasAsHeaderWithFilteredRows(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("people.csv");
        Files.writeString(csv, "id,name\n1,Ada\n2,Bob\n");

        String output = run(".load " + csv + "\nSELECT name AS person FROM people WHERE id = 1;\n");

        assertEquals("Loaded 2 rows into people\nperson\n------\nAda\n(1 row)\n", output);
    }

    @Test
    void printsAggregateAndGroupAliasesInWrittenOrder(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("people.csv");
        Files.writeString(csv, "id,dept\n1,eng\n2,eng\n3,math\n");

        String output = run(".load " + csv + "\nSELECT COUNT(*) AS n, dept AS team "
            + "FROM people GROUP BY dept ORDER BY 1 DESC;\n");

        assertEquals("Loaded 3 rows into people\nn | team\n--+-----\n2 | eng\n1 | math\n(2 rows)\n", output);
    }

    @Test
    void reportsDuplicateAliasesThenRunsAQueryWithAQuotedAlias(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("people.csv");
        Files.writeString(csv, "id,name\n1,Ada\n2,Bob\n");

        String output = run(".load " + csv + "\nSELECT id AS same, name AS same FROM people;\n"
            + "SELECT name \"Person Name\" FROM people WHERE id = 1 ORDER BY \"Person Name\";\n");

        assertEquals("Loaded 2 rows into people\nError: Duplicate column: same\n"
            + "Person Name\n-----------\nAda\n(1 row)\n", output);
    }

    @Test
    void unsupportedWhereReportsErrorAndAllowsNextQuery(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("people.csv");
        Files.writeString(csv, "id,name\n1,Ada\n2,Bob\n");

        String output = run(".load " + csv + "\nSELECT name FROM people WHERE name LIKE 'B%';\n"
            + "SELECT name FROM people WHERE id = 1;\n");

        assertTrue(output.contains("Error: Unsupported WHERE expression"));
        assertTrue(output.contains("Ada"));
        assertTrue(output.contains("(1 row)"));
        assertFalse(output.contains("Bob"));
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

        for (String command : new String[] {".load", ".save", ".export", ".tables", ".schema", ".read", ".quit"}) {
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
