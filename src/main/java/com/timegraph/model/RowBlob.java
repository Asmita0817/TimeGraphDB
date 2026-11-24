package com.timegraph.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.timegraph.storage.JsonUtils;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * RowBlob represents a serialized database row/record keyed by primary key.
 * Identical row data produces identical SHA-1 hashes for automatic deduplication.
 */
public class RowBlob extends StorageObject {

    private final String primaryKey;
    private final Map<String, Object> values;

    @JsonCreator
    public RowBlob(
            @JsonProperty("primaryKey") String primaryKey,
            @JsonProperty("values") Map<String, Object> values) {
        this.primaryKey = primaryKey != null ? primaryKey.trim() : "";
        TreeMap<String, Object> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (values != null) {
            sorted.putAll(values);
        }
        this.values = Collections.unmodifiableMap(sorted);
    }

    public String getPrimaryKey() {
        return primaryKey;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public Object getValue(String column) {
        return values.get(column);
    }

    @Override
    public byte[] serialize() {
        return JsonUtils.toBytes(this);
    }

    @Override
    public String toString() {
        return "RowBlob[pk=" + primaryKey + ", hash=" + getHash() + ", values=" + values + "]";
    }
}
