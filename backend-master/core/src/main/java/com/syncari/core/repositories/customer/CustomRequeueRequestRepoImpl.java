package com.syncari.core.repositories.customer;

import com.syncari.core.model.RequeueRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Slf4j
public class CustomRequeueRequestRepoImpl implements CustomRequeueRequestRepo {
    @Autowired
    protected MongoTemplate customerMongoTemplate;

    @Override
    public void upsert(List<RequeueRequest> requeueRequests) {
        if (requeueRequests.isEmpty()) return;
        List<Pair<Query, Update>> updates = requeueRequests.stream().map(requeueRequest ->
                Pair.of(
                        new Query().addCriteria(where("entityDefinitionId").is(requeueRequest.getEntityDefinitionId())
                                .and("recordId").is(requeueRequest.getRecordId())
                                .and("graphId").is(requeueRequest.getGraphId())
                                .and("recordType").is(requeueRequest.getRecordType())
                        ),
                        new Update()
                                .set("retryTimeLimit", requeueRequest.getRetryTimeLimit())
                                .set("processExpiredRecord", requeueRequest.isProcessExpiredRecord())
                                .set("emailAddresses", requeueRequest.getEmailAddresses())
                                .set("requeueReason", requeueRequest.getRequeueReason())
                                .setOnInsert("createdAt", new Date())
                                .set("updatedAt", new Date())
                )).collect(Collectors.toList());
        var result = customerMongoTemplate
                .bulkOps(BulkOperations.BulkMode.UNORDERED, RequeueRequest.class)
                .upsert(updates)
                .execute();
        log.info("Upserted requeue requests matched:{},modified:{},deleted:{},inserted:{}, upsertSize:{}", result.getMatchedCount(), result.getModifiedCount(), result.getDeletedCount(), result.getInsertedCount(), result.getUpserts().size());
    }
}
