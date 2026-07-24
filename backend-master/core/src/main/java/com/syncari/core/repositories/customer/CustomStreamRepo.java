package com.syncari.core.repositories.customer;

import com.syncari.core.model.SyncStream;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CustomStreamRepo {
    Optional<SyncStream> changeStatus(String streamId, String processorId, SyncStream.Status from, SyncStream.Status to);

    Optional<SyncStream> reclaim(String streamId, String processorId, SyncStream.Status status, long maxIdleTimeInMillis);

    Optional<SyncStream> changeStatus(String streamId, String processorId, List<SyncStream.Status> from, SyncStream.Status to);

    Optional<SyncStream> checkin(String streamId, String processorId);

    long relinquish(String processorId, List<String> syncStreamIds);

    List<SyncStream> orphans(long maxIdleTimeInMillis);

    List<SyncStream> stuck(long maxIdleTimeInMillis);

    SyncStream updateLastCleanup(String streamId, Instant lastCleanup);

    List<SyncStream> unclaimed(long maxIdleTimeInMillis);
}
