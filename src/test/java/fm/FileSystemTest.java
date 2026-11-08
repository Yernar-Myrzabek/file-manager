package fm;

import fm.db.Database;
import fm.db.MetadataRepository;
import fm.exception.AccessDeniedException;
import fm.exception.NotFoundException;
import fm.exception.ValidationException;
import fm.model.FileMeta;
import fm.model.FileType;
import fm.model.Folder;
import fm.proxy.FileAccessProxy;
import fm.service.FileAccess;
import fm.service.FileManager;
import fm.storage.DatabaseStorage;
import fm.storage.InMemoryStorage;
import fm.storage.LocalFileStorage;
import fm.storage.StorageException;
import fm.storage.StorageImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemTest {
    static final int ALICE = 1, BOB = 2, CAROL = 3;

    enum Kind { MEMORY, LOCAL, DATABASE }

    static class SpyStorage implements StorageImplementor {
        final Map<String, String> data = new HashMap<>();
        int saves, loads, deletes;

        @Override public void save(String key, String content) { saves++; data.put(key, content); }
        @Override public String load(String key) {
            loads++;
            String c = data.get(key);
            if (c == null) throw new StorageException("missing: " + key);
            return c;
        }
        @Override public void delete(String key) { deletes++; data.remove(key); }
        @Override public boolean exists(String key) { return data.containsKey(key); }
    }

    private MetadataRepository repo;
    private FileAccess access;

    private void open(Kind kind, Path dir) {
        Database db = new Database(dir.resolve("test.db"));
        StorageImplementor storage = switch (kind) {
            case MEMORY -> new InMemoryStorage();
            case LOCAL -> new LocalFileStorage(dir.resolve("files"));
            case DATABASE -> new DatabaseStorage(db);
        };
        open(db, storage);
    }

    private void open(Database db, StorageImplementor storage) {
        repo = new MetadataRepository(db);
        access = new FileAccessProxy(new FileManager(repo, storage), repo);
    }

    private FileMeta sharedFile() {
        FileMeta f = access.createFile(ALICE, "doc", FileType.TXT, "secret");
        access.shareFile(ALICE, f.id(), BOB);
        return f;
    }

    // ===================== Bridge =====================

    @ParameterizedTest @EnumSource(Kind.class)
    void T01_createAndRead_roundTrip(Kind kind, @TempDir Path dir) {
        open(kind, dir);
        FileMeta f = access.createFile(ALICE, "notes", FileType.TXT, "hello");
        assertEquals("hello", access.readFile(ALICE, f.id()));
    }

    @Test
    void T02_bridge_managerUsesOnlyStorageInterface(@TempDir Path dir) {
        SpyStorage spy = new SpyStorage();
        open(new Database(dir.resolve("t.db")), spy);
        FileMeta f = access.createFile(ALICE, "x", FileType.TXT, "data");
        assertEquals(1, spy.saves);
        assertEquals("data", access.readFile(ALICE, f.id()));
        assertEquals(1, spy.loads);
    }

    @Test
    void T03_deleteFile_removesContentFromStorage(@TempDir Path dir) {
        SpyStorage spy = new SpyStorage();
        open(new Database(dir.resolve("t.db")), spy);
        FileMeta f = access.createFile(ALICE, "x", FileType.TXT, "data");
        access.deleteFile(ALICE, f.id());
        assertEquals(1, spy.deletes);
        assertTrue(spy.data.isEmpty());
        assertThrows(NotFoundException.class, () -> access.readFile(ALICE, f.id()));
    }

    // ===================== Proxy =====================

    @ParameterizedTest @EnumSource(Kind.class)
    void T04_viewerCanReadAfterShare(Kind kind, @TempDir Path dir) {
        open(kind, dir);
        FileMeta f = sharedFile();
        assertEquals("secret", access.readFile(BOB, f.id()));
    }

    @Test
    void T05_readWithoutPermission_denied_storageNotTouched(@TempDir Path dir) {
        SpyStorage spy = new SpyStorage();
        open(new Database(dir.resolve("t.db")), spy);
        FileMeta f = access.createFile(ALICE, "x", FileType.TXT, "data");
        assertThrows(AccessDeniedException.class, () -> access.readFile(BOB, f.id()));
        assertEquals(0, spy.loads);
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void T06_viewerCannotRename(Kind kind, @TempDir Path dir) {
        open(kind, dir);
        FileMeta f = sharedFile();
        assertThrows(AccessDeniedException.class, () -> access.renameFile(BOB, f.id(), "hacked"));
        assertEquals("doc", access.getFileInfo(ALICE, f.id()).name());
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void T07_viewerCannotDelete(Kind kind, @TempDir Path dir) {
        open(kind, dir);
        FileMeta f = sharedFile();
        assertThrows(AccessDeniedException.class, () -> access.deleteFile(BOB, f.id()));
        assertEquals("secret", access.readFile(ALICE, f.id()));
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void T08_viewerCannotShare(Kind kind, @TempDir Path dir) {
        open(kind, dir);
        FileMeta f = sharedFile();
        assertThrows(AccessDeniedException.class, () -> access.shareFile(BOB, f.id(), CAROL));
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void T09_ownerCanRenameShareDelete(Kind kind, @TempDir Path dir) {
        open(kind, dir);
        FileMeta f = access.createFile(ALICE, "a", FileType.TXT, "x");
        access.shareFile(ALICE, f.id(), BOB);
        access.renameFile(ALICE, f.id(), "b");
        assertEquals("b", access.getFileInfo(ALICE, f.id()).name());
        access.deleteFile(ALICE, f.id());
        assertThrows(NotFoundException.class, () -> access.readFile(ALICE, f.id()));
    }

    @Test
    void T10_unknownUser_denied(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        assertThrows(AccessDeniedException.class, () -> access.createFile(99, "x", FileType.TXT, "c"));
        assertThrows(AccessDeniedException.class, () -> access.listFiles(99));
    }

    // ===================== Files =====================

    @Test
    void T11_listFiles_ownAndShared(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        FileMeta a = access.createFile(ALICE, "a", FileType.TXT, "1");
        access.createFile(ALICE, "b", FileType.TXT, "2");
        access.createFile(BOB, "c", FileType.TXT, "3");
        access.shareFile(ALICE, a.id(), BOB);
        Set<String> bobNames = access.listFiles(BOB).stream().map(FileMeta::name).collect(Collectors.toSet());
        assertEquals(Set.of("a", "c"), bobNames);
        assertEquals(2, access.listFiles(ALICE).size());
    }

    @Test
    void T12_search_caseInsensitive(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        access.createFile(ALICE, "Report", FileType.TXT, "1");
        access.createFile(ALICE, "notes", FileType.TXT, "2");
        var found = access.searchFiles(ALICE, "REP");
        assertEquals(1, found.size());
        assertEquals("Report", found.get(0).name());
    }

    @Test
    void T13_search_blankText_throws(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        assertThrows(ValidationException.class, () -> access.searchFiles(ALICE, "  "));
    }

    @Test
    void T14_fileInfo_sizeInUtf8Bytes(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        FileMeta f = access.createFile(ALICE, "u", FileType.TXT, "h\u00e9llo");
        FileMeta info = access.getFileInfo(ALICE, f.id());
        assertEquals(6, info.size());
        assertEquals(FileType.TXT, info.type());
        assertNotNull(info.createdAt());
    }

    // ===================== Validation =====================

    @Test
    void T15_createFile_invalidName_throws(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        for (String bad : new String[]{"", "   ", "a/b", "x:y", null}) {
            assertThrows(ValidationException.class,
                    () -> access.createFile(ALICE, bad, FileType.TXT, "c"), "name: " + bad);
        }
    }

    @Test
    void T16_createFile_nullTypeOrContent_throws(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        assertThrows(ValidationException.class, () -> access.createFile(ALICE, "a", null, "c"));
        assertThrows(ValidationException.class, () -> access.createFile(ALICE, "a", FileType.TXT, null));
    }

    @Test
    void T17_rename_invalidName_throws(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        FileMeta f = access.createFile(ALICE, "ok", FileType.TXT, "c");
        assertThrows(ValidationException.class, () -> access.renameFile(ALICE, f.id(), "bad/name"));
        assertEquals("ok", access.getFileInfo(ALICE, f.id()).name());
    }

    // ===================== Folders =====================

    @Test
    void T18_folder_createMoveList(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        Folder folder = access.createFolder(ALICE, "docs");
        FileMeta f = access.createFile(ALICE, "a", FileType.TXT, "c");
        access.moveFile(ALICE, f.id(), folder.id());
        assertEquals(1, access.listFolderContents(ALICE, folder.id()).size());
        assertEquals(folder.id(), access.getFileInfo(ALICE, f.id()).folderId());
        access.moveFile(ALICE, f.id(), null);
        assertTrue(access.listFolderContents(ALICE, folder.id()).isEmpty());
    }

    @Test
    void T19_deleteNonEmptyFolder_rejected_thenAllowed(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        Folder folder = access.createFolder(ALICE, "docs");
        FileMeta f = access.createFile(ALICE, "a", FileType.TXT, "c");
        access.moveFile(ALICE, f.id(), folder.id());
        assertThrows(ValidationException.class, () -> access.deleteFolder(ALICE, folder.id()));
        access.moveFile(ALICE, f.id(), null);
        access.deleteFolder(ALICE, folder.id());
        assertTrue(access.listFolders(ALICE).isEmpty());
    }

    @Test
    void T20_otherUserCannotAccessFolder(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        Folder folder = access.createFolder(ALICE, "private");
        assertThrows(AccessDeniedException.class, () -> access.listFolderContents(BOB, folder.id()));
        assertThrows(AccessDeniedException.class, () -> access.deleteFolder(BOB, folder.id()));
    }

    @Test
    void T21_moveIntoForeignFolder_denied(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        Folder bobsFolder = access.createFolder(BOB, "bob");
        FileMeta f = access.createFile(ALICE, "a", FileType.TXT, "c");
        assertThrows(AccessDeniedException.class, () -> access.moveFile(ALICE, f.id(), bobsFolder.id()));
    }

    // ===================== Sharing =====================

    @Test
    void T22_shareTwice_throws(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        FileMeta f = access.createFile(ALICE, "a", FileType.TXT, "c");
        access.shareFile(ALICE, f.id(), BOB);
        assertThrows(ValidationException.class, () -> access.shareFile(ALICE, f.id(), BOB));
    }

    @Test
    void T23_shareWithUnknownUser_throws(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        FileMeta f = access.createFile(ALICE, "a", FileType.TXT, "c");
        assertThrows(NotFoundException.class, () -> access.shareFile(ALICE, f.id(), 99));
    }

    // ===================== Persistence =====================

    @Test
    void T24_persistence_survivesRestart(@TempDir Path dir) {
        Path dbFile = dir.resolve("p.db");
        Path files = dir.resolve("files");
        open(new Database(dbFile), new LocalFileStorage(files));
        FileMeta f = access.createFile(ALICE, "keep", FileType.MD, "persisted");
        access.shareFile(ALICE, f.id(), BOB);

        // "перезапуск": новые объекты на тех же файлах
        open(new Database(dbFile), new LocalFileStorage(files));
        assertEquals("persisted", access.readFile(BOB, f.id()));
        assertEquals(1, access.listFiles(ALICE).size());
    }

    @Test
    void T25_tooLargeContent_throws(@TempDir Path dir) {
        open(Kind.MEMORY, dir);
        String big = "x".repeat(1_000_001);
        assertThrows(ValidationException.class, () -> access.createFile(ALICE, "big", FileType.TXT, big));
    }
}