package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.LockRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class UnlockMappingGraphPublish {

    @ChangeSet(order = "001", id = "unlockGraphPublish", author = "abhinav", runAlways = true)
    public void unlockGraphPublish(MongoTemplate db) {

        String graphId = System.getProperty("graphId");
        MappingGraphRepo mappingGraphRepo = MigrationContext.getMappingGraphRepo();
        LockRepo lockRepo = MigrationContext.getLockRepo();

        mappingGraphRepo.findById(graphId).ifPresent(graph -> {
            if (graph.getScope() != Scope.ENTITY) {
                log.info("Graph {}({}) is not an entity graph, skipping unlock", graph.getName(), graph.getId());
                return;
            }
            String lockId = "entity_" + graph.getTargetId();
            if (lockRepo.isLocked(lockId)) {
                log.info("Unlocking graph {}({}) with lockId {}", graph.getName(), graph.getId(), lockId);
                // Force acquire the lock and then unlock it to release it
                String ownerId = "migration_unlock_" + graph.getId();
                lockRepo.forceLock(lockId, ownerId);
                lockRepo.unlock(lockId, ownerId);
                log.info("Unlocked graph {}({})", graph.getName(), graph.getId());
            } else {
                log.info("Graph {}({}) is already unlocked", graph.getName(), graph.getId());
            }
        });
    }
}
