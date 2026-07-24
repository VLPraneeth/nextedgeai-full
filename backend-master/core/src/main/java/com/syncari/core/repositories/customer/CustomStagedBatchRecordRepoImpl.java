package com.syncari.core.repositories.customer;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.syncari.connector.exception.NonRetriableInternalException;
import com.syncari.core.model.StagedBatchRecord;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class CustomStagedBatchRecordRepoImpl implements CustomStagedBatchRecordRepo {
    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Override
    public List<StagedBatchRecord> findByStagedBatchIdIn(List<String> stagedBatchIds, String marker, int limit) {
        Criteria criteria = where("stagedBatchId").in(stagedBatchIds);
        if(marker!=null){
            criteria = criteria.and("_id").gt(new ObjectId(marker));
        }
        Query query = new Query().addCriteria(
                criteria
        ).with(Sort.by("_id").ascending()).limit(limit);
        return customerMongoTemplate.find(query, StagedBatchRecord.class);
    }

    @Override
    public boolean exists(List<String> stagedBatchIds, String syncariId, String key, Object value) {
        if (StringUtils.isBlank(syncariId) || StringUtils.isBlank(key)) {
            log.error("incomplete data to make count query. syncariId {} key {}", syncariId, key);
            return false;
        }
        String fullKeyPath = "entityData.values." + key;
        Criteria stagedBatchCriteria = Criteria.where("stagedBatchId").in(stagedBatchIds);
        Criteria keyCriteria = Criteria.where(fullKeyPath).is(value);
        Criteria undeletedCriteria = Criteria.where("deleted").is(false);
        Criteria isNewCriteris = Criteria.where("isNew").is(true);
        Criteria syncariIdCriteria = Criteria.where("syncariId").ne(syncariId);
        Query query = new Query(new Criteria().andOperator(stagedBatchCriteria, keyCriteria, undeletedCriteria, isNewCriteris, syncariIdCriteria));
        return customerMongoTemplate.exists(query, StagedBatchRecord.class);
    }

    @Override
    public List<StagedBatchRecord> getStagedRecordBySyncariId(String syncariId, List<String> stagedBatchIds) {
        Criteria stagedBatchCriteria = Criteria.where("stagedBatchId").in(stagedBatchIds);
        Criteria syncariIdCriteria = Criteria.where("syncariId").is(syncariId);
        Query query = new Query(new Criteria().andOperator(stagedBatchCriteria, syncariIdCriteria));
        return customerMongoTemplate.find(query, StagedBatchRecord.class);
    }

    @Override
    public List<StagedBatchRecord> updateMany(List<StagedBatchRecord> records) {
        if(records.isEmpty()){
            return records;
        }
        List<Pair<Query, Update>> updates = records.stream().map(stagedBatchRecord ->
                Pair.of(
                        new Query().addCriteria(where("_id").is(new ObjectId(stagedBatchRecord.getId()))),
                        new Update()
                                .set("stagedBatchId",stagedBatchRecord.getStagedBatchId())
                                .set("externalEntityDefinitionId",stagedBatchRecord.getExternalEntityDefinitionId())
                                .set("externalRecordId",stagedBatchRecord.getExternalRecordId())
                                .set("syncariId",stagedBatchRecord.getSyncariId())
                                .set("entityData",stagedBatchRecord.getEntityData())
                                .set("isNew",stagedBatchRecord.isNew())
                                .set("modifiedByPipeline",stagedBatchRecord.isModifiedByPipeline())
                                .set("deleted",stagedBatchRecord.isDeleted())
                                .set("isRequeued",stagedBatchRecord.isRequeued())
                                .set("updatedAt", new Date())
                )).collect(Collectors.toList());
				try {
					var result = customerMongoTemplate
							.bulkOps(BulkOperations.BulkMode.UNORDERED, StagedBatchRecord.class).updateMulti(updates)
							.execute();
					log.info("Matched  {} ,Updated {} stagedBatchRecords", result.getMatchedCount(),
							result.getModifiedCount());
				} catch (Exception e) {
					throw new NonRetriableInternalException("BULK_UPDATE", "BULK_UPDATE", e.getMessage(), e);
				}
        return records;
    }
    @Override
    public Optional<StagedBatchRecord> findFirstByExternalEntityDefinitionIdAndExternalRecordId(String externalEntityDefinitionId, String externalRecordId){
        Criteria criteria = where("externalEntityDefinitionId").is(externalEntityDefinitionId).and("externalRecordId").is(externalRecordId);
        Query query = new Query().addCriteria(
                criteria
        ).with(Sort.by("updatedAt").descending()).limit(1);
        StagedBatchRecord record = customerMongoTemplate.findOne(query, StagedBatchRecord.class);
        return Optional.ofNullable(record);
    }

}
