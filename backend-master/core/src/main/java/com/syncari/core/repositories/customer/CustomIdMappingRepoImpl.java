package com.syncari.core.repositories.customer;

import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteError;
import com.mongodb.BulkWriteException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.model.IdMapping;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@Slf4j
public class CustomIdMappingRepoImpl implements CustomIdMappingRepo {
    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Override
    @Transactional("customerTransactionManager")
    public void upsert(List<IdMapping> idMappings) {
        //TODO; This method NEEDS TO BE tramsactions
        if (idMappings.isEmpty()) return;
        List<IdMapping> updatedMappings = getUpdatedMappings(idMappings);
        List<Pair<Query, Update>> updates = updatedMappings.stream().map(idMapping ->
                Pair.of(
                        new Query().addCriteria(where("syncariId").is(idMapping.getSyncariId()).and("entityName").is(idMapping.getEntityName())),
                        new Update().set("mappings",idMapping.getMappings())
                                .setOnInsert("createdAt", new Date())
                                .set("updatedAt", new Date())
                )).collect(Collectors.toList());

        try {
            var result = customerMongoTemplate
                    .bulkOps(BulkOperations.BulkMode.UNORDERED, IdMapping.class)
                    .upsert(updates)
                    .execute();
            log.debug("Upserted IdMapping {}",result);
        } catch (BulkWriteException e) {
            List<BulkWriteError> errors = e.getWriteErrors();
            // Log or process the errors
            for (BulkWriteError error : errors) {
                log.error("Error saving IdMapping " + error.getMessage(), e);
            }
        }
    }

    private List<IdMapping> getUpdatedMappings(List<IdMapping> idMappings) {
        final Map<String, IdMapping> mappingBySyncariId = new HashMap<>();
        idMappings.stream().forEach(idMapping->{
            if(mappingBySyncariId.containsKey(idMapping.getSyncariId())){
                //merge mappings
                final IdMapping existing = mappingBySyncariId.get(idMapping.getSyncariId());
                idMapping.getMappings().forEach(mapping-> existing.addMapping(mapping));
            }else{
                mappingBySyncariId.put(idMapping.getSyncariId(), idMapping);
            }
        });

        final List<IdMapping> existingMappings = customerMongoTemplate.find(new Query().addCriteria(where("syncariId")
                .in(mappingBySyncariId.keySet())), IdMapping.class);
        final Map<String, IdMapping> existingMappingBySyncariId = existingMappings.stream().collect(Collectors.toMap(m -> m.getSyncariId(), m -> m));
        List<IdMapping> updatedMappings = new ArrayList<>();
        mappingBySyncariId.forEach((syncariId, incoming) ->{
            if(existingMappingBySyncariId.containsKey(incoming.getSyncariId())){
                updatedMappings.add(existingMappingBySyncariId.get(incoming.getSyncariId()).upsertMappings(incoming));
            }else{
                updatedMappings.add(incoming);
            }
        });
        return updatedMappings;
    }

    private UpdateResult switchConnectedStatus(String entityName, String syncariId, IdMapping.Mapping mapping, boolean isDisconneccted) {
        Query query = new Query().addCriteria(
                where("syncariId").is(syncariId)
                        .and("entityName").is(entityName)
                        .and("mappings.connectorId").is(mapping.getConnectorId())
                        .and("mappings.entityId").is(mapping.getEntityId())
                        .and("mappings.entityDefinitionId").is(mapping.getEntityDefinitionId())
        );

        Update update = new Update().set("mappings.$.disconnected", isDisconneccted);
        return customerMongoTemplate.updateFirst(query, update, IdMapping.class);
    }

    public List<IdMapping> findOrphans(String syncariEntityName, Instant ts){
        Instant since = ts == null ? Instant.now() : ts;
        final Document filters = new Document("$match",
                new Document("updatedAt", new Document("$gte",since)).append("entityName",syncariEntityName)
        );

        final Document andReducer = new Document("$and", List.of("$$value", "$$this.disconnected"));
        final Document reducer = new Document("$reduce", new Document("input", "$mappings").append("initialValue", true).append("in", andReducer));
        final Document projectFullyDisconnectedFlag = new Document("$project",
                new Document("fullyDisconnected", reducer)
                .append("_id", 1)
                .append("syncariId", 1).append("mappings",1).append("createdAt",1).append("updatedAt",1)

        );
        final Document matchFullyDisconnected = new Document("$match",
                new Document("fullyDisconnected", true)
        );
        final Document limits = new Document("$limit", 1000);


        final AggregateIterable<Map> disconnectedIdMappings = customerMongoTemplate.getDb().getCollection("idMapping").aggregate(
                List.of(filters, projectFullyDisconnectedFlag, matchFullyDisconnected,limits), Map.class
        );
        return disconnectedIdMappings.into(new ArrayList<>()).stream().map(m-> {
            final IdMapping idMapping = new IdMapping();
            idMapping.setId(m.get("_id").toString());
            idMapping.setSyncariId(m.get("syncariId").toString());
            idMapping.setEntityName(syncariEntityName);
            return idMapping;
        }).collect(Collectors.toList());
    }

	@Override
	public void removeExternalIdRef(String connectorId) {
		//Delete id mapping if it contains exactly this connector only
        Query query = new Query().addCriteria(where("mappings").size(1).elemMatch(where("connectorId").is(connectorId)));
        customerMongoTemplate.remove(query, IdMapping.class);
		
		//Pull the connector mapping if it has other mappings
		query = new Query()
				.addCriteria(where("mappings").elemMatch(where("connectorId").is(connectorId)));
		Update update = new Update();
		update.pull("mappings", new BasicDBObject("connectorId",connectorId));
		customerMongoTemplate.updateMulti(query, update, IdMapping.class);
	}


    /**
     db.idMapping.aggregate([
     { $project: { d:
     { $reduce:
     {
     input:"$mappings",
     initialValue:true,
     in:{
     $and:["$$value","$$this.disconnected"]
     }
     }
     }
     ,_id:1 }
     },
     {$match: {d : true}}
     ])
     */
}