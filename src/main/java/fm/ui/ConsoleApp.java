package fm.ui;

import fm.db.Database;
import fm.db.MetadataRepository;
import fm.exception.AppException;
import fm.exception.ValidationException;
import fm.model.FileMeta;
import fm.model.FileType;
import fm.model.Folder;
import fm.model.User;
import fm.proxy.FileAccessProxy;
import fm.service.FileAccess;
import fm.service.FileManager;
import fm.storage.DatabaseStorage;
import fm.storage.InMemoryStorage;
import fm.storage.LocalFileStorage;
import fm.storage.StorageImplementor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ConsoleApp {
    private final Scanner in = new Scanner(System.in);
    private Map<Integer, String> userNames;
    private FileAccess access;
    private int currentUser;

    public static void main(String[] args) {
        new ConsoleApp().run();
    }

    private void run() {
        Database db = new Database(Path.of("data/app/app.db"));
        MetadataRepository repo = new MetadataRepository(db);
        userNames = repo.findAllUsers().stream()
                .collect(Collectors.toMap(User::id, User::name, (a, b) -> a, TreeMap::new));

        StorageImplementor storage = chooseStorage(db);
        FileManager manager = new FileManager(repo, storage);
        access = new FileAccessProxy(manager, repo);

        switchUser();
        loop();
    }

    // ---------- меню ----------
    private void loop() {
        while (true) {
            printMenu();
            int choice = askInt("Choose");
            if (choice == 0) {
                System.out.println("Bye.");
                return;
            }
            try {
                handle(choice);
            } catch (AppException e) {
                System.out.println("  ERROR: " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println("\n=== " + userNames.get(currentUser) + " ===");
        System.out.println(" 1) Create file          8) Share file");
        System.out.println(" 2) List my files        9) Create folder");
        System.out.println(" 3) Search by name      10) List my folders");
        System.out.println(" 4) File info           11) Show folder contents");
        System.out.println(" 5) Read file           12) Move file to folder (0 = root)");
        System.out.println(" 6) Rename file         13) Delete folder");
        System.out.println(" 7) Delete file         14) Switch user");
        System.out.println(" 0) Exit");
    }

    private void handle(int choice) {
        switch (choice) {
            case 1 -> createFile();
            case 2 -> printFiles(access.listFiles(currentUser));
            case 3 -> printFiles(access.searchFiles(currentUser, ask("Name contains")));
            case 4 -> System.out.println("  " + describe(access.getFileInfo(currentUser, askInt("File id"))));
            case 5 -> {
                String content = access.readFile(currentUser, askInt("File id"));
                System.out.println("----\n" + content + "\n----");
            }
            case 6 -> {
                int id = askInt("File id");
                access.renameFile(currentUser, id, ask("New name"));
                System.out.println("  Renamed.");
            }
            case 7 -> {
                int id = askInt("File id");
                if (confirm("Delete file " + id + "?")) {
                    access.deleteFile(currentUser, id);
                    System.out.println("  Deleted.");
                }
            }
            case 8 -> {
                int id = askInt("File id");
                userNames.forEach((uid, name) -> System.out.println("  " + uid + ") " + name));
                int target = askInt("Share with user id");
                access.shareFile(currentUser, id, target);
                System.out.println("  Shared with " + userNames.getOrDefault(target, "user " + target) + " (viewer).");
            }
            case 9 -> {
                Folder f = access.createFolder(currentUser, ask("Folder name"));
                System.out.println("  Created folder [" + f.id() + "] " + f.name());
            }
            case 10 -> {
                List<Folder> folders = access.listFolders(currentUser);
                if (folders.isEmpty()) System.out.println("  (none)");
                folders.forEach(f -> System.out.println("  [" + f.id() + "] " + f.name()));
            }
            case 11 -> printFiles(access.listFolderContents(currentUser, askInt("Folder id")));
            case 12 -> {
                int fileId = askInt("File id");
                int folderId = askInt("Folder id (0 = root)");
                access.moveFile(currentUser, fileId, folderId == 0 ? null : folderId);
                System.out.println("  Moved.");
            }
            case 13 -> {
                int id = askInt("Folder id");
                if (confirm("Delete folder " + id + "?")) {
                    access.deleteFolder(currentUser, id);
                    System.out.println("  Folder deleted.");
                }
            }
            case 14 -> switchUser();
            default -> System.out.println("  Unknown option.");
        }
    }

    private void createFile() {
        String name = ask("File name (without extension)");
        FileType type = parseType(ask("Type " + Arrays.toString(FileType.values())));
        System.out.println("Content (finish with a line containing only END):");
        List<String> lines = new ArrayList<>();
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if (line.equals("END")) break;
            lines.add(line);
        }
        FileMeta f = access.createFile(currentUser, name, type, String.join("\n", lines));
        System.out.println("  Created: " + describe(f));
    }

    private FileType parseType(String text) {
        try {
            return FileType.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown file type. Allowed: " + Arrays.toString(FileType.values()));
        }
    }

    // ---------- вывод ----------
    private void printFiles(List<FileMeta> files) {
        if (files.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        files.forEach(f -> System.out.println("  " + describe(f)));
    }

    private String describe(FileMeta f) {
        return String.format("[%d] %s | %d bytes | %s | folder: %s | owner: %s",
                f.id(), f.fullName(), f.size(), f.createdAt(),
                f.folderId() == null ? "root" : f.folderId(),
                userNames.getOrDefault(f.ownerId(), "?"));
    }

    // ---------- выбор хранилища и пользователя ----------
    private StorageImplementor chooseStorage(Database db) {
        System.out.println("Choose storage for file content:");
        System.out.println("  1) Local files (data/app/files)");
        System.out.println("  2) Database (SQLite)");
        System.out.println("  3) In-memory (content is lost on exit!)");
        return switch (askIntInRange("Storage", 1, 3)) {
            case 1 -> new LocalFileStorage(Path.of("data/app/files"));
            case 2 -> new DatabaseStorage(db);
            default -> {
                System.out.println("  Warning: files created now cannot be read after restart.");
                yield new InMemoryStorage();
            }
        };
    }

    private void switchUser() {
        System.out.println("Users:");
        userNames.forEach((id, name) -> System.out.println("  " + id + ") " + name));
        while (true) {
            int id = askInt("Log in as user id");
            if (userNames.containsKey(id)) {
                currentUser = id;
                return;
            }
            System.out.println("  Unknown user id.");
        }
    }

    // ---------- безопасный ввод ----------
    private String ask(String prompt) {
        System.out.print(prompt + ": ");
        if (!in.hasNextLine()) {
            System.out.println("\nInput closed. Bye.");
            System.exit(0);
        }
        return in.nextLine().trim();
    }

    private int askInt(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(ask(prompt));
            } catch (NumberFormatException e) {
                System.out.println("  Please enter a whole number.");
            }
        }
    }

    private int askIntInRange(String prompt, int min, int max) {
        while (true) {
            int v = askInt(prompt);
            if (v >= min && v <= max) return v;
            System.out.println("  Please enter a number from " + min + " to " + max + ".");
        }
    }

    private boolean confirm(String question) {
        return ask(question + " (y/n)").equalsIgnoreCase("y");
    }
}