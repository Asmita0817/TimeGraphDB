package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;

public class DeleteCommand implements Command {
    private final String tableName;
    private final String primaryKey;

    public DeleteCommand(String tableName, String primaryKey) {
        this.tableName = tableName;
        this.primaryKey = primaryKey;
    }

    @Override
    public CommandResult execute(DatabaseEngine engine) {
        engine.delete(tableName, primaryKey);
        return CommandResult.success("Row with primary key '" + primaryKey + "' deleted from table '" + tableName + "'.")
                .tableName(tableName)
                .affectedRows(1)
                .build();
    }
}
