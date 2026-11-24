package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;
import java.util.Map;

public class UpsertCommand implements Command {
    private final String tableName;
    private final Map<String, Object> values;

    public UpsertCommand(String tableName, Map<String, Object> values) {
        this.tableName = tableName;
        this.values = values;
    }

    @Override
    public CommandResult execute(DatabaseEngine engine) {
        engine.upsert(tableName, values);
        return CommandResult.success("Row upserted into table '" + tableName + "'.")
                .tableName(tableName)
                .affectedRows(1)
                .build();
    }
}
