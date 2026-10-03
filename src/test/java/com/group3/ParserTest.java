package com.group3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.group3.parser.Query;
import com.group3.parser.SQLParser;

class ParserTest {
    SQLParser parser = new SQLParser();
    
    @Test
    void testValidParse() {
        String[] validQueries = {
            "SELECT * FROM my_table WHERE id = 42",
            "SELECT id FROM my_table WHERE id = 42",
            "SELECT *, id FROM my_table"
        };
        for (String validQuery: validQueries) {
            Query query = parser.parse(validQuery);
            assertNotNull(query);
        }  
    }

    @Test
    void testInvalidParse() {
        String[] invalidQueries = {
            "SELECT FROM my_table",
        };
        for (String invalidQuery: invalidQueries) {
            assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(invalidQuery)
            );
        }    
    }

    @Test
    void extractsTableAndColumns() {
        Query query = parser.parse("SELECT name, age FROM students");

        assertEquals("students", query.getTableName());
        assertEquals(List.of("name", "age"), query.getColumns());
    }

    @Test
    void starIsKeptAsStar() {
        assertEquals(List.of("*"), parser.parse("SELECT * FROM t").getColumns());
    }

    @Test
    void qualifiedColumnKeepsOnlyColumnName() {
        assertEquals(List.of("id"), parser.parse("SELECT t.id FROM t").getColumns());
    }

    @Test
    void malformedSqlThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("SELEC * FROM t"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("SELECT * FROM"));
    }

    @Test
    void nonSelectStatementsAreRejected() {
        assertThrows(UnsupportedOperationException.class,
            () -> parser.parse("DELETE FROM t"));
        assertThrows(UnsupportedOperationException.class,
            () -> parser.parse("INSERT INTO t VALUES (1)"));
    }

    @Test
    void subqueryInFromIsRejected() {
        assertThrows(UnsupportedOperationException.class,
            () -> parser.parse("SELECT * FROM (SELECT * FROM t) x"));
    }

    @Test
    void unsupportedExpressionsInSelectListAreRejected() {
        assertThrows(UnsupportedOperationException.class,
            () -> parser.parse("SELECT age + 1 FROM t"));
    }

    @Test
    void parsesAggregatesAndGroupBy() {
        Query query = parser.parse("SELECT department, COUNT(*) AS people, AVG(salary) FROM staff GROUP BY department");

        assertEquals(List.of("department"), query.getColumns());
        assertEquals(List.of("department"), query.getGroupByColumns());
        assertEquals(2, query.getAggregates().size());
        assertEquals("people", query.getAggregates().get(0).outputName());
        assertEquals("salary", query.getAggregates().get(1).column());
    }

    @Test
    void parsesHavingWithAnAggregateAlias() {
        Query query = parser.parse("SELECT department, COUNT(*) AS people FROM staff "
            + "GROUP BY department HAVING people >= 2");

        assertNotNull(query.getHaving());
    }

    @Test
    void parsesCompoundHavingConditions() {
        Query query = parser.parse("SELECT department, COUNT(*) AS people, AVG(salary) AS average_salary "
            + "FROM staff GROUP BY department "
            + "HAVING people >= 2 AND (average_salary > 50000 OR department = 'Engineering')");

        assertNotNull(query.getHaving());
    }

    @Test
    void queryColumnsAreImmutable() {
        Query query = parser.parse("SELECT id FROM t");

        assertThrows(UnsupportedOperationException.class, () -> query.getColumns().add("x"));
    }
}
