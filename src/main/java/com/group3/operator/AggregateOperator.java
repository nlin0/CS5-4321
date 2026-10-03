package com.group3.operator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.group3.data.Column;
import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Table;
import com.group3.data.Value;
import com.group3.engine.QueryResult;

/** Performs GROUP BY and aggregate calculations over an in-memory table. */
public class AggregateOperator {

    public QueryResult execute(Table table, List<String> groupByColumns, List<AggregateExpression> aggregates) {
        if (aggregates == null || aggregates.isEmpty()) {
            throw new IllegalArgumentException("At least one aggregate is required");
        }

        Schema input = table.getSchema();
        List<Integer> groupIndexes = indexes(input, groupByColumns);
        List<Integer> aggregateIndexes = aggregateIndexes(input, aggregates);
        Schema outputSchema = outputSchema(input, groupIndexes, aggregates, aggregateIndexes);

        Map<List<Value>, List<Row>> groups = new LinkedHashMap<>();
        if (groupIndexes.isEmpty()) {
            groups.put(List.of(), new ArrayList<>());
        }
        for (Row row : table.getRows()) {
            List<Value> key = new ArrayList<>();
            for (int index : groupIndexes) {
                key.add(row.get(index));
            }
            groups.computeIfAbsent(List.copyOf(key), ignored -> new ArrayList<>()).add(row);
        }

        List<Row> resultRows = new ArrayList<>();
        for (Map.Entry<List<Value>, List<Row>> group : groups.entrySet()) {
            List<Value> values = new ArrayList<>(group.getKey());
            for (int i = 0; i < aggregates.size(); i++) {
                values.add(calculate(group.getValue(), aggregates.get(i), aggregateIndexes.get(i), input));
            }
            resultRows.add(new Row(values));
        }
        return new QueryResult(outputSchema, resultRows);
    }

    private List<Integer> indexes(Schema schema, List<String> names) {
        List<Integer> indexes = new ArrayList<>();
        for (String name : names == null ? List.<String>of() : names) {
            int index = schema.indexOf(name);
            if (index < 0) throw new IllegalArgumentException("Column not found: " + name);
            if (indexes.contains(index)) throw new IllegalArgumentException("Duplicate GROUP BY column: " + name);
            indexes.add(index);
        }
        return indexes;
    }

    private List<Integer> aggregateIndexes(Schema schema, List<AggregateExpression> aggregates) {
        List<Integer> indexes = new ArrayList<>();
        for (AggregateExpression aggregate : aggregates) {
            if (aggregate.column() == null || aggregate.column().equals("*")) {
                indexes.add(-1);
            } else {
                int index = schema.indexOf(aggregate.column());
                if (index < 0) throw new IllegalArgumentException("Column not found: " + aggregate.column());
                indexes.add(index);
            }
        }
        return indexes;
    }

    private Schema outputSchema(Schema input, List<Integer> groupIndexes, List<AggregateExpression> aggregates,
                                List<Integer> aggregateIndexes) {
        List<Column> columns = new ArrayList<>();
        for (int index : groupIndexes) columns.add(input.getColumn(index));
        for (int i = 0; i < aggregates.size(); i++) {
            AggregateExpression aggregate = aggregates.get(i);
            DataType type = switch (aggregate.function()) {
                case COUNT -> DataType.INTEGER;
                case AVG -> DataType.FLOAT;
                case SUM, MIN, MAX -> input.getColumn(aggregateIndexes.get(i)).getType();
            };
            columns.add(new Column(aggregate.outputName(), type));
        }
        return new Schema(columns);
    }

    private Value calculate(List<Row> rows, AggregateExpression aggregate, int index, Schema schema) {
        if (aggregate.function() == AggregateFunction.COUNT) {
            int count = 0;
            for (Row row : rows) {
                if (index < 0 || !row.get(index).isNull()) count++;
            }
            return new Value(DataType.INTEGER, count);
        }

        DataType type = schema.getColumn(index).getType();
        if (aggregate.function() == AggregateFunction.SUM || aggregate.function() == AggregateFunction.AVG) {
            if (type != DataType.INTEGER && type != DataType.FLOAT) {
                throw new IllegalArgumentException(aggregate.function() + " requires a numeric column: " + aggregate.column());
            }
            double sum = 0;
            int count = 0;
            for (Row row : rows) {
                Value value = row.get(index);
                if (!value.isNull()) {
                    sum += ((Number) value.getValue()).doubleValue();
                    count++;
                }
            }
            if (count == 0) return new Value(aggregate.function() == AggregateFunction.AVG ? DataType.FLOAT : type, null);
            if (aggregate.function() == AggregateFunction.AVG) return new Value(DataType.FLOAT, sum / count);
            return type == DataType.INTEGER ? new Value(DataType.INTEGER, (int) sum) : new Value(DataType.FLOAT, sum);
        }

        Value best = null;
        for (Row row : rows) {
            Value candidate = row.get(index);
            if (candidate.isNull()) continue;
            if (best == null || compare(candidate, best) * (aggregate.function() == AggregateFunction.MIN ? 1 : -1) < 0) {
                best = candidate;
            }
        }
        return best == null ? new Value(type, null) : best;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private int compare(Value left, Value right) {
        if (left.getType() != right.getType()) throw new IllegalArgumentException("Cannot compare different data types");
        if (!(left.getValue() instanceof Comparable comparable)) {
            throw new IllegalArgumentException("Values are not comparable: " + left.getType());
        }
        return comparable.compareTo(right.getValue());
    }
}
