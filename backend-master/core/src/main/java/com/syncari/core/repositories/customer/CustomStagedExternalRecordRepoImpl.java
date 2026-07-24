package com.syncari.core.repositories.customer;

import com.google.common.collect.Lists;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.StagedExternalRecord;
import com.syncari.core.model.misc.ExternalFieldChange;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonMaximumSizeExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@Slf4j
public class CustomStagedExternalRecordRepoImpl implements CustomStagedExternalRecordRepo {
    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Override
    public void upsert(List<StagedExternalRecord> records, EntityDefinition entity) {
        if (records.isEmpty()) return;
        // The assumption is each batch is operating on same externalEntityDefinitionId
        List<StagedExternalRecord> recordsToBeUpserted = new ArrayList<>();

        List<String> recordIds = records.stream().map(r -> r.getExternalRecordId()).collect(Collectors.toList());
        List<StagedExternalRecord> existingRecords = new ArrayList<>();
        try {
            existingRecords = getRecords(records.get(0).getExternalEntityDefinitionId(), recordIds);
        } catch (BsonMaximumSizeExceededException e) {
            int batchSize = 1000;
            existingRecords = Lists.partition(recordIds, batchSize).stream().map(recs -> getRecords(records.get(0).getExternalEntityDefinitionId(), recs)).flatMap(List::stream).collect(Collectors.toList());
        }

        Map<String, StagedExternalRecord> existingMap = existingRecords.stream()
                .collect(Collectors.toMap(StagedExternalRecord::getExternalRecordId, Function.identity()));
        Map<String, Datatype> datatypeMap = entity.getAttributes().stream().collect(Collectors.toMap(AttributeDefinition::getApiName, AttributeDefinition::getDataType));
        records.forEach(newRecord -> {
            Optional<StagedExternalRecord> maybe = existingMap.containsKey(newRecord.getExternalRecordId()) ? Optional.of(existingMap.get(newRecord.getExternalRecordId())) : Optional.empty();
            maybe.ifPresent(existing -> {
                if(newRecord.getUpdatedAt() != null && newRecord.getUpdatedAt().compareTo(existing.getUpdatedAt()) < 0) return;
                List<ExternalFieldChange> fieldChanges = new ArrayList<>();
                existing.getEntityData().getValues().entrySet().forEach(i -> {
                    // check if the field is present in newRecord entityData
                    if(newRecord.getEntityData().hasValue(i.getKey())) {
                        final Datatype dataType = (datatypeMap.containsKey(i.getKey()) && datatypeMap.get(i.getKey()) != null) ? datatypeMap.get(i.getKey()) : new StringType();
                        Object typedExisting = dataType.convert(i.getValue());
                        Object typedNew = dataType.convert(newRecord.getEntityData().getValue(i.getKey()));
                        if (!newRecord.getEntityData().isIgnoredField(i.getKey()) && !Objects.equals(typedExisting, typedNew)) {
                            fieldChanges.add(new ExternalFieldChange().setApiName(i.getKey()).setNewValue(newRecord.getEntityData().getValue(i.getKey())).setOldValue(i.getValue()));
                        }
                    } else {
                        // field value if not present in newRecord but if its present in existing record then copy over to maintain superset
                        // This is needed because if same source is present in different pipelines and have different field mapping then
                        // stagedExternalRecord will keep changing to reflect different fields and we may get spurious updates in destination
                        newRecord.getEntityData().addValue(i.getKey(), i.getValue());
                    }
                });
                newRecord.setFieldChanges(fieldChanges);
            });
            recordsToBeUpserted.add(newRecord);
        });
        List<Pair<Query, Update>> upserts = recordsToBeUpserted.stream().map(record ->
                Pair.of(
                        new Query().addCriteria(
                                where("externalEntityDefinitionId").is(record.getExternalEntityDefinitionId()).and("externalRecordId").is(record.getExternalRecordId())),
                        new Update().set("externalRecordId", record.getExternalRecordId())
                                .set("externalEntityDefinitionId", record.getExternalEntityDefinitionId())
                                .set("entityData", record.getEntityData())
                                .set("fieldChanges", record.getFieldChanges())
                                .set("lastUpdatedStagedBatchId", record.getLastUpdatedStagedBatchId())
                                .set("lastUpdatedGraphId", record.getLastUpdatedGraphId())
                                .set("deleted", record.isDeleted())
                                .setOnInsert("createdAt", new Date())
                                .set("updatedAt", new Date())
                )).collect(Collectors.toList());
        var result = customerMongoTemplate
                .bulkOps(BulkOperations.BulkMode.UNORDERED, StagedExternalRecord.class)
                .upsert(upserts)
                .execute();
        log.debug("Upserted StagedExternalRecord {}", result);
    }

    private List<StagedExternalRecord> getRecords(String externalEntityDefinitionId, List<String> externalRecordIds) {
        Query q = new Query().addCriteria(
                where("externalRecordId").in(externalRecordIds).and("externalEntityDefinitionId").is(externalEntityDefinitionId));
        return customerMongoTemplate.find(q, StagedExternalRecord.class);
    }

}