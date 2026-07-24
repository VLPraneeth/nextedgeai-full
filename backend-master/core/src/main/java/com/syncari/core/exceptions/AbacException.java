package com.syncari.core.exceptions;

public class AbacException extends SyncariValidationException {

    public AbacException(String message, Object... messageParameters) {
        super(message, messageParameters);
    }

    public AbacException(String message) {
      super(message);
    }
}
