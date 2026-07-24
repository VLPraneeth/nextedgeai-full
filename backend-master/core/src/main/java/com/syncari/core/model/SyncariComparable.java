package com.syncari.core.model;

public interface SyncariComparable<T> extends Comparable<T> {
    default int compareTo(T o) {
        if (equals(o))
            return 0;
        return 1;
    }
}
