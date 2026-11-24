package com.timegraph.vcs;

import com.timegraph.exceptions.MergeConflictException;
import com.timegraph.exceptions.RepositoryNotInitializedException;
import com.timegraph.exceptions.TableNotFoundException;
import com.timegraph.exceptions.TimeGraphException;
import com.timegraph.model.CommitNode;
import com.timegraph.model.DatabaseSnapshot;
import com.timegraph.model.RowBlob;
import com.timegraph.model.TableTree;
import com.timegraph.storage.ObjectStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Repository manages the VCS state inside .timegraph/:
 * - HEAD pointer file (either "ref: refs/heads/<branch>" or direct commit hash)
 * - refs/heads/<branch> branch pointer files
 * - objects/ directory via ObjectStore
 */
public class Repository {

    private final Path repoRoot;
    private final Path headFile;
    private final Path refsHeadsDir;
    private final ObjectStore objectStore;

    public Repository(Path repoRoot) {
        this.repoRoot = repoRoot;
        this.headFile = repoRoot.resolve("HEAD");
        this.refsHeadsDir = repoRoot.resolve("refs").resolve("heads");
        this.objectStore = new ObjectStore(repoRoot);
    }

    public Path getRepoRoot() {
        return repoRoot;
    }

    public ObjectStore getObjectStore() {
        return objectStore;
    }

    public boolean isInitialized() {
        return Files.exists(repoRoot) && Files.exists(headFile);
    }

