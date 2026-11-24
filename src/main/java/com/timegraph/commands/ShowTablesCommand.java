package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;
import java.util.List;

public class ShowTablesCommand implements Command {
    @Override
    public CommandResult execute(DatabaseEngine engine) {
        List<String> tables = engine.listTables(null);
        return CommandResult.success("Tables in database: " + String.join(", ", tables))
                .columns(List.of("TableName"))
                .rows(tables.stream().map(t -> java.util.Map.<String, Object>of("TableName", t)).toList())
                .build();
    }
}
