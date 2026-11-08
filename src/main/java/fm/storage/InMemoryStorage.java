package fm.storage;

import java.util.HashMap;
import java.util.Map;

public class InMemoryStorage implements StorageImplementor {
    private final Map<String, String> data = new HashMap<>();

    @Override public void save(String key, String content) { data.put(key, content); }

    @Override public String load(String key) {
        String content = data.get(key);
        if (content == null) throw new StorageException("Content not found: " + key);
        return content;
    }

    @Override public void delete(String key) { data.remove(key); }
    @Override public boolean exists(String key) { return data.containsKey(key); }
}