package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;

public class StatusCommand implements Command {
    @Override
    public CommandResult execute(DatabaseEngine engine) {
        String branch = engine.getCurrentBranch();
        String head = engine.getHeadCommitHash();
        boolean dirty = engine.isDirty();

        String status = "On branch " + branch + "\nHEAD: " + (head != null ? head : "none") + "\n" +
                (dirty ? "Changes staged in working snapshot (run COMMIT to persist)." : "Working snapshot clean.");

        return CommandResult.success(status)
                .currentBranch(branch)
                .headCommitHash(head)
                .build();
    }
}
