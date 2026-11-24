package com.timegraph.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Base abstract class for all content-addressable objects in TimeGraph DB.
 * Every object calculates a deterministic SHA-1 hash of its serialized payload.
 */
public abstract class StorageObject {

    @JsonIgnore
    private volatile String cachedHash;

    /**
     * Serializes this object into a deterministic canonical byte array.
     */
    public abstract byte[] serialize();

    /**
     * Gets the unique SHA-1 hash representing this object's content.
     */
    @JsonIgnore
    public String getHash() {
        if (cachedHash == null) {
            synchronized (this) {
                if (cachedHash == null) {
                    cachedHash = computeHash(serialize());
                }
            }
        }
        return cachedHash;
    }

    /**
     * Invalidate cached hash if object mutation occurs during builder phases.
     */
    protected void invalidateHash() {
        this.cachedHash = null;
    }

    /**
     * Helper to compute a lowercase 40-character hexadecimal SHA-1 string.
     */
    public static String computeHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(content);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }

    public static String computeHash(String text) {
        return computeHash(text.getBytes(StandardCharsets.UTF_8));
    }
}
