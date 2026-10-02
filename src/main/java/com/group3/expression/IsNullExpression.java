package com.group3.expression;

import com.group3.data.Row;
import com.group3.data.Schema;

public final class IsNullExpression implements Expression {

    private final Operand operand;
    private final boolean negated;

    public IsNullExpression(Operand operand, boolean negated) {
        if (operand == null) {
            throw new IllegalArgumentException("IS NULL requires an operand");
        }
        this.operand = operand;
        this.negated = negated;
    }

    public static IsNullExpression isNull(String column) {
        return new IsNullExpression(Operand.column(column), false);
    }

    public static IsNullExpression isNotNull(String column) {
        return new IsNullExpression(Operand.column(column), true);
    }

    public Operand getOperand() {
        return operand;
    }

    public boolean isNegated() {
        return negated;
    }

    @Override
    public boolean evaluate(Schema schema, Row row) {
        boolean isNull = operand.resolve(schema, row).isNull();
        return negated ? !isNull : isNull;
    }
}
