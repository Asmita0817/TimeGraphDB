package com.timegraph.engine;

import com.timegraph.exceptions.RepositoryNotInitializedException;
import com.timegraph.exceptions.TableNotFoundException;
import com.timegraph.exceptions.TimeGraphException;
import com.timegraph.model.*;
import com.timegraph.storage.ObjectStore;
import com.timegraph.vcs.DiffEntry;
import com.timegraph.vcs.Repository;

import java.nio.file.Path;
import java.util.*;

/**
 * DatabaseEngine manages the working database snapshot, coordinates queries,
 * mutations, and git-like VCS operations.
 */
public class DatabaseEngine {

    private final Repository repository;
    private final ObjectStore objectStore;
    private DatabaseSnapshot workingSnapshot;
    private boolean isDirty = false;

    public DatabaseEngine(Path repoPath) {
        this.repository = new Repository(repoPath);
        this.objectStore = this.repository.getObjectStore();
        if (this.repository.isInitialized()) {
            loadWorkingSnapshotFromHead();
        }
    }

    public synchronized void init() {
        this.repository.init();
        this.workingSnapshot = new DatabaseSnapshot();
        this.isDirty = false;
    }

    public synchronized void clear() {
        this.repository.clearAll();
        this.workingSnapshot = new DatabaseSnapshot();
        this.isDirty = false;
    }

    public boolean isInitialized() {
        return repository.isInitialized();
    }

    public Repository getRepository() {
        return repository;
    }

    public ObjectStore getObjectStore() {
        return objectStore;
    }

    public synchronized DatabaseSnapshot getWorkingSnapshot() {
        if (workingSnapshot == null) {
            if (isInitialized()) {
                loadWorkingSnapshotFromHead();
            } else {
                throw new RepositoryNotInitializedException();
            }
        }
        return workingSnapshot;
    }

    public synchronized void loadWorkingSnapshotFromHead() {
        CommitNode head = repository.getHeadCommit();
        if (head != null && head.getSnapshotHash() != null) {
            DatabaseSnapshot snapshot = objectStore.get(head.getSnapshotHash(), DatabaseSnapshot.class);
            this.workingSnapshot = snapshot != null ? snapshot : new DatabaseSnapshot();
        } else {
            this.workingSnapshot = new DatabaseSnapshot();
        }
        this.isDirty = false;
    }

    public synchronized boolean isDirty() {
        return isDirty;
    }

    public synchronized void createTable(String tableName, TableSchema schema) {
        DatabaseSnapshot current = getWorkingSnapshot();
        if (current.hasTable(tableName)) {
            throw new TimeGraphException("Table '" + tableName + "' already exists.");
        }

        TableTree newTableTree = new TableTree(tableName, schema, Collections.emptyMap());
        String treeHash = objectStore.put(newTableTree);

        this.workingSnapshot = current.withTable(tableName, treeHash);
        this.isDirty = true;
    }

