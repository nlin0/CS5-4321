package com.group3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.expression.ComparisonExpression;
import com.group3.operator.FilterOperator;
import com.group3.operator.JoinOperator;

class JoinTest {

    private JoinOperator join;
    private Table students;
    private Table enrollments;

    @BeforeEach
    void setUp() {
        join = new JoinOperator();

        students = table("students",
            new Schema(List.of(
                new Column("id", DataType.INTEGER),
                new Column("name", DataType.STRING)
            )),
            student(1, "Billy"),
            student(2, "Alice"),
            student(3, "Bob"),
            student(4, "Cara")
        );

        enrollments = table("enrollments",
            new Schema(List.of(
                new Column("student_id", DataType.INTEGER),
                new Column("course", DataType.STRING)
            )),
            enrollment(1, "CS101"),
            enrollment(1, "MATH201"),
            enrollment(2, "CS101"),
            enrollment(5, "ART100")
        );
    }

    @Test
    void matchesRowsOnKey() {
        Table result = studentsJoinEnrollments();

        assertEquals(
            List.of(
                List.of(1, "Billy", 1, "CS101"),
                List.of(1, "Billy", 1, "MATH201"),
                List.of(2, "Alice", 2, "CS101")
            ),
            payloads(result)
        );
        assertEquals(3, result.size());
    }

    @Test
    void outputColumnsAreQualifiedWithAliases() {
        Table result = studentsJoinEnrollments();

        assertEquals(
            List.of("students.id", "students.name", "enrollments.student_id", "enrollments.course"),
            result.getSchema().getColumns().stream().map(Column::getName).toList()
        );
        assertEquals(
            List.of(DataType.INTEGER, DataType.STRING, DataType.INTEGER, DataType.STRING),
            result.getSchema().getColumns().stream().map(Column::getType).toList()
        );
    }

    @Test
    void outputKeepsColumnNullability() {
        Schema strict = new Schema(List.of(
            new Column("k", DataType.INTEGER, false),
            new Column("tag", DataType.STRING)
        ));
        Table left = table("a", strict, pair(1, "x"));
        Table right = table("b", strict, pair(1, "y"));

        Table result = join.innerJoin(left, right, "k", "k");

        assertEquals(
            List.of(false, true, false, true),
            result.getSchema().getColumns().stream().map(Column::isNullable).toList()
        );
    }

    @Test
    void rowsWithoutAPartnerAreDropped() {
        Table result = studentsJoinEnrollments();

        List<Object> names = column(result, "students.name");
        assertTrue(!names.contains("Bob"), "Bob has no enrollments");
        assertTrue(!names.contains("Cara"), "Cara has no enrollments");
        assertTrue(!column(result, "enrollments.course").contains("ART100"),
            "ART100 has no matching student");
    }

    @Test
    void oneToManyKeepsLeftOrderThenRightOrder() {
        Table result = studentsJoinEnrollments();

        assertEquals(List.of("Billy", "Billy", "Alice"), column(result, "students.name"));
        assertEquals(List.of("CS101", "MATH201", "CS101"), column(result, "enrollments.course"));
    }

    @Test
    void duplicateKeysOnBothSidesProduceEveryCombination() {
        Schema schema = pairSchema();
        Table left = table("a", schema, pair(1, "a1"), pair(1, "a2"), pair(2, "a3"));
        Table right = table("b", schema, pair(1, "b1"), pair(1, "b2"));

        Table result = join.innerJoin(left, right, "k", "k");

        assertEquals(
            List.of(
                List.of(1, "a1", 1, "b1"),
                List.of(1, "a1", 1, "b2"),
                List.of(1, "a2", 1, "b1"),
                List.of(1, "a2", 1, "b2")
            ),
            payloads(result)
        );
    }

    @Test
    void nullKeysNeverMatch() {
        Schema schema = pairSchema();
        Table left = table("a", schema, pair(null, "left-null"), pair(1, "left-one"));
        Table right = table("b", schema, pair(null, "right-null"), pair(1, "right-one"));

        Table result = join.innerJoin(left, right, "k", "k");

        // null = null must not match; only the 1 = 1 pair survives
        assertEquals(List.of(List.of(1, "left-one", 1, "right-one")), payloads(result));
    }

    @Test
    void integerKeyMatchesEqualFloatKey() {
        Table ints = table("i",
            new Schema(List.of(new Column("n", DataType.INTEGER))),
            new Row(List.of(new Value(DataType.INTEGER, 3))),
            new Row(List.of(new Value(DataType.INTEGER, 4)))
        );
        Table floats = table("f",
            new Schema(List.of(new Column("x", DataType.FLOAT))),
            new Row(List.of(new Value(DataType.FLOAT, 3.0))),
            new Row(List.of(new Value(DataType.FLOAT, 3.5)))
        );

        Table result = join.innerJoin(ints, floats, "n", "x");

        assertEquals(List.of(List.of(3, 3.0)), payloads(result));
    }

    @Test
    void stringKeysJoinCaseSensitively() {
        Table people = table("people",
            new Schema(List.of(
                new Column("name", DataType.STRING),
                new Column("age", DataType.INTEGER)
            )),
            new Row(List.of(new Value(DataType.STRING, "Alice"), new Value(DataType.INTEGER, 30))),
            new Row(List.of(new Value(DataType.STRING, "Bob"), new Value(DataType.INTEGER, 25)))
        );
        Table pets = table("pets",
            new Schema(List.of(
                new Column("owner", DataType.STRING),
                new Column("pet", DataType.STRING)
            )),
            new Row(List.of(new Value(DataType.STRING, "Alice"), new Value(DataType.STRING, "Rex"))),
            new Row(List.of(new Value(DataType.STRING, "alice"), new Value(DataType.STRING, "Tom")))
        );

        Table result = join.innerJoin(people, pets, "name", "owner");

        assertEquals(List.of(List.of("Alice", 30, "Alice", "Rex")), payloads(result));
    }

