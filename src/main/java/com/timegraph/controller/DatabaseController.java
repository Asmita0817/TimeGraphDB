package com.timegraph.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.timegraph.commands.Command;
import com.timegraph.commands.CommandResult;
import com.timegraph.engine.DatabaseEngine;
import com.timegraph.exceptions.MergeConflictException;
import com.timegraph.exceptions.TimeGraphException;
import com.timegraph.model.ColumnDef;
import com.timegraph.model.CommitNode;
import com.timegraph.model.TableSchema;
import com.timegraph.parser.SqlAndVcsParser;
import com.timegraph.seed.SampleDataSeeder;
import com.timegraph.storage.JsonUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class DatabaseController {

    private final DatabaseEngine engine;
    private final SqlAndVcsParser parser;
    private final SampleDataSeeder seeder;

    public DatabaseController(DatabaseEngine engine, SqlAndVcsParser parser, SampleDataSeeder seeder) {
        this.engine = engine;
        this.parser = parser;
        this.seeder = seeder;
    }

    public record ExecuteRequest(String command) {}

    public record CommitDto(
            String hash,
            List<String> parentHashes,
            String snapshotHash,
            long timestamp,
            String author,
            String message,
            boolean isMerge
    ) {}

    public record TableDto(
            String name,
            List<ColumnDef> columns,
            String primaryKeyColumn,
            int rowCount,
            List<Map<String, Object>> rows
    ) {}

    public record StateResponse(
            String currentBranch,
            String headCommitHash,
            boolean isDirty,
            List<String> branches,
            List<CommitDto> commits,
            Map<String, TableDto> tables
    ) {}

    public record StorageObjectInfo(
            String hash,
            String type,
            long sizeBytes,
            String preview,
            long lastModified
    ) {}

    public record StorageStatsResponse(
            int totalObjects,
            long totalBytes,
            int commitCount,
            int snapshotCount,
            int tableTreeCount,
            int rowBlobCount,
            int logicalRowsAcrossCommits,
            double deduplicationSavingsPercent,
            List<StorageObjectInfo> objects
    ) {}

    @GetMapping("/state")
    public ResponseEntity<StateResponse> getState() {
        if (!engine.isInitialized()) {
            return ResponseEntity.ok(new StateResponse("NONE", null, false, List.of(), List.of(), Map.of()));
        }

        String currentBranch = engine.getCurrentBranch();
        String headHash = engine.getHeadCommitHash();
        boolean isDirty = engine.isDirty();
        List<String> branches = engine.listBranches();

        List<CommitNode> allCommits = engine.getAllCommits();
        List<CommitDto> commitDtos = allCommits.stream().map(c -> new CommitDto(
                c.getHash(),
                c.getParentHashes(),
                c.getSnapshotHash(),
                c.getTimestamp(),
                c.getAuthor(),
                c.getMessage(),
                c.isMergeCommit()
        )).toList();

        Map<String, TableDto> tables = new LinkedHashMap<>();
        for (String tbl : engine.listTables(null)) {
            TableSchema schema = engine.getTableSchema(tbl, null);
            DatabaseEngine.SelectResult sel = engine.select(tbl, null);
            tables.put(tbl, new TableDto(
                    tbl,
                    schema != null ? schema.getColumns() : List.of(),
                    schema != null ? schema.getPrimaryKeyColumn() : "",
                    sel.rows().size(),
                    sel.rows()
            ));
        }

        return ResponseEntity.ok(new StateResponse(currentBranch, headHash, isDirty, branches, commitDtos, tables));
    }

    @PostMapping("/execute")
    public ResponseEntity<CommandResult> execute(@RequestBody ExecuteRequest request, jakarta.servlet.http.HttpSession session) {
        try {
            if (request.command() == null || request.command().isBlank()) {
                return ResponseEntity.badRequest().body(CommandResult.error("Command cannot be empty.").build());
            }

            Command command = parser.parse(request.command());

            // If a session user is authenticated, automatically associate them as the author of commits/merges
            String loggedUser = session != null ? (String) session.getAttribute("USER") : null;
            if (loggedUser != null && !loggedUser.isBlank()) {
                String fullName = (String) session.getAttribute("FULL_NAME");
                String authorLabel = (fullName != null && !fullName.isBlank()) ? fullName + " (" + loggedUser + ")" : loggedUser;
                if (command instanceof com.timegraph.commands.CommitCommand commitCmd) {
                    command = commitCmd.withAuthor(authorLabel);
                } else if (command instanceof com.timegraph.commands.MergeCommand mergeCmd) {
                    command = mergeCmd.withAuthor(authorLabel);
                }
            }

            CommandResult result = command.execute(engine);
            return ResponseEntity.ok(result);
        } catch (MergeConflictException e) {
            return ResponseEntity.ok(CommandResult.conflict(e.getMessage(), e.getConflicts())
                    .currentBranch(engine.getCurrentBranch())
                    .headCommitHash(engine.getHeadCommitHash())
                    .build());
        } catch (TimeGraphException e) {
            return ResponseEntity.ok(CommandResult.error(e.getMessage())
                    .currentBranch(engine.isInitialized() ? engine.getCurrentBranch() : "UNINITIALIZED")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.ok(CommandResult.error("Execution error: " + e.getMessage())
                    .currentBranch(engine.isInitialized() ? engine.getCurrentBranch() : "UNINITIALIZED")
                    .build());
        }
    }

    @GetMapping("/storage/inspect")
    public ResponseEntity<StorageStatsResponse> inspectStorage() {
        Path objectsDir = engine.getObjectStore().getObjectsDir();
        if (!Files.exists(objectsDir)) {
            return ResponseEntity.ok(new StorageStatsResponse(0, 0, 0, 0, 0, 0, 0, 0.0, List.of()));
        }

        List<StorageObjectInfo> objectList = new ArrayList<>();
        int commitCount = 0;
        int snapshotCount = 0;
        int tableTreeCount = 0;
        int rowBlobCount = 0;
        int totalLogicalRows = 0;
        long totalBytes = 0;

        try (Stream<Path> prefixes = Files.list(objectsDir)) {
            for (Path prefixDir : prefixes.filter(Files::isDirectory).toList()) {
                String prefix = prefixDir.getFileName().toString();
                try (Stream<Path> files = Files.list(prefixDir)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        String remainder = file.getFileName().toString();
                        String hash = prefix + remainder;
                        long size = Files.size(file);
                        totalBytes += size;
                        long lastMod = Files.getLastModifiedTime(file).toMillis();

                        try {
                            byte[] content = Files.readAllBytes(file);
                            JsonNode json = JsonUtils.getMapper().readTree(content);

                            String type = "Blob";
                            String preview = "";

                            if (json.has("parentHashes")) {
                                type = "CommitNode";
                                commitCount++;
                                preview = json.path("message").asText("");
                            } else if (json.has("schema") && json.has("rows")) {
                                type = "TableTree";
                                tableTreeCount++;
                                int rows = json.path("rows").size();
                                totalLogicalRows += rows;
                                preview = "Table: " + json.path("tableName").asText() + " (" + rows + " rows)";
                            } else if (json.has("primaryKey") && json.has("values")) {
                                type = "RowBlob";
                                rowBlobCount++;
                                preview = "PK=" + json.path("primaryKey").asText() + ": " + json.path("values").toString();
                            } else if (json.has("tables")) {
                                type = "DatabaseSnapshot";
                                snapshotCount++;
                                preview = "Tables=" + json.path("tables").size();
                            }

                            objectList.add(new StorageObjectInfo(hash, type, size, preview, lastMod));
                        } catch (Exception ignored) {
                            objectList.add(new StorageObjectInfo(hash, "Unknown", size, "", lastMod));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        objectList.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        double dedup = 0.0;
        if (totalLogicalRows > 0 && totalLogicalRows >= rowBlobCount) {
            dedup = Math.round((1.0 - ((double) rowBlobCount / totalLogicalRows)) * 1000.0) / 10.0;
        }

        return ResponseEntity.ok(new StorageStatsResponse(
                objectList.size(),
                totalBytes,
                commitCount,
                snapshotCount,
                tableTreeCount,
                rowBlobCount,
                totalLogicalRows,
                dedup,
                objectList
        ));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetDatabase() {
        seeder.resetAndSeed(engine);
        return ResponseEntity.ok(Map.of("message", "Database successfully reset and re-seeded with demo data."));
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clearDatabase() {
        engine.clear();
        return ResponseEntity.ok(Map.of("message", "Database cleared! Ready for custom schema creation."));
    }
}
