package com.timegraph.commands;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.timegraph.exceptions.MergeConflictException;
import com.timegraph.vcs.DiffEntry;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommandResult {

    public enum Status {
        SUCCESS,
        ERROR,
        CONFLICT
    }

    private final Status status;
    private final String message;
    private final String tableName;
    private final List<String> columns;
    private final List<Map<String, Object>> rows;
    private final List<DiffEntry> diffEntries;
    private final List<MergeConflictException.ConflictRecord> conflicts;
    private final List<Map<String, Object>> commits;
    private final List<String> branches;
    private final String currentBranch;
    private final String headCommitHash;
    private final Integer affectedRows;
    private final Boolean timeTravel;
    private final String asOfHash;

    private CommandResult(Builder builder) {
        this.status = builder.status;
        this.message = builder.message;
        this.tableName = builder.tableName;
        this.columns = builder.columns;
        this.rows = builder.rows;
        this.diffEntries = builder.diffEntries;
        this.conflicts = builder.conflicts;
        this.commits = builder.commits;
        this.branches = builder.branches;
        this.currentBranch = builder.currentBranch;
        this.headCommitHash = builder.headCommitHash;
        this.affectedRows = builder.affectedRows;
        this.timeTravel = builder.timeTravel;
        this.asOfHash = builder.asOfHash;
    }

    public static Builder success(String message) {
        return new Builder(Status.SUCCESS).message(message);
    }

    public static Builder error(String message) {
        return new Builder(Status.ERROR).message(message);
    }

    public static Builder conflict(String message, List<MergeConflictException.ConflictRecord> conflicts) {
        return new Builder(Status.CONFLICT).message(message).conflicts(conflicts);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public List<DiffEntry> getDiffEntries() {
        return diffEntries;
    }

    public List<MergeConflictException.ConflictRecord> getConflicts() {
        return conflicts;
    }

    public List<Map<String, Object>> getCommits() {
        return commits;
    }

    public List<String> getBranches() {
        return branches;
    }

    public String getCurrentBranch() {
        return currentBranch;
    }

    public String getHeadCommitHash() {
        return headCommitHash;
    }

    public Integer getAffectedRows() {
        return affectedRows;
    }

    public Boolean getTimeTravel() {
        return timeTravel;
    }

    public String getAsOfHash() {
        return asOfHash;
    }

    public static class Builder {
        private final Status status;
        private String message;
        private String tableName;
        private List<String> columns;
        private List<Map<String, Object>> rows;
        private List<DiffEntry> diffEntries;
        private List<MergeConflictException.ConflictRecord> conflicts;
        private List<Map<String, Object>> commits;
        private List<String> branches;
        private String currentBranch;
        private String headCommitHash;
        private Integer affectedRows;
        private Boolean timeTravel;
        private String asOfHash;

        public Builder(Status status) {
            this.status = status;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder columns(List<String> columns) {
            this.columns = columns;
            return this;
        }

        public Builder rows(List<Map<String, Object>> rows) {
            this.rows = rows;
            return this;
        }

        public Builder diffEntries(List<DiffEntry> diffEntries) {
            this.diffEntries = diffEntries;
            return this;
        }

        public Builder conflicts(List<MergeConflictException.ConflictRecord> conflicts) {
            this.conflicts = conflicts;
            return this;
        }

        public Builder commits(List<Map<String, Object>> commits) {
            this.commits = commits;
            return this;
        }

        public Builder branches(List<String> branches) {
            this.branches = branches;
            return this;
        }

        public Builder currentBranch(String currentBranch) {
            this.currentBranch = currentBranch;
            return this;
        }

        public Builder headCommitHash(String headCommitHash) {
            this.headCommitHash = headCommitHash;
            return this;
        }

        public Builder affectedRows(Integer affectedRows) {
            this.affectedRows = affectedRows;
            return this;
        }

        public Builder timeTravel(Boolean timeTravel) {
            this.timeTravel = timeTravel;
            return this;
        }

        public Builder asOfHash(String asOfHash) {
            this.asOfHash = asOfHash;
            return this;
        }

        public CommandResult build() {
            return new CommandResult(this);
        }
    }
}
