package com.syncari.viper.simulation;

import com.syncari.connector.EntityData;
import com.syncari.core.Features;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.utils.Criteria;
import com.syncari.core.utils.CustomerMongoUtils;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimulationEntityRepo extends EntityRepo {
    Map<String, EntityData> entities = new ConcurrentHashMap<>();

    public SimulationEntityRepo() {
        super();
    }

    @Override
    public EntityData save(EntityData entity) {
        String id = entity.getSyncariEntityId() == null ? ObjectId.get().toHexString() : entity.getSyncariEntityId();
        entity.setId(id);
        entity.setSyncariEntityId(id);
        entities.put(id, entity);
        return entity;
    }

    public EntityData save(EntityDefinition entityDefinition, EntityData entity) {
        return save(entity);
    }

    @Override
    public Slice<EntityData> find(String entityName, Instant start, Pageable page) {
        return new SliceImpl<>(new ArrayList<>(entities.values()));
    }

    @Override
    public List<EntityData> find(EntityDefinition entityDefinition, Instant start, PageCursor page) {
        return new ArrayList<>(entities.values());
    }

    @Override
    public Slice<EntityData> find(EntityDefinition entityDefinition, Instant start, Pageable page) {
        return new SliceImpl<>(new ArrayList<>(entities.values()));
    }

    @Override
    public Optional<EntityData> findById(EntityDefinition entityDefinition, String id) {
        return Optional.ofNullable(entities.get(id));
    }

    @Override
    public Iterable<EntityData> findByIds(EntityDefinition syncariEntityDefinition, Set<String> ids) {
        return new ArrayList<>();
    }

    @Override
    public void createIndexes(EntityDefinition entityDefinition, List<AttributeDefinition> attributes) {
        // No-op
    }

    @Override
    public void deleteAll(String entityName) {
        // No-op
    }

    @Override
    public void deleteAll(EntityDefinition entityDefinition, List<EntityData> entityDataList) {
        // No-op
    }

    @Override
    public void createCollection(EntityDefinition entityDefinition) {
        // Do Nothing
    }

    @Override
    public void updateLastTransaction(EntityDefinition entityDefinition, List<EntityData> entities) {
        // Do Nothing
    }

    public void updateValues(EntityDefinition entityDefinition, List<EntityData> updatedValues) {
        // Do nothing
    }

    @Override
    public void saveEntityBatch(EntityDefinition entityDefinition, List<EntityData> entityBatch, List<IdMapping> idMappings) {
        entityBatch.forEach(this::save);
    }

    @Override
    public long countWithFallback(EntityDefinition def, Optional<? extends Criteria> redisCriteria, Optional<? extends Criteria> mongoCriteria, boolean useCache) {
        return 0;
    }

    @Override
    public long count(EntityDefinition def,Optional<? extends Criteria> visitor) {
        return 0;
    }
}
