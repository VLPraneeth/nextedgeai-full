package com.syncari.karibu.rest.exceptions;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class TestConnectionError extends RuntimeException {

    public TestConnectionError(Throwable cause) {
        super(cause);
    }

    public TestConnectionError(String message) {
        super(message);
    }

    public TestConnectionError(Class clazz, String field, String value) {
        super(format(i18n("login_error"), clazz.getSimpleName().replaceAll("Connector", "Synapse"), field, value));
    }
}
