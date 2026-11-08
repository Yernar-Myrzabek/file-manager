package fm.service;

import fm.db.MetadataRepository;
import fm.exception.NotFoundException;
import fm.exception.ValidationException;
import fm.model.FileMeta;
import fm.model.FileType;
import fm.model.Folder;
import fm.model.Role;
import fm.storage.StorageImplementor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public class FileManager implements FileAccess {
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_CONTENT_BYTES = 1_000_000;
    private static final Pattern FORBIDDEN_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    private final MetadataRepository repo;
    private final StorageImplementor storage;      // <-- МОСТ (композиция)

    public FileManager(MetadataRepository repo, StorageImplementor storage) {
        this.repo = repo;
        this.storage = storage;
    }

    @Override
    public FileMeta createFile(int userId, String name, FileType type, String content) {
        String clean = validateName(name);
        if (type == null) throw new ValidationException("File type is required");
        if (content == null) throw new ValidationException("Content is required");
        long size = content.getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_CONTENT_BYTES) throw new ValidationException("Content is too large (max 1 MB)");

        String key = UUID.randomUUID().toString();
        storage.save(key, content);
        try {
            return repo.insertFileWithOwner(clean, type, size,
                    LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), userId, key);
        } catch (RuntimeException e) {
            storage.delete(key);       // откат: не оставляем "осиротевшее" содержимое
            throw e;
        }
    }

    @Override
    public String readFile(int userId, int fileId) {
        return storage.load(getMeta(fileId).storageKey());
    }

    @Override
    public FileMeta getFileInfo(int userId, int fileId) {
        return getMeta(fileId);
    }

    @Override
    public void renameFile(int userId, int fileId, String newName) {
        getMeta(fileId);
        repo.renameFile(fileId, validateName(newName));
    }

    @Override
    public void deleteFile(int userId, int fileId) {
        FileMeta meta = getMeta(fileId);
        repo.deleteFile(fileId);
        storage.delete(meta.storageKey());
    }

    @Override
    public void shareFile(int userId, int fileId, int targetUserId) {
        getMeta(fileId);
        repo.findUser(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found: " + targetUserId));
        if (repo.findRole(fileId, targetUserId).isPresent())
            throw new ValidationException("User " + targetUserId + " already has access to this file");
        repo.addPermission(fileId, targetUserId, Role.VIEWER);
    }

    @Override
    public List<FileMeta> listFiles(int userId) {
        return repo.findFilesForUser(userId);
    }

    @Override
    public List<FileMeta> searchFiles(int userId, String namePart) {
        if (namePart == null || namePart.isBlank())
            throw new ValidationException("Search text must not be empty");
        String q = namePart.trim().toLowerCase(Locale.ROOT);
        return repo.findFilesForUser(userId).stream()
                .filter(f -> f.name().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    @Override
    public Folder createFolder(int userId, String name) {
        return repo.insertFolder(validateName(name), userId);
    }

    @Override
    public List<Folder> listFolders(int userId) {
        return repo.findFoldersByOwner(userId);
    }

    @Override
    public List<FileMeta> listFolderContents(int userId, int folderId) {
        getFolder(folderId);
        return repo.findFilesInFolder(folderId);
    }

    @Override
    public void moveFile(int userId, int fileId, Integer folderId) {
        FileMeta meta = getMeta(fileId);
        if (folderId != null) {
            Folder folder = getFolder(folderId);
            if (folder.ownerId() != meta.ownerId())
                throw new ValidationException("Folder belongs to another user");
        }
        repo.moveFile(fileId, folderId);
    }

    @Override
    public void deleteFolder(int userId, int folderId) {
        getFolder(folderId);
        int count = repo.countFilesInFolder(folderId);
        if (count > 0)
            throw new ValidationException("Folder is not empty (" + count + " file(s)). "
                    + "Move or delete the files first.");
        repo.deleteFolder(folderId);
    }

    // ---------- private ----------
    private FileMeta getMeta(int fileId) {
        return repo.findFile(fileId).orElseThrow(() -> new NotFoundException("File not found: " + fileId));
    }

    private Folder getFolder(int folderId) {
        return repo.findFolder(folderId).orElseThrow(() -> new NotFoundException("Folder not found: " + folderId));
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) throw new ValidationException("Name must not be empty");
        String n = name.trim();
        if (n.length() > MAX_NAME_LENGTH)
            throw new ValidationException("Name is too long (max " + MAX_NAME_LENGTH + ")");
        if (FORBIDDEN_CHARS.matcher(n).find())
            throw new ValidationException("Name contains forbidden characters: \\ / : * ? \" < > |");
        return n;
    }
}