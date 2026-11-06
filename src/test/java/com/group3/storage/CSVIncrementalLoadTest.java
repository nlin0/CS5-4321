package com.group3.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.group3.data.DataType;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Table;
import com.group3.data.Value;

/**
 * Tests for the two-pass CSV load: types are inferred from the whole file
 * before any row is built, and repeated values share one Value object.
 */
class CSVIncrementalLoadTest {

    @TempDir Path directory;
    private final CSVReader reader = new CSVReader();

    private Table read(String name, String contents) throws IOException {
        Path file = directory.resolve(name + ".csv");
        Files.writeString(file, contents);
        return reader.read(file.toString(), name);
    }

    private Table read(String contents) throws IOException {
        return read("input", contents);
    }

    // ---------- pass 1: type inference over the whole file ----------

    @Test
    void floatOnTheLastRowMakesTheWholeColumnFloat() throws IOException {
        StringBuilder csv = new StringBuilder("x\n");
        for (int i = 0; i < 1000; i++) {
            csv.append(i).append('\n');
        }
        csv.append("2.5\n");

        Table table = read(csv.toString());

        assertEquals(DataType.FLOAT, table.getSchema().getColumn("x").getType());
        assertEquals(1001, table.size());
        assertEquals(0.0f, table.getRows().get(0).get(0).getValue());
        assertEquals(2.5f, table.getRows().get(1000).get(0).getValue());
    }

    @Test
    void textOnTheLastRowMakesTheWholeColumnString() throws IOException {
        Table table = read("x\n1\ntrue\n3\nhello\n");

        assertEquals(DataType.STRING, table.getSchema().getColumn("x").getType());
        assertEquals("1", table.getRows().get(0).get(0).getValue());
        assertEquals("true", table.getRows().get(1).get(0).getValue());
    }

    @Test
    void headerOnlyFileGivesEmptyTableWithStringColumns() throws IOException {
        Table table = read("id,name\n");

        assertEquals(0, table.size());
        assertEquals(2, table.getSchema().size());
        assertEquals(DataType.STRING, table.getSchema().getColumn("id").getType());
    }

    @Test
    void allBlankColumnIsStringWithNulls() throws IOException {
        Table table = read("id,note\n1,\n2,  \n");

        assertEquals(DataType.STRING, table.getSchema().getColumn("note").getType());
        assertTrue(table.getRows().get(0).get(1).isNull());
        assertTrue(table.getRows().get(1).get(1).isNull());
    }

    @Test
    void wrongWidthIsReportedWithItsRowNumberBeforeAnyRowIsBuilt() throws IOException {
        StringBuilder csv = new StringBuilder("a,b,c\n");
        for (int i = 0; i < 500; i++) {
            csv.append("1,2,3\n");
        }
        csv.append("1,2\n");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> read(csv.toString()));

