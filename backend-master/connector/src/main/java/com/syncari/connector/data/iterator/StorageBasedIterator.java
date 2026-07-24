package com.syncari.connector.data.iterator;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.BatchJob;
import com.syncari.connector.data.Stats;
import com.syncari.connector.data.SyncRequest;
import com.syncari.utils.Storage;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
public abstract class StorageBasedIterator implements EntityDataBatchIterator {
    protected static final int DEFAULT_PAGE_SIZE = 1000;
    protected Storage storage;
    protected BatchJob job;
    protected int currentStorageIndex=0;
    protected Optional<InputStream> currentBatch=Optional.empty();
    protected List<EntityData> currentPage = List.of();
    protected int pageSize;
    protected SyncRequest request;
    protected Stats stats = new Stats();
    protected long latestWatermark;
    protected long storageLastModified;
    protected Optional<String> currentURL = Optional.empty();

    public StorageBasedIterator(Storage storage, BatchJob job,  int pageSize, SyncRequest request) {
        this.storage = storage;
        this.job = job;
        this.pageSize = pageSize == 0 ? DEFAULT_PAGE_SIZE : pageSize;
        this.request = request;
        latestWatermark = request.getWatermark().getStart();
    }

    public boolean hasNext() {
        if (!currentPage.isEmpty() || currentStorageIndex < job.getDownloadedFielURLs().size()) return true;
        fetchNextPageWithStats();
        return !currentPage.isEmpty();
    }

    protected void fetchNextPageWithStats(){
        long ms = System.currentTimeMillis();
        fetchNextFile();
        fetchNextPage();
        stats.addLatencyCount(System.currentTimeMillis() - ms,currentPage.size());
    }

    private void fetchNextFile() {
        while(currentBatch.isEmpty() && currentStorageIndex < job.getDownloadedFielURLs().size()){
            job.getDownloadedFileURL(currentStorageIndex).ifPresent(url->{
                currentBatch = Optional.ofNullable(storage.read(url));
                currentURL = Optional.of(url);
                currentBatch.ifPresent(b ->{
                    storageLastModified = storage.lastModified(url);
                });
                log.info("Opening stream for url {}", url);
            });
            currentStorageIndex++;
        }
    }

    protected abstract void fetchNextPage();

    @Override
    public List<EntityData> next() {
        List<EntityData> returning = currentPage;
        while(currentPage.isEmpty() && currentStorageIndex < job.getDownloadedFielURLs().size()) {
            fetchNextFile();
            fetchNextPage();
            returning = currentPage;
        }
        Long currentPageMax = returning.stream().max(Comparator.comparingLong(EntityData::getLastModified)).map(e -> e.getLastModified()).orElse(latestWatermark);
        latestWatermark = Math.max(latestWatermark, currentPageMax);
        currentPage = List.of();
        return returning;
    }

    @Override
    public long getLastWatermark() {
        return latestWatermark;
    }

    @Override
    public Stats getStats() {
        return stats;
    }
}
