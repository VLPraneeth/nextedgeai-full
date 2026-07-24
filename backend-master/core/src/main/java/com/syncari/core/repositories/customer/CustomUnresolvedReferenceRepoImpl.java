package com.syncari.core.repositories.customer;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.model.UnresolvedReference;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
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
public class CustomUnresolvedReferenceRepoImpl implements CustomUnresolvedReferenceRepo {
    public static final int PAGE_SIZE=1000;
    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Override
    public void updateSyncariValues(List<UnresolvedReference> values) {
        log.debug("Starting updateSyncariValues with {} values",values.size());
        BulkOperations ops = customerMongoTemplate.bulkOps(BulkMode.UNORDERED, UnresolvedReference.class);
        List<Pair<Query,Update>> updates =values.stream().map(unresolvedReference ->{

            var criteria = where("connectorId").is(unresolvedReference.getConnectorId())
                                    .and("externalRefEntityName").is(unresolvedReference.getExternalRefEntityName())
                                    .and("externalRefRecordId").is(unresolvedReference.getExternalRefRecordId());

            if (!StringUtils.isEmpty(unresolvedReference.referredSyncariEntity)) {
                var referredCriteria = new Criteria().orOperator(where("referredSyncariEntity").exists(false),
                        where("referredSyncariEntity").is(unresolvedReference.referredSyncariEntity));
                criteria = criteria.andOperator(referredCriteria);
            }

            Query query = new Query().addCriteria(criteria);
            Update update = new Update().set("resolvedSyncariValue", unresolvedReference.getResolvedSyncariValue()).set("updatedAt", new Date());
            return Pair.of(query, update);

        }).collect(Collectors.toList());
        if(!updates.isEmpty()) {
            ops.updateMulti(updates);
            BulkWriteResult results = ops.execute();

            if (results.getInsertedCount() > 0 || results.getModifiedCount() > 0 || results.getDeletedCount() > 0) {
                log.info("Bulk updated unresolved references with {} inserts, {} updates, {} deletes", results.getInsertedCount(),
                        results.getModifiedCount(), results.getDeletedCount());
            }
        }
    }

    public void upsertUnResolved(List<UnresolvedReference> unresolvedReferences) {

        log.info("Starting upsert with {} values", unresolvedReferences);
        List<UnresolvedReference> emptyReferences = unresolvedReferences.stream()
            .filter(x -> StringUtils.isEmpty(x.getExternalRefRecordId())).collect(Collectors.toList());
        if (!emptyReferences.isEmpty()) {
            log.warn("Skipping empty reference records\n {} \n", emptyReferences);
        }

        List<UnresolvedReference> nonEmptyReferences = unresolvedReferences.stream()
            .filter(x -> StringUtils.isNotEmpty(x.getExternalRefRecordId())).collect(Collectors.toList());

        List<Pair<Query,Update>> updates = nonEmptyReferences.stream().map(unresolvedReference ->{

            var criteria = where("connectorId").is(unresolvedReference.getConnectorId())
                    .and("syncariEntityDefId").is(unresolvedReference.getSyncariEntityDefId())
                    .and("syncariRecordId").is(unresolvedReference.getSyncariRecordId())
                    .and("syncariAttributeName").is(unresolvedReference.getSyncariAttributeName())
                    .and("externalRefEntityName").is(unresolvedReference.getExternalRefEntityName())
                    .and("externalRefRecordId").is(unresolvedReference.getExternalRefRecordId());

            if (!StringUtils.isEmpty(unresolvedReference.getReferredSyncariEntity())) {
                criteria = criteria.and("referredSyncariEntity").is(unresolvedReference.getReferredSyncariEntity());
            }

            Query query = new Query().addCriteria(criteria);
            Update update = new Update().set("updatedAt", new Date())
                    .set("retries", unresolvedReference.getRetries())
                    .set("unresolvable", unresolvedReference.getUnresolvable());
            return Pair.of(query, update);

        }).collect(Collectors.toList());
        BulkOperations ops = customerMongoTemplate.bulkOps(BulkMode.UNORDERED, UnresolvedReference.class);
        if(!updates.isEmpty()) {
            ops.upsert(updates);
            BulkWriteResult results = ops.execute();
            if (results.getInsertedCount() > 0 || results.getModifiedCount() > 0 || results.getDeletedCount() > 0) {
                log.info("Bulk updated unresolved references with {} inserts, {} updates, {} deletes", results.getInsertedCount(),
                        results.getModifiedCount(), results.getDeletedCount());
            }
        }
    }

