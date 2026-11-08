package fm.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class LocalFileStorage implements StorageImplementor {
    private final Path root;

    public LocalFileStorage(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new StorageException("Cannot create storage dir: " + root, e);
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root.normalize()))
            throw new StorageException("Invalid key: " + key);
        return p;
    }

    @Override public void save(String key, String content) {
        try {
            Files.writeString(resolve(key), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("Cannot save: " + key, e);
        }
    }

    @Override public String load(String key) {
        try {
            return Files.readString(resolve(key), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new StorageException("Content not found: " + key);
        } catch (IOException e) {
            throw new StorageException("Cannot read: " + key, e);
        }
    }

    @Override public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Cannot delete: " + key, e);
        }
    }

    @Override public boolean exists(String key) { return Files.exists(resolve(key)); }
}