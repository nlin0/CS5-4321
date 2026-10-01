package com.group3.expression;

import com.group3.data.DataType;
import com.group3.data.Row;
import com.group3.data.Schema;
import com.group3.data.Value;

public final class ComparisonExpression implements Expression {

    private final Operand left;
    private final ComparisonOperator operator;
    private final Operand right;

    public ComparisonExpression(Operand left, ComparisonOperator operator, Operand right) {
        if (left == null || operator == null || right == null) {
            throw new IllegalArgumentException("Comparison requires a left operand, operator, and right operand");
        }
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    public static ComparisonExpression of(String column, String symbol, Value literal) {
        return new ComparisonExpression(
            Operand.column(column),
            ComparisonOperator.fromSymbol(symbol),
            Operand.literal(literal)
        );
    }

    public Operand getLeft() {
        return left;
    }

    public ComparisonOperator getOperator() {
        return operator;
    }

    public Operand getRight() {
        return right;
    }

    @Override
    public boolean evaluate(Schema schema, Row row) {
        Value leftValue = left.resolve(schema, row);
        Value rightValue = right.resolve(schema, row);

        if (leftValue.isNull() || rightValue.isNull()) {
            return false;
        }

        if (operator.isOrdering()
                && (leftValue.getType() == DataType.BOOLEAN || rightValue.getType() == DataType.BOOLEAN)) {
            throw new IllegalArgumentException("Boolean values only support = and !=");
        }

        int compared = ValueComparison.compare(leftValue, rightValue);
        return switch (operator) {
            case EQUAL -> compared == 0;
            case NOT_EQUAL -> compared != 0;
            case LESS_THAN -> compared < 0;
            case GREATER_THAN -> compared > 0;
            case LESS_OR_EQUAL -> compared <= 0;
            case GREATER_OR_EQUAL -> compared >= 0;
        };
    }
}
