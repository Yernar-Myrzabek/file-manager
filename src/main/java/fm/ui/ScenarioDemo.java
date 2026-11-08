package fm.ui;

import fm.db.Database;
import fm.db.MetadataRepository;
import fm.exception.AccessDeniedException;
import fm.exception.AppException;
import fm.model.FileMeta;
import fm.model.FileType;
import fm.proxy.FileAccessProxy;
import fm.service.FileAccess;
import fm.service.FileManager;
import fm.storage.DatabaseStorage;
import fm.storage.LocalFileStorage;
import fm.storage.StorageImplementor;

import java.nio.file.Path;
import java.util.function.Supplier;

public class ScenarioDemo {
    private static final int ALICE = 1, BOB = 2, CAROL = 3;

    public static void main(String[] args) {
        Database db1 = new Database(Path.of("data/demo/local.db"));
        scenario("Run 1", db1, new LocalFileStorage(Path.of("data/demo/files")));

        Database db2 = new Database(Path.of("data/demo/dbstorage.db"));
        scenario("Run 2", db2, new DatabaseStorage(db2));
    }

    static void scenario(String title, Database db, StorageImplementor storage) {
        MetadataRepository repo = new MetadataRepository(db);
        FileManager manager = new FileManager(repo, storage);
        FileAccess access = new FileAccessProxy(manager, repo);

        System.out.println("\n=== " + title + " | storage: " + storage.getClass().getSimpleName() + " ===");

        FileMeta f = access.createFile(ALICE, "report", FileType.TXT, "Secret quarterly report");
        System.out.println("1. Alice created " + f.fullName() + " (id=" + f.id() + ", " + f.size() + " bytes)");

        attempt("2. Bob reads BEFORE sharing", () -> access.readFile(BOB, f.id()));

        access.shareFile(ALICE, f.id(), BOB);
        System.out.println("3. Alice shared the file with Bob");

        attempt("4. Bob reads shared file", () -> access.readFile(BOB, f.id()));
        attempt("5. Bob renames", () -> { access.renameFile(BOB, f.id(), "hacked"); return "renamed"; });
        attempt("6. Bob deletes", () -> { access.deleteFile(BOB, f.id()); return "deleted"; });
        attempt("7. Bob shares with Carol", () -> { access.shareFile(BOB, f.id(), CAROL); return "shared"; });
        attempt("8. Carol reads (never shared)", () -> access.readFile(CAROL, f.id()));
        attempt("9. Alice renames", () -> { access.renameFile(ALICE, f.id(), "report-v2"); return "renamed"; });
    }

    private static void attempt(String label, Supplier<Object> action) {
        try {
            Object result = action.get();
            System.out.println(label + "\n     OK      -> " + result);
        } catch (AccessDeniedException e) {
            System.out.println(label + "\n     DENIED  -> " + e.getMessage());
        } catch (AppException e) {
            System.out.println(label + "\n     ERROR   -> " + e.getMessage());
        }
    }
}