package com.timegraph.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.timegraph.storage.JsonUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CommitNode is a DAG node storing commit hash, parent hashes (1 for normal, 2 for merge),
 * snapshot hash, timestamp, author, and commit message.
 */
public class CommitNode extends StorageObject {

    private final List<String> parentHashes;
    private final String snapshotHash;
    private final long timestamp;
    private final String author;
    private final String message;

    @JsonCreator
    public CommitNode(
            @JsonProperty("parentHashes") List<String> parentHashes,
            @JsonProperty("snapshotHash") String snapshotHash,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("author") String author,
            @JsonProperty("message") String message) {
        this.parentHashes = parentHashes != null ? Collections.unmodifiableList(new ArrayList<>(parentHashes)) : Collections.emptyList();
        this.snapshotHash = snapshotHash != null ? snapshotHash.trim() : "";
        this.timestamp = timestamp > 0 ? timestamp : Instant.now().toEpochMilli();
        this.author = author != null ? author.trim() : "system";
        this.message = message != null ? message.trim() : "";
    }

    public List<String> getParentHashes() {
        return parentHashes;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getAuthor() {
        return author;
    }

    public String getMessage() {
        return message;
    }

    @JsonIgnore
    public boolean isMergeCommit() {
        return parentHashes != null && parentHashes.size() > 1;
    }

    @Override
    public byte[] serialize() {
        return JsonUtils.toBytes(this);
    }

    @Override
    public String toString() {
        return "CommitNode[hash=" + getHash() + ", parents=" + parentHashes + ", msg='" + message + "']";
    }
}
