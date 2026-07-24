package com.syncari.core.repositories.customer;

import com.mongodb.client.result.UpdateResult;
import com.syncari.core.model.SyncStream;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
public class CustomStreamRepoImpl implements CustomStreamRepo {
    @Autowired
    private MongoTemplate customerMongoTemplate;

    public Optional<SyncStream> changeStatus(String streamId, String processorId, SyncStream.Status from, SyncStream.Status to) {
        return changeStatus(streamId, processorId, List.of(from), to);
    }

    public Optional<SyncStream> changeStatus(String streamId, String processorId, List<SyncStream.Status> from, SyncStream.Status to) {

        Query query = new Query().addCriteria(
                where("_id").is(new ObjectId(streamId))
                        .and("status").in(from.stream().map(status->status.name()).collect(Collectors.toList())));
        Update update = new Update().set("status", to.name())
                .set("checkin",new Date())
                .set("processorId", processorId);
        SyncStream modified = customerMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true).upsert(false)
                , SyncStream.class);
        return Optional.ofNullable(modified);
    }

    public Optional<SyncStream> reclaim(String streamId, String processorId, SyncStream.Status status, long maxIdleTimeInMillis) {

        Query query = new Query().addCriteria(
                where("_id").is(new ObjectId(streamId))
                        .and("status").is(status.name()).and("checkin").lte(Instant.now().minusMillis(maxIdleTimeInMillis)));
        Update update = new Update().set("status", status.name())
                .set("checkin",new Date())
                .set("processorId", processorId);
        SyncStream modified = customerMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true).upsert(false)
                , SyncStream.class);
        return Optional.ofNullable(modified);
    }

    @Override
    public Optional<SyncStream> checkin(String streamId, String processorId) {
        Query query = new Query().addCriteria(
                where("_id").is(new ObjectId(streamId)));

        Update update = new Update().set("checkin", Instant.now())
                .set("processorId", processorId);
        SyncStream modified = customerMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true)
                , SyncStream.class);
        return Optional.ofNullable(modified);
    }

    @Override
    public long relinquish(String processorId, List<String> syncStreamIds) {

        // Cannot use map, due to codegen bugs
        List<ObjectId> objectIds = new ArrayList<>();
        for(String syncStreamId: syncStreamIds){
            objectIds.add(new ObjectId(syncStreamId));
        }

        Query query = new Query().addCriteria(
                where("_id").in(objectIds)
                        .and("processorId").is(processorId));

        Update update = new Update().set("status", SyncStream.Status.READY);

        UpdateResult updateResult = customerMongoTemplate.updateMulti(query, update, SyncStream.class);
        return updateResult.getModifiedCount();

    }

    @Override
    public SyncStream updateLastCleanup(String streamId, Instant lastCleanup) {
        Query query = new Query().addCriteria(
                where("_id").is(new ObjectId(streamId)));

        Update update = new Update().set("lastCleanup", lastCleanup)
                .set("updatedAt", new Date());
        SyncStream modified = customerMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true)
                , SyncStream.class);
        return modified;
    }

    @Override
    public List<SyncStream> unclaimed(long maxTimeInMillis) {
        Query query = new Query().addCriteria(
                where("checkin").lte(Instant.now().minusMillis(maxTimeInMillis))
                        .and("status").is(SyncStream.Status.READY));
        return customerMongoTemplate.find(query,SyncStream.class);
    }


    @Override
    public List<SyncStream> orphans(long maxIdleTimeInMillis) {
        Query query = new Query().addCriteria(
                where("checkin").lte(Instant.now().minusMillis(maxIdleTimeInMillis))
                        .and("status").is(SyncStream.Status.RUNNING));
        return customerMongoTemplate.find(query,SyncStream.class);

    }
    @Override
    public List<SyncStream> stuck(long maxIdleTimeInMillis) {
        Query query = new Query().addCriteria(
                where("checkin").lte(Instant.now().minusMillis(maxIdleTimeInMillis))
                        .and("status").in(List.of(SyncStream.Status.RUNNING,SyncStream.Status.CLAIMED)));
        return customerMongoTemplate.find(query,SyncStream.class);

    }

}
