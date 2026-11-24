package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;

public class CheckoutCommand implements Command {
    private final String target;

    public CheckoutCommand(String target) {
        this.target = target;
    }

    @Override
    public CommandResult execute(DatabaseEngine engine) {
        engine.checkout(target);
        String currentBranch = engine.getCurrentBranch();
        String headHash = engine.getHeadCommitHash();
        String displayHash = headHash != null ? headHash.substring(0, Math.min(7, headHash.length())) : "empty";

        String msg = "DETACHED".equals(currentBranch)
                ? "Switched to detached HEAD at " + displayHash
                : "Switched to branch '" + currentBranch + "' (" + displayHash + ")";

        return CommandResult.success(msg)
                .currentBranch(currentBranch)
                .headCommitHash(headHash)
                .build();
    }
}
