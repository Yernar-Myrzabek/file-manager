# Testing

## How to run

```
mvn test
```

or in IntelliJ: right click `src/test/java` -> Run 'All Tests'.
Test class: `fm.FileSystemTest`. Every test uses a temporary directory (`@TempDir`), so a fresh database and storage are created each time and tests do not affect each other.

Tests marked **(x3)** are parameterized and run once for each storage (`MEMORY`, `LOCAL`, `DATABASE`): this proves that behaviour does not depend on the storage implementation (Bridge).

## Automated test cases

| ID | Area | Scenario | Expected result | Result |
|---|---|---|---|---|
| T01 (x3) | Bridge | Alice creates a file and reads it back | Content is identical for every storage | TODO |
| T02 | Bridge | `FileManager` with a spy storage: create, then read | Storage got exactly 1 `save` and 1 `load`; manager works only through the interface | TODO |
| T03 | Bridge / files | Delete a file with a spy storage | Content removed from storage, later read fails with `NotFoundException` | TODO |
| T04 (x3) | Proxy | Owner shares a file, viewer reads it | Viewer gets the content | TODO |
| T05 | Proxy | Bob reads a file that was not shared (spy storage) | `AccessDeniedException`; storage `load` was never called | TODO |
| T06 (x3) | Proxy | Viewer renames a shared file | `AccessDeniedException` | TODO |
| T07 (x3) | Proxy | Viewer deletes a shared file | `AccessDeniedException`, file still readable by owner | TODO |
| T08 (x3) | Proxy | Viewer shares a file with a third user | `AccessDeniedException` | TODO |
| T09 (x3) | Proxy | Owner renames, shares and deletes | All succeed; file is gone afterwards | TODO |
| T10 | Proxy | Unknown user id (99) creates a file and lists files | `AccessDeniedException` | TODO |
| T11 | Files | List files for owner and viewer | Viewer sees own and shared files only; owner sees own files | TODO |
| T12 | Files | Search `"REP"` with files `Report` and `notes` | One result (`Report`), case-insensitive | TODO |
| T13 | Validation | Search with blank text | `ValidationException` | TODO |
| T14 | Files | File info for content with a non-ASCII character | Size is counted in UTF-8 bytes (6), type and creation date are set | TODO |
| T15 | Validation | Create file with names: empty, spaces, `a/b`, `x:y`, null | `ValidationException` for each | TODO |
| T16 | Validation | Create file with null type / null content | `ValidationException` | TODO |
| T17 | Validation | Rename to `bad/name` | `ValidationException`, old name kept | TODO |
| T18 | Folders | Create folder, move file in, list contents, move back to root | Contents are correct at each step | TODO |
| T19 | Folders | Delete non-empty folder, then move file out and delete again | First attempt `ValidationException`, second succeeds | TODO |
| T20 | Folders | Another user lists / deletes Alice's folder | `AccessDeniedException` | TODO |
| T21 | Folders | Alice moves her file into Bob's folder | `AccessDeniedException` | TODO |
| T22 | Sharing | Share the same file with Bob twice | Second attempt `ValidationException` | TODO |
| T23 | Sharing | Share with non-existing user 99 | `NotFoundException` | TODO |
| T24 | Persistence | Create and share a file, create new `Database` and storage objects on the same files ("restart") | Data, permissions and content are still available | TODO |
| T25 | Validation | Create file with content larger than 1 MB | `ValidationException` | TODO |

Expected summary line: `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0` (25 test methods, 6 of them run 3 times).

After running the tests replace `TODO` with `Pass` (or `Fail`) and paste the real summary here:

```
<paste the Maven / IntelliJ test result here>
```

## Demonstration run (`ScenarioDemo`)

The scenario from the assignment, run with two storages (output of the real run):

```
=== Run 1 | storage: LocalFileStorage ===
1. Alice created report.txt (id=1, 23 bytes)
2. Bob reads BEFORE sharing
     DENIED  -> Access denied: Bob cannot READ file 1 (role: none)
3. Alice shared the file with Bob
4. Bob reads shared file
     OK      -> Secret quarterly report
5. Bob renames
     DENIED  -> Access denied: Bob cannot RENAME file 1 (role: VIEWER)
6. Bob deletes
     DENIED  -> Access denied: Bob cannot DELETE file 1 (role: VIEWER)
7. Bob shares with Carol
     DENIED  -> Access denied: Bob cannot SHARE file 1 (role: VIEWER)
8. Carol reads (never shared)
     DENIED  -> Access denied: Carol cannot READ file 1 (role: none)
9. Alice renames
     OK      -> renamed

=== Run 2 | storage: DatabaseStorage ===
(same output)
```

| ID | Check | Result |
|---|---|---|
| D01 | Owner creates and stores a file (both storages) | Pass |
| D02 | Access without permission is denied | Pass |
| D03 | Shared viewer can read | Pass |
| D04 | Viewer cannot rename, delete or share | Pass |
| D05 | Owner can rename | Pass |
| D06 | Same behaviour with two different storages without changing `FileManager` | Pass |

## Manual test cases (console application)

Run `fm.ui.ConsoleApp`, choose storage `1` (local files) and log in as the stated user.

| ID | Steps | Expected result | Result |
|---|---|---|---|
| M01 | Alice: menu 1, name `todo`, type `TXT`, content `buy milk`, `END` | File created, details printed | TODO |
| M02 | Menu 2 | `todo.txt` appears in the list | TODO |
| M03 | Menu 4 with the file id | Name, size (8 bytes), type, date are shown | TODO |
| M04 | Menu 3 with `TO` | `todo.txt` found | TODO |
| M05 | Menu 5 | Content `buy milk` is printed | TODO |
| M06 | Menu 8: share with user 2 (Bob) | Shared successfully | TODO |
| M07 | Menu 14, log in as Bob, menu 5 | Bob reads the content | TODO |
| M08 | Bob: menu 6 (rename), menu 7 (delete), menu 8 (share) | Each gives `Access denied ... (role: VIEWER)` | TODO |
| M09 | Type letters instead of a number in the menu | `Please enter a whole number.` and the question repeats | TODO |
| M10 | Create file with name `a/b` | `ERROR: Name contains forbidden characters ...` | TODO |
| M11 | Create type `EXE` | `ERROR: Unknown file type ...` | TODO |
| M12 | Alice: create folder, move the file into it, try to delete the folder | `ERROR: Folder is not empty ...` | TODO |
| M13 | Move the file back to the root (folder id `0`), delete the folder | Folder deleted | TODO |
| M14 | Exit the program, start it again with the same storage, list files | Files are still there (persistence) | TODO |
| M15 | Read file id 999 | `ERROR: File not found: 999` | TODO |
