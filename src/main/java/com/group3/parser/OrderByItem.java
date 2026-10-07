package com.group3.parser;

/**
 * One ORDER BY key: a column (or aggregate output name), a direction, and an
 * optional explicit NULL placement.
 *
 * @param column     name of the column in the schema being sorted
 * @param ascending  true for ASC, false for DESC
 * @param nullsFirst TRUE for NULLS FIRST, FALSE for NULLS LAST, or null when
 *                   the query did not say (see {@link #nullsFirstEffective()})
 */
public record OrderByItem(String column, boolean ascending, Boolean nullsFirst) {

    public OrderByItem {
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("ORDER BY column is required");
        }
    }

    /** ORDER BY key with the default NULL placement. */
    public OrderByItem(String column, boolean ascending) {
        this(column, ascending, null);
    }

    /**
     * Where NULLs go for this key. Default: NULLs are treated as the smallest
     * value, so they come first in ascending order and last in descending order.
     */
    public boolean nullsFirstEffective() {
        return nullsFirst != null ? nullsFirst : ascending;
    }
}
