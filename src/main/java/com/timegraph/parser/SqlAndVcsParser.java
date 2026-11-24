package com.timegraph.parser;

import com.timegraph.commands.*;
import com.timegraph.exceptions.InvalidCommandException;
import com.timegraph.model.ColumnDef;
import com.timegraph.model.DataType;
import com.timegraph.model.TableSchema;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlAndVcsParser {

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "^CREATE\\s+TABLE\\s+([a-zA-Z0-9_]+)\\s*\\((.*)\\)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern UPSERT_PATTERN = Pattern.compile(
            "^(?:UPSERT|INSERT)\\s+(?:INTO\\s+)?([a-zA-Z0-9_]+)\\s*(?:\\(([^)]+)\\))?\\s*VALUES\\s*\\((.+)\\)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern DELETE_PATTERN = Pattern.compile(
            "^DELETE\\s+(?:FROM\\s+)?([a-zA-Z0-9_]+)(?:\\s+WHERE\\s+[a-zA-Z0-9_]+\\s*=\\s*(.+))?$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "^SELECT\\s+(?:\\*\\s+FROM\\s+|FROM\\s+)?([a-zA-Z0-9_]+)(?:\\s+AS_OF\\s+([a-zA-Z0-9_\\-]+))?$",
            Pattern.CASE_INSENSITIVE);

    public Command parse(String input) {
        if (input == null || input.isBlank()) {
            throw new InvalidCommandException("Empty command");
        }

        String trimmed = input.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        String upper = trimmed.toUpperCase();

        if (upper.equals("INIT")) {
            return new InitCommand();
        }

        if (upper.equals("LOG") || upper.equals("GIT LOG")) {
            return new LogCommand();
        }

        if (upper.equals("SHOW TABLES") || upper.equals("TABLES")) {
            return new ShowTablesCommand();
        }

        if (upper.equals("STATUS") || upper.equals("GIT STATUS")) {
            return new StatusCommand();
        }

        if (upper.startsWith("COMMIT")) {
            return parseCommit(trimmed);
        }

        if (upper.startsWith("BRANCH")) {
            return parseBranch(trimmed);
        }

        if (upper.startsWith("CHECKOUT")) {
            return parseCheckout(trimmed);
        }

        if (upper.startsWith("DIFF")) {
            return parseDiff(trimmed);
        }

        if (upper.startsWith("MERGE")) {
            return parseMerge(trimmed);
        }

        if (upper.startsWith("CREATE TABLE")) {
            return parseCreateTable(trimmed);
        }

        if (upper.startsWith("UPSERT") || upper.startsWith("INSERT")) {
            return parseUpsert(trimmed);
        }

        if (upper.startsWith("DELETE")) {
            return parseDelete(trimmed);
        }

        if (upper.startsWith("SELECT")) {
            return parseSelect(trimmed);
        }

        throw new InvalidCommandException("Unrecognized command or SQL statement: " + trimmed);
    }

    private Command parseCommit(String input) {
        String rest = input.substring("COMMIT".length()).trim();
        if (rest.startsWith("-m") || rest.startsWith("-M")) {
            rest = rest.substring(2).trim();
        }
        rest = unquote(rest);
        if (rest.isEmpty()) {
            rest = "Snapshot commit " + System.currentTimeMillis();
        }
        return new CommitCommand(rest);
    }

    private Command parseBranch(String input) {
        String rest = input.substring("BRANCH".length()).trim();
        if (rest.isEmpty()) {
            return new BranchCommand();
        }
        // If "BRANCH create <name>", strip "create"
        if (rest.toUpperCase().startsWith("CREATE ")) {
            rest = rest.substring("CREATE ".length()).trim();
        }
        return new BranchCommand(rest);
    }

    private Command parseCheckout(String input) {
        String rest = input.substring("CHECKOUT".length()).trim();
        if (rest.isEmpty()) {
            throw new InvalidCommandException("CHECKOUT requires branch name or commit hash.");
        }
        if (rest.startsWith("-b ")) {
            // Shortcut for create branch and checkout
            String newBranch = rest.substring(3).trim();
            return new Command() {
                @Override
                public CommandResult execute(com.timegraph.engine.DatabaseEngine engine) {
                    engine.createBranch(newBranch);
                    engine.checkout(newBranch);
                    return CommandResult.success("Switched to a new branch '" + newBranch + "'")
                            .currentBranch(engine.getCurrentBranch())
                            .build();
                }
            };
        }
        return new CheckoutCommand(rest);
    }

    private Command parseDiff(String input) {
        // Syntax: DIFF <ref1> <ref2> <table> OR DIFF <table> <ref1> <ref2>
        String rest = input.substring("DIFF".length()).trim();
        String[] parts = rest.split("\\s+");
        if (parts.length < 3) {
            throw new InvalidCommandException("DIFF syntax: DIFF <commit1/branch1> <commit2/branch2> <tableName>");
        }
        String ref1 = parts[0];
        String ref2 = parts[1];
        String table = parts[2];
        return new DiffCommand(ref1, ref2, table);
    }

    private Command parseMerge(String input) {
        String rest = input.substring("MERGE".length()).trim();
        if (rest.isEmpty()) {
            throw new InvalidCommandException("MERGE requires target branch name: MERGE <targetBranch>");
        }
        return new MergeCommand(rest);
    }

    private Command parseCreateTable(String input) {
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new InvalidCommandException("Invalid CREATE TABLE syntax: " + input);
        }

        String tableName = matcher.group(1).trim();
        String colsPart = matcher.group(2).trim();

        List<String> colDefs = splitArgs(colsPart);
        List<ColumnDef> columns = new ArrayList<>();
        String primaryKeyCol = null;

        for (String colDefStr : colDefs) {
            String def = colDefStr.trim();
            if (def.isEmpty()) continue;

            String[] tokens = def.split("\\s+");
            if (tokens.length < 2) {
                throw new InvalidCommandException("Invalid column definition: " + def);
            }

            String colName = tokens[0].trim();
            DataType type = DataType.fromString(tokens[1].trim());
            boolean isPk = false;

            if (def.toUpperCase().contains("PRIMARY KEY")) {
                isPk = true;
                primaryKeyCol = colName;
            }

            columns.add(new ColumnDef(colName, type, isPk));
        }

        if (primaryKeyCol == null && !columns.isEmpty()) {
            // Default first column as primary key if not explicitly marked
            ColumnDef first = columns.getFirst();
            primaryKeyCol = first.getName();
            columns.set(0, new ColumnDef(first.getName(), first.getType(), true));
        }

        TableSchema schema = new TableSchema(columns, primaryKeyCol);
        return new CreateTableCommand(tableName, schema);
    }

    private Command parseUpsert(String input) {
        Matcher matcher = UPSERT_PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new InvalidCommandException("Invalid UPSERT/INSERT syntax. Expected: UPSERT INTO <table> (col1, col2) VALUES (val1, val2)");
        }

        String tableName = matcher.group(1).trim();
        String colsPart = matcher.group(2);
        String valsPart = matcher.group(3).trim();

        List<String> valuesRaw = splitArgs(valsPart);
        Map<String, Object> valuesMap = new LinkedHashMap<>();

        if (colsPart != null && !colsPart.isBlank()) {
            List<String> colNames = splitArgs(colsPart);
            if (colNames.size() != valuesRaw.size()) {
                throw new InvalidCommandException("Column count (" + colNames.size() + ") does not match value count (" + valuesRaw.size() + ")");
            }
            for (int i = 0; i < colNames.size(); i++) {
                String col = colNames.get(i).trim();
                Object parsedVal = parseLiteral(valuesRaw.get(i).trim());
                valuesMap.put(col, parsedVal);
            }
        } else {
            // No column names given, will use schema order during execution or require column names
            throw new InvalidCommandException("Column names are required: UPSERT INTO " + tableName + " (col1, col2, ...) VALUES (...)");
        }

        return new UpsertCommand(tableName, valuesMap);
    }

    private Command parseDelete(String input) {
        Matcher matcher = DELETE_PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new InvalidCommandException("Invalid DELETE syntax: DELETE FROM <table> WHERE <pkCol> = <value>");
        }

        String tableName = matcher.group(1).trim();
        String valPart = matcher.group(2);
        if (valPart == null || valPart.isBlank()) {
            throw new InvalidCommandException("DELETE statement requires WHERE clause with primary key: DELETE FROM " + tableName + " WHERE id = 1");
        }

        String pkVal = unquote(valPart.trim());
        return new DeleteCommand(tableName, pkVal);
    }

    private Command parseSelect(String input) {
        Matcher matcher = SELECT_PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new InvalidCommandException("Invalid SELECT syntax: SELECT * FROM <table> [AS_OF <commitHash>]");
        }

        String tableName = matcher.group(1).trim();
        String asOfHash = matcher.group(2);

        return new SelectCommand(tableName, asOfHash != null ? asOfHash.trim() : null);
    }

    private String unquote(String str) {
        if (str == null) return "";
        String s = str.trim();
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            if (s.length() >= 2) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    private Object parseLiteral(String token) {
        String s = token.trim();
        if (s.equalsIgnoreCase("null")) {
            return null;
        }
        if (s.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (s.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            return unquote(s);
        }
        // Try Integer
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {}

        // Try Double
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {}

        return s;
    }

    private List<String> splitArgs(String text) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                sb.append(c);
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                sb.append(c);
            } else if (c == ',' && !inSingleQuotes && !inDoubleQuotes) {
                list.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }

        if (sb.length() > 0) {
            list.add(sb.toString().trim());
        }

        return list;
    }
}
