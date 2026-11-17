package com.group3.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.expression.ComparisonOperator;
import com.group3.expression.Expression;

/**
 * An ordered index on one column that holds only the rows matching its predicate
 * (a partial index). With no predicate it covers every row.
 *
 * It stores row positions, not rows, so lookups hand rows back in their original
 * table order and query output is the same as a full scan.
 */
public final class PartialIndex {

    private final String name;
    private final Table table;
    private final String column;
    private final int columnIndex;
    private final DataType columnType;
    private final Expression predicate;
    private final String predicateText;
    private final List<Expression> predicateConjuncts;

    private int builtForSize = -1;
    private int[] allPositions;
    private TreeMap<Value, List<Integer>> byKey;

    public PartialIndex(String name, Table table, String column, Expression predicate, String predicateText) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Index name is required");
        }
        if (table == null) {
            throw new IllegalArgumentException("Table is required");
        }
        Schema schema = table.getSchema();
        int index = column == null ? -1 : schema.indexOf(column);
        if (index < 0) {
            throw new IllegalArgumentException("Column not found: " + column);
        }
        this.name = name;
        this.table = table;
        this.columnIndex = index;
        this.column = schema.getColumn(index).getName();
        this.columnType = schema.getColumn(index).getType();
        this.predicate = predicate;
        this.predicateText = predicateText;
        this.predicateConjuncts = Predicates.conjuncts(predicate);
        validatePredicate(schema);
        rebuild();
    }

    /** Index predicates are limited to ANDs of column-vs-literal comparisons and IS [NOT] NULL. */
    private void validatePredicate(Schema schema) {
        for (Expression conjunct : predicateConjuncts) {
            if (Predicates.comparison(conjunct) == null && Predicates.nullCheck(conjunct) == null) {
                throw new IllegalArgumentException(
                    "Index WHERE supports only AND of column-vs-literal comparisons and IS [NOT] NULL");
            }
        }
        if (!Predicates.neverThrows(predicate, schema)) {
            throw new IllegalArgumentException("Index WHERE has an unknown column or mismatched types");
        }
    }

    private void rebuild() {
        Schema schema = table.getSchema();
        List<Row> rows = table.getRows();
        List<Integer> all = new ArrayList<>();
        TreeMap<Value, List<Integer>> keys = new TreeMap<>(Predicates::compare);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (predicate != null && !predicate.evaluate(schema, row)) {
                continue;
            }
            all.add(i);
            Value key = row.get(columnIndex);
            if (!key.isNull()) {
                keys.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            }
        }
        allPositions = all.stream().mapToInt(Integer::intValue).toArray();
        byKey = keys;
        builtForSize = rows.size();
    }

    /**
     * True when this WHERE can never throw on this schema, whatever the rows hold.
     * Only then may a query skip rows: a full scan could otherwise hit an error
     * (such as a type mismatch) on a row the index skipped.
     */
    public static boolean safeToNarrow(Expression where, Schema schema) {
        return Predicates.neverThrows(where, schema);
    }

    public String getName() {
        return name;
    }

    public Table getTable() {
        return table;
    }

    public String getColumn() {
        return column;
    }

    /** Number of rows the index holds. */
    public int size() {
        refreshIfStale();
        return allPositions.length;
    }

    public String describe() {
        String text = name + " ON " + table.getName() + "(" + column + ")";
        if (predicate != null) {
            text += " WHERE " + (predicateText != null ? predicateText : "<predicate>");
        }
        return text + " [" + size() + " of " + table.size() + " rows]";
    }

    private void refreshIfStale() {
        if (builtForSize != table.size()) {
            rebuild();
        }
    }

    /**
     * Positions of candidate rows for this WHERE, in table order, or null when the index
     * cannot answer it (its predicate is not implied by the WHERE). The caller must still
     * apply the full WHERE to the candidates.
     */
    public int[] candidates(Expression where) {
        refreshIfStale();
        List<Expression> query = Predicates.conjuncts(where);
        for (Expression needed : predicateConjuncts) {
            boolean implied = false;
            for (Expression q : query) {
                if (Predicates.implies(q, needed)) {
                    implied = true;
                    break;
                }
            }
            if (!implied) {
                return null;
            }
        }
        return lookup(query);
    }

    private int[] lookup(List<Expression> query) {
        Value lower = null;
        boolean lowerInclusive = false;
        Value upper = null;
        boolean upperInclusive = false;
        boolean bounded = false;

        for (Expression q : query) {
            Predicates.Comparison cmp = Predicates.comparison(q);
            if (cmp == null || !cmp.column().equalsIgnoreCase(column)
                    || !Predicates.compatible(columnType, cmp.literal().getType())) {
                continue;
            }
            ComparisonOperator op = cmp.op();
            if (op == ComparisonOperator.NOT_EQUAL) {
                continue;
            }
            if (op.isOrdering() && columnType == DataType.BOOLEAN) {
                continue;
            }
            Value v = cmp.literal();
            boolean setsLower = op == ComparisonOperator.EQUAL
                || op == ComparisonOperator.GREATER_THAN || op == ComparisonOperator.GREATER_OR_EQUAL;
            boolean setsUpper = op == ComparisonOperator.EQUAL
                || op == ComparisonOperator.LESS_THAN || op == ComparisonOperator.LESS_OR_EQUAL;
            boolean inclusive = op != ComparisonOperator.GREATER_THAN && op != ComparisonOperator.LESS_THAN;
            if (setsLower) {
                int c = lower == null ? 1 : Predicates.compare(v, lower);
                if (c > 0 || (c == 0 && !inclusive)) {
                    lower = v;
                    lowerInclusive = inclusive;
                }
            }
            if (setsUpper) {
                int c = upper == null ? -1 : Predicates.compare(v, upper);
                if (c < 0 || (c == 0 && !inclusive)) {
                    upper = v;
                    upperInclusive = inclusive;
                }
            }
            bounded = true;
        }

        if (!bounded) {
            return allPositions.clone();
        }
        if (lower != null && upper != null) {
            int c = Predicates.compare(lower, upper);
            if (c > 0 || (c == 0 && !(lowerInclusive && upperInclusive))) {
                return new int[0];
            }
        }
        NavigableMap<Value, List<Integer>> range = byKey;
        if (lower != null) {
            range = range.tailMap(lower, lowerInclusive);
        }
        if (upper != null) {
            range = range.headMap(upper, upperInclusive);
        }
        int count = 0;
        for (List<Integer> positions : range.values()) {
            count += positions.size();
        }
        int[] out = new int[count];
        int i = 0;
        for (List<Integer> positions : range.values()) {
            for (int position : positions) {
                out[i++] = position;
            }
        }
        Arrays.sort(out);
        return out;
    }
}
