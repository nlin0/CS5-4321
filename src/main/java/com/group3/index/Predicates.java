package com.group3.index;

import java.util.ArrayList;
import java.util.List;

import com.group3.data.DataType;
import com.group3.data.Schema;
import com.group3.data.Value;
import com.group3.expression.ComparisonExpression;
import com.group3.expression.ComparisonOperator;
import com.group3.expression.Expression;
import com.group3.expression.InExpression;
import com.group3.expression.IsNullExpression;
import com.group3.expression.LogicalExpression;
import com.group3.expression.LogicalOperator;
import com.group3.expression.Operand;

/**
 * Reads WHERE expressions for index matching, through their public getters only.
 * Everything here is conservative: when in doubt it answers "no", which makes the
 * engine fall back to a full scan.
 */
final class Predicates {

    private Predicates() {
    }

    /** column op literal, with the column always on the left and a non-null literal. */
    record Comparison(String column, ComparisonOperator op, Value literal) {
    }

    /** column IS NULL (notNull false) or column IS NOT NULL (notNull true). */
    record NullCheck(String column, boolean notNull) {
    }

    /** Splits nested ANDs into a flat list of conjuncts. A null WHERE has none. */
    static List<Expression> conjuncts(Expression expression) {
        List<Expression> out = new ArrayList<>();
        collect(expression, out);
        return out;
    }

    private static void collect(Expression expression, List<Expression> out) {
        if (expression == null) {
            return;
        }
        if (expression instanceof LogicalExpression logical && logical.getOperator() == LogicalOperator.AND) {
            for (Expression child : logical.getChildren()) {
                collect(child, out);
            }
            return;
        }
        out.add(expression);
    }

    /** Returns the comparison in column-op-literal form, or null when it is not one. */
    static Comparison comparison(Expression expression) {
        if (!(expression instanceof ComparisonExpression cmp)) {
            return null;
        }
        Operand left = cmp.getLeft();
        Operand right = cmp.getRight();
        if (left.isColumn() && !right.isColumn() && !right.getLiteral().isNull()) {
            return new Comparison(left.getColumnName(), cmp.getOperator(), right.getLiteral());
        }
        if (!left.isColumn() && right.isColumn() && !left.getLiteral().isNull()) {
            return new Comparison(right.getColumnName(), flip(cmp.getOperator()), left.getLiteral());
        }
        return null;
    }

    static NullCheck nullCheck(Expression expression) {
        if (expression instanceof IsNullExpression check && check.getOperand().isColumn()) {
            return new NullCheck(check.getOperand().getColumnName(), check.isNegated());
        }
        return null;
    }

    private static ComparisonOperator flip(ComparisonOperator op) {
        return switch (op) {
            case LESS_THAN -> ComparisonOperator.GREATER_THAN;
            case GREATER_THAN -> ComparisonOperator.LESS_THAN;
            case LESS_OR_EQUAL -> ComparisonOperator.GREATER_OR_EQUAL;
            case GREATER_OR_EQUAL -> ComparisonOperator.LESS_OR_EQUAL;
            default -> op;
        };
    }

    static boolean isNumeric(DataType type) {
        return type == DataType.INTEGER || type == DataType.FLOAT;
    }

    /** True when two types can be compared without ValueComparison throwing. */
    static boolean compatible(DataType a, DataType b) {
        return (isNumeric(a) && isNumeric(b)) || a == b;
    }

    /** Same ordering as the engine's ValueComparison; callers check compatible() first. */
    static int compare(Value a, Value b) {
        if (isNumeric(a.getType()) && isNumeric(b.getType())) {
            return Double.compare(((Number) a.getValue()).doubleValue(), ((Number) b.getValue()).doubleValue());
        }
        return switch (a.getType()) {
            case STRING -> ((String) a.getValue()).compareTo((String) b.getValue());
            case BOOLEAN -> Boolean.compare((Boolean) a.getValue(), (Boolean) b.getValue());
            default -> throw new IllegalStateException("Numeric values are compared above");
        };
    }

