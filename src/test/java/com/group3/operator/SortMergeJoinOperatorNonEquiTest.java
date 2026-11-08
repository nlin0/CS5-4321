package com.group3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
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
import com.group3.expression.ComparisonOperator;
import com.group3.expression.Operand;
import com.group3.operator.FilterOperator;
import com.group3.operator.SortMergeJoinOperator;

/**
 * Broader sort-merge join tests: extra equi-join behavior, plus every non-equi operator
 * (!=, <, <=, >, >=).
 */
class SortMergeJoinNonEquiTest {

    private SortMergeJoinOperator join;
    private Table students;
    private Table enrollments;

    @BeforeEach
    void setUp() {
        join = new SortMergeJoinOperator();

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

    // ================= equi-joins (=) =================

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
    void outputColumnTypesMatchTheInputColumns() {
        Table result = studentsJoinEnrollments();

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

    // ============ non-equi joins (!=, <, <=, >, >=) ============

    @Test
    void everyOperatorMatchesABruteForceNestedLoop() {
        // duplicates on both sides, a null on both sides, unsorted input
        Table left = table("l", pairSchema(),
            pair(1, "l0"), pair(2, "l1"), pair(2, "l2"), pair(3, "l3"), pair(null, "l4"), pair(5, "l5"));
        Table right = table("r", pairSchema(),
            pair(2, "r0"), pair(2, "r1"), pair(3, "r2"), pair(4, "r3"), pair(null, "r4"), pair(0, "r5"));

        for (ComparisonOperator operator : ComparisonOperator.values()) {
            Table result = join.innerJoin(left, right, "k", operator, "k");

            assertEquals(expectedPairs(left, right, operator), tagPairs(result), "operator " + operator);
        }
    }

    @Test
    void lessThanPairsEachLeftRowWithLargerRightKeysInOrder() {
        // input deliberately unsorted; output is ordered by left key, then right key
        Table left = table("l", pairSchema(), pair(3, "a"), pair(1, "b"), pair(2, "c"));
        Table right = table("r", pairSchema(), pair(3, "d"), pair(2, "e"));

        Table result = join.innerJoin(left, right, "k", ComparisonOperator.LESS_THAN, "k");

        assertEquals(
            List.of(List.of(1, 2), List.of(1, 3), List.of(2, 3)),
            keyPairs(result)
        );
    }

    @Test
    void lessOrEqualIncludesEqualKeys() {
        Table left = table("l", pairSchema(), pair(1, "a"), pair(2, "b"));
        Table right = table("r", pairSchema(), pair(2, "c"), pair(3, "d"));

        Table result = join.innerJoin(left, right, "k", ComparisonOperator.LESS_OR_EQUAL, "k");

        assertEquals(
            List.of(List.of(1, 2), List.of(1, 3), List.of(2, 2), List.of(2, 3)),
            keyPairs(result)
        );
    }

    @Test
    void greaterThanAndGreaterOrEqual() {
        Table left = table("l", pairSchema(), pair(2, "a"), pair(3, "b"));
        Table right = table("r", pairSchema(), pair(1, "c"), pair(2, "d"), pair(3, "e"));

        assertEquals(
            List.of(List.of(2, 1), List.of(3, 1), List.of(3, 2)),
            keyPairs(join.innerJoin(left, right, "k", ComparisonOperator.GREATER_THAN, "k"))
        );
        assertEquals(
            List.of(List.of(2, 1), List.of(2, 2), List.of(3, 1), List.of(3, 2), List.of(3, 3)),
            keyPairs(join.innerJoin(left, right, "k", ComparisonOperator.GREATER_OR_EQUAL, "k"))
        );
    }

    @Test
    void notEqualSkipsOnlyTheEqualKeys() {
        Table left = table("l", pairSchema(), pair(1, "a"), pair(2, "b"));
        Table right = table("r", pairSchema(), pair(2, "c"), pair(3, "d"));

        Table result = join.innerJoin(left, right, "k", ComparisonOperator.NOT_EQUAL, "k");

        assertEquals(
            List.of(List.of(1, 2), List.of(1, 3), List.of(2, 3)),
            keyPairs(result)
        );
    }

    @Test
    void nullKeysNeverSatisfyAnyOperator() {
        Table left = table("l", pairSchema(), pair(null, "a"));
        Table right = table("r", pairSchema(), pair(1, "b"), pair(null, "c"));

        for (ComparisonOperator operator : ComparisonOperator.values()) {
            assertEquals(0, join.innerJoin(left, right, "k", operator, "k").size(),
                "operator " + operator);
        }
        // and the reverse direction: non-null left against null-only right
        Table nullOnlyRight = table("r", pairSchema(), pair(null, "c"));
        Table plainLeft = table("l", pairSchema(), pair(1, "a"));
        for (ComparisonOperator operator : ComparisonOperator.values()) {
            assertEquals(0, join.innerJoin(plainLeft, nullOnlyRight, "k", operator, "k").size(),
                "operator " + operator);
        }
    }

    @Test
    void integerAndFloatKeysCompareAsNumbers() {
        Table ints = table("i",
            new Schema(List.of(new Column("n", DataType.INTEGER))),
            new Row(List.of(new Value(DataType.INTEGER, 2))),
            new Row(List.of(new Value(DataType.INTEGER, 3)))
        );
        Table floats = table("f",
            new Schema(List.of(new Column("x", DataType.FLOAT))),
            new Row(List.of(new Value(DataType.FLOAT, 2.5))),
            new Row(List.of(new Value(DataType.FLOAT, 3.0)))
        );

        Table result = join.innerJoin(ints, floats, "n", ComparisonOperator.LESS_THAN, "x");

        assertEquals(
            List.of(List.of(2, 2.5), List.of(2, 3.0)),
            payloads(result)
        );
    }

    @Test
    void stringKeysSupportOrdering() {
        Schema schema = new Schema(List.of(new Column("s", DataType.STRING)));
        Table left = table("l", schema, strRow("b"), strRow("d"));
        Table right = table("r", schema, strRow("a"), strRow("c"), strRow("e"));

        Table result = join.innerJoin(left, right, "s", ComparisonOperator.GREATER_THAN, "s");

        assertEquals(
            List.of(List.of("b", "a"), List.of("d", "a"), List.of("d", "c")),
            payloads(result)
        );
    }

    @Test
    void selfJoinWithAliasesComparesRowsAgainstEachOther() {
        Table people = table("people",
            new Schema(List.of(
                new Column("name", DataType.STRING),
                new Column("salary", DataType.INTEGER)
            )),
            person("Ann", 30), person("Bo", 10), person("Cy", 20)
        );

        // every pair (a, b) where a earns less than b
        Table result = join.innerJoin(
            people, "a", people, "b", "salary", ComparisonOperator.LESS_THAN, "salary");

        assertEquals(
            List.of(List.of("Bo", "Cy"), List.of("Bo", "Ann"), List.of("Cy", "Ann")),
            namePairs(result)
        );
    }

    @Test
    void equalityOverloadsBehaveLikeTheEqualOperator() {
        Table left = table("l", pairSchema(), pair(1, "a"), pair(2, "b"), pair(2, "c"));
        Table right = table("r", pairSchema(), pair(2, "d"), pair(3, "e"));

        assertEquals(
            payloads(join.innerJoin(left, right, "k", ComparisonOperator.EQUAL, "k")),
            payloads(join.innerJoin(left, right, "k", "k"))
        );
        assertEquals(
            payloads(join.innerJoin(left, "x", right, "y", "k", ComparisonOperator.EQUAL, "k")),
            payloads(join.innerJoin(left, "x", right, "y", "k", "k"))
        );
        assertEquals(
            List.of(List.of(2, "b", 2, "d"), List.of(2, "c", 2, "d")),
            payloads(join.innerJoin(left, right, "k", "k"))
        );
    }

    @Test
    void compoundConditionIsJoinPlusFilter() {
        // ON l.k = r.k AND l.tag < r.tag  -> equi-join, then filter the joined rows
        Table left = table("l", pairSchema(), pair(1, "a"), pair(1, "z"));
        Table right = table("r", pairSchema(), pair(1, "m"));

        Table joined = join.innerJoin(left, right, "k", ComparisonOperator.EQUAL, "k");
        List<Row> kept = new FilterOperator().apply(
            joined.getSchema(),
            joined.getRows(),
            new ComparisonExpression(
                Operand.column("l.tag"), ComparisonOperator.LESS_THAN, Operand.column("r.tag"))
        );

        assertEquals(1, kept.size());
        assertEquals("a", kept.get(0).get(1).getValue());
    }

    @Test
    void outputSchemaIsQualifiedAndKeepsNullability() {
        Schema strict = new Schema(List.of(
            new Column("k", DataType.INTEGER, false),
            new Column("tag", DataType.STRING)
        ));
        Table left = table("l", strict, pair(1, "a"));
        Table right = table("r", strict, pair(2, "b"));

        Table result = join.innerJoin(left, right, "k", ComparisonOperator.LESS_THAN, "k");

        assertEquals(
            List.of("l.k", "l.tag", "r.k", "r.tag"),
            result.getSchema().getColumns().stream().map(Column::getName).toList()
        );
        assertEquals(
            List.of(false, true, false, true),
            result.getSchema().getColumns().stream().map(Column::isNullable).toList()
        );
    }

    @Test
    void emptySideGivesEmptyResult() {
        Table full = table("l", pairSchema(), pair(1, "a"), pair(2, "b"));
        Table empty = new Table("r", pairSchema());

        for (ComparisonOperator operator : ComparisonOperator.values()) {
            assertEquals(0, join.innerJoin(full, empty, "k", operator, "k").size());
            assertEquals(0, join.innerJoin(empty, full, "k", operator, "k").size());
        }
    }

    @Test
    void inputsAreNotModifiedByInequalityJoins() {
        Table left = table("l", pairSchema(), pair(3, "a"), pair(1, "b"));
        Table right = table("r", pairSchema(), pair(2, "c"), pair(0, "d"));
        List<Row> leftBefore = new ArrayList<>(left.getRows());
        List<Row> rightBefore = new ArrayList<>(right.getRows());

        join.innerJoin(left, right, "k", ComparisonOperator.GREATER_THAN, "k");

        assertEquals(leftBefore, left.getRows());
        assertEquals(rightBefore, right.getRows());
    }

    @Test
    void invalidJoinsFail() {
        Table left = table("l", pairSchema(), pair(1, "a"));
        Table right = table("r", pairSchema(), pair(2, "b"));

        // missing operator
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(left, right, "k", null, "k"));
        // unknown column
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(left, right, "nope", ComparisonOperator.LESS_THAN, "k"));
        // INTEGER vs STRING
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(left, right, "k", ComparisonOperator.LESS_THAN, "tag"));
        // same name on both sides
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(left, left, "k", ComparisonOperator.LESS_THAN, "k"));
        // missing table
        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(null, right, "k", ComparisonOperator.LESS_THAN, "k"));
    }

    @Test
    void booleanColumnsOnlySupportEqualityOperators() {
        Schema schema = new Schema(List.of(new Column("flag", DataType.BOOLEAN)));
        Table left = table("l", schema, new Row(List.of(new Value(DataType.BOOLEAN, true))));
        Table right = table("r", schema, new Row(List.of(new Value(DataType.BOOLEAN, false))));

        assertThrows(IllegalArgumentException.class,
            () -> join.innerJoin(left, right, "flag", ComparisonOperator.LESS_THAN, "flag"));
        assertEquals(1,
            join.innerJoin(left, right, "flag", ComparisonOperator.NOT_EQUAL, "flag").size());
    }

    // ---- helpers ----

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

    private static Row strRow(String text) {
        return new Row(List.of(new Value(DataType.STRING, text)));
    }

    private static Row person(String name, int salary) {
        return new Row(List.of(
            new Value(DataType.STRING, name),
            new Value(DataType.INTEGER, salary)
        ));
    }

    // Brute-force reference: test every pair directly, skipping nulls.
    private static List<String> expectedPairs(Table left, Table right, ComparisonOperator operator) {
        List<String> out = new ArrayList<>();
        for (Row l : left.getRows()) {
            for (Row r : right.getRows()) {
                if (l.get(0).isNull() || r.get(0).isNull()) {
                    continue;
                }
                int a = (Integer) l.get(0).getValue();
                int b = (Integer) r.get(0).getValue();
                boolean holds = switch (operator) {
                    case EQUAL -> a == b;
                    case NOT_EQUAL -> a != b;
                    case LESS_THAN -> a < b;
                    case LESS_OR_EQUAL -> a <= b;
                    case GREATER_THAN -> a > b;
                    case GREATER_OR_EQUAL -> a >= b;
                };
                if (holds) {
                    out.add(l.get(1).getValue() + "|" + r.get(1).getValue());
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    // "leftTag|rightTag" for every result row, sorted so order doesn't matter.
    private static List<String> tagPairs(Table result) {
        int leftTag = result.getSchema().indexOf("l.tag");
        int rightTag = result.getSchema().indexOf("r.tag");
        List<String> out = new ArrayList<>();
        for (Row row : result.getRows()) {
            out.add(row.get(leftTag).getValue() + "|" + row.get(rightTag).getValue());
        }
        Collections.sort(out);
        return out;
    }

    // [leftKey, rightKey] per row, in output order.
    private static List<List<Object>> keyPairs(Table result) {
        int leftKey = result.getSchema().indexOf("l.k");
        int rightKey = result.getSchema().indexOf("r.k");
        List<List<Object>> out = new ArrayList<>();
        for (Row row : result.getRows()) {
            out.add(List.of(row.get(leftKey).getValue(), row.get(rightKey).getValue()));
        }
        return out;
    }

    // [a.name, b.name] per row, in output order.
    private static List<List<Object>> namePairs(Table result) {
        int a = result.getSchema().indexOf("a.name");
        int b = result.getSchema().indexOf("b.name");
        List<List<Object>> out = new ArrayList<>();
        for (Row row : result.getRows()) {
            out.add(List.of(row.get(a).getValue(), row.get(b).getValue()));
        }
        return out;
    }
}