package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;
import com.timegraph.exceptions.MergeConflictException;
import com.timegraph.model.CommitNode;

public class MergeCommand implements Command {
    private final String targetBranch;
    private final String author;

    public MergeCommand(String targetBranch, String author) {
        this.targetBranch = targetBranch;
        this.author = author != null ? author : "system";
    }

    public MergeCommand(String targetBranch) {
        this(targetBranch, "system");
    }

    public String getAuthor() {
        return author;
    }

    public String getTargetBranch() {
        return targetBranch;
    }

    public MergeCommand withAuthor(String author) {
        return new MergeCommand(this.targetBranch, author);
    }

    @Override
    public CommandResult execute(DatabaseEngine engine) {
        try {
            CommitNode mergeCommit = engine.merge(targetBranch, author);
            return CommandResult.success("Successfully merged branch '" + targetBranch + "' into " + engine.getCurrentBranch() + " (" + mergeCommit.getHash().substring(0, Math.min(7, mergeCommit.getHash().length())) + ").")
                    .headCommitHash(mergeCommit.getHash())
                    .currentBranch(engine.getCurrentBranch())
                    .build();
        } catch (MergeConflictException e) {
            return CommandResult.conflict(e.getMessage(), e.getConflicts())
                    .currentBranch(engine.getCurrentBranch())
                    .headCommitHash(engine.getHeadCommitHash())
                    .build();
        }
    }
}