    @Test
    void emptySideGivesEmptyResultWithFullSchema() {
        Table noEnrollments = new Table("enrollments", enrollments.getSchema());
        Table noStudents = new Table("students", students.getSchema());

        Table result = join.innerJoin(students, noEnrollments, "id", "student_id");
        assertEquals(0, result.size());
        assertEquals(4, result.getSchema().size());

        assertEquals(0, join.innerJoin(noStudents, enrollments, "id", "student_id").size());
    }

    @Test
    void tableCanBeJoinedToItselfUsingAliases() {
        Table employees = table("employees",
            new Schema(List.of(
                new Column("id", DataType.INTEGER),
                new Column("name", DataType.STRING),
                new Column("manager_id", DataType.INTEGER)
            )),
            employee(1, "Dana", null),
            employee(2, "Eli", 1),
            employee(3, "Fay", 1),
            employee(4, "Gus", 2)
        );

        Table result = join.innerJoin(
            employees, "emp",
            employees, "mgr",
            "manager_id", "id"
        );

        assertEquals(List.of("Eli", "Fay", "Gus"), column(result, "emp.name"));
        assertEquals(List.of("Dana", "Dana", "Eli"), column(result, "mgr.name"));
    }

    @Test
    void tableNamesAreUsedWhenNoAliasesAreGiven() {
        Table result = join.innerJoin(students, enrollments, "id", "student_id");

        assertTrue(result.getSchema().hasColumn("students.id"));
        assertTrue(result.getSchema().hasColumn("enrollments.course"));
    }

    @Test
    void aliasesOverrideTableNames() {
        Table result = join.innerJoin(
            students, "s",
            enrollments, "e",
            "id", "student_id"
        );

        assertTrue(result.getSchema().hasColumn("s.id"));
        assertTrue(result.getSchema().hasColumn("e.course"));
        assertTrue(!result.getSchema().hasColumn("students.id"));
    }

    @Test
    void joinColumnLookupIgnoresCase() {
        Table result = join.innerJoin(students, enrollments, "ID", "Student_ID");

        assertEquals(3, result.size());
    }

    @Test
    void joinedTableCanBeFilteredWithQualifiedColumnNames() {
        Table result = studentsJoinEnrollments();

        List<Row> inCs101 = new FilterOperator().apply(
            result.getSchema(),
            result.getRows(),
            ComparisonExpression.of("enrollments.course", "=", new Value(DataType.STRING, "CS101"))
        );

        assertEquals(2, inCs101.size());
    }

    @Test
    void inputsAreNotModified() {
        List<Row> studentsBefore = new ArrayList<>(students.getRows());
        List<Row> enrollmentsBefore = new ArrayList<>(enrollments.getRows());

        studentsJoinEnrollments();

        assertEquals(studentsBefore, students.getRows());
        assertEquals(enrollmentsBefore, enrollments.getRows());
    }

    @Test
    void unknownJoinColumnFails() {
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(students, enrollments, "missing", "student_id"));
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(students, enrollments, "id", "missing"));
    }

    @Test
    void incompatibleKeyTypesFail() {
        // INTEGER id vs STRING course
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(students, enrollments, "id", "course"));
    }

    @Test
    void sameNameOnBothSidesFails() {
        // no aliases: both sides would be called "students"
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(students, students, "id", "id"));
        // aliases that differ only by case still collide
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(students, "S", students, "s", "id", "id"));
    }

    @Test
    void missingInputsFail() {
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(null, enrollments, "id", "student_id"));
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(students, null, "id", "student_id"));
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(students, enrollments, null, "student_id"));
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(students, " ", enrollments, "e", "id", "student_id"));
    }

    private Table studentsJoinEnrollments() {
        return join.innerJoin(
            students, "students",
            enrollments, "enrollments",
            "id", "student_id"
        );
    }

    private static Table table(String name, Schema schema, Row... rows) {
        return new Table(name, schema, List.of(rows));
    }

    private static Schema pairSchema() {
        return new Schema(List.of(
            new Column("k", DataType.INTEGER),
            new Column("tag", DataType.STRING)
        ));
    }

    private static Row student(int id, String name) {
        return new Row(List.of(
            new Value(DataType.INTEGER, id),
            new Value(DataType.STRING, name)
        ));
    }

    private static Row enrollment(Integer studentId, String course) {
        return new Row(List.of(
            new Value(DataType.INTEGER, studentId),
            new Value(DataType.STRING, course)
        ));
    }

    private static Row pair(Integer key, String tag) {
        return new Row(List.of(
            new Value(DataType.INTEGER, key),
            new Value(DataType.STRING, tag)
        ));
    }

    private static Row employee(int id, String name, Integer managerId) {
        return new Row(List.of(
            new Value(DataType.INTEGER, id),
            new Value(DataType.STRING, name),
            new Value(DataType.INTEGER, managerId)
        ));
    }

    // Every row as a list of raw payloads, in column order.
    private static List<List<Object>> payloads(Table table) {
        List<List<Object>> out = new ArrayList<>();
        for (Row row : table.getRows()) {
            List<Object> values = new ArrayList<>();
            for (Value value : row.getValues()) {
                values.add(value.getValue());
            }
            out.add(values);
        }
        return out;
    }

    // One column's payloads, looked up by its (qualified) name.
    private static List<Object> column(Table table, String name) {
        int index = table.getSchema().indexOf(name);
        assertTrue(index >= 0, "Column not found in result: " + name);
        List<Object> out = new ArrayList<>();
        for (Row row : table.getRows()) {
            out.add(row.get(index).getValue());
        }
        return out;
    }
}