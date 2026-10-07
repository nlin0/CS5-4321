package com.group3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.group3.parser.OrderByItem;
import com.group3.parser.Query;
import com.group3.parser.SQLParser;

class OrderLimitDistinctParserTest {

    private final SQLParser parser = new SQLParser();

    private List<OrderByItem> orderBy(String sql) {
        return parser.parse(sql).getOrderBy();
    }

    // ---------- ORDER BY ----------

    @Test
    void noOrderByGivesEmptyList() {
        assertTrue(orderBy("SELECT * FROM t").isEmpty());
    }

    @Test
    void parsesMultipleKeysWithDirections() {
        assertEquals(List.of(new OrderByItem("age", false), new OrderByItem("name", true)),
                orderBy("SELECT name, age FROM t ORDER BY age DESC, name"));
    }

    @Test
    void parsesExplicitNullOrdering() {
        List<OrderByItem> items = orderBy("SELECT * FROM t ORDER BY age ASC NULLS LAST, name DESC NULLS FIRST");

        assertEquals(new OrderByItem("age", true, false), items.get(0));
        assertEquals(new OrderByItem("name", false, true), items.get(1));
        assertFalse(items.get(0).nullsFirstEffective());
        assertTrue(items.get(1).nullsFirstEffective());
    }

    @Test
    void defaultNullOrderingFollowsDirection() {
        List<OrderByItem> items = orderBy("SELECT * FROM t ORDER BY age, name DESC");

        assertNull(items.get(0).nullsFirst());
        assertTrue(items.get(0).nullsFirstEffective());
        assertFalse(items.get(1).nullsFirstEffective());
    }

    @Test
    void qualifiedOrderByColumnKeepsOnlyColumnName() {
        assertEquals("age", orderBy("SELECT * FROM t ORDER BY t.age").get(0).column());
    }

    @Test
    void orderByPositionResolvesToSelectedColumn() {
        assertEquals(new OrderByItem("age", false),
                orderBy("SELECT name, age FROM t ORDER BY 2 DESC").get(0));
    }

    @Test
    void orderByPositionResolvesToAggregateOutputName() {
        assertEquals("n",
                orderBy("SELECT dept, COUNT(*) AS n FROM t GROUP BY dept ORDER BY 2").get(0).column());
        assertEquals("count(*)",
                orderBy("SELECT dept, COUNT(*) FROM t GROUP BY dept ORDER BY 2").get(0).column());
    }

    @Test
    void orderByPositionOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("SELECT name, age FROM t ORDER BY 3"));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("SELECT name FROM t ORDER BY 0"));
    }

    @Test
    void orderByPositionWithStarIsRejected() {
        assertThrows(UnsupportedOperationException.class,
                () -> parser.parse("SELECT * FROM t ORDER BY 1"));
    }

    @Test
    void orderByColumnAliasResolvesToSourceColumn() {
        assertEquals("age", orderBy("SELECT age AS years FROM t ORDER BY years").get(0).column());
        assertEquals("age", orderBy("SELECT age AS years FROM t ORDER BY YEARS").get(0).column());
    }

    @Test
    void orderByAggregateAliasOrText() {
        assertEquals("n",
                orderBy("SELECT dept, COUNT(*) AS n FROM t GROUP BY dept ORDER BY n DESC").get(0).column());
        assertEquals("count(*)",
                orderBy("SELECT dept, COUNT(*) FROM t GROUP BY dept ORDER BY COUNT(*) DESC").get(0).column());
        assertEquals("dept",
                orderBy("SELECT dept, COUNT(*) FROM t GROUP BY dept ORDER BY dept").get(0).column());
    }

    @Test
    void orderByUnselectedAggregateIsRejected() {
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> parser.parse("SELECT dept, COUNT(*) FROM t GROUP BY dept ORDER BY SUM(x)"));
        assertTrue(e.getMessage().startsWith("ORDER BY"));
    }

    @Test
    void orderByExpressionIsRejected() {
        assertThrows(UnsupportedOperationException.class,
                () -> parser.parse("SELECT * FROM t ORDER BY age + 1"));
    }

    // ---------- LIMIT ----------

    @Test
    void parsesLimit() {
        assertNull(parser.parse("SELECT * FROM t").getLimit());
        assertEquals(5, parser.parse("SELECT * FROM t LIMIT 5").getLimit());
        assertEquals(0, parser.parse("SELECT * FROM t LIMIT 0").getLimit());
    }

    @Test
    void invalidLimitIsRejected() {
        assertThrows(RuntimeException.class, () -> parser.parse("SELECT * FROM t LIMIT -1"));
        assertThrows(RuntimeException.class, () -> parser.parse("SELECT * FROM t LIMIT 'x'"));
        assertThrows(UnsupportedOperationException.class,
                () -> parser.parse("SELECT * FROM t LIMIT 1 OFFSET 1"));
    }

    @Test
    void queryRejectsNegativeLimitWhenBuiltDirectly() {
        assertThrows(IllegalArgumentException.class, () -> new Query(
                "t", List.of("*"), List.of(), List.of(), null, List.of(), -1, false));
    }

    // ---------- DISTINCT ----------

    @Test
    void parsesDistinctFlag() {
        assertTrue(parser.parse("SELECT DISTINCT age FROM t").isDistinct());
        assertFalse(parser.parse("SELECT age FROM t").isDistinct());
    }

    @Test
    void distinctOnIsRejected() {
        assertThrows(UnsupportedOperationException.class,
                () -> parser.parse("SELECT DISTINCT ON (age) age, name FROM t"));
    }

    @Test
    void distinctWithOrderByOnUnselectedColumnIsRejected() {
        assertThrows(UnsupportedOperationException.class,
                () -> parser.parse("SELECT DISTINCT name FROM t ORDER BY age"));
    }

    @Test
    void distinctWithOrderByOnSelectedColumnOrAliasIsAllowed() {
        assertEquals("NAME", orderBy("SELECT DISTINCT name FROM t ORDER BY NAME DESC").get(0).column());
        assertEquals("age", orderBy("SELECT DISTINCT age AS years FROM t ORDER BY years").get(0).column());
        assertEquals("age", orderBy("SELECT DISTINCT * FROM t ORDER BY age").get(0).column());
    }
}
