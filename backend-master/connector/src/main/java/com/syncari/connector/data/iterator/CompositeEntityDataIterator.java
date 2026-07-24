package com.syncari.connector.data.iterator;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.Stats;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static java.util.Comparator.comparingLong;

public class CompositeEntityDataIterator implements EntityDataBatchIterator {
    protected Stats stats = new Stats();
    private Queue<EntityDataBatchIterator> storageIterators;
    private int maxResults;
    private int countSoFar = 0;
    EntityDataBatchIterator currentIterator;
    long watermark;

    public CompositeEntityDataIterator(List<? extends EntityDataBatchIterator> storageIterators,int maxResults) {
        this.storageIterators = new ArrayDeque<>(storageIterators);
        this.maxResults = maxResults == 0 ? Integer.MAX_VALUE : maxResults;
        currentIterator = this.storageIterators.poll();
    }

    public boolean hasNext() {
        if (currentIterator == null || countSoFar >= maxResults) return false;
        while (!currentIterator.hasNext() && !storageIterators.isEmpty()) {
            stats = stats.merge(currentIterator.getStats());
            currentIterator = this.storageIterators.poll();
        }
        boolean hasNext = currentIterator.hasNext();
        //last iterator. Merge stats
        if (!hasNext && storageIterators.isEmpty()) {
            stats = stats.merge(currentIterator.getStats());
        }
        return hasNext;
    }

    @Override
    public List<EntityData> next() {
        if (currentIterator == null) return List.of();
        List<EntityData> next = currentIterator.next();
        int sliceLength = Math.min(next.size(),maxResults - countSoFar);
        next =next.subList(0, sliceLength);
        watermark = Math.max(watermark, next.stream().max(comparingLong(EntityData::getLastModified)).map(e->e.getLastModified()).orElse(watermark));
        countSoFar+=next.size();
        return next;
    }

    @Override
    public long getLastWatermark() {
        return watermark;
    }

    @Override
    public Stats getStats() {
        return stats;
    }
}
