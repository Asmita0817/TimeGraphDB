package com.timegraph.vcs;

import java.util.Map;

public class DiffEntry {
    public enum DiffType {
        ADDED,
        DELETED,
        CHANGED
    }

    private final DiffType type;
    private final String tableName;
    private final String primaryKey;
    private final Map<String, Object> oldValues;
    private final Map<String, Object> newValues;

    public DiffEntry(DiffType type, String tableName, String primaryKey, Map<String, Object> oldValues, Map<String, Object> newValues) {
        this.type = type;
        this.tableName = tableName;
        this.primaryKey = primaryKey;
        this.oldValues = oldValues;
        this.newValues = newValues;
    }

    public DiffType getType() {
        return type;
    }

    public String getTableName() {
        return tableName;
    }

    public String getPrimaryKey() {
        return primaryKey;
    }

    public Map<String, Object> getOldValues() {
        return oldValues;
    }

    public Map<String, Object> getNewValues() {
        return newValues;
    }

    @Override
    public String toString() {
        return switch (type) {
            case ADDED -> "+ [" + tableName + "] PK=" + primaryKey + " -> " + newValues;
            case DELETED -> "- [" + tableName + "] PK=" + primaryKey + " (was " + oldValues + ")";
            case CHANGED -> "~ [" + tableName + "] PK=" + primaryKey + " from " + oldValues + " to " + newValues;
        };
    }
}