        assertTrue(e.getMessage().contains("CSV row 501"), e.getMessage());
        assertTrue(e.getMessage().contains("has 2 values but expected 3"), e.getMessage());
    }

    @Test
    void emptyFileStillNeedsAHeader() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(""));

        assertTrue(e.getMessage().contains("must contain a header"), e.getMessage());
    }

    // ---------- pass 2: shared values ----------

    @Test
    void repeatedValuesInAColumnShareOneObject() throws IOException {
        Table table = read("dept,year,active\neng,2024,true\nmath,2025,false\neng,2024,true\n");
        Row first = table.getRows().get(0);
        Row third = table.getRows().get(2);

        assertSame(first.get(0), third.get(0));
        assertSame(first.get(1), third.get(1));
        assertSame(first.get(2), third.get(2));
        assertNotSame(first.get(0), table.getRows().get(1).get(0));
    }

    @Test
    void nullsOfTheSameTypeShareOneObject() throws IOException {
        Table table = read("a,b\n1,\n2,\n3,x\n");

        assertSame(table.getRows().get(0).get(1), table.getRows().get(1).get(1));
        assertTrue(table.getRows().get(0).get(1).isNull());
        assertEquals(DataType.STRING, table.getRows().get(0).get(1).getType());
    }

    @Test
    void sharedValuesStillEqualFreshValues() throws IOException {
        Table table = read("dept,gpa\neng,3.5\neng,3.5\n");
        Value dept = table.getRows().get(1).get(0);
        Value gpa = table.getRows().get(1).get(1);

        assertEquals(new Value(DataType.STRING, "eng"), dept);
        assertEquals(new Value(DataType.STRING, "eng").hashCode(), dept.hashCode());
        assertEquals(new Value(DataType.FLOAT, 3.5f), gpa);
        assertEquals(table.getRows().get(0), table.getRows().get(1));
    }

    @Test
    void columnsWithMoreDistinctValuesThanTheLimitAreStillCorrect() throws IOException {
        int rows = CSVReader.MAX_SHARED_VALUES_PER_COLUMN + 100;
        StringBuilder csv = new StringBuilder("id,name\n");
        for (int i = 0; i < rows; i++) {
            csv.append(i).append(",student_").append(i).append('\n');
        }
        // A repeat of an early value is shared; one past the limit is not
        // remembered, but it is still parsed correctly.
        csv.append("0,student_0\n");

        Table table = read(csv.toString());

        assertEquals(rows + 1, table.size());
        for (int i = 0; i < rows; i++) {
            assertEquals(i, table.getRows().get(i).get(0).getValue());
            assertEquals("student_" + i, table.getRows().get(i).get(1).getValue());
        }
        assertSame(table.getRows().get(0).get(1), table.getRows().get(rows).get(1));
    }

    @Test
    void sharingIsPerLoadAndDoesNotLeakBetweenTables() throws IOException {
        Value a = read("first", "dept\neng\n").getRows().get(0).get(0);
        Value b = read("second", "dept\neng\n").getRows().get(0).get(0);

        assertEquals(a, b);
        assertNotSame(a, b);
    }

    // ---------- keys still work on a loaded table ----------

    @Test
    void primaryAndForeignKeysWorkOnLoadedTables() throws IOException {
        Database database = new Database();
        database.addTable(read("students", "id,name\n1,Ada\n2,Bob\n"));
        database.addTable(read("enrollments", "student_id,course\n1,cs4321\n2,cs4321\n,cs4320\n"));

        database.getTable("students").setPrimaryKey("id");
        database.addForeignKey("enrollments", "student_id", "students", "id");

        Table students = database.getTable("students");
        Table enrollments = database.getTable("enrollments");
        // The shared "cs4321" Value must not confuse key checks.
        assertSame(enrollments.getRows().get(0).get(1), enrollments.getRows().get(1).get(1));

        assertThrows(IllegalArgumentException.class, () -> students.addRow(new Row(List.of(
            new Value(DataType.INTEGER, 1), new Value(DataType.STRING, "Dup")))));
        assertThrows(IllegalArgumentException.class, () -> enrollments.addRow(new Row(List.of(
            new Value(DataType.INTEGER, 99), new Value(DataType.STRING, "cs1110")))));
    }

    @Test
    void duplicateKeyInTheCsvIsCaughtWhenThePrimaryKeyIsDeclared() throws IOException {
        Table table = read("id,name\n1,Ada\n1,Bob\n");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> table.setPrimaryKey("id"));
        assertTrue(e.getMessage().contains("Duplicate primary key"), e.getMessage());
    }

    @Test
    void nullKeyInTheCsvIsCaughtWhenThePrimaryKeyIsDeclared() throws IOException {
        Table table = read("id,name\n1,Ada\n,Bob\n");

        assertThrows(IllegalArgumentException.class, () -> table.setPrimaryKey("id"));
        assertNull(table.getRows().get(1).get(0).getValue());
    }
}
