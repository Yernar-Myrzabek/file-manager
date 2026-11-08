package fm.service;

import fm.model.FileMeta;
import fm.model.FileType;
import fm.model.Folder;

import java.util.List;

public interface FileAccess {
    FileMeta createFile(int userId, String name, FileType type, String content);
    String readFile(int userId, int fileId);
    FileMeta getFileInfo(int userId, int fileId);
    void renameFile(int userId, int fileId, String newName);
    void deleteFile(int userId, int fileId);
    void shareFile(int userId, int fileId, int targetUserId);
    List<FileMeta> listFiles(int userId);
    List<FileMeta> searchFiles(int userId, String namePart);

    Folder createFolder(int userId, String name);
    List<Folder> listFolders(int userId);
    List<FileMeta> listFolderContents(int userId, int folderId);
    void moveFile(int userId, int fileId, Integer folderId);   // null = в корень
    void deleteFolder(int userId, int folderId);
}