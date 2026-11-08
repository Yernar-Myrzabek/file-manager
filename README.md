# File Management System (Bridge + Protection Proxy)

Console application in Java 17 that manages files and folders, shares files between simulated users and protects them with roles.
The project demonstrates two design patterns working together:

- **Bridge**: file management logic is separated from where the file content is stored.
- **Protection Proxy**: every request goes through a proxy that checks the user's permissions.

Author: Yernar Myrzabek/250103035

## Technologies

- Java 17, Maven
- SQLite (via JDBC, `sqlite-jdbc`) for metadata and permissions
- JUnit 5 for tests

No server installation is needed: the database is a single file created automatically in `data/`.

## How to run

1. Open the project in IntelliJ IDEA (JDK 17) and let Maven download dependencies.
2. Run one of the entry points:
   - `fm.ui.ConsoleApp`: interactive console menu.
   - `fm.ui.ScenarioDemo`: automatic demonstration of the required scenario (runs it with two different storages).
3. Run tests: `mvn test` (or right click `src/test/java` -> Run All Tests).

## Features

**Files:** create (name, content, type), list, search by name, rename, delete, show info (name, type, size, creation date).
**Folders:** create, list, show contents, move a file into a folder (or back to the root), delete.
**Sharing and roles:** the owner can share a file with another user, who becomes a viewer.

Simulated users (no registration): `1 Alice`, `2 Bob`, `3 Carol`.

### Permissions

| Operation | Owner | Viewer | No access |
|---|---|---|---|
| Read file / view info | yes | yes | denied |
| Rename file | yes | denied | denied |
| Delete file | yes | denied | denied |
| Share file | yes | denied | denied |
| Move file / manage folders | yes (own) | denied | denied |

A role belongs to the pair *(user, file)*, not to the user: Alice can be the owner of one file and a viewer of another. Roles are stored in the `permissions` table.

## Architecture

```
Client (ConsoleApp / ScenarioDemo)
      |
FileAccess (interface)
      |
FileAccessProxy        <- Protection Proxy: checks the role
      |
FileManager            <- Real Subject and Bridge abstraction
      |  composition
StorageImplementor (interface)   <- Bridge implementor
      |
InMemoryStorage | LocalFileStorage | DatabaseStorage
```

Metadata (names, types, sizes, dates, folders, owners, roles) is stored in SQLite by `MetadataRepository`.
File content is stored by a `StorageImplementor`, found by a `storageKey` kept in the metadata.

See the UML class diagram: `docs/uml.png` (source: `docs/uml.puml`).

### Bridge pattern

- **Abstraction:** `FileManager` (file logic: validation, metadata, folders, sharing).
- **Implementor interface:** `StorageImplementor` (`save`, `load`, `delete`, `exists`).
- **Concrete implementors:** `InMemoryStorage`, `LocalFileStorage`, `DatabaseStorage`.
- **Connection:** `FileManager` receives a `StorageImplementor` through its constructor (composition).

The storage can be replaced without changing `FileManager`: `ScenarioDemo` runs the same scenario first with `LocalFileStorage` and then with `DatabaseStorage`, and `ConsoleApp` lets the user pick the storage at startup.

**How Bridge reduces coupling:** `FileManager` depends only on the `StorageImplementor` interface, never on a concrete class (it has no import of `LocalFileStorage` or `DatabaseStorage`). Therefore:

- a new storage (cloud, encrypted, ...) is added by writing one new class, with no edits in `FileManager`, the proxy or the UI;
- storage code and file-management code can change and be tested independently (the tests use a spy storage to prove that `FileManager` only talks to the interface);
- without Bridge we would need a class per combination (`LocalFileManager`, `DatabaseFileManager`, ...) or `if/else` on the storage type inside the file logic.

### Proxy pattern

- **Subject:** `FileAccess` (common interface for file operations).
- **Real Subject:** `FileManager` performs the operations and knows nothing about roles.
- **Protection Proxy:** `FileAccessProxy` implements the same interface, receives the real subject and checks the permission before every restricted call. The client only holds a `FileAccess` created as `new FileAccessProxy(manager, repo)`.

The proxy reads the actual role from the `permissions` table (`MetadataRepository.findRole`). Owner-only operations (rename, delete, share, move) require `OWNER`; reading requires any role. Unauthorized requests throw `AccessDeniedException` and never reach the real service or the storage.

### Required scenario

1. A user creates a file.
2. The content is saved by the selected storage.
3. The owner shares the file with another user.
4. The second user reads it: the request passes the proxy, the proxy checks the role, the file is read through `FileManager` and its storage.
5. Without permission the operation is rejected.

`ScenarioDemo` prints this flow; its output is included in `TESTING.md`.

## Design decision: folders that contain files

**A non-empty folder cannot be deleted.** Trying to do so fails with the message `Folder is not empty (N file(s)). Move or delete the files first.`
Reason: cascading deletion could destroy files by accident (including files that were shared with other users), and refusing is simpler and safer. The user first moves the files out or deletes them.

Other folder rules: folders are private to their owner, a file can only be moved into a folder owned by the file's owner, a file lives in at most one folder (or in the root).

## Validation and error handling

- Names must not be empty, are limited to 100 characters and must not contain `\ / : * ? " < > |`.
- File type must be one of `TXT, MD, JSON, CSV`; content is limited to 1 MB.
- Numeric input in the console is re-requested until valid; unknown ids give `NotFoundException`; sharing twice or with an unknown user is rejected.
- All application errors extend `AppException` (`ValidationException`, `NotFoundException`, `AccessDeniedException`, `DatabaseException`, `StorageException`) and are shown to the user as readable messages instead of stack traces.
- File creation is transactional on the database side (file row and owner permission are inserted together); if saving metadata fails, the already stored content is removed.

## Persistence

Metadata and permissions are always stored in SQLite (`data/app/app.db`). Content is persistent with `LocalFileStorage` (`data/app/files`) and `DatabaseStorage`.
`InMemoryStorage` is for tests and demonstration of Bridge: its content is lost when the program exits (metadata stays), so use it only in a single run.

## Project structure

```
src/main/java/fm
  exception/   AppException and its subclasses
  model/       FileMeta, Folder, User (records), FileType, Role (enums)
  db/          Database (schema, connections), MetadataRepository (SQL)
  storage/     StorageImplementor + InMemoryStorage, LocalFileStorage, DatabaseStorage
  service/     FileAccess (interface), FileManager
  proxy/       FileAccessProxy
  ui/          ConsoleApp, ScenarioDemo
src/test/java/fm  FileSystemTest
```

## Testing

Automated tests (JUnit 5) cover Bridge (all storages, spy storage), Proxy (every role/operation), files, folders, validation and persistence. Test cases and results are documented in `TESTING.md`.

## Known limitations

- Users are a predefined list; there is no authentication.
- Sharing always gives the `VIEWER` role.
- Content is text only (stored as UTF-8 strings), up to 1 MB.
- Folders are flat (no nested folders).
