package com.group3.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Database;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.expression.ComparisonExpression;
import com.group3.expression.ComparisonOperator;
import com.group3.expression.Expression;
import com.group3.expression.InExpression;
import com.group3.expression.IsNullExpression;
import com.group3.expression.LogicalExpression;
import com.group3.expression.LogicalOperator;
import com.group3.expression.Operand;
import com.group3.parser.JoinSpec;
import com.group3.parser.OrderByItem;
import com.group3.parser.Query;
import com.group3.parser.SelectColumn;

/**
 * Partial indexes must never change a query's result: same rows, same order, same errors
 * as a full scan. Each test compares an engine with indexes against a plain engine.
 */
class PartialIndexTest {

    private static final String[] STATUSES = {"shipped", "pending", "returned"};

    private Database database;
    private QueryEngine indexed;
    private QueryEngine plain;

    @BeforeEach
    void setUp() {
        Schema schema = new Schema(List.of(
            new Column("id", DataType.INTEGER, false),
            new Column("status", DataType.STRING, true),
            new Column("amount", DataType.INTEGER, true)));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            // every 10th amount is NULL; status cycles shipped / pending / returned
            Integer amount = i % 10 == 9 ? null : (i * 37) % 500;
            rows.add(row(i, STATUSES[i % 3], amount));
        }
        database = new Database();
        database.addTable(new Table("orders", schema, rows));
        indexed = new QueryEngine(database);
        plain = new QueryEngine(database);
    }

    private static Row row(int id, String status, Integer amount) {
        return new Row(List.of(
            new Value(DataType.INTEGER, id),
            new Value(DataType.STRING, status),
            new Value(DataType.INTEGER, amount)));
    }

    private static Expression cmp(String column, String op, Object literal) {
        DataType type = literal instanceof String ? DataType.STRING : DataType.INTEGER;
        return ComparisonExpression.of(column, op, new Value(type, literal));
    }

    private static Expression and(Expression... parts) {
        return new LogicalExpression(LogicalOperator.AND, List.of(parts));
    }

    private static Query select(Expression where) {
        return select(where, List.of(), null);
    }

    private static Query select(Expression where, List<OrderByItem> orderBy, Integer limit) {
        return new Query("orders", null, List.of(SelectColumn.star()), List.of(), List.of(),
            where, null, orderBy, limit, false);
    }

    /** Runs the query on both engines and checks they agree; returns the indexed result. */
    private QueryResult same(Query query) {
        QueryResult expected = plain.processQuery(query);
        QueryResult actual = indexed.processQuery(query);
        assertEquals(expected.getRows(), actual.getRows());
        return actual;
    }

    private void shippedIndex() {
        indexed.createIndex("shipped_amount", "orders", "amount", cmp("status", "=", "shipped"), "status = 'shipped'");
    }

    @Test
    void withoutIndexesNothingChanges() {
        same(select(cmp("amount", ">", 100)));
        assertNull(indexed.getLastIndex());
    }

    @Test
    void partialIndexHoldsOnlyMatchingRows() {
        shippedIndex();
        assertEquals(334, indexed.getIndexes().get(0).size());
    }

    @Test
    void usedWhenWhereImpliesPredicate() {
        shippedIndex();
        QueryResult result = same(select(and(cmp("status", "=", "shipped"), cmp("amount", ">", 400))));
        assertNotNull(indexed.getLastIndex());
        assertTrue(indexed.getLastIndex().startsWith("shipped_amount (scanned "));
        assertTrue(result.getRowCount() > 0);
    }

    @Test
    void scansOnlyTheRangeInsideTheIndex() {
        shippedIndex();
        QueryResult result = same(select(and(cmp("amount", ">=", 400), cmp("status", "=", "shipped"))));
        String expected = "shipped_amount (scanned " + result.getRowCount() + " of 1000 rows)";
        assertEquals(expected, indexed.getLastIndex());
    }

    @Test
    void strongerConditionStillUsesIndex() {
        indexed.createIndex("big", "orders", "id", cmp("amount", ">", 300), null);
        same(select(cmp("amount", ">", 450)));
        assertNotNull(indexed.getLastIndex());
        same(select(cmp("amount", "=", 301)));
        assertNotNull(indexed.getLastIndex());
        same(select(new ComparisonExpression(
            Operand.literal(new Value(DataType.INTEGER, 400)), ComparisonOperator.LESS_THAN, Operand.column("amount"))));
        assertNotNull(indexed.getLastIndex());
    }

    @Test
    void notUsedWhenWhereDoesNotImplyPredicate() {
        shippedIndex();
        same(select(cmp("status", "=", "pending")));
        assertNull(indexed.getLastIndex());
        same(select(cmp("amount", ">", 100)));
        assertNull(indexed.getLastIndex());
        same(select(new LogicalExpression(LogicalOperator.OR,
            List.of(cmp("status", "=", "shipped"), cmp("amount", ">", 100)))));
        assertNull(indexed.getLastIndex());
        same(select(null));
        assertNull(indexed.getLastIndex());
    }

    @Test
    void weakerConditionDoesNotUseIndex() {
        indexed.createIndex("big", "orders", "id", cmp("amount", ">", 300), null);
        same(select(cmp("amount", ">", 200)));
        assertNull(indexed.getLastIndex());
        same(select(cmp("amount", ">=", 300)));
        assertNull(indexed.getLastIndex());
    }

    @Test
    void keepsTableOrderAndLimit() {
        shippedIndex();
        Expression where = and(cmp("status", "=", "shipped"), cmp("amount", "<", 250));
        QueryResult result = same(select(where, List.of(), 7));
        assertNotNull(indexed.getLastIndex());
        assertEquals(7, result.getRowCount());
        int previous = -1;
        for (Row row : result.getRows()) {
            int id = (Integer) row.get(0).getValue();
            assertTrue(id > previous);
            previous = id;
        }
        same(select(where, List.of(new OrderByItem("amount", false)), 5));
    }

    @Test
    void keepsRowsWhoseIndexedColumnIsNull() {
        shippedIndex();
        QueryResult result = same(select(and(cmp("status", "=", "shipped"),
            new IsNullExpression(Operand.column("amount"), false))));
        assertNotNull(indexed.getLastIndex());
        assertTrue(result.getRowCount() > 0);
    }

    @Test
    void conflictingBoundsGiveNoRows() {
        shippedIndex();
        QueryResult result = same(select(and(cmp("status", "=", "shipped"),
            cmp("amount", ">", 300), cmp("amount", "<", 200))));
        assertEquals(0, result.getRowCount());
        assertEquals("shipped_amount (scanned 0 of 1000 rows)", indexed.getLastIndex());
    }

    @Test
    void typeErrorsMatchFullScan() {
        shippedIndex();
        Query query = select(and(cmp("status", "=", "shipped"), cmp("amount", "=", "abc")));
        IllegalArgumentException expected = assertThrows(IllegalArgumentException.class, () -> plain.processQuery(query));
        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, () -> indexed.processQuery(query));
        assertEquals(expected.getMessage(), actual.getMessage());
    }

    @Test
    void unknownColumnErrorsMatchFullScan() {
        shippedIndex();
        Query query = select(and(cmp("status", "=", "shipped"), cmp("missing", "=", 1)));
        IllegalArgumentException expected = assertThrows(IllegalArgumentException.class, () -> plain.processQuery(query));
        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, () -> indexed.processQuery(query));
        assertEquals(expected.getMessage(), actual.getMessage());
    }

    @Test
    void joinedQueriesIgnoreIndexes() {
        Schema schema = new Schema(List.of(new Column("status", DataType.STRING), new Column("label", DataType.STRING)));
        database.addTable(new Table("labels", schema, List.of(
            new Row(List.of(new Value(DataType.STRING, "shipped"), new Value(DataType.STRING, "S"))))));
        shippedIndex();
        Query query = new Query("orders", "o", List.of(SelectColumn.star()), List.of(), List.of(),
            null, null, List.of(), null, false, new JoinSpec("labels", "l", "status", "status"));
        same(query);
        assertNull(indexed.getLastIndex());
    }

    @Test
    void picksTheIndexWithFewestCandidates() {
        indexed.createIndex("all_amount", "orders", "amount", null, null);
        shippedIndex();
        same(select(and(cmp("status", "=", "shipped"), cmp("amount", ">", 10))));
        assertTrue(indexed.getLastIndex().startsWith("shipped_amount"));
    }

    @Test
    void createAndDropValidation() {
        shippedIndex();
        assertThrows(IllegalArgumentException.class,
            () -> indexed.createIndex("SHIPPED_AMOUNT", "orders", "id", null, null));
        assertThrows(IllegalArgumentException.class,
            () -> indexed.createIndex("x", "orders", "missing", null, null));
        assertThrows(IllegalArgumentException.class,
            () -> indexed.createIndex("x", "nope", "id", null, null));
        assertThrows(IllegalArgumentException.class, () -> indexed.createIndex("x", "orders", "id",
            new LogicalExpression(LogicalOperator.OR, List.of(cmp("id", "=", 1), cmp("id", "=", 2))), null));
        assertThrows(IllegalArgumentException.class,
            () -> indexed.createIndex("x", "orders", "id", cmp("status", ">", 3), null));
        indexed.dropIndex("shipped_amount");
        assertTrue(indexed.getIndexes().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> indexed.dropIndex("shipped_amount"));
    }

    /** Random WHEREs against random indexes: every result must equal the full scan's. */
    @Test
    void randomQueriesMatchFullScan() {
        Random random = new Random(4321);
        String[] ops = {"=", "!=", "<", ">", "<=", ">="};
        indexed.createIndex("a", "orders", "amount", cmp("status", "=", "returned"), null);
        indexed.createIndex("b", "orders", "id", cmp("amount", ">=", 200), null);
        indexed.createIndex("c", "orders", "status", and(cmp("amount", "<", 100), cmp("id", ">", 50)), null);
        indexed.createIndex("d", "orders", "amount", null, null);
        int used = 0;
        for (int i = 0; i < 2000; i++) {
            List<Expression> parts = new ArrayList<>();
            int count = 1 + random.nextInt(3);
            for (int j = 0; j < count; j++) {
                parts.add(switch (random.nextInt(6)) {
                    case 0 -> cmp("status", ops[random.nextInt(2)], STATUSES[random.nextInt(3)]);
                    case 1 -> cmp("id", ops[random.nextInt(6)], random.nextInt(1100) - 50);
                    case 2 -> new IsNullExpression(Operand.column("amount"), random.nextBoolean());
                    case 3 -> new InExpression(Operand.column("status"),
                        List.of(new Value(DataType.STRING, STATUSES[random.nextInt(3)])));
                    default -> cmp("amount", ops[random.nextInt(6)], random.nextInt(600) - 50);
                });
            }
            Expression where = parts.size() == 1 ? parts.get(0)
                : new LogicalExpression(LogicalOperator.AND, parts);
            same(select(where, List.of(), random.nextInt(3) == 0 ? random.nextInt(10) : null));
            if (indexed.getLastIndex() != null) {
                used++;
            }
        }
        assertTrue(used > 500, "index used in only " + used + " of 2000 queries");
    }
}
