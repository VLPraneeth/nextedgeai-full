package com.syncari.core.repositories.customer;

import com.syncari.core.model.Lock;

import java.time.temporal.TemporalAmount;
import java.util.Optional;
import java.util.function.Supplier;

public interface CustomLockRepo {

    Optional<Lock> lock(String lockKey, String ownerId, TemporalAmount forceLockTimeout);

    Optional<Lock> lock(String lockKey, String ownerId, boolean reacquire);
    boolean unlock(String lockKey, String ownerId);

    boolean isLocked(String lockKey);
    Lock forceLock(String lockKey, String ownerId);

    <T> T withLock(Supplier<T> supplier, String lockId, String ownerId);

    void withLock(Runnable runnable, String lockId, String ownerId);
}
