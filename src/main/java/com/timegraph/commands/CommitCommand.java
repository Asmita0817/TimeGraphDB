package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;
import com.timegraph.model.CommitNode;

public class CommitCommand implements Command {
    private final String message;
    private final String author;

    public CommitCommand(String message, String author) {
        this.message = message != null ? message.trim() : "Commit";
        this.author = author != null ? author.trim() : "developer";
    }

    public CommitCommand(String message) {
        this(message, "developer");
    }

    public String getAuthor() {
        return author;
    }

    public String getMessage() {
        return message;
    }

    public CommitCommand withAuthor(String author) {
        return new CommitCommand(this.message, author);
    }

    @Override
    public CommandResult execute(DatabaseEngine engine) {
        CommitNode commit = engine.commit(message, author);
        return CommandResult.success("[" + engine.getCurrentBranch() + " " + commit.getHash().substring(0, Math.min(7, commit.getHash().length())) + "] " + message)
                .headCommitHash(commit.getHash())
                .currentBranch(engine.getCurrentBranch())
                .build();
    }
}
