package com.syncari.core.repositories.syncari;

import com.syncari.core.model.Organization;
import com.syncari.core.model.util.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@Slf4j
public class CustomOrganizationRepo {

    @Autowired
    private MongoTemplate syncariMongoTemplate;

    public Optional<Organization> findAndModifyInstanceStatus(String instanceId, Status statusToQuery, Status statusToUpdateTo) {
        try {
            Query query = new Query().addCriteria(where("instances").elemMatch(where("syncariId").is(instanceId).and("status").is(statusToQuery.name())));
            Update update = new Update().set("instances.$.status", statusToUpdateTo)
                    .set("updatedAt", new Date());
            Organization org = syncariMongoTemplate.findAndModify(query, update,
                    new FindAndModifyOptions().returnNew(true).upsert(false), Organization.class);
            return Optional.ofNullable(org);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return Optional.empty();
        }
    }
}
