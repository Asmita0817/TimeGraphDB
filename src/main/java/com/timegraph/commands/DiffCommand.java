package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;
import com.timegraph.vcs.DiffEntry;
import java.util.List;

public class DiffCommand implements Command {
    private final String ref1;
    private final String ref2;
    private final String tableName;

    public DiffCommand(String ref1, String ref2, String tableName) {
        this.ref1 = ref1;
        this.ref2 = ref2;
        this.tableName = tableName;
    }

    @Override
    public CommandResult execute(DatabaseEngine engine) {
        List<DiffEntry> diffs = engine.diff(ref1, ref2, tableName);
        return CommandResult.success("Found " + diffs.size() + " diff entry/entries between " + ref1 + " and " + ref2 + " on table '" + tableName + "'")
                .tableName(tableName)
                .diffEntries(diffs)
                .build();
    }
}
