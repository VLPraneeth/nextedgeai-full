package com.syncari.core.service;

import com.mongodb.BulkWriteError;
import com.mongodb.BulkWriteException;
import com.syncari.connector.data.Result;
import com.syncari.connector.data.SyncResponse;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.IdMapping;
import com.syncari.core.repositories.customer.IdMappingRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IdMappingService {
    protected IdMappingRepo mappingRepo;

    @Autowired
    protected MongoTemplate customerMongoTemplate;

    @Autowired
    public IdMappingService(IdMappingRepo mappingRepo){
        this.mappingRepo = mappingRepo;
    }

    public List<IdMapping> findBySyncariIds(String syncariEntityName, Collection<String> syncariId) {
        return mappingRepo.findBySyncariIds(syncariEntityName, syncariId);
    }

    public Optional<IdMapping> findByExternalId(String entityName, String connectorId, String externalEntityDefinitionId, String entityId) {
        return mappingRepo.findByExternalId(entityName, connectorId, externalEntityDefinitionId, entityId);
    }

    public void upsert(List<IdMapping> idMappings) {
        mappingRepo.upsert(idMappings);
    }

    public void upsert(IdMapping idMapping) {
        mappingRepo.upsert(List.of(idMapping));
    }

    public void save(IdMapping idMapping) {
        try {
            mappingRepo.save(idMapping);
        } catch (DuplicateKeyException e) {
            log.error("Found an existing entry for the idMapping. Skipped save for {} ", idMapping, e);
        }
    }

    public void delete(IdMapping idMapping) {
        mappingRepo.delete(idMapping);
    }

    public Optional<IdMapping> findExistingMapping(String syncariEntityName, String syncariId, String connectorId, String externalEntityDefinitionId){
        return mappingRepo.findExistingMapping(syncariEntityName,syncariId,connectorId, externalEntityDefinitionId);
    }

    public List<IdMapping> saveAll(List<IdMapping> idMappings) {
        try {
            return mappingRepo.saveAll(idMappings);
        } catch (DuplicateKeyException e) {
            // Fall back to one by one processing and narrow down the failure record to log error.
            idMappings.forEach(x -> save(x));
        }
        return idMappings;
    }

    public void deleteAll(List<IdMapping> idMappings) {
        mappingRepo.deleteAll(idMappings);
    }

    public Optional<IdMapping> findBySyncariId(String entityName, String syncariId) {
        return mappingRepo.findBySyncariId(entityName,syncariId);
    }
    
    public void delete(String entityName) {
        mappingRepo.deleteByEntityName(entityName);
    }

    public List<IdMapping> saveIdMapping(EntityDefinition syncariEntity, String connectorId, SyncResponse createResponse, EntityDefinition sink) {
        var uniqueRecords = createResponse.getResults().stream().filter(v -> v.isSuccess()).collect(Collectors.toMap(r->r.getSyncariId(), r -> r, (r1, r2) -> r2));
        List<IdMapping> mappings = uniqueRecords.keySet().stream().map(syncariId -> {
            Result r = uniqueRecords.get(syncariId);
            return new IdMapping().setSyncariId(syncariId).setEntityName(syncariEntity.getApiName()).addMapping(connectorId, r.getId(), sink.getId());
        }).collect(Collectors.toList());

        try {
            mappingRepo.upsert(mappings);
        } catch (Exception e) {
            for (IdMapping mapping : mappings) {
                try {
                    mappingRepo.upsert(List.of(mapping));
                } catch (DuplicateKeyException e1) {
                    log.error("Error saving to IdMappig " + e1.getMessage());
                    log.debug(e1.getMessage(), e);
                }
            }
        }
        return mappings;
    }

    public List<IdMapping> findOrphans(String syncariEntityName, Instant since) {
        return mappingRepo.findOrphans(syncariEntityName,since);
    }
}
