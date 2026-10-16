package com.group3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void redirect() {
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
    }

    @AfterEach
    void restore() {
        System.setIn(originalIn);
        System.setOut(originalOut);
    }

    private String runMain(String stdin, String... args) throws Exception {
        System.setIn(new ByteArrayInputStream(stdin.getBytes()));
        Main.main(args);
        return captured.toString();
    }

    @Test
    void printsHelpHintAndExitsOnEof() throws Exception {
        assertTrue(runMain("").contains("Type .help for commands."));
    }

    @Test
    void endToEndLoadAndQueryFromStdin(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("people.csv");
        Files.writeString(csv, "name,age\nAda,30\n");

        String output = runMain(".load " + csv + "\nSELECT name FROM people;\n");

        assertTrue(output.contains("Ada"));
        assertTrue(output.contains("(1 row)"));
    }

    @Test
    void scriptArgumentsRunBeforeStdin(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("t.csv");
        Files.writeString(csv, "id\n1\n2\n");
        Path script = dir.resolve("setup.sql");
        Files.writeString(script, ".load " + csv + "\n");

        String output = runMain("SELECT id FROM t;\n", script.toString());

        assertTrue(output.contains("(2 rows)"));
    }

    @Test
    void scriptArgumentsExportCsvAndAllowAnotherExportFromStdin(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("people.csv");
        Files.writeString(csv, "id,name\n1,Ada\n2,Bob\n");
        Path destination = dir.resolve("result.csv");
        Path second = dir.resolve("second.csv");
        Path script = dir.resolve("demo.sql");
        Files.writeString(script, ".load " + csv + " people\nSELECT name AS person FROM people WHERE id = 1;\n"
            + ".export " + destination + "\n");

        String output = runMain(".export " + second + "\n", script.toString());

        assertTrue(output.contains("Exported 1 row to " + destination));
        assertTrue(output.contains("Exported 1 row to " + second));
        try (var reader = Files.newBufferedReader(destination); var parser = CSVFormat.DEFAULT.parse(reader)) {
            assertEquals(List.of(List.of("person"), List.of("Ada")),
                parser.getRecords().stream().map(record -> record.toList()).toList());
        }
        assertEquals(Files.readString(destination), Files.readString(second));
    }

    @Test
    void missingScriptReportsErrorAndContinues() throws Exception {
        String output = runMain(".tables\n", "does-not-exist.sql");

        assertTrue(output.contains("Error: Script not found"));
        assertTrue(output.contains("(no tables loaded)"));
    }
}