    public synchronized void upsert(String tableName, Map<String, Object> values) {
        DatabaseSnapshot current = getWorkingSnapshot();
        String treeHash = current.getTableTreeHash(tableName);
        if (treeHash == null) {
            throw new TableNotFoundException(tableName);
        }

        TableTree tableTree = objectStore.get(treeHash, TableTree.class);
        if (tableTree == null) {
            throw new TableNotFoundException(tableName);
        }

        TableSchema schema = tableTree.getSchema();
        String pkCol = schema.getPrimaryKeyColumn();

        // Extract primary key
        Object pkVal = null;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(pkCol)) {
                pkVal = entry.getValue();
                break;
            }
        }

        if (pkVal == null) {
            throw new TimeGraphException("Primary key value for column '" + pkCol + "' must be provided.");
        }

        String pkString = String.valueOf(pkVal).trim();

        // Validate and coerce all fields according to schema
        Map<String, Object> coercedValues = new HashMap<>();
        for (ColumnDef col : schema.getColumns()) {
            Object rawVal = null;
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(col.getName())) {
                    rawVal = entry.getValue();
                    break;
                }
            }
            if (rawVal != null) {
                coercedValues.put(col.getName(), col.getType().coerce(rawVal));
            } else {
                coercedValues.put(col.getName(), null);
            }
        }

        RowBlob rowBlob = new RowBlob(pkString, coercedValues);
        String rowHash = objectStore.put(rowBlob);

        TableTree updatedTree = tableTree.withRow(pkString, rowHash);
        String updatedTreeHash = objectStore.put(updatedTree);

        this.workingSnapshot = current.withTable(tableName, updatedTreeHash);
        this.isDirty = true;
    }

    public synchronized void delete(String tableName, String primaryKey) {
        DatabaseSnapshot current = getWorkingSnapshot();
        String treeHash = current.getTableTreeHash(tableName);
        if (treeHash == null) {
            throw new TableNotFoundException(tableName);
        }

        TableTree tableTree = objectStore.get(treeHash, TableTree.class);
        if (tableTree == null) {
            throw new TableNotFoundException(tableName);
        }

        if (tableTree.getRowHash(primaryKey) == null) {
            throw new TimeGraphException("Row with primary key '" + primaryKey + "' does not exist in table '" + tableName + "'.");
        }

        TableTree updatedTree = tableTree.withoutRow(primaryKey);
        String updatedTreeHash = objectStore.put(updatedTree);

        this.workingSnapshot = current.withTable(tableName, updatedTreeHash);
        this.isDirty = true;
    }

    /**
     * Executes SELECT query with optional time-travel (AS_OF <commitHash>).
     */
    public synchronized SelectResult select(String tableName, String asOfCommitHash) {
        DatabaseSnapshot snapshot;
        boolean isTimeTravel = false;
        String resolvedHash = null;

        if (asOfCommitHash != null && !asOfCommitHash.isBlank()) {
            resolvedHash = repository.resolveCommitHash(asOfCommitHash);
            if (resolvedHash == null) {
                throw new TimeGraphException("Cannot resolve commit for time-travel query: " + asOfCommitHash);
            }
            CommitNode commit = objectStore.get(resolvedHash, CommitNode.class);
            if (commit == null) {
                throw new TimeGraphException("Commit node not found for hash: " + resolvedHash);
            }
            snapshot = objectStore.get(commit.getSnapshotHash(), DatabaseSnapshot.class);
            isTimeTravel = true;
        } else {
            snapshot = getWorkingSnapshot();
        }

        if (snapshot == null || !snapshot.hasTable(tableName)) {
            throw new TableNotFoundException(tableName);
        }

        String treeHash = snapshot.getTableTreeHash(tableName);
        TableTree tableTree = objectStore.get(treeHash, TableTree.class);
        if (tableTree == null) {
            throw new TableNotFoundException(tableName);
        }

        TableSchema schema = tableTree.getSchema();
        List<String> columnNames = schema.getColumns().stream().map(ColumnDef::getName).toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (String rowHash : tableTree.getRows().values()) {
            RowBlob blob = objectStore.get(rowHash, RowBlob.class);
            if (blob != null) {
                rows.add(blob.getValues());
            }
        }

        return new SelectResult(tableName, columnNames, rows, isTimeTravel, resolvedHash);
    }

    public record SelectResult(
            String tableName,
            List<String> columns,
            List<Map<String, Object>> rows,
            boolean timeTravel,
            String asOfHash
    ) {}

    public synchronized List<String> listTables(String asOfCommitHash) {
        DatabaseSnapshot snapshot;
        if (asOfCommitHash != null && !asOfCommitHash.isBlank()) {
            String resolvedHash = repository.resolveCommitHash(asOfCommitHash);
            CommitNode commit = objectStore.get(resolvedHash, CommitNode.class);
            snapshot = commit != null ? objectStore.get(commit.getSnapshotHash(), DatabaseSnapshot.class) : null;
        } else {
            snapshot = getWorkingSnapshot();
        }
        if (snapshot == null) return Collections.emptyList();
        return new ArrayList<>(snapshot.getTables().keySet());
    }

    public synchronized TableSchema getTableSchema(String tableName, String asOfCommitHash) {
        DatabaseSnapshot snapshot;
        if (asOfCommitHash != null && !asOfCommitHash.isBlank()) {
            String resolvedHash = repository.resolveCommitHash(asOfCommitHash);
            CommitNode commit = objectStore.get(resolvedHash, CommitNode.class);
            snapshot = commit != null ? objectStore.get(commit.getSnapshotHash(), DatabaseSnapshot.class) : null;
        } else {
            snapshot = getWorkingSnapshot();
        }
        if (snapshot == null || !snapshot.hasTable(tableName)) return null;

        String treeHash = snapshot.getTableTreeHash(tableName);
        TableTree tree = objectStore.get(treeHash, TableTree.class);
        return tree != null ? tree.getSchema() : null;
    }

    public synchronized CommitNode commit(String message, String author) {
        DatabaseSnapshot current = getWorkingSnapshot();
        CommitNode node = repository.commit(message, author != null ? author : "system", current);
        this.isDirty = false;
        return node;
    }

    public synchronized void createBranch(String branchName) {
        repository.createBranch(branchName);
    }

    public synchronized void checkout(String branchOrCommit) {
        repository.checkout(branchOrCommit);
        loadWorkingSnapshotFromHead();
    }

    public synchronized CommitNode merge(String targetBranch, String author) {
        CommitNode merged = repository.merge(targetBranch, author);
        loadWorkingSnapshotFromHead();
        return merged;
    }

    public List<DiffEntry> diff(String ref1, String ref2, String tableName) {
        return repository.diff(ref1, ref2, tableName);
    }

    public List<CommitNode> log() {
        return repository.log();
    }

    public List<CommitNode> getAllCommits() {
        return repository.getAllCommits();
    }

    public String getCurrentBranch() {
        return repository.getCurrentBranch();
    }

    public String getHeadCommitHash() {
        return repository.getHeadCommitHash();
    }

    public List<String> listBranches() {
        return repository.listBranches();
    }
}
