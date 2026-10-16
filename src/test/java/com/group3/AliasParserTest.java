package com.group3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.group3.parser.Query;
import com.group3.parser.SQLParser;
import com.group3.parser.SelectColumn;

class AliasParserTest {
    private final SQLParser parser = new SQLParser();

    static Stream<Arguments> projections() {
        return Stream.of(
            Arguments.of("SELECT id AS identifier FROM t", List.of("id"), List.of("identifier")),
            Arguments.of("SELECT id identifier FROM t", List.of("id"), List.of("identifier")),
            Arguments.of("SELECT id AS RecordId FROM t", List.of("id"), List.of("RecordId")),
            Arguments.of("SELECT id AS \"Record Id\" FROM t", List.of("id"), List.of("Record Id")),
            Arguments.of("SELECT id \"Record Id\" FROM t", List.of("id"), List.of("Record Id")),
            Arguments.of("SELECT name AS \"Person \"\"Name\"\"\" FROM t",
                List.of("name"), List.of("Person \"Name\"")),
            Arguments.of("SELECT id AS identifier, name, age years FROM t",
                List.of("id", "name", "age"), List.of("identifier", "name", "years")),
            Arguments.of("SELECT age AS first_age, age AS second_age FROM t",
                List.of("age", "age"), List.of("first_age", "second_age")),
            Arguments.of("SELECT *, id AS identifier FROM t",
                List.of("*", "id"), List.of("*", "identifier")),
            Arguments.of("SELECT id AS identifier, * FROM t",
                List.of("id", "*"), List.of("identifier", "*")),
            Arguments.of("SELECT COUNT(*) AS \"Row Count\" FROM t",
                List.of("Row Count"), List.of("Row Count")),
            Arguments.of("SELECT COUNT(*) n, dept AS team, AVG(age) AS mean_age FROM t GROUP BY dept",
                List.of("n", "dept", "mean_age"), List.of("n", "team", "mean_age"))
        );
    }

    @ParameterizedTest
    @MethodSource("projections")
    void preservesSourceNamesOutputNamesAndWrittenOrder(String sql, List<String> sources, List<String> outputs) {
        Query query = parser.parse(sql);
        assertEquals(sources, query.getSelectList().stream().map(SelectColumn::source).toList());
        assertEquals(outputs, query.getSelectList().stream().map(SelectColumn::outputName).toList());
    }

    @Test
    void retainsSourceNamesForGroupingAndWhere() {
        Query query = parser.parse("SELECT dept AS team, COUNT(*) AS n FROM t "
            + "WHERE dept = 'eng' GROUP BY dept HAVING n > 1 ORDER BY team");
        assertEquals(List.of("dept"), query.getColumns());
        assertEquals(List.of("dept"), query.getGroupByColumns());
        assertEquals("dept", query.getOrderBy().get(0).column());
    }

    @Test
    void unaliasedColumnsKeepTheirNames() {
        Query query = parser.parse("SELECT id, name FROM t");
        assertEquals(List.of("id", "name"), query.getSelectList().stream().map(SelectColumn::outputName).toList());
        assertNull(query.getSelectList().get(0).alias());
    }

    @Test
    void ordinalsUseTheWrittenOrderOfAggregatesAndGroupColumns() {
        Query query = parser.parse("SELECT COUNT(*) AS n, dept AS team FROM t GROUP BY dept ORDER BY 1, 2");
        assertEquals(List.of("n", "dept"), query.getOrderBy().stream().map(item -> item.column()).toList());
    }

    @Test
    void ordinalsDoNotFollowAnAliasThatShadowsTheirSource() {
        Query query = parser.parse("SELECT age AS name, name AS person FROM t ORDER BY name, 2");
        assertEquals(List.of("age", "name"), query.getOrderBy().stream().map(item -> item.column()).toList());
    }
}
