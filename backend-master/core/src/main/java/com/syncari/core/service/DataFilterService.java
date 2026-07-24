package com.syncari.core.service;

import static com.syncari.utils.I18n.i18n;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.mongodb.BasicDBObject;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.DataFilter;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.core.repositories.customer.DataFilterRepo;
import com.syncari.core.utils.CustomerMongoUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DataFilterService {
    @Autowired
    private DataFilterRepo repo;
    @Autowired
    private CustomerMongoUtils customerMongoUtils;

    public Page<DataFilter> getAll(PageCursor pageInfo) {
        Bson sort = new BasicDBObject("name", 1);
        Function<Document, DataFilter> converter = document -> createEntry(document);
        List<DataFilter> records = customerMongoUtils.searchPaged("dataFilter", Optional.empty(), sort, converter,
                pageInfo.getPageSize() + 1);
        boolean hasMore = records.size() == pageInfo.getPageSize() + 1;
        if (records.size() > pageInfo.getPageSize()) {
            records = records.subList(0, records.size() - 1);
        }
        String pageStart = records.size() > 0 ? records.get(0).getId() : null;
        String pageEnd = records.size() > 0 ? records.get(records.size() - 1).getId() : null;
        Page<DataFilter> page = new Page<>();
        page.setRecords(records);
        page.setPageInfo(
                new PageInfo(pageStart, pageEnd, hasMore).addSort("name", true));
        return page;
    }
    
    public Page<DataFilter> getByEntity(String entityId, PageCursor pageInfo) {
        Bson sort = new BasicDBObject("name", 1);
        Bson condition = new BasicDBObject("syncariEntityId", entityId);
        Function<Document, DataFilter> converter = document -> createEntry(document);
        List<DataFilter> records = customerMongoUtils.searchPaged("dataFilter", Optional.of(condition), sort, converter,
                pageInfo.getPageSize() + 1);
        boolean hasMore = records.size() == pageInfo.getPageSize() + 1;
        if (records.size() > pageInfo.getPageSize()) {
            records = records.subList(0, records.size() - 1);
        }
        String pageStart = records.size() > 0 ? records.get(0).getId() : null;
        String pageEnd = records.size() > 0 ? records.get(records.size() - 1).getId() : null;
        Page<DataFilter> page = new Page<>();
        page.setRecords(records);
        page.setPageInfo(
                new PageInfo(pageStart, pageEnd, hasMore).addSort("name", true));
        return page;
    }
    
    public Page<DataFilter> getByEntityByIds(String entityId, Set<String> ids, PageCursor pageInfo) {
        Iterator<DataFilter> records = repo.findBySyncariEntityIdAndIdsIn(entityId, ids.stream().map(i -> new ObjectId(i)).collect(Collectors.toList())).iterator();
        return getPage(pageInfo, records);
    }

    public Page<DataFilter> getByIds(Set<String> ids, PageCursor pageInfo) {
        Iterator<DataFilter> records = repo.findAllById(ids).iterator();
        return getPage(pageInfo, records);
    }

    public DataFilter create(DataFilter filter) {
        validate(filter);
        filter.setId(null);
        return repo.save(filter);
    }

    public DataFilter update(DataFilter filter) {
        if(StringUtils.isBlank(filter.getId())) {
            throw new SyncariValidationException(i18n("filter_id_required"));
        }
        validate(filter);
        Optional<DataFilter> existing = repo.findById(filter.getId());
        if(existing.isEmpty()) {
            throw new SyncariValidationException(String.format(i18n("invalid_filter_id"), filter.getId()));
        }
        if(!existing.get().getSyncariEntityId().equalsIgnoreCase(filter.getSyncariEntityId())) {
            throw new SyncariValidationException(i18n("filter_entity_cannot_change"));
        }
        return repo.save(filter);
    }
    
    public void delete(String filterId) {
        repo.deleteById(filterId);
        log.info("Data Filter with id {} deleted");
    }
    
    private DataFilter createEntry(Document document) {
        var record = new DataFilter();
        record.setSyncariEntityId(document.getString("syncariEntityId"));
        record.setId(document.getObjectId("_id").toHexString());
        record.setName(document.getString("name"));
        record.setCriteria((Map) document.get("criteria"));
        record.setTags((List<String>) document.get("tags"));
        record.setDescription(document.getString("description"));
        record.setCreatedAt(document.getDate("createdAt"));
        record.setUpdatedAt(document.getDate("updatedAt"));
        record.setCreatedBy(document.getString("createdBy"));
        record.setUpdatedBy(document.getString("updatedBy"));
        return record;
    }

    private void validate(DataFilter filter) {
        if(filter == null) throw new SyncariValidationException(i18n("filter_required"));
        if(StringUtils.isBlank(filter.getName())) {
            throw new SyncariValidationException(i18n("filter_name_required"));
        }
        if(StringUtils.isBlank(filter.getSyncariEntityId())) {
            throw new SyncariValidationException(i18n("filter_entity_required"));
        }
        if(filter.getCriteria() == null || filter.getCriteria().isEmpty()) {
            throw new SyncariValidationException(i18n("filter_criteria_required"));
        }
    }
    
    private Page<DataFilter> getPage(PageCursor pageInfo, Iterator<DataFilter> records) {
        Page<DataFilter> page = new Page<>();
        page.setRecords(Lists.newArrayList(records));
        String pageStart = page.getRecords().size() > 0 ? page.getRecords().get(0).getId() : null;
        String pageEnd = page.getRecords().size() > 0 ? page.getRecords().get(page.getRecords().size() - 1).getId() : null;
        page.setPageInfo(new PageInfo(pageStart, pageEnd, page.getRecords().size() > pageInfo.getPageSize()).addSort("name", true));
        return page;
    }
}
