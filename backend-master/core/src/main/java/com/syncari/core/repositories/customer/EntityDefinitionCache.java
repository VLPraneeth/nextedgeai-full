package com.syncari.core.repositories.customer;

import com.syncari.connector.Constants;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.abac.Permission;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.service.ConnectorMetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EntityDefinitionCache implements EntityDefinitionRepo {

    private static final Logger logger = LoggerFactory.getLogger(EntityDefinitionCache.class);

    @Autowired
    private EntityDefinitionRepo entityDefinitionRepo;

    @Resource
    private EntityDefinitionCache self;

    @Override
    @Cacheable(value = "entityDefCache", key = "'connector:'.concat(#connectorId != null ? #connectorId : '')")
    public List<EntityDefinition> findAllByConnectorId(String connectorId) {
        return entityDefinitionRepo.findAllByConnectorId(connectorId);
    }

    @Override
    @Cacheable(value = "entityDefCache", key = "'connectorType:'.concat(#connectorTypeId != null ? #connectorTypeId : '')")
    public List<EntityDefinition> findByConnectorTypeId(String connectorTypeId) {
        return entityDefinitionRepo.findByConnectorTypeId(connectorTypeId);
    }

    @Override
    public List<EntityDefinition> findByConnectorId(String connectorId) {
        return self.findAllByConnectorId(connectorId).stream().filter(EntityDefinition::isApproved).collect(Collectors.toList());
    }

    @Override
    public List<EntityDefinition> findActiveEntitiesByConnectorIds(Set<String> connectorIds) {
        List<EntityDefinition> entityDefinitions = new ArrayList<>();
        connectorIds.stream().forEach(c -> entityDefinitions.addAll(findActiveEntities(c)));
        return entityDefinitions;
    }

    @Caching(evict = {
            @CacheEvict(value = "entityDefCache", key = "'entity:'.concat(#entityDefinition.getId() != null ? #entityDefinition.getId() : '' )"),
            @CacheEvict(value = "entityDefCache", key = "'connectorType:'.concat(#entityDefinition.getConnectorTypeId() != null ? #entityDefinition.getConnectorTypeId() : '')"),
            @CacheEvict(value = "entityDefCache", key = "'connector:'.concat(#entityDefinition.getConnectorId() != null ? #entityDefinition.getConnectorId() : '')")
    })
    
//    @AbacCheck(action = {Permission.CREATE, Permission.UPDATE})
    @Override
    public EntityDefinition save(EntityDefinition entityDefinition) {
        try {
            return entityDefinitionRepo.save(entityDefinition);
        } catch (DuplicateKeyException e) {
            logger.error("Duplicate key exception when saving EntityDefinition. Entity details: " +
                    "connectorId='{}', apiName='{}', draftStatus='{}', entityId='{}', displayName='{}'. " +
                    "MongoDB error: {}", 
                    entityDefinition.getConnectorId(),
                    entityDefinition.getApiName(), 
                    entityDefinition.getDraftStatus(),
                    entityDefinition.getId(),
                    entityDefinition.getDisplayName(),
                    e.getMessage());
            
            // Find and log the existing conflicting entity
            try {
                List<EntityDefinition> existingEntities = entityDefinitionRepo.findEntityVersions(
                    entityDefinition.getConnectorId(), 
                    entityDefinition.getApiName());
                
                Optional<EntityDefinition> conflictingEntity = existingEntities.stream()
                    .filter(existing -> existing.getDraftStatus().equals(entityDefinition.getDraftStatus()))
                    .findFirst();
                
                if (conflictingEntity.isPresent()) {
                    EntityDefinition existing = conflictingEntity.get();
                    logger.error("Found existing conflicting entity: " +
                            "connectorId='{}', apiName='{}', draftStatus='{}', entityId='{}', displayName='{}', " +
                            "active='{}', createdAt='{}', updatedAt='{}'",
                            existing.getConnectorId(),
                            existing.getApiName(),
                            existing.getDraftStatus(),
                            existing.getId(),
                            existing.getDisplayName(),
                            existing.isActive(),
                            existing.getCreatedAt(),
                            existing.getUpdatedAt());
                } else {
                    logger.error("Could not find the existing conflicting entity, but {} entities found with same connectorId/apiName: {}",
                            existingEntities.size(),
                            existingEntities.stream()
                                .map(entity -> String.format("id=%s, draftStatus=%s", entity.getId(), entity.getDraftStatus()))
                                .collect(Collectors.joining(", ")));
                }
            } catch (Exception queryException) {
                logger.error("Failed to query for existing conflicting entity: {}", queryException.getMessage());
            }
            
            throw e;
        }
    }

    @Caching(evict = {
            @CacheEvict(value = "entityDefCache", key = "'entity:'.concat(#entityDefinition.getId() != null ? #entityDefinition.getId() : '' )"),
            @CacheEvict(value = "entityDefCache", key = "'connectorType:'.concat(#entityDefinition.getConnectorTypeId() != null ? #entityDefinition.getConnectorTypeId() : '')"),
            @CacheEvict(value = "entityDefCache", key = "'connector:'.concat(#entityDefinition.getConnectorId() != null ? #entityDefinition.getConnectorId() : '')")
            //@CacheEvict(value = "entityDefCache", key = "'parent:'.concat(#entityDefinition.getId())")
    })
    @Override
    public void delete(EntityDefinition entityDefinition) {
        entityDefinitionRepo.delete(entityDefinition);
    }

    @Override
    public List<EntityDefinition> findAllByParentId(String parentId) {
        return entityDefinitionRepo.findAllByParentId(parentId);
    }

    @Cacheable(value = "entityDefCache", key = "'entity:'.concat(#entityDefinitionId)", unless = "#result == null")
    @Override
    public Optional<EntityDefinition> findById(String entityDefinitionId) {
        return entityDefinitionRepo.findById(entityDefinitionId);
    }

    // This method does case insensitive comparison for api name
    @Override
    public List<EntityDefinition> findEntities(String connectorId, String apiName) {
        return self.findAllByConnectorId(connectorId).stream()
                .filter(e -> e.getApiName().equalsIgnoreCase(apiName) && e.isActive() && (e.isDraft() || e.isApproved())).collect(Collectors.toList());
    }

    @Override
    //TODO: fix this
    public Optional<EntityDefinition> findByConnectorIdAndApiName(String connectorId, String apiName) {
        return entityDefinitionRepo.findByConnectorIdAndApiName(connectorId, apiName);
    }

    @Override
    public Optional<EntityDefinition> findActiveEntityByConnectorIdAndApiName(String connectorId, String apiName) {
        return self.findAllByConnectorId(connectorId).stream()
                .filter(e -> e.getApiName().equals(apiName) && e.isApproved() && e.isActive()).findFirst();
    }

    @Override
    public List<EntityDefinition> findActiveEntities(String connectorId) {
        return self.findAllByConnectorId(connectorId).stream().filter(e -> e.isActive() && e.isApproved()).collect(Collectors.toList());
    }

    @Override
    public Optional<EntityDefinition> findEntityByConnectorIdAndApiName(String connectorId, String apiName) {
        return self.findAllByConnectorId(connectorId).stream()
                .filter(e -> e.getApiName().equals(apiName) && e.isApproved()).findFirst();
    }

    @Override
    public Optional<EntityDefinition> findChildEntityByConnectorIdAndApiName(String connectorId, String apiName) {
        return self.findEntityByConnectorIdAndApiName(connectorId, apiName);
    }

    @Override
    public Optional<EntityDefinition> findDraftEntityByConnectorIdAndApiName(String connectorId, String apiName) {
        return Optional.empty();
    }

    @Override
    public List<EntityDefinition> findEntityVersions(String connectorId, String apiName) {
        return self.findAllByConnectorId(connectorId).stream()
                .filter(e -> e.getApiName().equals(apiName)).collect(Collectors.toList());
    }

    @Override
    public Optional<EntityDefinition> findActiveDraftFor(String parentId) {
        return self.findAllByParentId(parentId).stream().filter(EntityDefinition::isDraft).findFirst();
    }

    @Override
    public void deleteAllById(List<String> ids) {
        ids.stream().map(id -> self.findById(id)).flatMap(Optional::stream).forEach(e -> self.delete(e));
    }

    @Override
    @CacheEvict(value="entityDefCache",allEntries=true)
    public void reset() {
        entityDefinitionRepo.reset();
    }

    @Override
    public <S extends EntityDefinition> List<S> saveAll(Iterable<S> iterable) {
        List<S> list = new ArrayList<>();
        iterable.forEach(i -> list.add((S)self.save(i)));
        return list;
    }

    @Override
    public boolean existsById(String s) {
        return false;
    }

    @Override
    public List<EntityDefinition> findAll() {
        return entityDefinitionRepo.findAll();
    }

    @Override
    public Iterable<EntityDefinition> findAllById(Iterable<String> iterable) {
        List<EntityDefinition> result = new ArrayList<>();
        iterable.forEach(e -> self.findById(e).ifPresent(result::add));
        return result;
    }

    @Override
    public long count() {
        return entityDefinitionRepo.count();
    }

    @Override
    public void deleteById(String s) {
        self.findById(s).ifPresent(e -> self.save(e));
    }

    @Override
    public void deleteAll(Iterable<? extends EntityDefinition> iterable) {
        iterable.forEach(i -> delete(i));
    }

    @Override
    @CacheEvict(value="entityDefCache",allEntries=true)
    public void deleteAll() {
        entityDefinitionRepo.deleteAll();
    }

    @Override
    public List<EntityDefinition> findAll(Sort sort) {
        return entityDefinitionRepo.findAll(sort);
    }

    @Override
    public Page<EntityDefinition> findAll(Pageable pageable) {
        return entityDefinitionRepo.findAll(pageable);
    }

    @Override
    public <S extends EntityDefinition> S insert(S s) {
        throw new UnsupportedOperationException("Unsupported operation insert");
    }

    @Override
    public <S extends EntityDefinition> List<S> insert(Iterable<S> iterable) {
        throw new UnsupportedOperationException("Unsupported operation insert");
    }

    @Override
    public <S extends EntityDefinition> Optional<S> findOne(Example<S> example) {
        return entityDefinitionRepo.findOne(example);
    }

    @Override
    public <S extends EntityDefinition> List<S> findAll(Example<S> example) {
        return entityDefinitionRepo.findAll(example);
    }

    @Override
    public <S extends EntityDefinition> List<S> findAll(Example<S> example, Sort sort) {
        return entityDefinitionRepo.findAll(example, sort);
    }

    
    @Override
    public <S extends EntityDefinition> Page<S> findAll(Example<S> example, Pageable pageable) {
        return entityDefinitionRepo.findAll(example, pageable);
    }

    @Override
    public <S extends EntityDefinition> long count(Example<S> example) {
        return entityDefinitionRepo.count(example);
    }

    @Override
    public <S extends EntityDefinition> boolean exists(Example<S> example) {
        return entityDefinitionRepo.exists(example);
    }

    @Override
    public List<EntityDefinition> findByConnectorIdAndDraftStatus(String connectorId, String draftStatus, String entityId, int limit) {
        return entityDefinitionRepo.findByConnectorIdAndDraftStatus(connectorId, draftStatus, entityId, limit);
    }
}
