package com.timegraph.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.timegraph.storage.JsonUtils;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * TableTree stores table name, column schema definitions, and a deterministic sorted
 * TreeMap<String, String> mapping PrimaryKey -> RowBlob SHA-1 hash.
 */
public class TableTree extends StorageObject {

    private final String tableName;
    private final TableSchema schema;
    private final TreeMap<String, String> rows; // PrimaryKey -> RowBlob SHA-1

    @JsonCreator
    public TableTree(
            @JsonProperty("tableName") String tableName,
            @JsonProperty("schema") TableSchema schema,
            @JsonProperty("rows") Map<String, String> rows) {
        this.tableName = tableName != null ? tableName.trim() : "";
        this.schema = schema;
        this.rows = new TreeMap<>();
        if (rows != null) {
            this.rows.putAll(rows);
        }
    }

    public String getTableName() {
        return tableName;
    }

    public TableSchema getSchema() {
        return schema;
    }

    public Map<String, String> getRows() {
        return Collections.unmodifiableMap(rows);
    }

    public String getRowHash(String primaryKey) {
        return rows.get(primaryKey);
    }

    @JsonIgnore
    public int getRowCount() {
        return rows.size();
    }

    /**
     * Creates a new TableTree with an added or updated row blob hash.
     */
    public TableTree withRow(String primaryKey, String rowBlobHash) {
        TreeMap<String, String> updatedRows = new TreeMap<>(this.rows);
        updatedRows.put(primaryKey, rowBlobHash);
        return new TableTree(this.tableName, this.schema, updatedRows);
    }

    /**
     * Creates a new TableTree with a removed row.
     */
    public TableTree withoutRow(String primaryKey) {
        TreeMap<String, String> updatedRows = new TreeMap<>(this.rows);
        updatedRows.remove(primaryKey);
        return new TableTree(this.tableName, this.schema, updatedRows);
    }

    @Override
    public byte[] serialize() {
        return JsonUtils.toBytes(this);
    }

    @Override
    public String toString() {
        return "TableTree[name=" + tableName + ", rows=" + rows.size() + ", hash=" + getHash() + "]";
    }
}
