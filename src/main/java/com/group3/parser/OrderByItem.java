package com.group3.parser;

/** One ORDER BY key: a column (or aggregate output name) and a direction. */
public record OrderByItem(String column, boolean ascending) {
}
