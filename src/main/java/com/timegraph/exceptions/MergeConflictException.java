package com.timegraph.exceptions;

import java.util.List;
import java.util.Map;

public class MergeConflictException extends TimeGraphException {

    public record ConflictRecord(
            String tableName,
            String primaryKey,
            Map<String, Object> baseRow,
            Map<String, Object> oursRow,
            Map<String, Object> theirsRow
    ) {}

    private final List<ConflictRecord> conflicts;

    public MergeConflictException(String message, List<ConflictRecord> conflicts) {
        super(message);
        this.conflicts = conflicts;
    }

    public List<ConflictRecord> getConflicts() {
        return conflicts;
    }
}
