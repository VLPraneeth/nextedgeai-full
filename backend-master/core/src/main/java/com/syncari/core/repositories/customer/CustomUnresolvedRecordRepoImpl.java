package com.syncari.core.repositories.customer;

import com.syncari.core.model.UnresolvedRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@Slf4j
public class CustomUnresolvedRecordRepoImpl implements CustomUnresolvedRecordRepo {
    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Override
    public void upsert(List<UnresolvedRecord> unresolvedEntities) {
        if (unresolvedEntities.isEmpty()) return;
        List<Pair<Query, Update>> updates = unresolvedEntities.stream().map(unresolvedEntity ->
                Pair.of(
                        new Query().addCriteria(where("syncariId").is(unresolvedEntity.getSyncariId())
                                .and("externalEntityDefinitionId")
                                .is(unresolvedEntity.getExternalEntityDefinitionId())
                                .and("connectorId")
                                .is(unresolvedEntity.getConnectorId())
                                .and("syncariEntityDefinitionId")
                                .is(unresolvedEntity.getSyncariEntityDefinitionId())),
                        new Update()
                                .set("updatedAt", new Date())
                                .setOnInsert("createdAt", new Date())
                                .set("status", unresolvedEntity.getStatus().name())
                                .set("unresolvedFieldIds", unresolvedEntity.getUnresolvedFieldIds())
                )).collect(Collectors.toList());
        var result = customerMongoTemplate
                .bulkOps(BulkOperations.BulkMode.UNORDERED, UnresolvedRecord.class)
                .upsert(updates)
                .execute();
        log.info("Upserted unresolved entities  {}", result);
    }

    public void delete(List<UnresolvedRecord> unresolvedEntities) {
        if(unresolvedEntities.isEmpty()) {
            return;
        }
        List<Query> deletes = unresolvedEntities.stream().map(unresolvedEntity ->

                new Query().addCriteria(where("syncariId").is(unresolvedEntity.getSyncariId())
                        .and("externalEntityDefinitionId")
                        .is(unresolvedEntity.getExternalEntityDefinitionId())
                        .and("connectorId")
                        .is(unresolvedEntity.getConnectorId())
                        .and("syncariEntityDefinitionId")
                        .is(unresolvedEntity.getSyncariEntityDefinitionId()))
        ).collect(Collectors.toList());
        var result = customerMongoTemplate
                .bulkOps(BulkOperations.BulkMode.UNORDERED, UnresolvedRecord.class)
                .remove(deletes)
                .execute();
        log.info("Deleted unresolved entities  {}", result);
    }
}