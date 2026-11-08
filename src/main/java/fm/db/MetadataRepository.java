package fm.db;

import fm.exception.DatabaseException;
import fm.model.FileMeta;
import fm.model.FileType;
import fm.model.Folder;
import fm.model.Role;
import fm.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MetadataRepository {

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static final String FILE_COLS =
            "f.id, f.name, f.type, f.size, f.created_at, f.folder_id, f.owner_id, f.storage_key";

    private static final RowMapper<User> USER =
            rs -> new User(rs.getInt("id"), rs.getString("name"));

    private static final RowMapper<Folder> FOLDER =
            rs -> new Folder(rs.getInt("id"), rs.getString("name"), rs.getInt("owner_id"));

    private static final RowMapper<FileMeta> FILE = rs -> {
        int folder = rs.getInt("folder_id");
        Integer folderId = rs.wasNull() ? null : folder;
        return new FileMeta(
                rs.getInt("id"),
                rs.getString("name"),
                FileType.valueOf(rs.getString("type")),
                rs.getLong("size"),
                LocalDateTime.parse(rs.getString("created_at")),
                folderId,
                rs.getInt("owner_id"),
                rs.getString("storage_key"));
    };

    private final Database db;

    public MetadataRepository(Database db) { this.db = db; }

    // ---------- users ----------
    public Optional<User> findUser(int id) {
        return queryOne("SELECT id, name FROM users WHERE id = ?", USER, id);
    }

    public List<User> findAllUsers() {
        return queryList("SELECT id, name FROM users ORDER BY id", USER);
    }

    // ---------- files ----------
    /** Вставляет файл и запись OWNER в одной транзакции. */
    public FileMeta insertFileWithOwner(String name, FileType type, long size,
                                        LocalDateTime createdAt, int ownerId, String storageKey) {
        String insertFile = "INSERT INTO files(name, type, size, created_at, folder_id, owner_id, storage_key) "
                + "VALUES (?, ?, ?, ?, NULL, ?, ?)";
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement ps = c.prepareStatement(insertFile, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.setString(2, type.name());
                    ps.setLong(3, size);
                    ps.setString(4, createdAt.toString());
                    ps.setInt(5, ownerId);
                    ps.setString(6, storageKey);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        id = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO permissions(file_id, user_id, role) VALUES (?, ?, ?)")) {
                    ps.setInt(1, id);
                    ps.setInt(2, ownerId);
                    ps.setString(3, Role.OWNER.name());
                    ps.executeUpdate();
                }
                c.commit();
                return new FileMeta(id, name, type, size, createdAt, null, ownerId, storageKey);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Cannot insert file", e);
        }
    }

    public Optional<FileMeta> findFile(int id) {
        return queryOne("SELECT " + FILE_COLS + " FROM files f WHERE f.id = ?", FILE, id);
    }

    public List<FileMeta> findFilesForUser(int userId) {
        return queryList("SELECT " + FILE_COLS + " FROM files f "
                + "JOIN permissions p ON p.file_id = f.id WHERE p.user_id = ? ORDER BY f.id", FILE, userId);
    }

    public List<FileMeta> findFilesInFolder(int folderId) {
        return queryList("SELECT " + FILE_COLS + " FROM files f WHERE f.folder_id = ? ORDER BY f.id",
                FILE, folderId);
    }

    public int countFilesInFolder(int folderId) {
        return queryOne("SELECT COUNT(*) AS n FROM files WHERE folder_id = ?",
                rs -> rs.getInt("n"), folderId).orElse(0);
    }

    public void renameFile(int id, String newName) {
        update("UPDATE files SET name = ? WHERE id = ?", newName, id);
    }

    public void moveFile(int id, Integer folderId) {
        update("UPDATE files SET folder_id = ? WHERE id = ?", folderId, id);
    }

    public void deleteFile(int id) {
        update("DELETE FROM files WHERE id = ?", id);   // permissions удалятся каскадом
    }

    // ---------- permissions ----------
    public Optional<Role> findRole(int fileId, int userId) {
        return queryOne("SELECT role FROM permissions WHERE file_id = ? AND user_id = ?",
                rs -> Role.valueOf(rs.getString("role")), fileId, userId);
    }

    public void addPermission(int fileId, int userId, Role role) {
        update("INSERT INTO permissions(file_id, user_id, role) VALUES (?, ?, ?)",
                fileId, userId, role.name());
    }

    // ---------- folders ----------
    public Folder insertFolder(String name, int ownerId) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO folders(name, owner_id) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, ownerId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return new Folder(rs.getInt(1), name, ownerId);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Cannot insert folder", e);
        }
    }

    public Optional<Folder> findFolder(int id) {
        return queryOne("SELECT id, name, owner_id FROM folders WHERE id = ?", FOLDER, id);
    }

    public List<Folder> findFoldersByOwner(int ownerId) {
        return queryList("SELECT id, name, owner_id FROM folders WHERE owner_id = ? ORDER BY id",
                FOLDER, ownerId);
    }

    public void deleteFolder(int id) {
        update("DELETE FROM folders WHERE id = ?", id);
    }

    // ---------- helpers ----------
    private <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database query failed", e);
        }
    }

    private <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> list = queryList(sql, mapper, params);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private void update(String sql, Object... params) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Database update failed", e);
        }
    }

    private void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }
}