package fm.storage;

public interface StorageImplementor {
    void save(String key, String content);
    String load(String key);
    void delete(String key);
    boolean exists(String key);
}