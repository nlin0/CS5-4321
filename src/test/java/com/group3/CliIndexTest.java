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

/** .index / .indexes / .dropindex, and that indexed queries print the same rows. */
class CliIndexTest {

    private String run(String script) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Cli cli = new Cli(new PrintStream(buffer));
        cli.run(new BufferedReader(new StringReader(script)), false);
        return buffer.toString();
    }

    private Path orders(Path dir) throws Exception {
        Path csv = dir.resolve("orders.csv");
        StringBuilder text = new StringBuilder("id,status,amount\n");
        String[] statuses = {"shipped", "pending", "returned"};
        for (int i = 0; i < 30; i++) {
            text.append(i).append(',').append(statuses[i % 3]).append(',').append((i * 7) % 50).append('\n');
        }
        Files.writeString(csv, text.toString());
        return csv;
    }

    @Test
    void indexedQueryPrintsSameRowsPlusIndexLine(@TempDir Path dir) throws Exception {
        String load = ".load " + orders(dir) + "\n";
        String query = "SELECT id, amount FROM orders WHERE status = 'returned' AND amount > 20;\n";

        String plain = run(load + query);
        String indexed = run(load + ".index ret orders amount WHERE status = 'returned'\n" + query);

        assertTrue(indexed.contains("Created index ret ON orders(amount) WHERE status = 'returned' [10 of 30 rows]"),
            indexed);
        assertTrue(indexed.contains("Index: ret (scanned "), indexed);
        String withoutIndexLines = indexed.replaceAll("(?m)^(Created index|Index:).*\\R", "");
        assertEquals(plain, withoutIndexLines);
    }

    @Test
    void queryNotMatchingPredicateDoesNotUseIndex(@TempDir Path dir) throws Exception {
        String output = run(".load " + orders(dir) + "\n"
            + ".index ret orders amount WHERE status = 'returned'\n"
            + "SELECT id FROM orders WHERE status = 'pending';\n");
        assertFalse(output.contains("Index:"), output);
    }

    @Test
    void listAndDropIndexes(@TempDir Path dir) throws Exception {
        String output = run(".load " + orders(dir) + "\n"
            + ".indexes\n"
            + ".index all_ids orders id\n"
            + ".index ret orders amount where status = 'returned' and amount >= 10\n"
            + ".indexes\n"
            + ".dropindex all_ids\n"
            + ".dropindex all_ids\n");
        assertTrue(output.contains("No indexes"), output);
        assertTrue(output.contains("all_ids ON orders(id) [30 of 30 rows]"), output);
        assertTrue(output.contains("ret ON orders(amount) WHERE status = 'returned' and amount >= 10"), output);
        assertTrue(output.contains("Dropped index all_ids"), output);
        assertTrue(output.contains("Error: Index not found: all_ids"), output);
    }

    @Test
    void badIndexCommandsReportErrors(@TempDir Path dir) throws Exception {
        String output = run(".load " + orders(dir) + "\n"
            + ".index\n"
            + ".index x orders missing\n"
            + ".index x nope id\n"
            + ".index x orders id WHERE status = 'a' OR status = 'b'\n");
        assertTrue(output.contains("Error: Usage: .index"), output);
        assertTrue(output.contains("Error: Column not found: missing"), output);
        assertTrue(output.contains("Error: Table not found: nope"), output);
        assertTrue(output.contains("Error: Index WHERE supports only"), output);
    }

    @Test
    void helpListsIndexCommands() throws Exception {
        String output = run(".help\n");
        assertTrue(output.contains(".index"));
        assertTrue(output.contains(".indexes"));
        assertTrue(output.contains(".dropindex"));
    }
}
