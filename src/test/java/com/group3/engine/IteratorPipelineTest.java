package com.group3.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.parser.SQLParser;

/** Filtering and projection run lazily; checks results at the supported size limit. */
class IteratorPipelineTest {
    private static final int N = QueryEngine.SUPPORTED_MAX_ROWS_PER_TABLE;
    private final SQLParser parser = new SQLParser();
    private QueryEngine engine;

    @BeforeEach
    void setUp() {
        Schema schema = new Schema(List.of(
            new Column("id", DataType.INTEGER), new Column("grp", DataType.INTEGER)));
        List<Row> rows = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            rows.add(new Row(List.of(new Value(DataType.INTEGER, i), new Value(DataType.INTEGER, i % 10))));
        }
        Database database = new Database();
        database.addTable(new Table("big", schema, rows));
        engine = new QueryEngine(database);
    }

    private QueryResult run(String sql) {
        return engine.processQuery(parser.parse(sql));
    }

    @Test
    void supportedLimitIs100000() {
        assertEquals(100_000, N);
    }

    @Test
    void filterAndProjectOverFullTable() {
        QueryResult result = run("SELECT id FROM big WHERE grp = 3");
        assertEquals(N / 10, result.getRows().size());
        assertEquals(1, result.getSchema().size());
    }

    @Test
    void limitWithoutOrderByStopsEarly() {
        QueryResult result = run("SELECT id FROM big WHERE grp = 3 LIMIT 5");
        assertEquals(5, result.getRows().size());
        assertEquals(3, result.getRows().get(0).get(0).getValue());
        assertEquals(43, result.getRows().get(4).get(0).getValue());
    }

    @Test
    void distinctOverFullTable() {
        assertEquals(10, run("SELECT DISTINCT grp FROM big").getRows().size());
    }

    @Test
    void orderByThenLimit() {
        QueryResult result = run("SELECT id FROM big WHERE grp = 0 ORDER BY id DESC LIMIT 2");
        assertEquals(N - 10, result.getRows().get(0).get(0).getValue());
        assertEquals(N - 20, result.getRows().get(1).get(0).getValue());
    }

    @Test
    void groupByOverFullTable() {
        QueryResult result = run("SELECT grp, COUNT(*) FROM big GROUP BY grp");
        assertEquals(10, result.getRows().size());
        assertEquals(N / 10L, ((Number) result.getRows().get(0).get(1).getValue()).longValue());
    }

    @Test
    void limitZeroReturnsNoRows() {
        assertEquals(0, run("SELECT id FROM big LIMIT 0").getRows().size());
    }
}
