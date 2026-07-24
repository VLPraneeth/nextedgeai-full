package com.syncari.core.model.misc;

public enum ResyncStatus {
    NEW,
    PROCESSING,
    @Deprecated(forRemoval = true)
    READYTOSYNC,
    @Deprecated(forRemoval = true)
    SYNCING,
    SUCCESS,
    ERROR,
    CANCELLED,
    CANCEL_REQUESTED
}
