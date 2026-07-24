package com.syncari.core.repositories.customer;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Lock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.Optional;
import java.util.function.Supplier;

import static com.syncari.utils.I18n.i18n;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@Slf4j
public class CustomLockRepoImpl implements CustomLockRepo {
    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Override
    public Optional<Lock> lock(String lockKey, String ownerId, TemporalAmount forceLockTimeout) {
        Optional<Lock> lock = lock(lockKey, ownerId, true);
        if(lock.isEmpty()){
            Optional<Lock> existingLock = findLock(lockKey);
            return existingLock
                    .filter(e1 -> Instant.now().minus(forceLockTimeout).toEpochMilli() > e1.getUpdatedAt().toInstant().toEpochMilli())
                    .flatMap(e->tryLock(lockKey, ownerId, e.getUpdatedAt()));
        }
        return lock;
    }

    @Override
    public Optional<Lock> lock(String lockKey, String ownerId, boolean reacquire) {
        Optional<Lock> lock = tryLock(lockKey, ownerId);
        if(lock.isEmpty() && reacquire){
            log.info("Unable to lock. Trying to reacquire, if caller is the owner of current lock");
            return reacquire(lockKey, ownerId);
        }
        return lock;
    }

    private Optional<Lock> tryLock(String lockKey, String ownerId) {
        try {
            Query query = new Query().addCriteria(where("status").is(Lock.LockStatus.UNLOCKED).and("lockKey").is(lockKey));
            Update update = new Update().set("ownerId", ownerId)
                    .set("status", Lock.LockStatus.LOCKED)
                    .setOnInsert("createdAt", new Date())
                    .set("updatedAt", new Date());
            Lock lock = customerMongoTemplate.findAndModify(query, update,
                    new FindAndModifyOptions().returnNew(true).upsert(true), Lock.class);
            return Optional.ofNullable(lock);
        }catch(org.springframework.dao.DuplicateKeyException e){
            return Optional.empty();
        }
    }

    private Optional<Lock> tryLock(String lockKey, String ownerId, Date updatedAt) {
        try {
            Query query = new Query().addCriteria(where("updatedAt").is(updatedAt).and("lockKey").is(lockKey));
            Update update = new Update().set("ownerId", ownerId)
                    .set("status", Lock.LockStatus.LOCKED)
                    .setOnInsert("createdAt", new Date())
                    .set("updatedAt", new Date());
            Lock lock = customerMongoTemplate.findAndModify(query, update,
                    new FindAndModifyOptions().returnNew(true).upsert(true), Lock.class);
            return Optional.ofNullable(lock);
        }catch(org.springframework.dao.DuplicateKeyException e){
            return Optional.empty();
        }
    }

    @Override
    public boolean unlock(String lockKey, String ownerId) {
        Query query = new Query().addCriteria(where("lockKey")
                .is(lockKey).and("status").is(Lock.LockStatus.LOCKED).and("ownerId").is(ownerId));
        Update update = new Update().set("status", Lock.LockStatus.UNLOCKED)
                .set("updatedAt", new Date());

        Lock lock = customerMongoTemplate.findAndModify(query, update, new FindAndModifyOptions().returnNew(true).upsert(false), Lock.class);
        return lock != null;
    }

    @Override
    public Lock forceLock(String lockKey, String ownerId) {
        Query query = new Query().addCriteria(where("lockKey")
                .is(lockKey));
        Update update = new Update().set("status", Lock.LockStatus.LOCKED).set("ownerId",ownerId).set("updatedAt", new Date());
        var lock = customerMongoTemplate.findAndModify(query, update, new FindAndModifyOptions().returnNew(true).upsert(false), Lock.class);
        assert lock!=null : String.format("Unable to force lock key {} with owner {}",lockKey, ownerId);
        return lock;
    }

    public Optional<Lock> reacquire(String lockKey, String ownerId) {
        Query query = new Query().addCriteria(where("lockKey")
                .is(lockKey).and("ownerId").is(ownerId));
        Update update = new Update().set("status", Lock.LockStatus.LOCKED).set("updatedAt", new Date());
        var lock = customerMongoTemplate.findAndModify(query, update, new FindAndModifyOptions().returnNew(true).upsert(false), Lock.class);
        return Optional.ofNullable(lock);
    }

    private Optional<Lock> findLock(String lockKey) {
        Query query = new Query().addCriteria(where("lockKey")
                .is(lockKey));
        Lock lock = customerMongoTemplate.findOne(query, Lock.class);
        return Optional.ofNullable(lock);
    }

    @Override
    public boolean isLocked(String lockKey) {
        return findLock(lockKey)
                .map(lock -> lock.getStatus() == Lock.LockStatus.LOCKED)
                .orElse(false);
    }

    public void withLock(Runnable runnable, String lockId, String ownerId) {
        withLock(() -> {
            runnable.run();
            return null;
        }, lockId, ownerId);
    }

    public <T> T withLock(Supplier<T> supplier, String lockId, String ownerId) {
        try {
            var locked = lock(lockId, ownerId, Duration.ofMinutes(3));
            if (locked.isEmpty()) {
                throw new SyncariValidationException(i18n("lock_not_acquired", lockId));
            }
            log.info("Acquired Lock with lockId {}", lockId);
            return supplier.get();
        } finally {
            unlock(lockId, ownerId);
            log.info("Released Lock with lockId {}", lockId);
        }
    }

}
