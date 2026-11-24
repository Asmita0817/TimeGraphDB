package com.timegraph.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.timegraph.storage.JsonUtils;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * DatabaseSnapshot represents the Root Tree of the entire database state at a commit.
 * Maps TableName -> TableTree SHA-1 hash.
 */
public class DatabaseSnapshot extends StorageObject {

    private final TreeMap<String, String> tables; // TableName (case-insensitive key) -> TableTree SHA-1

    public DatabaseSnapshot() {
        this.tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    @JsonCreator
    public DatabaseSnapshot(@JsonProperty("tables") Map<String, String> tables) {
        this.tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (tables != null) {
            this.tables.putAll(tables);
        }
    }

    public Map<String, String> getTables() {
        return Collections.unmodifiableMap(tables);
    }

    public String getTableTreeHash(String tableName) {
        return tables.get(tableName);
    }

    public boolean hasTable(String tableName) {
        return tables.containsKey(tableName);
    }

    public DatabaseSnapshot withTable(String tableName, String tableTreeHash) {
        TreeMap<String, String> updated = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        updated.putAll(this.tables);
        updated.put(tableName, tableTreeHash);
        return new DatabaseSnapshot(updated);
    }

    public DatabaseSnapshot withoutTable(String tableName) {
        TreeMap<String, String> updated = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        updated.putAll(this.tables);
        updated.remove(tableName);
        return new DatabaseSnapshot(updated);
    }

    @Override
    public byte[] serialize() {
        return JsonUtils.toBytes(this);
    }

    @Override
    public String toString() {
        return "DatabaseSnapshot[tables=" + tables.keySet() + ", hash=" + getHash() + "]";
    }
}
