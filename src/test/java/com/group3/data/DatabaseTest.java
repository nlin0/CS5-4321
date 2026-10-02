package com.group3.data;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class DatabaseTest {

    // ---------------------------------------------------
    // BASIC TABLE STORAGE
    // ---------------------------------------------------

    @Test
    void newDatabaseContainsNoTables() {
        Database database = new Database();

        assertTrue(database.getTables().isEmpty());
    }

    @Test
    void addsTable() {
        Database database = new Database();

        Table students = createStudentTable();

        database.addTable(students);

        assertTrue(database.containsTable("students"));
    }

    @Test
    void retrievesAddedTable() {
        Database database = new Database();

        Table students = createStudentTable();

        database.addTable(students);

        Table result = database.getTable("students");

        assertSame(students, result);
    }

    @Test
    void supportsMultipleTables() {
        Database database = new Database();

        Table students = createStudentTable();
        Table courses = createCourseTable();

        database.addTable(students);
        database.addTable(courses);

        assertEquals(2, database.getTables().size());

        assertTrue(database.containsTable("students"));
        assertTrue(database.containsTable("courses"));
    }


    // ---------------------------------------------------
    // CASE INSENSITIVITY
    // ---------------------------------------------------

    @Test
    void getTableIsCaseInsensitive() {
        Database database = new Database();

        Table students = createStudentTable();

        database.addTable(students);

        assertSame(
                students,
                database.getTable("STUDENTS")
        );
    }

    @Test
    void getTableSupportsMixedCase() {
        Database database = new Database();

        Table students = createStudentTable();

        database.addTable(students);

        assertSame(
                students,
                database.getTable("StUdEnTs")
        );
    }

    @Test
    void containsTableIsCaseInsensitive() {
        Database database = new Database();

        database.addTable(createStudentTable());

        assertTrue(database.containsTable("STUDENTS"));
        assertTrue(database.containsTable("Students"));
        assertTrue(database.containsTable("students"));
    }


    // ---------------------------------------------------
    // DUPLICATES
    // ---------------------------------------------------

    @Test
    void rejectsDuplicateTableName() {
        Database database = new Database();

        database.addTable(createStudentTable());

        Table duplicate = createStudentTable();

        assertThrows(
                IllegalArgumentException.class,
                () -> database.addTable(duplicate)
        );
    }

    @Test
    void rejectsDuplicateTableNameIgnoringCase() {
        Database database = new Database();

        database.addTable(createStudentTable());

        Table duplicate = new Table(
                "STUDENTS",
                createStudentSchema()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> database.addTable(duplicate)
        );
    }


    // ---------------------------------------------------
    // MISSING TABLES
    // ---------------------------------------------------

    @Test
    void containsTableReturnsFalseForMissingTable() {
        Database database = new Database();

        assertFalse(database.containsTable("students"));
    }

    @Test
    void getMissingTableThrowsException() {
        Database database = new Database();

        assertThrows(
                IllegalArgumentException.class,
                () -> database.getTable("students")
        );
    }

    @Test
    void missingTableDoesNotAffectExistingTables() {
        Database database = new Database();

        Table students = createStudentTable();

        database.addTable(students);

        assertThrows(
                IllegalArgumentException.class,
                () -> database.getTable("courses")
        );

        assertSame(
                students,
                database.getTable("students")
        );
    }


    // ---------------------------------------------------
    // TABLE CONTENT
    // ---------------------------------------------------

    @Test
    void retrievedTablePreservesSchema() {
        Database database = new Database();

        Table students = createStudentTable();

        database.addTable(students);

        Table result = database.getTable("students");

        assertEquals(4, result.getSchema().size());
        assertTrue(result.getSchema().hasColumn("gpa"));
    }

    @Test
    void retrievedTablePreservesRows() {
        Database database = new Database();

        Table students = createStudentTable();

        students.addRow(new Row(List.of(
                new Value(DataType.INTEGER, 1),
                new Value(DataType.STRING, "Billy"),
                new Value(DataType.FLOAT, 3.65),
                new Value(DataType.BOOLEAN, true)
        )));

        database.addTable(students);

        Table result = database.getTable("students");

        assertEquals(1, result.size());
        assertEquals(
                "Billy",
                result.getRows().get(0).get(1).getValue()
        );
    }


    // ---------------------------------------------------
    // HELPERS
    // ---------------------------------------------------

    private Table createStudentTable() {
        return new Table(
                "students",
                createStudentSchema()
        );
    }

    private Schema createStudentSchema() {
        return new Schema(List.of(
                new Column("id", DataType.INTEGER, false),
                new Column("name", DataType.STRING),
                new Column("gpa", DataType.FLOAT),
                new Column("active", DataType.BOOLEAN)
        ));
    }

    private Table createCourseTable() {
        Schema schema = new Schema(List.of(
                new Column("course_id", DataType.INTEGER, false),
                new Column("name", DataType.STRING)
        ));

        return new Table(
                "courses",
                schema
        );
    }

    @Test
    void returnedTableCollectionCannotBeModified() {
        Database database = new Database();

        database.addTable(createStudentTable());

        assertThrows(
                UnsupportedOperationException.class,
                () -> database.getTables().clear()
        );

        assertTrue(database.containsTable("students"));
    }
}