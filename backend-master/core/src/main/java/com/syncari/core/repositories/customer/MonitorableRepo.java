package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

public interface MonitorableRepo<T> {
    Optional<T> process(String id, Class<T> type);

    Optional<T> checkin(String id, Class<T> type);

    Optional<T> finish(String id, Class<T> type);

    Optional<T> finishWithError(String id, String errorMsg, Class<T> type);

    List<T> getStuck(long maxIdleTimeInMillis, Class<T> type);

    Optional<T> clearTheDead(String id, String errorMsg, Class<T> type);
}
