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
}