    /**
     * True when every row satisfying query conjunct q also satisfies index conjunct p.
     * Only simple, provable cases answer true.
     */
    static boolean implies(Expression q, Expression p) {
        NullCheck pNull = nullCheck(p);
        if (pNull != null) {
            NullCheck qNull = nullCheck(q);
            if (qNull != null) {
                return qNull.column().equalsIgnoreCase(pNull.column()) && qNull.notNull() == pNull.notNull();
            }
            if (!pNull.notNull()) {
                return false;
            }
            // Comparisons and IN are false on NULL, so they imply IS NOT NULL.
            Comparison qCmp = comparison(q);
            if (qCmp != null) {
                return qCmp.column().equalsIgnoreCase(pNull.column());
            }
            return q instanceof InExpression in && in.getOperand().isColumn()
                && in.getOperand().getColumnName().equalsIgnoreCase(pNull.column());
        }

        Comparison pc = comparison(p);
        Comparison qc = comparison(q);
        if (pc == null || qc == null || !pc.column().equalsIgnoreCase(qc.column())
                || !compatible(pc.literal().getType(), qc.literal().getType())) {
            return false;
        }
        int c = compare(qc.literal(), pc.literal());
        ComparisonOperator qo = qc.op();
        return switch (pc.op()) {
            case EQUAL -> qo == ComparisonOperator.EQUAL && c == 0;
            case NOT_EQUAL -> (qo == ComparisonOperator.NOT_EQUAL && c == 0)
                || (qo == ComparisonOperator.EQUAL && c != 0)
                || (qo == ComparisonOperator.GREATER_THAN && c >= 0)
                || (qo == ComparisonOperator.GREATER_OR_EQUAL && c > 0)
                || (qo == ComparisonOperator.LESS_THAN && c <= 0)
                || (qo == ComparisonOperator.LESS_OR_EQUAL && c < 0);
            case GREATER_THAN -> (qo == ComparisonOperator.GREATER_THAN && c >= 0)
                || (qo == ComparisonOperator.GREATER_OR_EQUAL && c > 0)
                || (qo == ComparisonOperator.EQUAL && c > 0);
            case GREATER_OR_EQUAL -> (qo == ComparisonOperator.GREATER_THAN && c >= 0)
                || (qo == ComparisonOperator.GREATER_OR_EQUAL && c >= 0)
                || (qo == ComparisonOperator.EQUAL && c >= 0);
            case LESS_THAN -> (qo == ComparisonOperator.LESS_THAN && c <= 0)
                || (qo == ComparisonOperator.LESS_OR_EQUAL && c < 0)
                || (qo == ComparisonOperator.EQUAL && c < 0);
            case LESS_OR_EQUAL -> (qo == ComparisonOperator.LESS_THAN && c <= 0)
                || (qo == ComparisonOperator.LESS_OR_EQUAL && c <= 0)
                || (qo == ComparisonOperator.EQUAL && c <= 0);
        };
    }

    /**
     * True when evaluating this WHERE can never throw on this schema, whatever the rows hold.
     * Only then may the engine skip rows: a full scan could otherwise hit an error on a row
     * the index skipped, and the user would see a different outcome.
     */
    static boolean neverThrows(Expression expression, Schema schema) {
        if (expression == null) {
            return true;
        }
        if (expression instanceof LogicalExpression logical) {
            for (Expression child : logical.getChildren()) {
                if (!neverThrows(child, schema)) {
                    return false;
                }
            }
            return true;
        }
        if (expression instanceof ComparisonExpression cmp) {
            DataType left = typeOf(cmp.getLeft(), schema);
            DataType right = typeOf(cmp.getRight(), schema);
            if (left == null || right == null) {
                return false;
            }
            if (isNullLiteral(cmp.getLeft()) || isNullLiteral(cmp.getRight())) {
                return true;
            }
            if (cmp.getOperator().isOrdering() && (left == DataType.BOOLEAN || right == DataType.BOOLEAN)) {
                return false;
            }
            return compatible(left, right);
        }
        if (expression instanceof InExpression in) {
            DataType type = typeOf(in.getOperand(), schema);
            if (type == null) {
                return false;
            }
            for (Value candidate : in.getValues()) {
                if (candidate != null && !candidate.isNull() && !compatible(type, candidate.getType())) {
                    return false;
                }
            }
            return true;
        }
        if (expression instanceof IsNullExpression check) {
            return typeOf(check.getOperand(), schema) != null;
        }
        return false;
    }

    private static boolean isNullLiteral(Operand operand) {
        return !operand.isColumn() && operand.getLiteral().isNull();
    }

    /** Type of an operand, or null when it names a missing column. */
    private static DataType typeOf(Operand operand, Schema schema) {
        if (!operand.isColumn()) {
            return operand.getLiteral().getType();
        }
        int index = schema.indexOf(operand.getColumnName());
        return index < 0 ? null : schema.getColumn(index).getType();
    }
}
