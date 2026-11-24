package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;

public class InitCommand implements Command {
    @Override
    public CommandResult execute(DatabaseEngine engine) {
        engine.init();
        return CommandResult.success("Initialized empty TimeGraph repository in .timegraph/")
                .currentBranch(engine.getCurrentBranch())
                .build();
    }
}
