package com.group3.parser;

/**
 * One item of a SELECT list, in the order it was written: *, a column with an
 * optional alias, or an aggregate referenced by its output name.
 *
 * @param source    "*", the source column name, or the aggregate's output name
 * @param alias     output name for a plain column, or null to keep the source name
 * @param aggregate true when this item is an aggregate such as COUNT(*)
 */
public record SelectColumn(String source, String alias, boolean aggregate) {

    public SelectColumn {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("SELECT item requires a column name");
        }
        if (alias != null && alias.isBlank()) {
            alias = null;
        }
        if (alias != null && (aggregate || source.equals("*"))) {
            throw new IllegalArgumentException("Alias is only allowed on a plain column: " + source);
        }
    }

    public static SelectColumn star() {
        return new SelectColumn("*", null, false);
    }

    public static SelectColumn column(String name, String alias) {
        return new SelectColumn(name, alias, false);
    }

    /** An aggregate, named by AggregateExpression.outputName() (its alias is already part of that name). */
    public static SelectColumn aggregate(String outputName) {
        return new SelectColumn(outputName, null, true);
    }

    public boolean isStar() {
        return !aggregate && source.equals("*");
    }

    public String outputName() {
        return alias != null ? alias : source;
    }
}