    public synchronized void init() {
        try {
            Files.createDirectories(refsHeadsDir);
            objectStore.init();
            if (!Files.exists(headFile)) {
                // Point HEAD to refs/heads/main
                Files.writeString(headFile, "ref: refs/heads/main\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new TimeGraphException("Failed to initialize repository at " + repoRoot, e);
        }
    }

    public synchronized void clearAll() {
        try {
            if (Files.exists(refsHeadsDir)) {
                try (Stream<Path> stream = Files.walk(refsHeadsDir)) {
                    stream.sorted(Comparator.reverseOrder())
                            .filter(p -> !p.equals(refsHeadsDir))
                            .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
                }
            }
            if (Files.exists(headFile)) {
                Files.delete(headFile);
            }
            objectStore.clearCache();
            init();
        } catch (Exception e) {
            throw new TimeGraphException("Failed to clear repository", e);
        }
    }

    private void ensureInitialized() {
        if (!isInitialized()) {
            throw new RepositoryNotInitializedException();
        }
    }

    public synchronized String getCurrentBranch() {
        ensureInitialized();
        try {
            String headContent = Files.readString(headFile, StandardCharsets.UTF_8).trim();
            if (headContent.startsWith("ref: refs/heads/")) {
                return headContent.substring("ref: refs/heads/".length()).trim();
            }
            return "DETACHED";
        } catch (IOException e) {
            throw new TimeGraphException("Failed to read HEAD file", e);
        }
    }

    public synchronized String getHeadCommitHash() {
        ensureInitialized();
        try {
            String headContent = Files.readString(headFile, StandardCharsets.UTF_8).trim();
            if (headContent.startsWith("ref: refs/heads/")) {
                String branch = headContent.substring("ref: refs/heads/".length()).trim();
                Path branchFile = refsHeadsDir.resolve(branch);
                if (Files.exists(branchFile)) {
                    return Files.readString(branchFile, StandardCharsets.UTF_8).trim();
                }
                return null; // Empty branch without commits yet
            }
            return headContent.isEmpty() ? null : headContent;
        } catch (IOException e) {
            throw new TimeGraphException("Failed to resolve HEAD commit hash", e);
        }
    }

    public synchronized CommitNode getHeadCommit() {
        String hash = getHeadCommitHash();
        if (hash == null) return null;
        return objectStore.get(hash, CommitNode.class);
    }

    public synchronized List<String> listBranches() {
        ensureInitialized();
        List<String> branches = new ArrayList<>();
        if (Files.exists(refsHeadsDir)) {
            try (Stream<Path> stream = Files.list(refsHeadsDir)) {
                stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .forEach(branches::add);
            } catch (IOException e) {
                throw new TimeGraphException("Failed to list branches", e);
            }
        }
        return branches;
    }

    public synchronized String getBranchCommitHash(String branch) {
        ensureInitialized();
        Path branchFile = refsHeadsDir.resolve(branch.trim());
        if (!Files.exists(branchFile)) {
            return null;
        }
        try {
            return Files.readString(branchFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new TimeGraphException("Failed to read branch ref for " + branch, e);
        }
    }

    public synchronized void createBranch(String branchName) {
        ensureInitialized();
        String currentHash = getHeadCommitHash();
        if (currentHash == null) {
            throw new TimeGraphException("Cannot create branch '" + branchName + "' on an empty repository without commits.");
        }
        Path branchFile = refsHeadsDir.resolve(branchName.trim());
        if (Files.exists(branchFile)) {
            throw new TimeGraphException("Branch '" + branchName + "' already exists.");
        }
        try {
            Files.writeString(branchFile, currentHash + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TimeGraphException("Failed to write branch file for " + branchName, e);
        }
    }

    public synchronized void checkout(String branchOrCommit) {
        ensureInitialized();
        String target = branchOrCommit.trim();
        Path branchFile = refsHeadsDir.resolve(target);
        try {
            if (Files.exists(branchFile)) {
                // Checkout existing branch
                Files.writeString(headFile, "ref: refs/heads/" + target + "\n", StandardCharsets.UTF_8);
                return;
            }

            // Check if it's a commit hash
            String fullHash = resolveCommitHash(target);
            if (fullHash != null) {
                // Detached HEAD checkout
                Files.writeString(headFile, fullHash + "\n", StandardCharsets.UTF_8);
                return;
            }

            throw new TimeGraphException("Cannot checkout '" + target + "': branch or commit not found.");
        } catch (IOException e) {
            throw new TimeGraphException("Failed to checkout " + target, e);
        }
    }

    public synchronized CommitNode commit(String message, String author, DatabaseSnapshot snapshot, List<String> customParents) {
        ensureInitialized();
        String snapshotHash = objectStore.put(snapshot);

        List<String> parents;
        if (customParents != null) {
            parents = customParents;
        } else {
            String headHash = getHeadCommitHash();
            parents = headHash != null ? List.of(headHash) : Collections.emptyList();
        }

        CommitNode commitNode = new CommitNode(
                parents,
                snapshotHash,
                System.currentTimeMillis(),
                author,
                message
        );

        String commitHash = objectStore.put(commitNode);

        // Update branch ref or detached HEAD
        try {
            String headContent = Files.readString(headFile, StandardCharsets.UTF_8).trim();
            if (headContent.startsWith("ref: refs/heads/")) {
                String branch = headContent.substring("ref: refs/heads/".length()).trim();
                Path branchFile = refsHeadsDir.resolve(branch);
                Files.writeString(branchFile, commitHash + "\n", StandardCharsets.UTF_8);
            } else {
                Files.writeString(headFile, commitHash + "\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new TimeGraphException("Failed to update HEAD to new commit", e);
        }

        return commitNode;
    }

    public synchronized CommitNode commit(String message, String author, DatabaseSnapshot snapshot) {
        return commit(message, author, snapshot, null);
    }

    public String resolveCommitHash(String hashOrBranchOrPrefix) {
        if (hashOrBranchOrPrefix == null || hashOrBranchOrPrefix.isBlank()) return null;
        String query = hashOrBranchOrPrefix.trim();

        if (query.equalsIgnoreCase("HEAD")) {
            return getHeadCommitHash();
        }

        // Branch name?
        String branchHash = getBranchCommitHash(query);
        if (branchHash != null) return branchHash;

        // Exact commit object?
        if (objectStore.exists(query)) {
            CommitNode node = objectStore.get(query, CommitNode.class);
            if (node != null) return query;
        }

        // Prefix match across all known commits in the DAG
        List<CommitNode> allCommits = getAllCommits();
        List<String> matches = allCommits.stream()
                .map(CommitNode::getHash)
                .filter(h -> h.startsWith(query.toLowerCase()))
                .toList();

        if (matches.size() == 1) {
            return matches.getFirst();
        }
        if (matches.size() > 1) {
            throw new TimeGraphException("Ambiguous commit prefix '" + query + "': matches " + matches);
        }

        return null;
    }

    /**
     * Traverses history backward from HEAD.
     */
    public synchronized List<CommitNode> log() {
        return logFromCommit(getHeadCommitHash());
    }

    public List<CommitNode> logFromCommit(String startHash) {
        if (startHash == null) return Collections.emptyList();

        List<CommitNode> history = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();

        queue.add(startHash);
        visited.add(startHash);

        while (!queue.isEmpty()) {
            String currentHash = queue.poll();
            CommitNode node = objectStore.get(currentHash, CommitNode.class);
            if (node != null) {
                history.add(node);
                for (String parent : node.getParentHashes()) {
                    if (visited.add(parent)) {
                        queue.add(parent);
                    }
                }
            }
        }

        // Sort descending by timestamp for clean log view
        history.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return history;
    }

    /**
     * Collects all reachable commits across all branch refs and HEAD.
     */
    public synchronized List<CommitNode> getAllCommits() {
        ensureInitialized();
        Set<String> allHashes = new HashSet<>();
        String headHash = getHeadCommitHash();
        if (headHash != null) allHashes.add(headHash);

        for (String b : listBranches()) {
            String bHash = getBranchCommitHash(b);
            if (bHash != null) allHashes.add(bHash);
        }

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>(allHashes);
        visited.addAll(allHashes);

        List<CommitNode> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String hash = queue.poll();
            CommitNode node = objectStore.get(hash, CommitNode.class);
            if (node != null) {
                result.add(node);
                for (String p : node.getParentHashes()) {
                    if (visited.add(p)) {
                        queue.add(p);
                    }
                }
            }
        }

        result.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return result;
    }

    /**
     * Finds the Lowest Common Ancestor (LCA) between two commits using BFS.
     */
    public String findLowestCommonAncestor(String hash1, String hash2) {
        if (hash1 == null || hash2 == null) return null;
        if (hash1.equals(hash2)) return hash1;

        // Collect all ancestors of hash1 (including hash1)
        Set<String> ancestors1 = new HashSet<>();
        Queue<String> q1 = new ArrayDeque<>();
        q1.add(hash1);
        ancestors1.add(hash1);

        while (!q1.isEmpty()) {
            String curr = q1.poll();
            CommitNode node = objectStore.get(curr, CommitNode.class);
            if (node != null) {
                for (String p : node.getParentHashes()) {
                    if (ancestors1.add(p)) {
                        q1.add(p);
                    }
                }
            }
        }

        // BFS from hash2; the first node found in ancestors1 is the LCA
        Queue<String> q2 = new ArrayDeque<>();
        Set<String> visited2 = new HashSet<>();
        q2.add(hash2);
        visited2.add(hash2);

        while (!q2.isEmpty()) {
            String curr = q2.poll();
            if (ancestors1.contains(curr)) {
                return curr;
            }
            CommitNode node = objectStore.get(curr, CommitNode.class);
            if (node != null) {
                for (String p : node.getParentHashes()) {
                    if (visited2.add(p)) {
                        q2.add(p);
                    }
                }
            }
        }

        return null; // Disjoint graph
    }

    /**
     * Computes the difference in rows for a given table between two commits or branches.
     */
    public List<DiffEntry> diff(String commitRef1, String commitRef2, String tableName) {
        ensureInitialized();
        String hash1 = resolveCommitHash(commitRef1);
        String hash2 = resolveCommitHash(commitRef2);

        if (hash1 == null) throw new TimeGraphException("Unknown commit or branch: " + commitRef1);
        if (hash2 == null) throw new TimeGraphException("Unknown commit or branch: " + commitRef2);

        CommitNode commit1 = objectStore.get(hash1, CommitNode.class);
        CommitNode commit2 = objectStore.get(hash2, CommitNode.class);

        if (commit1 == null) throw new TimeGraphException("Commit node not found for hash: " + hash1);
        if (commit2 == null) throw new TimeGraphException("Commit node not found for hash: " + hash2);

        DatabaseSnapshot snapshot1 = objectStore.get(commit1.getSnapshotHash(), DatabaseSnapshot.class);
        DatabaseSnapshot snapshot2 = objectStore.get(commit2.getSnapshotHash(), DatabaseSnapshot.class);

        return diffSnapshots(snapshot1, snapshot2, tableName);
    }

    public List<DiffEntry> diffSnapshots(DatabaseSnapshot snapshot1, DatabaseSnapshot snapshot2, String tableName) {
        List<DiffEntry> diffEntries = new ArrayList<>();

        String treeHash1 = snapshot1 != null ? snapshot1.getTableTreeHash(tableName) : null;
        String treeHash2 = snapshot2 != null ? snapshot2.getTableTreeHash(tableName) : null;

        if (treeHash1 == null && treeHash2 == null) {
            throw new TableNotFoundException(tableName);
        }

        TableTree tree1 = treeHash1 != null ? objectStore.get(treeHash1, TableTree.class) : null;
        TableTree tree2 = treeHash2 != null ? objectStore.get(treeHash2, TableTree.class) : null;

        Map<String, String> rows1 = tree1 != null ? tree1.getRows() : Collections.emptyMap();
        Map<String, String> rows2 = tree2 != null ? tree2.getRows() : Collections.emptyMap();

        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(rows1.keySet());
        allKeys.addAll(rows2.keySet());

        for (String pk : allKeys) {
            String rHash1 = rows1.get(pk);
            String rHash2 = rows2.get(pk);

            if (rHash1 == null && rHash2 != null) {
                // ADDED
                RowBlob newRow = objectStore.get(rHash2, RowBlob.class);
                diffEntries.add(new DiffEntry(DiffEntry.DiffType.ADDED, tableName, pk, null, newRow != null ? newRow.getValues() : Collections.emptyMap()));
            } else if (rHash1 != null && rHash2 == null) {
                // DELETED
                RowBlob oldRow = objectStore.get(rHash1, RowBlob.class);
                diffEntries.add(new DiffEntry(DiffEntry.DiffType.DELETED, tableName, pk, oldRow != null ? oldRow.getValues() : Collections.emptyMap(), null));
            } else if (rHash1 != null && !rHash1.equals(rHash2)) {
                // CHANGED
                RowBlob oldRow = objectStore.get(rHash1, RowBlob.class);
                RowBlob newRow = objectStore.get(rHash2, RowBlob.class);
                diffEntries.add(new DiffEntry(DiffEntry.DiffType.CHANGED, tableName, pk,
                        oldRow != null ? oldRow.getValues() : Collections.emptyMap(),
                        newRow != null ? newRow.getValues() : Collections.emptyMap()));
            }
        }

        return diffEntries;
    }

    /**
     * Performs a 3-way merge from targetBranch into the current branch.
     * Identifies LCA (Base), compares Base vs Ours vs Theirs row-by-row, auto-merges,
     * and reports structured conflicts if same PK was changed conflictingly.
     */
    public synchronized CommitNode merge(String targetBranch, String author) {
        ensureInitialized();
        String currentBranch = getCurrentBranch();
        if ("DETACHED".equals(currentBranch)) {
            throw new TimeGraphException("Cannot merge while in DETACHED HEAD state. Checkout a branch first.");
        }
        if (currentBranch.equalsIgnoreCase(targetBranch)) {
            throw new TimeGraphException("Cannot merge branch '" + targetBranch + "' into itself.");
        }

        String oursHash = getHeadCommitHash();
        String theirsHash = getBranchCommitHash(targetBranch);

        if (theirsHash == null) {
            throw new TimeGraphException("Target branch '" + targetBranch + "' does not exist.");
        }
        if (oursHash == null) {
            // Empty repo, fast forward
            Path branchFile = refsHeadsDir.resolve(currentBranch);
            try {
                Files.writeString(branchFile, theirsHash + "\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new TimeGraphException("Failed to update branch ref", e);
            }
            return objectStore.get(theirsHash, CommitNode.class);
        }

        if (oursHash.equals(theirsHash)) {
            throw new TimeGraphException("Already up to date with branch '" + targetBranch + "'.");
        }

        String baseHash = findLowestCommonAncestor(oursHash, theirsHash);
        if (baseHash == null) {
            throw new TimeGraphException("No common ancestor found between branch '" + currentBranch + "' and '" + targetBranch + "'.");
        }

        if (baseHash.equals(theirsHash)) {
            throw new TimeGraphException("Already up to date. Current branch is ahead of '" + targetBranch + "'.");
        }

        if (baseHash.equals(oursHash)) {
            // Fast-forward merge!
            Path branchFile = refsHeadsDir.resolve(currentBranch);
            try {
                Files.writeString(branchFile, theirsHash + "\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new TimeGraphException("Failed to fast-forward branch ref", e);
            }
            return objectStore.get(theirsHash, CommitNode.class);
        }

        // True 3-Way Merge
        CommitNode baseCommit = objectStore.get(baseHash, CommitNode.class);
        CommitNode oursCommit = objectStore.get(oursHash, CommitNode.class);
        CommitNode theirsCommit = objectStore.get(theirsHash, CommitNode.class);

        DatabaseSnapshot baseSnapshot = objectStore.get(baseCommit.getSnapshotHash(), DatabaseSnapshot.class);
        DatabaseSnapshot oursSnapshot = objectStore.get(oursCommit.getSnapshotHash(), DatabaseSnapshot.class);
        DatabaseSnapshot theirsSnapshot = objectStore.get(theirsCommit.getSnapshotHash(), DatabaseSnapshot.class);

        Set<String> allTables = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (baseSnapshot != null) allTables.addAll(baseSnapshot.getTables().keySet());
        if (oursSnapshot != null) allTables.addAll(oursSnapshot.getTables().keySet());
        if (theirsSnapshot != null) allTables.addAll(theirsSnapshot.getTables().keySet());

        List<MergeConflictException.ConflictRecord> conflicts = new ArrayList<>();
        Map<String, String> mergedTableTrees = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (String tableName : allTables) {
            String baseTreeHash = baseSnapshot != null ? baseSnapshot.getTableTreeHash(tableName) : null;
            String oursTreeHash = oursSnapshot != null ? oursSnapshot.getTableTreeHash(tableName) : null;
            String theirsTreeHash = theirsSnapshot != null ? theirsSnapshot.getTableTreeHash(tableName) : null;

            TableTree baseTree = baseTreeHash != null ? objectStore.get(baseTreeHash, TableTree.class) : null;
            TableTree oursTree = oursTreeHash != null ? objectStore.get(oursTreeHash, TableTree.class) : null;
            TableTree theirsTree = theirsTreeHash != null ? objectStore.get(theirsTreeHash, TableTree.class) : null;

            // Determine schema
            TableTree schemaSource = oursTree != null ? oursTree : (theirsTree != null ? theirsTree : baseTree);
            if (schemaSource == null) continue;

            Map<String, String> baseRows = baseTree != null ? baseTree.getRows() : Collections.emptyMap();
            Map<String, String> oursRows = oursTree != null ? oursTree.getRows() : Collections.emptyMap();
            Map<String, String> theirsRows = theirsTree != null ? theirsTree.getRows() : Collections.emptyMap();

            Set<String> allPks = new TreeSet<>();
            allPks.addAll(baseRows.keySet());
            allPks.addAll(oursRows.keySet());
            allPks.addAll(theirsRows.keySet());

            TreeMap<String, String> mergedRows = new TreeMap<>();

            for (String pk : allPks) {
                String bHash = baseRows.get(pk);
                String oHash = oursRows.get(pk);
                String tHash = theirsRows.get(pk);

                if (Objects.equals(oHash, tHash)) {
                    // Both kept same or both changed identically
                    if (oHash != null) {
                        mergedRows.put(pk, oHash);
                    }
                } else if (Objects.equals(oHash, bHash)) {
                    // Ours didn't touch it -> accept Theirs (update or delete)
                    if (tHash != null) {
                        mergedRows.put(pk, tHash);
                    }
                } else if (Objects.equals(tHash, bHash)) {
                    // Theirs didn't touch it -> keep Ours (update or delete)
                    if (oHash != null) {
                        mergedRows.put(pk, oHash);
                    }
                } else {
                    // CONFLICT: Both modified the row differently!
                    RowBlob baseBlob = bHash != null ? objectStore.get(bHash, RowBlob.class) : null;
                    RowBlob oursBlob = oHash != null ? objectStore.get(oHash, RowBlob.class) : null;
                    RowBlob theirsBlob = tHash != null ? objectStore.get(tHash, RowBlob.class) : null;

                    conflicts.add(new MergeConflictException.ConflictRecord(
                            tableName,
                            pk,
                            baseBlob != null ? baseBlob.getValues() : null,
                            oursBlob != null ? oursBlob.getValues() : null,
                            theirsBlob != null ? theirsBlob.getValues() : null
                    ));
                }
            }

            TableTree mergedTree = new TableTree(schemaSource.getTableName(), schemaSource.getSchema(), mergedRows);
            String savedTreeHash = objectStore.put(mergedTree);
            mergedTableTrees.put(tableName, savedTreeHash);
        }

        if (!conflicts.isEmpty()) {
            throw new MergeConflictException(
                    "Merge conflict detected between '" + currentBranch + "' and '" + targetBranch + "'. " +
                            conflicts.size() + " conflicting row(s) found.",
                    conflicts
            );
        }

        // All clean! Build merged snapshot and commit
        DatabaseSnapshot mergedSnapshot = new DatabaseSnapshot(mergedTableTrees);
        String commitMsg = "Merge branch '" + targetBranch + "' into " + currentBranch;
        return commit(commitMsg, author != null ? author : "system", mergedSnapshot, List.of(oursHash, theirsHash));
    }
}
