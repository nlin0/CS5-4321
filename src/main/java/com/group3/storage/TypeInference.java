package com.group3.storage;

import com.group3.data.DataType;

import java.util.List;

public final class TypeInference {

    private TypeInference() {
        // prevent instantiation
    }

    /**
     * Infers the data type of a single non-null CSV value.
     */
    public static DataType inferValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "Cannot infer a type from a null or blank value"
            );
        }

        String trimmed = value.trim();

        // INTERGER
        try {
            Integer.parseInt(trimmed);
            return DataType.INTEGER;
        } catch (NumberFormatException ignored) {
        }

        // FLOAT
        try {
            Float.parseFloat(trimmed);
            return DataType.FLOAT;
        } catch (NumberFormatException ignored) {
        }

        // BOOLEAN
        if (trimmed.equalsIgnoreCase("true")
                || trimmed.equalsIgnoreCase("false")) {
            return DataType.BOOLEAN;
        }

        // anything else is STRING
        return DataType.STRING;
    }

    /**
     * Infers the data type of an entire CSV column.
     *
     * Null and blank values are ignored
     */
    public static DataType inferColumn(List<String> values) {
        if (values == null) {
            throw new IllegalArgumentException(
                "Values cannot be null"
            );
        }

        DataType inferredType = null;

        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }

            DataType currentType = inferValue(value);

            if (inferredType == null) {
                inferredType = currentType;
            } else {
                inferredType = mergeTypes(inferredType, currentType);
            }

            // Once a column is STRING, nothing can make it
            // more specific again.
            if (inferredType == DataType.STRING) {
                return DataType.STRING;
            }
        }

        // Use STRING as a safe default.
        return inferredType == null
            ? DataType.STRING
            : inferredType;
    }

    /**
     * Determines the common type between two inferred values.
     */
    private static DataType mergeTypes(
            DataType first,
            DataType second) {

        if (first == second) {
            return first;
        }

        // INTEGER can safely be promoted to FLOAT
        if ((first == DataType.INTEGER && second == DataType.FLOAT)
                || (first == DataType.FLOAT
                    && second == DataType.INTEGER)) {
            return DataType.FLOAT;
        }

        // Mixed incompatible types = STRING
        return DataType.STRING;
    }
}