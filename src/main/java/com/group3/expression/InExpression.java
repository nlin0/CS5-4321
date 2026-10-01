package com.group3.expression;

import java.util.ArrayList;
import java.util.List;

import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Value;

public final class InExpression implements Expression {

    private final Operand operand;
    private final List<Value> values;

    public InExpression(Operand operand, List<Value> values) {
        if (operand == null || values == null) {
            throw new IllegalArgumentException("IN requires an operand and a list of values");
        }
        List<Value> copy = new ArrayList<>();
        for (Value value : values) {
            if (value == null) {
                throw new IllegalArgumentException(
                    "IN list cannot contain a null entry; use a Value with a null payload for SQL NULL"
                );
            }
            copy.add(value);
        }
        this.operand = operand;
        this.values = List.copyOf(copy);
    }

    public static InExpression of(String column, List<Value> values) {
        return new InExpression(Operand.column(column), values);
    }

    public Operand getOperand() {
        return operand;
    }

    public List<Value> getValues() {
        return values;
    }

    @Override
    public boolean evaluate(Schema schema, Row row) {
        Value actual = operand.resolve(schema, row);
        if (actual.isNull()) {
            return false;
        }

        for (Value candidate : values) {
            if (candidate.isNull()) {
                continue;
            }
            if (ValueComparison.equals(actual, candidate)) {
                return true;
            }
        }
        return false;
    }
}
