package com.group3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.group3.data.Column;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Table;
import com.group3.engine.QueryEngine;
import com.group3.engine.QueryResult;
import com.group3.parser.Query;
import com.group3.parser.SQLParser;
import com.group3.storage.CSVReader;
import com.group3.storage.CSVWriter;

/** Interactive shell: dot-commands (.load, .tables, ...) and SQL statements. */
public class Cli {

    private static final String PROMPT = "sql> ";

    private final Database database = new Database();
    private final SQLParser parser = new SQLParser();
    private final QueryEngine engine = new QueryEngine(database);
    private final CSVReader csvReader = new CSVReader();
    private final CSVWriter csvWriter = new CSVWriter();
    private final PrintStream out;
    private boolean running = true;

    public Cli(PrintStream out) {
        this.out = out;
    }

    public Database getDatabase() {
        return database;
    }

    /** Reads lines until EOF or .quit. Statements may span lines and end with ';'. */
    public void run(BufferedReader in, boolean interactive) throws IOException {
        StringBuilder pending = new StringBuilder();

        while (running) {
            if (interactive) {
                out.print(pending.length() == 0 ? PROMPT : "...> ");
                out.flush();
            }
            String line = in.readLine();
            if (line == null) {
                break;
            }

            String trimmed = line.trim();
            if (pending.length() == 0) {
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                if (trimmed.startsWith(".")) {
                    execute(trimmed);
                    continue;
                }
            }

            pending.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                execute(pending.toString().trim());
                pending.setLength(0);
            }
        }

        if (pending.toString().trim().length() > 0) {
            execute(pending.toString().trim());
        }
    }

    /** Runs one dot-command or SQL statement; errors are printed, not thrown. */
    public void execute(String input) {
        try {
            if (input.startsWith(".")) {
                runCommand(input);
            } else {
                runSql(input);
            }
        } catch (Exception e) {
            out.println("Error: " + e.getMessage());
        }
    }

    private void runSql(String sql) {
        String statement = sql.endsWith(";") ? sql.substring(0, sql.length() - 1) : sql;
        Query query = parser.parse(statement);
        QueryResult result = engine.processQuery(query);
        printResult(result);
    }

    private void runCommand(String input) throws IOException {
        String[] parts = input.split("\\s+");
        String command = parts[0].toLowerCase();

        switch (command) {
            case ".help" -> printHelp();
            case ".quit", ".exit" -> running = false;
            case ".tables" -> listTables();
            case ".schema" -> {
                requireArgs(parts, 2, ".schema <table>");
                printSchema(database.getTable(parts[1]));
            }
            case ".load" -> {
                if (parts.length < 2 || parts.length > 3) {
                    throw new IllegalArgumentException("Usage: .load <file.csv> [table]");
                }
                load(parts[1], parts.length == 3 ? parts[2] : defaultTableName(parts[1]));
            }
            case ".save" -> {
                requireArgs(parts, 3, ".save <table> <file.csv>");
                csvWriter.write(database.getTable(parts[1]), parts[2]);
                out.println("Saved " + parts[1] + " to " + parts[2]);
            }
            case ".read" -> {
                requireArgs(parts, 2, ".read <script.sql>");
                readScript(parts[1]);
            }
            default -> throw new IllegalArgumentException(
                "Unknown command: " + parts[0] + " (try .help)");
        }
    }

    private void requireArgs(String[] parts, int expected, String usage) {
        if (parts.length != expected) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private void load(String path, String name) throws IOException {
        if (database.containsTable(name)) {
            throw new IllegalArgumentException("Table already exists: " + name);
        }
        Table table = csvReader.read(path, name);
        database.addTable(table);
        out.println("Loaded " + table.size() + " rows into " + name);
    }

    private void readScript(String path) throws IOException {
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Script not found: " + path);
        }
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            run(reader, false);
        }
    }

    private static String defaultTableName(String path) {
        String name = Path.of(path).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void listTables() {
        if (database.getTables().isEmpty()) {
            out.println("(no tables loaded)");
            return;
        }
        for (Table table : database.getTables()) {
            out.println(table.getName() + " (" + table.size() + " rows)");
        }
    }

    private void printSchema(Table table) {
        for (Column column : table.getSchema().getColumns()) {
            out.println(column.getName() + " " + column.getType());
        }
    }

    private void printHelp() {
        out.println("Commands:");
        out.println("  .load <file.csv> [table]   load a CSV file as a table");
        out.println("  .save <table> <file.csv>   write a table to a CSV file");
        out.println("  .tables                    list loaded tables");
        out.println("  .schema <table>            show a table's columns");
        out.println("  .read <script.sql>         run commands from a file");
        out.println("  .help                      show this message");
        out.println("  .quit                      exit");
        out.println("SQL statements end with ';' and may span multiple lines.");
    }

    private void printResult(QueryResult result) {
        List<Column> columns = result.getSchema().getColumns();
        int width = columns.size();

        List<String[]> cells = new ArrayList<>();
        int[] widths = new int[width];
        String[] header = new String[width];
        for (int i = 0; i < width; i++) {
            header[i] = columns.get(i).getName();
            widths[i] = header[i].length();
        }
        for (Row row : result.getRows()) {
            String[] line = new String[width];
            for (int i = 0; i < width; i++) {
                line[i] = row.get(i).toString();
                widths[i] = Math.max(widths[i], line[i].length());
            }
            cells.add(line);
        }

        printLine(header, widths);
        StringBuilder rule = new StringBuilder();
        for (int i = 0; i < width; i++) {
            rule.append(i == 0 ? "" : "-+-").append("-".repeat(widths[i]));
        }
        out.println(rule);
        for (String[] line : cells) {
            printLine(line, widths);
        }
        out.println("(" + result.getRowCount() + (result.getRowCount() == 1 ? " row)" : " rows)"));
    }

    private void printLine(String[] cells, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(" | ");
            }
            line.append(cells[i]).append(" ".repeat(widths[i] - cells[i].length()));
        }
        out.println(line.toString().stripTrailing());
    }
}
