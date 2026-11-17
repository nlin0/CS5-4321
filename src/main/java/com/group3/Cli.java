package com.group3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.group3.data.Column;
import com.group3.data.Database;
import com.group3.data.ForeignKey;
import com.group3.data.Row;
import com.group3.data.Table;
import com.group3.engine.QueryEngine;
import com.group3.engine.QueryResult;
import com.group3.expression.Expression;
import com.group3.index.PartialIndex;
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
    private QueryResult lastResult;
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

    private static final Pattern INDEX_COMMAND = Pattern.compile(
        "(?is)\\.index\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)(?:\\s+where\\s+(.+?))?\\s*;?\\s*");

    /** .index name table column [WHERE condition]; the condition goes through the normal SQL parser. */
    private void createIndex(String input) {
        Matcher m = INDEX_COMMAND.matcher(input.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Usage: .index <name> <table> <column> [WHERE <condition>]");
        }
        String condition = m.group(4);
        Expression predicate = null;
        if (condition != null) {
            Table table = database.getTable(m.group(2));
            predicate = parser.parse("SELECT * FROM " + table.getName() + " WHERE " + condition).getWhere();
        }
        PartialIndex index = engine.createIndex(m.group(1), m.group(2), m.group(3), predicate, condition);
        out.println("Created index " + index.describe());
    }

    private void runSql(String sql) {
        // A failed query must not leave an older result available for accidental export.
        lastResult = null;
        String statement = sql.endsWith(";") ? sql.substring(0, sql.length() - 1) : sql;
        Query query = parser.parse(statement);
        QueryResult result = engine.processQuery(query);
        if (engine.getLastJoin() != null) {
            out.println("Join: " + engine.getLastJoin());
        }
        if (engine.getLastIndex() != null) {
            out.println("Index: " + engine.getLastIndex());
        }
        printResult(result);
        lastResult = result;
    }

    private void runCommand(String input) throws IOException {
        String[] parts = input.split("\\s+");
        String command = parts[0].toLowerCase();

        switch (command) {
            case ".help" -> printHelp();
            case ".quit", ".exit" -> running = false;
            case ".tables" -> listTables();
            case ".join" -> {
                if (parts.length != 2 && parts.length != 4) {
                    throw new IllegalArgumentException("Usage: .join auto|hash|grace [partitions maxRows]");
                }
                int partitions = 16;
                int maxRows = 100_000;
                if (parts.length == 4) {
                    partitions = Integer.parseInt(parts[2]);
                    maxRows = Integer.parseInt(parts[3]);
                }
                String mode = parts[1].toLowerCase(java.util.Locale.ROOT);
                engine.setJoin(mode, partitions, maxRows);
                out.println("Join mode: " + mode + "; partitions: " + partitions + "; build-row limit: " + maxRows);
            }
            case ".schema" -> {
                requireArgs(parts, 2, ".schema <table>");
                printSchema(database.getTable(parts[1]));
            }
            case ".index" -> createIndex(input);
            case ".indexes" -> {
                if (engine.getIndexes().isEmpty()) {
                    out.println("No indexes");
                }
                for (PartialIndex index : engine.getIndexes()) {
                    out.println(index.describe());
                }
            }
            case ".dropindex" -> {
                requireArgs(parts, 2, ".dropindex <name>");
                engine.dropIndex(parts[1]);
                out.println("Dropped index " + parts[1]);
            }
            case ".pk" -> {
                requireArgs(parts, 3, ".pk <table> <column>");
                Table table = database.getTable(parts[1]);
                table.setPrimaryKey(parts[2]);
                out.println("Primary key " + table.getPrimaryKeyColumn() + " on " + table.getName());
            }
            case ".fk" -> {
                requireArgs(parts, 5, ".fk <child> <childColumn> <parent> <parentColumn>");
                database.addForeignKey(parts[1], parts[2], parts[3], parts[4]);
                out.println("Foreign key " + parts[1] + "." + parts[2]
                    + " references " + parts[3] + "." + parts[4]);
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
            case ".export" -> exportResult(exportPath(input.substring(parts[0].length())));
            case ".read" -> {
                requireArgs(parts, 2, ".read <script.sql>");
                readScript(parts[1]);
            }
            default -> throw new IllegalArgumentException(
                "Unknown command: " + parts[0] + " (try .help)");
        }
    }

    /** Export accepts one path; double quotes allow spaces without changing other dot-commands. */
    private String exportPath(String argument) {
        String path = argument.trim();
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
            if (!path.isBlank() && !path.contains("\"")) {
                return path;
            }
        } else if (!path.isBlank() && !path.contains("\"")
                && path.chars().noneMatch(Character::isWhitespace)) {
            return path;
        }
        throw new IllegalArgumentException("Usage: .export <file.csv> (double-quote paths containing spaces)");
    }

    private void exportResult(String path) throws IOException {
        if (lastResult == null) {
            throw new IllegalStateException("No query result to export");
        }
        csvWriter.write(lastResult.getSchema(), lastResult.getRows(), path);
        int count = lastResult.getRowCount();
        out.println("Exported " + count + (count == 1 ? " row to " : " rows to ") + path);
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
            StringBuilder line = new StringBuilder(column.getName())
                .append(' ')
                .append(column.getType());
            if (column.getName().equalsIgnoreCase(table.getPrimaryKeyColumn())) {
                line.append(" primary key");
            }
            for (ForeignKey foreignKey : table.getForeignKeys()) {
                if (foreignKey.getChildColumn().equalsIgnoreCase(column.getName())) {
                    line.append(" foreign key");
                }
            }
            out.println(line);
        }
    }

    private void printHelp() {
        out.println("Commands:");
        out.println("  .load <file.csv> [table]   load a CSV file as a table");
        out.println("  .save <table> <file.csv>   write a table to a CSV file");
        out.println("  .export <file.csv>         write the latest query result to CSV");
        out.println("  .join auto|hash|grace [partitions maxRows]");
        out.println("                             choose join mode and optional limits");
        out.println("  .tables                    list loaded tables");
        out.println("  .schema <table>            show a table's columns");
        out.println("  .pk <table> <column>       declare a primary key");
        out.println("  .fk <child> <column> <parent> <column>");
        out.println("                             declare a foreign key");
        out.println("  .index <name> <table> <column> [WHERE <condition>]");
        out.println("                             create an index; with WHERE, a partial index");
        out.println("  .indexes                   list indexes");
        out.println("  .dropindex <name>          remove an index");
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
