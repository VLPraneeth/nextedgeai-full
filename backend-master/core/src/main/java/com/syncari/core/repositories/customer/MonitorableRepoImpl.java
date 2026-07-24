package com.syncari.core.repositories.customer;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.syncari.core.model.util.Status;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MonitorableRepoImpl<T> implements MonitorableRepo<T> {

    @Autowired
    private MongoTemplate customerMongoTemplate;

    private Optional<T> changeStatus(String id, Status from, Status to, Class<T> type) {
        return changeStatus(id, from, to, "", type);
    }

    private Optional<T> changeStatus(String id, Status from, Status to, String errorMsg, Class<T> type) {
        Query query = new Query().addCriteria(
                where("_id").is(new ObjectId(id))
                        .and("status").is(from));
        Update update = new Update().set("status", to)
                .set("checkin",new Date())
                .set("errorMsg", errorMsg);
        T modified = customerMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true).upsert(false), type);
        return Optional.ofNullable(modified);
    }

    @Override
    public Optional<T> process(String id, Class<T> type) {
       return changeStatus(id, Status.NEW, Status.PROCESSING, type);
    }

    @Override
    public Optional<T> checkin(String id, Class<T> type) {
        Query query = new Query().addCriteria(where("_id").is(new ObjectId(id)).and("status").is(Status.PROCESSING));
        Update update = new Update().set("checkin", Instant.now());
        T modified = customerMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), type);
        return Optional.ofNullable(modified);
    }

    @Override
    public Optional<T> finish(String id, Class<T> type) {
        return changeStatus(id, Status.PROCESSING, Status.COMPLETED, type);
    }

    @Override
    public Optional<T> finishWithError(String id, String errorMsg, Class<T> type) {
        return changeStatus(id, Status.PROCESSING, Status.ERROR, errorMsg, type);
    }

    @Override
    public List<T> getStuck(long maxIdleTimeInMillis, Class<T> type) {
        Query query = new Query().addCriteria(
                where("checkin").lte(Instant.now().minusMillis(maxIdleTimeInMillis))
                        .and("status").is(Status.PROCESSING));
        return customerMongoTemplate.find(query, type);
    }

    @Override
    public Optional<T> clearTheDead(String id, String errorMsg, Class<T> type) {
        return changeStatus(id, Status.PROCESSING, Status.ERROR, errorMsg, type);
    }
    
}
