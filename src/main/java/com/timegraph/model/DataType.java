package com.timegraph.model;

public enum DataType {
    INT,
    VARCHAR,
    DOUBLE,
    BOOLEAN;

    public static DataType fromString(String typeStr) {
        if (typeStr == null) return VARCHAR;
        String upper = typeStr.trim().toUpperCase();
        return switch (upper) {
            case "INT", "INTEGER" -> INT;
            case "VARCHAR", "STRING", "TEXT" -> VARCHAR;
            case "DOUBLE", "FLOAT", "NUMERIC", "DECIMAL" -> DOUBLE;
            case "BOOLEAN", "BOOL" -> BOOLEAN;
            default -> VARCHAR;
        };
    }

    public Object coerce(Object val) {
        if (val == null) return null;
        String str = val.toString().trim();
        if (str.equalsIgnoreCase("null")) return null;

        return switch (this) {
            case INT -> {
                if (val instanceof Number n) yield n.intValue();
                yield Integer.parseInt(str);
            }
            case DOUBLE -> {
                if (val instanceof Number n) yield n.doubleValue();
                yield Double.parseDouble(str);
            }
            case BOOLEAN -> {
                if (val instanceof Boolean b) yield b;
                yield Boolean.parseBoolean(str);
            }
            case VARCHAR -> str;
        };
    }
}
