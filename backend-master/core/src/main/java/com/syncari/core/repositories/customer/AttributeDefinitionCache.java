package com.syncari.core.repositories.customer;

import com.syncari.connector.Constants;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.service.ConnectorMetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AttributeDefinitionCache implements AttributeRepo {

    @Autowired
    private AttributeRepo attributeRepo;

    @Resource
    private AttributeDefinitionCache self;

    public List<AttributeDefinition> findActiveByEntityId(String entityId) {
        return self.findByEntityId(entityId).stream().filter(AttributeDefinition::isActive).collect(Collectors.toList());
    }

    public List<AttributeDefinition> findAllByDataType(String datatype) {
        return attributeRepo.findAllByDataType(datatype);
    }

    @Override
    public List<AttributeDefinition> findActiveByEntityIds(Iterable<String> entityIds) {
        Map<String, AttributeDefinition> attributes = new LinkedHashMap<>();
        entityIds.forEach(e -> findActiveByEntityId(e).stream().forEach(a -> attributes.put(a.getId(), a)));
        return new ArrayList<>(attributes.values());
    }

    @Cacheable(value = "attributeDefCache", key = "'entity:'.concat(#entityId != null ? #entityId : '')")
    public List<AttributeDefinition> findByEntityId(String entityId) {
        return attributeRepo.findByEntityId(entityId);
    }

    @Cacheable(value = "attributeDefCache", key = "'attribute:'.concat(#attributeId != null ? #attributeId : '')", unless = "#result == null")
    @Override
    public Optional<AttributeDefinition> findById(String attributeId) {
        return attributeRepo.findById(attributeId);
    }

    @Override
    public List<AttributeDefinition> findAllById(Iterable<String> attributeIds) {
        List<AttributeDefinition> attributes = new ArrayList<>();
        attributeIds.forEach(a -> findById(a).ifPresent(attr -> attributes.add(attr)));
        return attributes;
    }

    public Optional<AttributeDefinition> findByEntityIdAndApiName(String entityId, String apiName) {
        return self.findByEntityId(entityId).stream().filter(a -> apiName.equalsIgnoreCase(a.getApiName()) && !a.isArchived()).findFirst();
    }

    @Caching(evict = {
            @CacheEvict(value = "attributeDefCache", key = "'entity:'.concat(#attributeDefinition.getEntityId())"),
            @CacheEvict(value = "attributeDefCache", key = "'attribute:'.concat(#attributeDefinition.getId())")
    })
    @Override
    public AttributeDefinition save(AttributeDefinition attributeDefinition) {
        return attributeRepo.save(attributeDefinition);
    }

    @Caching(evict = {
            @CacheEvict(value = "attributeDefCache", key = "'entity:'.concat(#attributeDefinition.getEntityId())"),
            @CacheEvict(value = "attributeDefCache", key = "'attribute:'.concat(#attributeDefinition.getId())")
    })
    public void delete(AttributeDefinition attributeDefinition) {
        attributeRepo.delete(attributeDefinition);
    }

    public List<AttributeDefinition> saveAll(List<AttributeDefinition> attributeDefinitions) {
        return attributeDefinitions.stream().map(a -> self.save(a)).collect(Collectors.toList());
    }

    public void deleteAll(List<AttributeDefinition> attributeDefinitions) {
        attributeDefinitions.stream().forEach(a -> self.delete(a));
    }

    @Cacheable(value = "entityDefCache", key = "'parent:'.concat(#parentId)")
    @Override
    public List<AttributeDefinition> findAllByParentId(String parentId) {
        return attributeRepo.findAllByParentId(parentId);
    }

    @Override
    public Optional<AttributeDefinition> findActiveDraftFor(String parentId) {
        return self.findAllByParentId(parentId).stream().filter(AttributeDefinition::isDraft).findFirst();
    }

    @Override
    public void deleteAllById(List<String> ids) {
        ids.stream().map(id -> self.findById(id)).flatMap(Optional::stream).forEach(e -> self.delete(e));
    }

    @Override
    @CacheEvict(value="attributeDefCache",allEntries=true)
    public void reset() {
        attributeRepo.reset();
    }

    @Override
    public <S extends AttributeDefinition> List<S> saveAll(Iterable<S> iterable) {
        List<S> list = new ArrayList<>();
        iterable.forEach(i -> list.add((S)self.save(i)));
        return list;
    }

    @Override
    public boolean existsById(String s) {
        return attributeRepo.existsById(s);
    }

    @Override
    public List<AttributeDefinition> findAll() {
        return attributeRepo.findAll();
    }

    @Override
    public long count() {
        return attributeRepo.count();
    }

    @Override
    public void deleteById(String s) {
        self.findById(s).ifPresent(a -> self.delete(a));
    }

    @Override
    public void deleteAll(Iterable<? extends AttributeDefinition> iterable) {
        iterable.forEach(s -> delete(s));
    }

    @Override
    public void deleteAll() {
        throw new UnsupportedOperationException("This operation not supported for cache");
    }

    @Override
    public List<AttributeDefinition> findAll(Sort sort) {
        return attributeRepo.findAll(sort);
    }

    @Override
    public Page<AttributeDefinition> findAll(Pageable pageable) {
        return attributeRepo.findAll(pageable);
    }

    @Override
    public <S extends AttributeDefinition> S insert(S s) {
        throw new UnsupportedOperationException("This operation not supported for cache");
    }

    @Override
    public <S extends AttributeDefinition> List<S> insert(Iterable<S> iterable) {
        throw new UnsupportedOperationException("This operation not supported for cache");
    }

    @Override
    public <S extends AttributeDefinition> Optional<S> findOne(Example<S> example) {
        return attributeRepo.findOne(example);
    }

    @Override
    public <S extends AttributeDefinition> List<S> findAll(Example<S> example) {
        return attributeRepo.findAll(example);
    }

    @Override
    public <S extends AttributeDefinition> List<S> findAll(Example<S> example, Sort sort) {
        return attributeRepo.findAll(example, sort);
    }

    @Override
    public <S extends AttributeDefinition> Page<S> findAll(Example<S> example, Pageable pageable) {
        return attributeRepo.findAll(example, pageable);
    }

    @Override
    public <S extends AttributeDefinition> long count(Example<S> example) {
        return attributeRepo.count();
    }

    @Override
    public <S extends AttributeDefinition> boolean exists(Example<S> example) {
        return attributeRepo.exists(example);
    }

	@Override
	public List<AttributeDefinition> findExternalId(List<String> entityIds) {
		return attributeRepo.findExternalId(entityIds);
	}

	@Override
	public List<AttributeDefinition> findActiveAndInactiveByEntityIds(Iterable<String> entityIds) {
		Map<String, AttributeDefinition> attributes = new LinkedHashMap<>();
		entityIds.forEach(e -> findByEntityId(e).stream()
				.filter(attr -> attr.getStatus() == Status.ACTIVE || attr.getStatus() == Status.INACTIVE)
				.forEach(a -> attributes.put(a.getId(), a)));
        return new ArrayList<>(attributes.values());
	}
}
