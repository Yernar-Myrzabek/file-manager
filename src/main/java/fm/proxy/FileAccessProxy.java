package fm.proxy;

import fm.db.MetadataRepository;
import fm.exception.AccessDeniedException;
import fm.exception.NotFoundException;
import fm.model.FileMeta;
import fm.model.FileType;
import fm.model.Folder;
import fm.model.Role;
import fm.model.User;
import fm.service.FileAccess;

import java.util.List;
import java.util.Optional;

public class FileAccessProxy implements FileAccess {
    private final FileAccess real;                 // Real Subject (FileManager)
    private final MetadataRepository repo;

    public FileAccessProxy(FileAccess real, MetadataRepository repo) {
        this.real = real;
        this.repo = repo;
    }

    @Override
    public FileMeta createFile(int userId, String name, FileType type, String content) {
        requireUser(userId);
        return real.createFile(userId, name, type, content);
    }

    @Override
    public String readFile(int userId, int fileId) {
        requireFileAccess(userId, fileId, "READ", false);
        return real.readFile(userId, fileId);
    }

    @Override
    public FileMeta getFileInfo(int userId, int fileId) {
        requireFileAccess(userId, fileId, "VIEW INFO of", false);
        return real.getFileInfo(userId, fileId);
    }

    @Override
    public void renameFile(int userId, int fileId, String newName) {
        requireFileAccess(userId, fileId, "RENAME", true);
        real.renameFile(userId, fileId, newName);
    }

    @Override
    public void deleteFile(int userId, int fileId) {
        requireFileAccess(userId, fileId, "DELETE", true);
        real.deleteFile(userId, fileId);
    }

    @Override
    public void shareFile(int userId, int fileId, int targetUserId) {
        requireFileAccess(userId, fileId, "SHARE", true);
        real.shareFile(userId, fileId, targetUserId);
    }

    @Override
    public List<FileMeta> listFiles(int userId) {
        requireUser(userId);
        return real.listFiles(userId);
    }

    @Override
    public List<FileMeta> searchFiles(int userId, String namePart) {
        requireUser(userId);
        return real.searchFiles(userId, namePart);
    }

    @Override
    public Folder createFolder(int userId, String name) {
        requireUser(userId);
        return real.createFolder(userId, name);
    }

    @Override
    public List<Folder> listFolders(int userId) {
        requireUser(userId);
        return real.listFolders(userId);
    }

    @Override
    public List<FileMeta> listFolderContents(int userId, int folderId) {
        requireFolderOwner(userId, folderId, "LIST");
        return real.listFolderContents(userId, folderId);
    }

    @Override
    public void moveFile(int userId, int fileId, Integer folderId) {
        requireFileAccess(userId, fileId, "MOVE", true);
        if (folderId != null) requireFolderOwner(userId, folderId, "MOVE INTO");
        real.moveFile(userId, fileId, folderId);
    }

    @Override
    public void deleteFolder(int userId, int folderId) {
        requireFolderOwner(userId, folderId, "DELETE");
        real.deleteFolder(userId, folderId);
    }

    // ---------- проверки ----------
    private User requireUser(int userId) {
        return repo.findUser(userId)
                .orElseThrow(() -> new AccessDeniedException("Unknown user id: " + userId));
    }

    private void requireFileAccess(int userId, int fileId, String action, boolean ownerOnly) {
        User user = requireUser(userId);
        repo.findFile(fileId).orElseThrow(() -> new NotFoundException("File not found: " + fileId));
        Optional<Role> role = repo.findRole(fileId, userId);
        boolean allowed = role.isPresent() && (!ownerOnly || role.get() == Role.OWNER);
        if (!allowed) {
            throw new AccessDeniedException("Access denied: " + user.name() + " cannot " + action
                    + " file " + fileId + " (role: " + role.map(Enum::name).orElse("none") + ")");
        }
    }

    private void requireFolderOwner(int userId, int folderId, String action) {
        User user = requireUser(userId);
        Folder folder = repo.findFolder(folderId)
                .orElseThrow(() -> new NotFoundException("Folder not found: " + folderId));
        if (folder.ownerId() != userId) {
            throw new AccessDeniedException("Access denied: " + user.name() + " cannot " + action
                    + " folder " + folderId + " (not the owner)");
        }
    }
}