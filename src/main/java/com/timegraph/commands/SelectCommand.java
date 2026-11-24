package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;

public class SelectCommand implements Command {
    private final String tableName;
    private final String asOfCommitHash;

    public SelectCommand(String tableName, String asOfCommitHash) {
        this.tableName = tableName;
        this.asOfCommitHash = asOfCommitHash;
    }

    public SelectCommand(String tableName) {
        this(tableName, null);
    }

    @Override
    public CommandResult execute(DatabaseEngine engine) {
        DatabaseEngine.SelectResult result = engine.select(tableName, asOfCommitHash);
        String msg = result.timeTravel()
                ? "Time-travel query on table '" + tableName + "' as of snapshot " + result.asOfHash()
                : "Selected " + result.rows().size() + " row(s) from table '" + tableName + "'";

        return CommandResult.success(msg)
                .tableName(tableName)
                .columns(result.columns())
                .rows(result.rows())
                .timeTravel(result.timeTravel())
                .asOfHash(result.asOfHash())
                .build();
    }
}
