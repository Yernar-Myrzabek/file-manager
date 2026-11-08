package fm.storage;

import fm.exception.AppException;

public class StorageException extends AppException {
    public StorageException(String message) { super(message); }
    public StorageException(String message, Throwable cause) { super(message, cause); }
}