package com.syncari.core.exceptions;

import com.syncari.utils.I18n;

import java.util.Locale;
import java.util.ResourceBundle;

import static com.syncari.utils.I18n.i18n;

public class SyncariValidationException extends RuntimeException {
    private String message;
    private Object[] messageParameters;

    public SyncariValidationException(String message, Object... messageParameters) {
        this.message = message;
        this.messageParameters = messageParameters;
    }

    public SyncariValidationException(String message) {
        this.message = message;
    }

    public String getMessage() {
        if (messageParameters == null) return message;
        return String.format(i18n(message), messageParameters);
    }

    public String getMessage(Locale locale) {
        if (messageParameters == null) return i18n(message);
        return String.format(i18n(message,locale), messageParameters);
    }
}
