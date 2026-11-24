package com.timegraph.exceptions;

public class TableNotFoundException extends TimeGraphException {
    public TableNotFoundException(String tableName) {
        super("Table not found: " + tableName);
    }
}
