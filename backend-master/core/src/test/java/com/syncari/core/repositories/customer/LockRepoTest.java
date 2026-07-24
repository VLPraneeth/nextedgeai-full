package com.syncari.core.repositories.customer;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Lock;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.Period;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class LockRepoTest extends AbstractSyncariTest {
    @Autowired
    LockRepo lockRepo;
    @Autowired
    MongoTemplate customerMongoTemplate;
    @Test
    public void simpleLock() {
        Optional<Lock> lock = lockRepo.lock("key1", "ownerId",false);
        assertEquals(lock.get().getLockKey(),"key1");
        assertEquals(lock.get().getOwnerId(),"ownerId");
        assertEquals(lock.get().getStatus(),Lock.LockStatus.LOCKED);

        assertTrue(lockRepo.lock("key1", "ownerId",false).isEmpty());
    }
    @Test
    public void simpleLockWithReacquire() {
        Optional<Lock> lock = lockRepo.lock("key1", "ownerId",false);
        assertEquals(lock.get().getLockKey(),"key1");
        assertEquals(lock.get().getOwnerId(),"ownerId");
        assertEquals(lock.get().getStatus(),Lock.LockStatus.LOCKED);

        assertTrue(lockRepo.lock("key1", "ownerId",false).isEmpty());
        assertTrue(lockRepo.lock("key1", "ownerId",true).isPresent());
    }

    @Test
    public void lockAndUnlock() {
        Optional<Lock> lock = lockRepo.lock("key1", "ownerId",false);
        assertTrue(lock.isPresent());
        assertFalse(lockRepo.unlock("key1", "ownerId2"));
        assertFalse(lockRepo.unlock("key2", "ownerId"));
        assertTrue(lockRepo.unlock("key1", "ownerId"));
    }

    @Test
    public void timeBasedLock() {
        Optional<Lock> lock = lockRepo.lock("key1", "ownerId",false);
        assertTrue(lock.isPresent());
        //2 days old -acquire lock
        Instant instant1 = Instant.now().minusSeconds(60 * 60*24*2);
        Date date1 = new Date(instant1.toEpochMilli());
        //20 hrs old - no lock
        Instant instant2 = Instant.now().minusSeconds(60 * 60*5);
        Date date2 = new Date(instant2.toEpochMilli());

        customerMongoTemplate.updateFirst(new Query(Criteria.where("lockKey").is("key1")),new Update().set("updatedAt",date2),"lock");
        assertFalse(lockRepo.lock("key1", "ownerId2", Period.ofDays(1)).isPresent());
        customerMongoTemplate.updateFirst(new Query(Criteria.where("lockKey").is("key1")),new Update().set("updatedAt",date1),"lock");
        assertTrue(lockRepo.lock("key1", "ownerId2", Period.ofDays(1)).isPresent());
    }

    @Test
    public void multiThreadedLockUnlock() {
        final AtomicInteger releasedCounter = new AtomicInteger();
        final AtomicInteger acquiredCounter = new AtomicInteger();
        var org = SyncariContext.getOrganziation();
        var instance = SyncariContext.getInstance();
        var user = SyncariContext.getUser();
        Runnable r = () -> {
            SyncariContext.runWithContext(org,instance,user,()-> {
                try {
                    Thread.sleep(new Random().nextInt(5000));
                }catch (Exception e){}
                        Optional<Lock> lock = lockRepo.lock("key1", "ownerId",false);
                        lock.ifPresent(l ->acquiredCounter.getAndIncrement());

                        lock.ifPresent(l -> {
                            assertTrue(lockRepo.unlock("key1", "ownerId"));
                            releasedCounter.getAndIncrement();
                            System.out.println(String.format("Thread %s locked and released ", Thread.currentThread().getName()));
                        });
                    }
            );
        };
        List<Runnable> runnables = List.of(() -> r.run(), () -> r.run(), () -> r.run(), () -> r.run());
        runnables.parallelStream().forEach(e -> e.run());
        assertTrue(releasedCounter.get() > 0);
        assertTrue(acquiredCounter.get() > 0);
        assertEquals(acquiredCounter.get(), releasedCounter.get());

    }

    @Test
    public void isLockedReturnsFalseWhenNoLockExists() {
        assertFalse(lockRepo.isLocked("nonExistentKey"));
    }

    @Test
    public void isLockedReturnsTrueWhenLocked() {
        lockRepo.lock("key1", "ownerId", false);
        assertTrue(lockRepo.isLocked("key1"));
    }

    @Test
    public void isLockedReturnsFalseAfterUnlock() {
        lockRepo.lock("key1", "ownerId", false);
        assertTrue(lockRepo.isLocked("key1"));

        lockRepo.unlock("key1", "ownerId");
        assertFalse(lockRepo.isLocked("key1"));
    }

    @Test
    public void isLockedReturnsFalseForDifferentKey() {
        lockRepo.lock("key1", "ownerId", false);
        assertTrue(lockRepo.isLocked("key1"));
        assertFalse(lockRepo.isLocked("key2"));
    }

    @Test
    public void isLockedAfterForceLock() {
        // First lock by owner1
        lockRepo.lock("key1", "owner1", false);
        assertTrue(lockRepo.isLocked("key1"));

        // Force lock by owner2
        lockRepo.forceLock("key1", "owner2");
        assertTrue(lockRepo.isLocked("key1"));

        // Unlock by owner2
        lockRepo.unlock("key1", "owner2");
        assertFalse(lockRepo.isLocked("key1"));
    }

}