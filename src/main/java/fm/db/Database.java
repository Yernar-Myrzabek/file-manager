package fm.db;

import fm.exception.DatabaseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private final String url;

    public Database(Path dbFile) {
        Path abs = dbFile.toAbsolutePath();
        try {
            Files.createDirectories(abs.getParent());
        } catch (IOException e) {
            throw new DatabaseException("Cannot create DB directory", e);
        }
        this.url = "jdbc:sqlite:" + abs;
        init();
    }

    public Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return c;
    }

    private void init() {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)");
            st.execute("CREATE TABLE IF NOT EXISTS folders ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, "
                    + "owner_id INTEGER NOT NULL REFERENCES users(id))");
            st.execute("CREATE TABLE IF NOT EXISTS files ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL, "
                    + "size INTEGER NOT NULL, created_at TEXT NOT NULL, "
                    + "folder_id INTEGER REFERENCES folders(id), "
                    + "owner_id INTEGER NOT NULL REFERENCES users(id), "
                    + "storage_key TEXT NOT NULL UNIQUE)");
            st.execute("CREATE TABLE IF NOT EXISTS permissions ("
                    + "file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE, "
                    + "user_id INTEGER NOT NULL REFERENCES users(id), "
                    + "role TEXT NOT NULL, PRIMARY KEY (file_id, user_id))");
            st.execute("CREATE TABLE IF NOT EXISTS file_contents ("
                    + "storage_key TEXT PRIMARY KEY, content TEXT NOT NULL)");
            st.execute("INSERT OR IGNORE INTO users(id, name) VALUES (1,'Alice'),(2,'Bob'),(3,'Carol')");
        } catch (SQLException e) {
            throw new DatabaseException("Cannot initialize database", e);
        }
    }
}