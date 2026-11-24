package com.timegraph.commands;

import com.timegraph.engine.DatabaseEngine;
import com.timegraph.model.CommitNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogCommand implements Command {
    @Override
    public CommandResult execute(DatabaseEngine engine) {
        List<CommitNode> commits = engine.log();
        List<Map<String, Object>> commitList = new ArrayList<>();
        for (CommitNode node : commits) {
            Map<String, Object> map = new HashMap<>();
            map.put("hash", node.getHash());
            map.put("parentHashes", node.getParentHashes());
            map.put("snapshotHash", node.getSnapshotHash());
            map.put("timestamp", node.getTimestamp());
            map.put("author", node.getAuthor());
            map.put("message", node.getMessage());
            map.put("isMerge", node.isMergeCommit());
            commitList.add(map);
        }

        return CommandResult.success("Commit history (" + commitList.size() + " commits)")
                .commits(commitList)
                .currentBranch(engine.getCurrentBranch())
                .headCommitHash(engine.getHeadCommitHash())
                .build();
    }
}
