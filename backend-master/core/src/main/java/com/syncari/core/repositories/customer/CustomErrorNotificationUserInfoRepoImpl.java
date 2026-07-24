package com.syncari.core.repositories.customer;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.syncari.core.model.ErrorNotificationUserInfo;

import lombok.extern.slf4j.Slf4j;

@Repository
@Slf4j
public class CustomErrorNotificationUserInfoRepoImpl implements CustomErrorNotificationUserInfoRepo {
    @Autowired
    private MongoTemplate customerMongoTemplate;

    private static final String CREATED_AT = "createdAt";

    @Override
    public Optional<ErrorNotificationUserInfo> findLatestNotifForUserByKey(String key, String userId) {

        Query query = new Query().addCriteria(where("userId").is(userId).and("key").is(key))
                .with(Sort.by(CREATED_AT).descending()).limit(1);

        ErrorNotificationUserInfo userInfo = customerMongoTemplate.findOne(query, ErrorNotificationUserInfo.class);
        return Optional.ofNullable(userInfo);
    }
}
