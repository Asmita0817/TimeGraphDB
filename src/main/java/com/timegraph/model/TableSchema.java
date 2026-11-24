package com.timegraph.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TableSchema {
    private final List<ColumnDef> columns;
    private final String primaryKeyColumn;

    @JsonCreator
    public TableSchema(
            @JsonProperty("columns") List<ColumnDef> columns,
            @JsonProperty("primaryKeyColumn") String primaryKeyColumn) {
        this.columns = columns != null ? Collections.unmodifiableList(columns) : Collections.emptyList();
        this.primaryKeyColumn = primaryKeyColumn != null ? primaryKeyColumn.trim() : "";
    }

    public List<ColumnDef> getColumns() {
        return columns;
    }

    public String getPrimaryKeyColumn() {
        return primaryKeyColumn;
    }

    public ColumnDef findColumn(String columnName) {
        if (columnName == null) return null;
        for (ColumnDef col : columns) {
            if (col.getName().equalsIgnoreCase(columnName.trim())) {
                return col;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableSchema that)) return false;
        return Objects.equals(columns, that.columns) &&
                Objects.equals(primaryKeyColumn.toLowerCase(), that.primaryKeyColumn.toLowerCase());
    }

    @Override
    public int hashCode() {
        return Objects.hash(columns, primaryKeyColumn.toLowerCase());
    }
}
