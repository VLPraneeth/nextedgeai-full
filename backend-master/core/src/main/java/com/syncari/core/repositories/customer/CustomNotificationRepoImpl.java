package com.syncari.core.repositories.customer;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import com.mongodb.client.result.UpdateResult;
import com.syncari.core.model.Notification;

import lombok.extern.slf4j.Slf4j;

@Repository
@Slf4j
public class CustomNotificationRepoImpl implements CustomNotificationRepo {
    @Autowired
    private MongoTemplate customerMongoTemplate;

    private static final String CREATED_AT = "createdAt";

    @Override
    public long archiveAll(String userId) {
        Query query = new Query().addCriteria(where("userId").is(userId));
        Update update = new Update().set("archived", true);

        UpdateResult updateResult = customerMongoTemplate.updateMulti(query, update, Notification.class);
        return updateResult.getModifiedCount();
    }

    @Override
    public long readAll(String userId) {
        Query query = new Query().addCriteria(where("userId").is(userId)
                .and("read").is(false).and("archived").is(false)).with(Sort.by("_id").descending());
        Update update = new Update().set("read", true);

        UpdateResult updateResult = customerMongoTemplate.updateMulti(query, update, Notification.class);
        return updateResult.getModifiedCount();
    }

    @Override
    public long archiveMany(String userId, List<String> ids) {
        Query query = new Query().addCriteria(where("_id").in(ids).and("userId").is(userId));
        Update update = new Update().set("archived", true);

        UpdateResult updateResult = customerMongoTemplate.updateMulti(query, update, Notification.class);
        return updateResult.getModifiedCount();
    }

    @Override
    public long readMany(String userId, List<String> ids) {
        Query query = new Query().addCriteria(where("_id").in(ids).and("userId").is(userId));
        Update update = new Update().set("read", true);

        UpdateResult updateResult = customerMongoTemplate.updateMulti(query, update, Notification.class);
        return updateResult.getModifiedCount();
    }
    
    @Override
    public long unreadMany(String userId, List<String> ids) {
        Query query = new Query().addCriteria(where("_id").in(ids).and("userId").is(userId));
        Update update = new Update().set("read", false);
        
        UpdateResult updateResult = customerMongoTemplate.updateMulti(query, update, Notification.class);
        return updateResult.getModifiedCount();
    }

    @Override
    public long unreadAll(String userId) {
        Query query = new Query().addCriteria(where("userId").is(userId));
        Update update = new Update().set("read", false);

        UpdateResult updateResult = customerMongoTemplate.updateMulti(query, update, Notification.class);
        return updateResult.getModifiedCount();
    }

    @Override
    public Optional<Notification> findLatestNotifForUserByKey(String key, String userId) {

        Query query = new Query().addCriteria(where("userId").is(userId).and("key").is(key))
                .with(Sort.by(CREATED_AT).descending()).limit(1);

        Notification notif = customerMongoTemplate.findOne(query, Notification.class);
        return Optional.ofNullable(notif);
    }
}
