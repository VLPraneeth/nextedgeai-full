package com.syncari.utils;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class RewindableIterator<T> implements Iterator<T> {
    private final Iterator<T> delegate;
    private boolean collecting = false;
    private int currentIndex = 0;
    private List<T> collectedItems = new LinkedList<>();

    public RewindableIterator(Iterator<T> delegate) {
        this.delegate = delegate;
    }

    public void collect(boolean collecting) {
        this.collecting = collecting;
    }

    public boolean isCollecting() {
        return collecting;
    }

    public void rewind() {
        currentIndex = 0;
    }

    public void rewind(int numRecords) {
        currentIndex = Math.max(currentIndex - numRecords, 0);
    }

    public void reset() {
        currentIndex = 0;
        collectedItems = new LinkedList<>();
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext() || currentIndex < collectedItems.size();
    }

    @Override
    public T next() {
        if (currentIndex >= 0 && currentIndex < collectedItems.size()) {
            T item = collectedItems.get(currentIndex);
            currentIndex++;
            return item;
        }
        T item = delegate.next();
        if (collecting) {
            collectedItems.add(item);
            currentIndex++;
        }
        return item;
    }
}

