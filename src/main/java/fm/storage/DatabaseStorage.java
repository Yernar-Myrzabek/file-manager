package fm.storage;

import fm.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseStorage implements StorageImplementor {
    private final Database db;

    public DatabaseStorage(Database db) { this.db = db; }

    @Override
    public void save(String key, String content) {
        String sql = "INSERT OR REPLACE INTO file_contents(storage_key, content) VALUES (?, ?)";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, content);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot save: " + key, e);
        }
    }

    @Override
    public String load(String key) {
        String sql = "SELECT content FROM file_contents WHERE storage_key = ?";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new StorageException("Content not found: " + key);
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM file_contents WHERE storage_key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot delete: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM file_contents WHERE storage_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot check: " + key, e);
        }
    }
}