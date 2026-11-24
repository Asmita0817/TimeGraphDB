package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;
import com.timegraph.model.TableSchema;

public class CreateTableCommand implements Command {
    private final String tableName;
    private final TableSchema schema;

    public CreateTableCommand(String tableName, TableSchema schema) {
        this.tableName = tableName;
        this.schema = schema;
    }

    @Override
    public CommandResult execute(DatabaseEngine engine) {
        engine.createTable(tableName, schema);
        return CommandResult.success("Table '" + tableName + "' created successfully.")
                .tableName(tableName)
                .columns(schema.getColumns().stream().map(c -> c.getName() + " " + c.getType()).toList())
                .build();
    }
}
