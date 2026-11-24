package com.timegraph.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class ColumnDef {
    private final String name;
    private final DataType type;
    private final boolean primaryKey;

    @JsonCreator
    public ColumnDef(
            @JsonProperty("name") String name,
            @JsonProperty("type") DataType type,
            @JsonProperty("primaryKey") boolean primaryKey) {
        this.name = name != null ? name.trim() : "";
        this.type = type != null ? type : DataType.VARCHAR;
        this.primaryKey = primaryKey;
    }

    public String getName() {
        return name;
    }

    public DataType getType() {
        return type;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColumnDef columnDef)) return false;
        return primaryKey == columnDef.primaryKey &&
                name.equalsIgnoreCase(columnDef.name) &&
                type == columnDef.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name.toLowerCase(), type, primaryKey);
    }

    @Override
    public String toString() {
        return name + " " + type + (primaryKey ? " PRIMARY KEY" : "");
    }
}
