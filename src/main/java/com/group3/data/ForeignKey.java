package com.group3.data;

/** One child column that must match a parent table's primary key. Nulls are allowed. */
public final class ForeignKey {

    private final String childColumn;
    private final int childIndex;
    private final Table parent;
    private final String parentColumn;

    ForeignKey(String childColumn, int childIndex, Table parent, String parentColumn) {
        this.childColumn = childColumn;
        this.childIndex = childIndex;
        this.parent = parent;
        this.parentColumn = parentColumn;
    }

    public String getChildColumn() {
        return childColumn;
    }

    public String getParentTable() {
        return parent.getName();
    }

    public String getParentColumn() {
        return parentColumn;
    }

    void check(Row row) {
        Value value = row.get(childIndex);
        if (value.isNull()) {
            return;
        }
        if (!parent.containsPrimaryKey(value)) {
            throw new IllegalArgumentException(
                "Foreign key " + childColumn + " has no matching "
                    + parent.getName() + "." + parentColumn + ": " + value
            );
        }
    }
}
