package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;
import java.util.List;

public class BranchCommand implements Command {
    private final String branchName;

    public BranchCommand(String branchName) {
        this.branchName = branchName;
    }

    public BranchCommand() {
        this.branchName = null;
    }

    @Override
    public CommandResult execute(DatabaseEngine engine) {
        if (branchName != null && !branchName.isBlank()) {
            engine.createBranch(branchName.trim());
            return CommandResult.success("Created branch '" + branchName.trim() + "'.")
                    .currentBranch(engine.getCurrentBranch())
                    .branches(engine.listBranches())
                    .build();
        } else {
            List<String> branches = engine.listBranches();
            return CommandResult.success("Branches: " + String.join(", ", branches))
                    .currentBranch(engine.getCurrentBranch())
                    .branches(branches)
                    .build();
        }
    }
}
