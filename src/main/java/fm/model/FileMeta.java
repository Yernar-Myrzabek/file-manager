package fm.model;

import java.time.LocalDateTime;

public record FileMeta(
        int id,
        String name,
        FileType type,
        long size,
        LocalDateTime createdAt,
        Integer folderId,      // null = корень
        int ownerId,
        String storageKey) {

    public String fullName() {
        return name + "." + type.name().toLowerCase();
    }
}