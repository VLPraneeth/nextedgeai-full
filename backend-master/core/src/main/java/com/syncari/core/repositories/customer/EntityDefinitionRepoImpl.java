package com.syncari.core.repositories.customer;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.utils.CustomerMongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
@Slf4j
public class EntityDefinitionRepoImpl implements EntityDefinitionCustom {

    @Autowired
    protected MongoTemplate customerMongoTemplate;

    public List<EntityDefinition> findByConnectorIdAndDraftStatus(String connectorId, String draftStatus, String entityId, int limit){
        Criteria criteria = where("connectorId").is(connectorId).and("draftStatus").is(draftStatus);
        if(entityId!=null){
            criteria = criteria.and("_id").gt(new ObjectId(entityId));
        }
        Query query = new Query().addCriteria(
                criteria
        ).with(Sort.by("_id").ascending()).limit(limit);
        return customerMongoTemplate.find(query, EntityDefinition.class);
    }
}
