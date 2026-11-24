package com.timegraph.storage;

import com.timegraph.model.StorageObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ObjectStore persists immutable content-addressable objects under
 * .timegraph/objects/<2-char-prefix>/<remaining-hash> using Java NIO.
 */
public class ObjectStore {

    private final Path objectsDir;
    // Fast in-memory cache to avoid disk thrashing for frequently accessed objects
    private final ConcurrentHashMap<String, StorageObject> cache = new ConcurrentHashMap<>();

    public ObjectStore(Path repositoryRoot) {
        this.objectsDir = repositoryRoot.resolve("objects");
    }

    public Path getObjectsDir() {
        return objectsDir;
    }

    public void init() throws IOException {
        Files.createDirectories(objectsDir);
    }

    private Path resolvePath(String hash) {
        if (hash == null || hash.length() < 3) {
            throw new IllegalArgumentException("Invalid object hash: " + hash);
        }
        String prefix = hash.substring(0, 2);
        String remainder = hash.substring(2);
        return objectsDir.resolve(prefix).resolve(remainder);
    }

    public boolean exists(String hash) {
        if (cache.containsKey(hash)) {
            return true;
        }
        Path path = resolvePath(hash);
        return Files.exists(path);
    }

    public String put(StorageObject object) {
        byte[] bytes = object.serialize();
        String hash = object.getHash();

        cache.put(hash, object);

        Path path = resolvePath(hash);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path.getParent());
                Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to persist object with hash: " + hash, e);
            }
        }
        return hash;
    }

    @SuppressWarnings("unchecked")
    public <T extends StorageObject> T get(String hash, Class<T> clazz) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        StorageObject cached = cache.get(hash);
        if (cached != null && clazz.isInstance(cached)) {
            return (T) cached;
        }

        Path path = resolvePath(hash);
        if (!Files.exists(path)) {
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(path);
            T obj = JsonUtils.fromBytes(bytes, clazz);
            cache.put(hash, obj);
            return obj;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read object from disk with hash: " + hash, e);
        }
    }

    public void clearCache() {
        cache.clear();
    }
}
