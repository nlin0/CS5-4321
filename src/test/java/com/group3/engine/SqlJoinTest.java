package com.group3.engine;

import com.group3.Cli;
import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.parser.SQLParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlJoinTest {
    private Database database;
    private QueryEngine engine;
    private final SQLParser parser = new SQLParser();
    private static final String FROM = " FROM students s JOIN enrollments e ON s.id = e.student_id";

    @BeforeEach
    void setup() {
        database = new Database();
        database.addTable(students());
        database.addTable(enrollments());
        engine = new QueryEngine(database);
    }

    private Table students() {
        Schema schema = new Schema(List.of(new Column("id", DataType.INTEGER),
            new Column("name", DataType.STRING)));
        return new Table("students", schema, List.of(
            row(1, "Alice"), row(2, "Bob"), row(3, "Cara"), row(null, "Unknown")));
    }

    private Table enrollments() {
        Schema schema = new Schema(List.of(new Column("student_id", DataType.INTEGER),
            new Column("course", DataType.STRING)));
        return new Table("enrollments", schema, List.of(
            row(1, "CS101"), row(1, "MATH201"), row(3, "CS101"), row(null, "NONE")));
    }

    private Row row(Integer id, String text) {
        return new Row(List.of(new Value(DataType.INTEGER, id), new Value(DataType.STRING, text)));
    }

    private QueryResult run(String sql) {
        return engine.processQuery(parser.parse(sql));
    }

    private List<List<Object>> values(QueryResult result) {
        List<List<Object>> rows = new ArrayList<>();
        for (Row row : result.getRows()) {
            List<Object> values = new ArrayList<>();
            for (Value value : row.getValues()) {
                values.add(value.getValue());
            }
            rows.add(values);
        }
        return rows;
    }

    @Test
    void joinsModes() {
        String sql = "SELECT s.name, e.course" + FROM + " ORDER BY s.name, e.course";
        List<List<Object>> expected = List.of(List.of("Alice", "CS101"),
            List.of("Alice", "MATH201"), List.of("Cara", "CS101"));
        for (String mode : List.of("auto", "hash", "grace")) {
            engine.setJoin(mode, 8, 4);
            assertEquals(expected, values(run(sql)));
            String expectedMode = "hash";
            if (mode.equals("grace")) {
                expectedMode = "grace";
            }
            assertEquals(expectedMode, engine.getLastJoin());
        }
    }

    @Test
    void usesTableNames() {
        assertEquals(3, run("SELECT students.name, enrollments.course FROM students "
            + "INNER JOIN enrollments ON students.id = enrollments.student_id").getRowCount());
    }

    @Test
    void filtersSortsLimits() {
        engine.setJoin("grace", 8, 4);
        QueryResult result = run("SELECT s.name AS person, e.course" + FROM
            + " WHERE s.id = 1 AND e.course IN ('CS101', 'MATH201')"
            + " ORDER BY person, e.course DESC LIMIT 1");
        assertEquals(List.of(List.of("Alice", "MATH201")), values(result));
        assertEquals("person", result.getSchema().getColumn(0).getName());
    }

    @Test
    void groupsJoin() {
        engine.setJoin("grace", 8, 4);
        QueryResult result = run("SELECT e.course, COUNT(*) AS total" + FROM
            + " GROUP BY e.course HAVING total > 1 ORDER BY total DESC");
        assertEquals(List.of(List.of("CS101", 2)), values(result));
        assertEquals(List.of(List.of(5)), values(run("SELECT SUM(s.id) AS total" + FROM)));
    }

    @Test
    void selectsDistinct() {
        engine.setJoin("grace", 8, 4);
        assertEquals(List.of(List.of("CS101"), List.of("MATH201")),
            values(run("SELECT DISTINCT e.course" + FROM + " ORDER BY e.course")));
    }

    @Test
    void joinsSelf() {
        engine.setJoin("grace", 8, 4);
        QueryResult result = run("SELECT a.name, b.name FROM students a "
            + "JOIN students b ON b.id = a.id ORDER BY a.name");
        assertEquals(List.of(List.of("Alice", "Alice"), List.of("Bob", "Bob"),
            List.of("Cara", "Cara")), values(result));
    }

    @Test
    void selectsStar() {
        QueryResult result = run("SELECT *" + FROM + " ORDER BY  s.id, e.course");
        assertEquals(4, result.getSchema().size());
        assertEquals(List.of("s.id", "s.name", "e.student_id", "e.course"),
            result.getSchema().getColumns().stream().map(Column::getName).toList());
        assertEquals(3, result.getRowCount());
    }

    @Test
    void joinsEmpty() {
        database.addTable(new Table("empty", students().getSchema()));
        engine.setJoin("grace", 8, 1);
        QueryResult result = run("SELECT s.name, e.course FROM empty s "
            + "JOIN enrollments e ON s.id = e.student_id");
        assertEquals(0, result.getRowCount());
        assertEquals(2, result.getSchema().size());
    }

    @Test
    void selectsGraceAutomatically() {
        engine.setJoin("auto", 16, 2);
        assertEquals(3, run("SELECT *" + FROM).getRowCount());
        assertEquals("grace", engine.getLastJoin());
    }

    @Test
    void rejectsSkew() {
        Table repeated = new Table("repeated", students().getSchema(),
            List.of(row(1, "One"), row(1, "Two"), row(1, "Three")));
        database.addTable(repeated);
        engine.setJoin("grace", 4, 1);
        String sql = "SELECT * FROM repeated s JOIN enrollments e ON s.id = e.student_id";
        assertThrows(IllegalStateException.class, () -> run(sql));
        engine.setJoin("hash", 4, 1);
        assertEquals(6, run(sql).getRowCount());
    }

    @Test
    void rejectsJoinForms() {
        List<String> queries = List.of(
            "SELECT * FROM students s LEFT JOIN enrollments e ON s.id=e.student_id",
            "SELECT * FROM students s RIGHT JOIN enrollments e ON s.id=e.student_id",
            "SELECT * FROM students s FULL JOIN enrollments e ON s.id=e.student_id",
            "SELECT * FROM students CROSS JOIN enrollments",
            "SELECT * FROM students NATURAL JOIN enrollments",
            "SELECT * FROM students, enrollments",
            "SELECT * FROM students s JOIN enrollments e USING(id)",
            "SELECT * FROM students s JOIN enrollments e ON s.id>e.student_id",
            "SELECT * FROM students s JOIN enrollments e ON s.id=e.student_id AND s.id>0",
            "SELECT *" + FROM + " JOIN students t ON t.id=e.student_id"
        );
        for (String sql : queries) {
            assertThrows(UnsupportedOperationException.class, () -> parser.parse(sql), sql);
        }
    }

    @Test
    void rejectsBadReferences() {
        for (String sql : List.of(
                "SELECT name" + FROM,
                "SELECT * FROM students s JOIN enrollments e ON id=e.student_id",
                "SELECT * FROM students s JOIN enrollments e ON s.id=s.id",
                "SELECT * FROM students s JOIN enrollments s ON s.id=s.student_id")) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(sql), sql);
        }
        assertThrows(UnsupportedOperationException.class, () -> parser.parse("SELECT s.*" + FROM));
        assertThrows(IllegalArgumentException.class, () -> run("SELECT x.name" + FROM));
        assertThrows(IllegalArgumentException.class, () -> run("SELECT * FROM students s "
            + "JOIN enrollments e ON s.missing=e.student_id"));
        assertThrows(IllegalArgumentException.class, () -> run("SELECT * FROM students s "
            + "JOIN enrollments e ON s.name=e.student_id"));
    }

    @Test
    void preservesSingleTable() {
        engine.setJoin("grace", 2, 1);
        assertEquals(List.of(List.of("Cara")), values(run(
            "SELECT name FROM students WHERE id = 3 ORDER BY name")));
        assertNull(engine.getLastJoin());
    }

    @Test
    void configuresCli() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Cli cli = new Cli(new PrintStream(output));
        cli.getDatabase().addTable(students());
        cli.getDatabase().addTable(enrollments());
        cli.execute(".join grace 8 4");
        cli.execute("SELECT s.name, e.course" + FROM + " ORDER BY s.name, e.course;");
        assertTrue(output.toString().contains("Join: grace"));
        assertTrue(output.toString().contains("(3 rows)"));
        cli.execute(".join hash");
        cli.execute("SELECT *" + FROM + ";");
        assertTrue(output.toString().contains("Join: hash"));
    }

    @Test
    void keepsValidConfig() {
        engine.setJoin("hash", 4, 1);
        assertThrows(IllegalArgumentException.class, () -> engine.setJoin("unknown", 4, 1));
        assertThrows(IllegalArgumentException.class, () -> engine.setJoin("grace", 1, 1));
        assertEquals(3, run("SELECT *" + FROM).getRowCount());
        assertEquals("hash", engine.getLastJoin());
    }

    @Test
    void joinsMixedNumbers() {
        Schema schema = new Schema(List.of(new Column("id", DataType.FLOAT)));
        database.addTable(new Table("floats", schema,
            List.of(new Row(List.of(new Value(DataType.FLOAT, 1.0))))));
        engine.setJoin("grace", 4, 1);
        assertEquals(List.of(List.of("Alice")), values(run(
            "SELECT s.name FROM students s JOIN floats f ON s.id=f.id")));
    }

    @Test
    void quotesAliases() {
        engine.setJoin("grace", 8, 4);
        assertEquals(3, run("SELECT \"s\".name FROM students \"s\" JOIN enrollments \"e\" "
            + "ON \"s\".id=\"e\".student_id").getRowCount());
    }

}