    @Override
    public List<UnresolvedReference> findUnResolvedReferencesBy(String nextId, String connectorId, String externalRefEntityName, int pageSize) {
        if (pageSize <= 0) pageSize = PAGE_SIZE;
        Criteria rangeQuery;
        if (StringUtils.isBlank(nextId)) {
            rangeQuery = Criteria.where("resolvedSyncariValue").is(null)
                .and("externalRefEntityName").is(externalRefEntityName).and("connectorId").is(connectorId);
        } else {
            rangeQuery =  Criteria.where("_id").gt(new ObjectId(nextId)).and("resolvedSyncariValue").is(null)
                .and("externalRefEntityName").is(externalRefEntityName).and("connectorId").is(connectorId);
        }
        Query pagedQuery = Query.query(rangeQuery)
                .addCriteria(Criteria.where("unresolvable").ne(true))
                .with(Sort.by("_id").ascending()).limit(pageSize);
        log.debug("Query: {} ", pagedQuery.toString());
        return customerMongoTemplate.find(pagedQuery, UnresolvedReference.class);
    }

    @Override
    public List<UnresolvedReference> findResolvedReferenceBy(String syncariEntityDefId) {
        //TODO page the results
        Criteria criteria =  Criteria.where("syncariEntityDefId").is(syncariEntityDefId).and("resolvedSyncariValue").ne(null);
        Query query = Query.query(criteria);
        return customerMongoTemplate.find(query, UnresolvedReference.class);
    }

    @Override
    public long reparentLoserReferences(List<String> loserIds, String winnerId) {
        Query query = new Query().addCriteria(
                where("resolvedSyncariValue").in(loserIds));
        Update update = new Update().set("resolvedSyncariValue", winnerId);

        UpdateResult updateResult = customerMongoTemplate.updateMulti(query, update, UnresolvedReference.class);
        return updateResult.getModifiedCount();
    }

    @Override
    public void markUnresolvable(List<UnresolvedReference> unresolvedReferences) {
        log.info("Starting markUnresolvable with {} values",unresolvedReferences.size());
        BulkOperations ops = customerMongoTemplate.bulkOps(BulkMode.UNORDERED, UnresolvedReference.class);
        List<Pair<Query,Update>> updates =unresolvedReferences.stream().map(unresolvedReference ->{
            Query query = new Query().addCriteria(
                    where("connectorId").is(unresolvedReference.getConnectorId())
                            .and("externalRefEntityName").is(unresolvedReference.getExternalRefEntityName())
                            .and("externalRefRecordId").is(unresolvedReference.getExternalRefRecordId())
            );
            Update update = new Update().set("unresolvable", true).set("updatedAt", new Date());
            return Pair.of(query, update);

        }).collect(Collectors.toList());
        if(!updates.isEmpty()) {
            ops.updateMulti(updates);
            BulkWriteResult results = ops.execute();

            if (results.getInsertedCount() > 0 || results.getModifiedCount() > 0 || results.getDeletedCount() > 0 || results.getMatchedCount() > 0) {
                log.info("Bulk updated unresolved references with {} matches, {} inserts, {} updates, {} deletes", results.getMatchedCount(), results.getInsertedCount(),
                        results.getModifiedCount(), results.getDeletedCount());
            }
        }
    }
}
