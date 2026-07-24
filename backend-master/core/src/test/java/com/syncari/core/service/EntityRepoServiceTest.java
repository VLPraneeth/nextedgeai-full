package com.syncari.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.ExternalIdType;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.dfi.DfiRuleAssignmentServiceTest;
import com.syncari.core.dfi.RuleConstants;
import com.syncari.core.model.*;
import com.syncari.core.model.RuleDefinition.Impact;
import com.syncari.core.model.RuleDefinition.RuleType;
import com.syncari.core.model.misc.EntityScoreWrapper;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.repositories.customer.*;
import com.syncari.utils.DateUtil;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import javax.xml.bind.DatatypeConverter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class EntityRepoServiceTest extends AbstractSyncariTest {
    @Autowired
    EntityRepoService service;
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    EntityDatabaseRepo entityDatabaseRepo;
    @Autowired
    SchemaService schemaService;
    @Autowired
    DfiRuleAssignmentService dfiRuleAssignmentService;
    @Autowired
    DfiRuleAssignmentRepo dfiRuleAssignmentRepo;
    @Autowired
    EntityDataScoreSnapshotRepo entitySnapshotRepo;
    @Autowired
    FieldDataScoreSnapshotRepo fieldSnapshotRepo;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    DateUtil util;
    @Autowired
    CustomDataScoreRepoImpl scoreImpl;
    @Autowired
    IdMappingService mappingService;
    @Autowired
    IdMappingRepo mappingRepo;
    @Autowired
    TransactionLogService txnService;
    @Autowired
    BatchRepo batchRepo;
    ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() {
        super.setUp();
        entityRepo.deleteAll("account");
        entityRepo.deleteAll("user");
        entityRepo.deleteAll("contact");
        entityRepo.deleteAll("opportunity");
        entitySnapshotRepo.deleteAll();
        fieldSnapshotRepo.deleteAll();
        fieldSnapshotRepo.deleteAll();
        //txnService.deleteAll();
    }
    @After
    public void cleanup(){
        entityRepo.deleteAll("account");
        entityRepo.deleteAll("contact");
        entityRepo.deleteAll("user");
        entityRepo.deleteAll("opportunity");
        mappingRepo.deleteAll();
        entitySnapshotRepo.deleteAll();
        fieldSnapshotRepo.deleteAll();
        super.tearDown();
    }

    @Test
    public void getTotalRecords() {
        assertEquals(0, service.getCount("account"));

        EntityData data1 = new EntityData("account");
        data1.addValue("name", "test account1");
        data1.addValue("website", "www.test1.com");
        data1.addValue("test_field", "test value1");
        data1.addValue("watermark", 6);
        data1 = entityRepo.save(data1);

        assertEquals(1, service.getCount("account"));
        entityRepo.deleteAll(new EntityDefinition("account", "account"), List.of(data1));
    }

    @Test
    public void deleteAllForEntity() {
        assertEquals(0, service.getCount("account"));

        EntityData data1 = new EntityData("account");
        data1.addValue("name", "test account1");
        data1.addValue("website", "www.test1.com");
        data1.addValue("test_field", "test value1");
        data1.addValue("watermark", 6);
        data1 = entityRepo.save(data1);

        EntityData data2 = new EntityData("contact");
        data2.addValue("name", "test contact");
        data2.addValue("website", "www.test1.com");
        data2.addValue("test_field", "test value1");
        data2.addValue("watermark", 6);
        data2.addValue("hasOptedOutOfEmail", false);
        data2 = entityRepo.save(data2);

        IdMapping idMapping = new IdMapping().setSyncariId(data2.getSyncariEntityId())
            .addMapping("123", data1.getId(), "123").setEntityName("account");
        mappingService.save(idMapping);
        assertEquals(1, mappingRepo.count());

        DatastoreService mockService = mock(DatastoreService.class);
        ArgumentCaptor<List> captor1 = ArgumentCaptor.forClass(List.class);
        service.datastoreService = mockService;
        entityRepo.setDatastoreService(mockService);
        entityDatabaseRepo.setDatastoreService(mockService);

        doNothing().when(mockService).deleteEntity(any(), any());

        connectorService.getSyncariDatastore().ifPresentOrElse(c -> {}, () -> {
        	Connector connector = new Connector();
        	connector.setMetadata(connectorService.describe("datastore"));
        	connector.setMetadataId(connector.getMetadata().getId());
        	connector.setAuthConfig(new AuthConfig());
        	connector.setName("test");
			connectorService.save(connector);
        });
        assertEquals(1, service.getCount("account"));
        assertEquals(1, service.getCount("contact"));
        entityRepo.delete("account");
        assertEquals(0, mappingRepo.count());
        assertEquals(0, service.getCount("account"));
        assertEquals(1, service.getCount("contact"));
    }

    @Test
    public void deleteRecord() {
        assertEquals(0, service.getCount("account"));

        EntityData data1 = new EntityData("account");
        data1.addValue("name", "test account1");
        data1.addValue("website", "www.test1.com");
        data1.addValue("test_field", "test value1");
        data1.addValue("watermark", 6);
        data1 = entityRepo.save(data1);

        IdMapping idMapping = new IdMapping().setSyncariId(data1.getSyncariEntityId())
            .addMapping("123", data1.getId(), "123").setEntityName("account");
        mappingService.save(idMapping);
        assertEquals(1, mappingRepo.count());

        assertNotNull(entityRepo.findByIds("account", Set.of(data1.getId())));

        // soft delete
        service.deleteRecord("account", data1.getId(), true);
        assertNotNull(entityRepo.findByIds("account", Set.of(data1.getId())).iterator().next());
        assertEquals(1, mappingRepo.count());

        try {
            // hard delete
            service.deleteRecord("account", data1.getId(), false);
            entityRepo.findByIds("account", Set.of(data1.getId())).iterator().next();
            fail();
        } catch (Exception e) {
            assertEquals(e.getClass(), NoSuchElementException.class);
            assertEquals(0, mappingRepo.count());
        }
    }
    
    @Test
    public void deleteRecords() throws JsonProcessingException {
        assertEquals(0, service.getCount("account"));

        EntityData data1 = new EntityData("account");
        data1.addValue("Name", "test account");
        data1.addValue("Website", "www.test1.com");
        data1.addValue("test_field", "test value1");
        data1.addValue("watermark", 6);
        data1 = entityRepo.save(data1);
        EntityData data2 = new EntityData("account");
        data2.addValue("Name", "test account");
        data2.addValue("Website", "www.test2.com");
        data2.addValue("test_field", "test value2");
        data2.addValue("watermark", 7);
        data2 = entityRepo.save(data2);

        IdMapping idMapping = new IdMapping().setSyncariId(data1.getSyncariEntityId())
            .addMapping("123", data1.getId(), "123").setEntityName("account");
        mappingService.save(idMapping);

        idMapping = new IdMapping().setSyncariId(data2.getSyncariEntityId())
                .addMapping("123", data2.getId(), "123").setEntityName("account");
        mappingService.save(idMapping);

        assertEquals(2, mappingRepo.count());

        assertNotNull(entityRepo.findByIds("account", Set.of(data1.getId())));

        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        Map<String, Object> map = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("Website").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "www.test1.com")
        );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(map).getBytes());
        // soft delete
        Batch batch = new Batch().setEntityId(entityDefinition.getId()).setConfig(Map.of(EntityRepoService.DELETE_IN_END_SYSTEMS, true, "filter", base64));
		service.deleteRecords(entityDefinition.getId(), batchRepo.save(batch));
        assertNotNull(entityRepo.findByIds("account", Set.of(data1.getId())).iterator().next());
        assertEquals(1, entityRepo.count(entityDefinition.getApiName(), true));
        assertEquals(2, mappingRepo.count());

        // hard delete
        map = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("Name").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "test account")
        );
        base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(map).getBytes());
        batch = new Batch().setConfig(Map.of("filter", base64));
        batch.setEntityId(entityDefinition.getId());
        service.deleteRecords(entityDefinition.getId(), batchRepo.save(batch));
        assertEquals(0, entityRepo.count(entityDefinition.getApiName(), false));
        assertEquals(0, mappingRepo.count());
    }

    @Test
    public void deleteRecordsPage() throws JsonProcessingException {
        assertEquals(0, service.getCount("account"));

        List<EntityData> entities = new ArrayList<>();
        for (int i=0; i < 1200; i++) {
            EntityData data = new EntityData("account");
            data.addValue("Name", "test account " + i);
            data.addValue("Website", "www.test1.com");
            data.addValue("test_field", "test value1");
            data.addValue("watermark", 6);
            data = entityRepo.save(data);
            entities.add(data);

            IdMapping idMapping = new IdMapping().setSyncariId(data.getSyncariEntityId())
                    .addMapping("123", data.getId(), "123").setEntityName("account");
            mappingService.save(idMapping);
        }

        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();

        Map<String, Object> map = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("Website").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "www.test1.com")
        );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(map).getBytes());
        // soft delete
        Batch batch = new Batch().setEntityId(entityDefinition.getId()).setConfig(Map.of(EntityRepoService.DELETE_IN_END_SYSTEMS, true, "filter", base64));

        service.deleteRecords(entityDefinition.getId(), batchRepo.save(batch));

        assertEquals(1200, entityRepo.count(entityDefinition.getApiName(), true));

        batch = new Batch().setEntityId(entityDefinition.getId()).setConfig(Map.of(EntityRepoService.DELETE_IN_END_SYSTEMS, false, "filter", base64));
        service.deleteRecords(entityDefinition.getId(), batchRepo.save(batch));
        assertEquals(0, entityRepo.count(entityDefinition.getApiName(), true));
    }
    
    @Test
    public void updateRecords() throws JsonProcessingException {
        assertEquals(0, service.getCount("account"));

        long start = Instant.now().toEpochMilli();

        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        EntityData data1 = new EntityData("account");
        data1.addValue("Name", "test account");
        data1.addValue("Website", "www.test1.com");
        data1.addValue("test_field", "test value1");
        data1.addValue("watermark", 6);
        data1 = entityRepo.save(entityDefinition, data1);
        EntityData data2 = new EntityData("account");
        data2.addValue("Name", "test account");
        data2.addValue("Website", "www.test2.com");
        data2.addValue("test_field", "test value2");
        data2.addValue("watermark", 7);
        data2 = entityRepo.save(entityDefinition, data2);

        Map<String, Object> map = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("Website").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "www.test1.com")
        );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(map).getBytes());
        
        Batch batch = new Batch();
        batch.getConfig().put("changes", Map.of("Name", "test account changed", "Website", "www.test1.com changed"));
        batch.getConfig().put("filter", base64);
        batch.setEntityId(entityDefinition.getId());
        batch = batchRepo.save(batch);
        service.updateRecords(entityDefinition.getId(), batch);
        Iterator<EntityData> iterator = entityRepo.findByIds(entityDefinition, Set.of(data1.getId(), data2.getId())).iterator();
        EntityData saved1 = iterator.next();
        EntityData saved2 = iterator.next();
        assertEquals("test account changed", saved1.getValue("Name"));
        assertEquals("www.test1.com changed", saved1.getValue("Website"));
        assertTrue(saved1.getLastTransactionLogId() != null);
        AttributeDefinition nameAttrib = schemaService.getAttributeByName(entityDefinition.getId(), "Name");
        AttributeDefinition websiteAttrib = schemaService.getAttributeByName(entityDefinition.getId(), "Website");
        assertTrue(!txnService.findByTransactionLogId(saved1.getLastTransactionLogId(), start).isEmpty());
        assertEquals("test account changed", txnService.findByTransactionLogId(saved1.getLastTransactionLogId(), start).get().getChanges().get(nameAttrib.getId()).getNewValue());
        assertEquals("www.test1.com changed", txnService.findByTransactionLogId(saved1.getLastTransactionLogId(),start).get().getChanges().get(websiteAttrib.getId()).getNewValue());

        assertEquals("test account", saved2.getValue("Name"));
        assertEquals("www.test2.com", saved2.getValue("Website"));
        assertFalse(saved2.getLastTransactionLogId() != null);
    }

    @Test
    public void disconnectExternalId() {
        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        entityDefinition.getAttributes().get(0).setReferenceTo("656757656").setDataType(new ExternalIdType());
        String extFieldName = entityDefinition.getExternalIdFields().get(0).getApiName();

        EntityData existing = new EntityData();
        existing.addValue(entityDefinition.getExternalIdFields().get(0).getApiName(), "123");
        service.disconnectExternalId(entityDefinition, existing, "656757656", Optional.empty(), Optional.empty());
        assertNull(existing.getValue(extFieldName));

        EntityData incoming = new EntityData();
        existing.addValue(entityDefinition.getExternalIdFields().get(0).getApiName(), "123");
        incoming.addValue(entityDefinition.getExternalIdFields().get(0).getApiName(), "234");
        incoming.setId("234");
        service.disconnectExternalId(entityDefinition, existing, "656757656", Optional.empty(), Optional.of(incoming));
        assertNotNull(existing.getValue(extFieldName));
    }
    
    @Test
    public void updateMultiPageRecords() throws JsonProcessingException {
    	EntityRepoService service1 = spy(service);
    	when(service1.getPageSize()).thenReturn(1);
        assertEquals(0, service.getCount("account"));
        Instant start = Instant.now();
        assertEquals(0, txnService.count(start));
        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        EntityData tmpData1 = new EntityData("account");
        tmpData1.addValue("Name", "test account");
        tmpData1.addValue("Website", "www.test1.com");
        tmpData1.addValue("test_field", "test value1");
        tmpData1.addValue("watermark", 6);
        final EntityData data1 = entityRepo.save(entityDefinition, tmpData1);
        EntityData tmpData2 = new EntityData("account");
        tmpData2.addValue("Name", "test account");
        tmpData2.addValue("Website", "www.test2.com");
        tmpData2.addValue("test_field", "test value2");
        tmpData2.addValue("watermark", 7);
        final EntityData data2 = entityRepo.save(entityDefinition, tmpData2);

        Map<String, Object> map = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("LastModifiedDate").getId()),
                "operator", "empty"
        );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(map).getBytes());
        
        Batch batch = new Batch();
        batch.getConfig().put("changes", Map.of("Name", "test account changed", "Website", "www.test1.com changed"));
        batch.getConfig().put("filter", base64);
        batch.setEntityId(entityDefinition.getId());
        batch = batchRepo.save(batch);
        service1.updateRecords(entityDefinition.getId(), batch);
        Iterator<EntityData> iterator = entityRepo.findByIds(entityDefinition, Set.of(data1.getId(), data2.getId())).iterator();
        EntityData saved1 = iterator.next();
        EntityData saved2 = iterator.next();
        assertEquals("test account changed", saved1.getValue("Name"));
        assertEquals("www.test1.com changed", saved1.getValue("Website"));
        assertEquals("test account changed", saved2.getValue("Name"));
        assertEquals("www.test1.com changed", saved2.getValue("Website"));
        assertEquals(2, txnService.count(start));
        assertTrue(txnService.findAll(Pageable.unpaged(), start).stream().filter(txn -> txn.getSyncariId().equalsIgnoreCase(data1.getId())).findFirst().isPresent());
        assertTrue(txnService.findAll(Pageable.unpaged(), start).stream().filter(txn -> txn.getSyncariId().equalsIgnoreCase(data2.getId())).findFirst().isPresent());
    }

    @Test
    public void startWith() {
        assertEquals(0, service.getCount("account"));

        EntityData data1 = new EntityData("account");
        data1.addValue("Name", "test account1");
        data1.addValue("Website", "www.test1.com");
        data1.addValue("Phone", "+1 222");
        data1 = entityRepo.save(data1);

        assertEquals(1, service.getCount("account"));

        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        Map<String, Object> map = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("Phone").getId()),
                "operator", "starts_with",
                "right", Map.of("type", "literal", "value", "+1")
        );
        Optional<Expression> filter = Optional.of(new PredicateParser(StringUtils.EMPTY).fromMap(map));
        PageCursor pageInfo = new PageCursor(0, 10);
        Page<EntityData> query = service.query(entityDefinition.getId() , filter, pageInfo,true);
        assertEquals(1, query.getRecords().size());

        entityRepo.deleteAll(new EntityDefinition("account", "account"), List.of(data1));
    }

    @Test
    public void contains() {
        assertEquals(0, service.getCount("account"));

        EntityData data1 = new EntityData("account");
        data1.addValue("Name", "test account1");
        data1.addValue("Website", "www.test1.com");
        data1.addValue("Phone", "+1 222");
        data1 = entityRepo.save(data1);

        assertEquals(1, service.getCount("account"));

        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        Map<String, Object> map = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("Name").getId()),
                "operator", "contains",
                "right", Map.of("type", "literal", "value", "ccou")
                );
        Optional<Expression> filter = Optional.of(new PredicateParser(StringUtils.EMPTY).fromMap(map));
        PageCursor pageInfo = new PageCursor(0, 10);
        Page<EntityData> query = service.query(entityDefinition.getId() , filter, pageInfo,true);
        assertEquals(1, query.getRecords().size());
        assertEquals(1, query.getPageInfo().getFilteredCount());
        assertEquals(1, service.countData(entityDefinition.getId() , filter));

        entityRepo.deleteAll(new EntityDefinition("account", "account"), List.of(data1));
    }
    @Test
    public void in() {
        assertEquals(0, service.getCount("account"));

        EntityData data1 = new EntityData("account");
        data1.addValue("Name", "test account1");
        data1.addValue("Website", "www.test1.com");
        data1.addValue("Phone", "+1 222");
        data1 = entityRepo.save(data1);

        assertEquals(1, service.getCount("account"));

        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        Map<String, Object> map = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("Website").getId()),
                "operator", "in",
                "right", Map.of("type", "literal", "value", List.of("www.test1.com","www.test2.com","www.test3.com"))
        );
        Optional<Expression> filter = Optional.of(new PredicateParser(StringUtils.EMPTY).fromMap(map));
        PageCursor pageInfo = new PageCursor(0, 10);
        Page<EntityData> query = service.query(entityDefinition.getId() , filter, pageInfo,true);
        assertEquals(1, query.getRecords().size());
        assertEquals(1, query.getPageInfo().getFilteredCount());

        Map<String, Object> noMatch = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("Website").getId()),
                "operator", "in",
                "right", Map.of("type", "literal", "value", List.of("www.test2.com","www.test3.com"))
        );
        Optional<Expression> noMatchFilter = Optional.of(new PredicateParser(StringUtils.EMPTY).fromMap(noMatch));
        Page<EntityData> results = service.query(entityDefinition.getId() , noMatchFilter, new PageCursor(0, 10),true);
        assertEquals(0, results.getRecords().size());

        entityRepo.deleteAll(new EntityDefinition("account", "account"), List.of(data1));
    }

    @Test
    public void notIn() {
        assertEquals(0, service.getCount("account"));

        EntityData data1 = new EntityData("account");
        data1.addValue("Name", "test account1");
        data1.addValue("Website", "www.test1.com");
        data1.addValue("Phone", "+1 222");
        data1 = entityRepo.save(data1);

        assertEquals(1, service.getCount("account"));

        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        Map<String, Object> map = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("Website").getId()),
                "operator", "not_in",
                "right", Map.of("type", "literal", "value", List.of("www.test2.com","www.test3.com"))
        );
        Optional<Expression> filter = Optional.of(new PredicateParser(StringUtils.EMPTY).fromMap(map));
        PageCursor pageInfo = new PageCursor(0, 10);
        Page<EntityData> query = service.query(entityDefinition.getId() , filter, pageInfo,true);
        assertEquals(1, query.getRecords().size());
        assertEquals(1, query.getPageInfo().getFilteredCount());
        assertEquals(1, service.countData(entityDefinition.getId() , filter));

        Map<String, Object> noMatch = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("Website").getId()),
                "operator", "not_in",
                "right", Map.of("type", "literal", "value", List.of("www.test1.com","www.test2.com","www.test3.com"))
        );
        Optional<Expression> noMatchFilter = Optional.of(new PredicateParser(StringUtils.EMPTY).fromMap(noMatch));
        Page<EntityData> results = service.query(entityDefinition.getId() , noMatchFilter, new PageCursor(0, 10),true);
        assertEquals(0, results.getRecords().size());

        entityRepo.deleteAll(new EntityDefinition("account", "account"), List.of(data1));
    }
    @Test
    public void notContains() {
        assertEquals(0, service.getCount("account"));

        EntityData data1 = new EntityData("account");
        data1.addValue("Name", "test account1");
        data1.addValue("Website", "www.test1.com");
        data1.addValue("Phone", "+1 222");
        data1 = entityRepo.save(data1);

        assertEquals(1, service.getCount("account"));

        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        Map<String, Object> map = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("Name").getId()),
                "operator", "not_contains",
                "right", Map.of("type", "literal", "value", "invalid")
                );
        Optional<Expression> filter = Optional.of(new PredicateParser(StringUtils.EMPTY).fromMap(map));
        PageCursor pageInfo = new PageCursor(0, 10);
        Page<EntityData> query = service.query(entityDefinition.getId() , filter, pageInfo,true);
        assertEquals(1, query.getRecords().size());
        assertEquals(1, query.getPageInfo().getFilteredCount());

        entityRepo.deleteAll(new EntityDefinition("account", "account"), List.of(data1));
    }

    @Test
    public void getFilteredCount() {
        assertEquals(0, service.getCount("account"));
        List<EntityData> list = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            EntityData data1 = new EntityData("account");
            data1.addValue("Name", "test account"+i);
            data1.addValue("Website", "www.test"+i+".com");
            data1.addValue("Phone", "+1 222");
            data1 = entityRepo.save(data1);
            list.add(data1);
        }

        assertEquals(30, service.getCount("account"));

        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        Map<String, Object> map = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDefinition.getFieldByName("Name").getId()),
                "operator", "starts_with",
                "right", Map.of("type", "literal", "value", "test account1")
                );
        Optional<Expression> filter = Optional.of(new PredicateParser(StringUtils.EMPTY).fromMap(map));
        PageCursor pageInfo = new PageCursor(0, 5);
        Page<EntityData> query = service.query(entityDefinition.getId() , filter, pageInfo,true);
        assertEquals(5, query.getRecords().size());
        assertEquals(11, query.getPageInfo().getFilteredCount());
        assertEquals(30, query.getPageInfo().getTotalCount());
        assertEquals(11, service.countData(entityDefinition.getId() , filter));

        pageInfo.setCursor(query.getRecords().get(4).getId());
        query = service.query(entityDefinition.getId() , filter, pageInfo,true);
        assertEquals(5, query.getRecords().size());
        assertEquals(11, query.getPageInfo().getFilteredCount());
        assertEquals(30, query.getPageInfo().getTotalCount());
        assertEquals(11, service.countData(entityDefinition.getId() , filter));
        entityRepo.deleteAll(new EntityDefinition("account", "account"), list);
    }

    @Test
    public void getTotalDeletedRecords() {
        assertEquals(0, service.getDeletedCount("account"));

        EntityData data1 = new EntityData("account");
        data1.addValue("name", "test account1");
        data1.addValue("website", "www.test1.com");
        data1.addValue("test_field", "test value1");
        data1.addValue("watermark", 6);
        data1 = entityRepo.save(data1);

        assertEquals(1, service.getCount("account"));
        assertEquals(0, service.getDeletedCount("account"));

        data1.setDeleted(true);
        entityRepo.save(data1);

        assertEquals(1, service.getCount("account"));
        assertEquals(1, service.getDeletedCount("account"));
        entityRepo.deleteAll(new EntityDefinition("account", "account"), List.of(data1));
    }

    @Test
    public void accountNameFieldScore() {
        EntityDefinition def = schemaService.getEntityByName(connectorService.getSyncariConnector().getId(), "account").get();
        entityRepo.createCollection(def);

        // Name not empty, not camel cased
        EntityData data = new EntityData("account");
        data.addValue("name", "test account");
        data.addValue("phone", "6505554545");
        data.addValue("website", "www.test.com");
        data.addValue("NumberOfEmployees", 10);
        data.addValue("Domain", "test account");
        data.addValue("billingCity", "Foster");
        data.addValue("billingCountry", "USA");
        data.addValue("billingState", "CA");
        data.addValue("billingPostalCode", "123456");
        data.addValue("AnnualRevenue", "123456");
        data = entityRepo.save(data);
        // Name empty
        EntityData data1 = new EntityData("account");
        data1.addValue("Domain", "www.test.com");
        data1 = entityRepo.save(data1);
        // Name not empty, camel cased
        EntityData data2 = new EntityData("account");
        data2.addValue("name", "Test Account");
        data2.addValue("phone", "+16505554545");
        data2.addValue("website", "www.test.com");
        data2.addValue("NumberOfEmployees", 10);
        data2.addValue("Domain", "test account");
        data2.addValue("billingCity", "Foster");
        data2.addValue("billingCountry", "USA");
        data2.addValue("billingState", "CA");
        data2.addValue("billingPostalCode", "123456");
        data2.addValue("AnnualRevenue", "123456");
        data2 = entityRepo.save(data2);

        service.computeScore(List.of(data,  data1, data2), def.getApiName());
        // Name = 100 + 70 / 2 (100 for not empty, 70 for camel case)
        assertEquals(97, data.getSyncariScore().getRecordScore());
        // Name = 0 + 0 / 2 (0 for not empty , 0 for camel case)
        assertEquals(10, data1.getSyncariScore().getRecordScore());
        // Name = 100 + 100 / 2 (100 for not empty, 100 for camel case)
        assertEquals(100, data2.getSyncariScore().getRecordScore());
        entityRepo.save(data);
        entityRepo.save(data1);
        entityRepo.save(data2);

        assertEquals(69, service.getTop3AvgScores(def.getId()).getEntityScore().getScore());
    }

    @Test
    public void scoreExcludesDeleted() {
        EntityDefinition def = schemaService.getEntityByName(connectorService.getSyncariConnector().getId(), "account").get();
        entityRepo.createCollection(def);

        // Name not empty, not camel cased
        EntityData data = new EntityData("account");
        data.addValue("name", "test account");
        data.addValue("phone", "6505554545");
        data.addValue("website", "www.test.com");
        data.addValue("NumberOfEmployees", 10);
        data.addValue("Domain", "test account");
        data.addValue("billingCity", "Foster");
        data.addValue("billingCountry", "USA");
        data.addValue("billingState", "CA");
        data.addValue("billingPostalCode", "123456");
        data.addValue("AnnualRevenue", "123456");
        data = entityRepo.save(data);
        // Name empty
        EntityData data1 = new EntityData("account");
        data1.addValue("Domain", "www.test.com");
        data1 = entityRepo.save(data1);
        // Name not empty, camel cased
        EntityData data2 = new EntityData("account");
        data2.addValue("name", "Test Account");
        data2.addValue("phone", "+16505554545");
        data2.addValue("website", "www.test.com");
        data2.addValue("NumberOfEmployees", 10);
        data2.addValue("Domain", "test account");
        data2.addValue("billingCity", "Foster");
        data2.addValue("billingCountry", "USA");
        data2.addValue("billingState", "CA");
        data2.addValue("billingPostalCode", "123456");
        data2.addValue("AnnualRevenue", "123456");
        data2.setDeleted(true);
        data2 = entityRepo.save(data2);

        service.computeScore(List.of(data,  data1, data2), def.getApiName());
        // Name = 100 + 70 / 2 (100 for not empty, 70 for camel case)
        assertEquals(97, data.getSyncariScore().getRecordScore());
        // Name = 0 + 0 / 2 (0 for not empty , 0 for camel case)
        assertEquals(10, data1.getSyncariScore().getRecordScore());
        // Name = 100 + 100 / 2 (100 for not empty, 100 for camel case)
        assertEquals(100, data2.getSyncariScore().getRecordScore());
        entityRepo.save(data);
        entityRepo.save(data1);
        entityRepo.save(data2);

        assertEquals(54, service.getTop3AvgScores(def.getId()).getEntityScore().getScore());
    }

    @Test
    public void snapshotScore() {
        EntityDefinition def = schemaService.getEntityByName(connectorService.getSyncariConnector().getId(), "account").get();
        accountNameFieldScore();

        service.snapshotScore();
        assertEquals(3, entitySnapshotRepo.count());
        assertEquals(12, fieldSnapshotRepo.count());

        List<EntityDataScoreSnapshot> all = entitySnapshotRepo.findAll();
        EntityDataScoreSnapshot entityDataScoreSnapshot = all.stream().filter(s -> s.getEntityDefId().equalsIgnoreCase(def.getId())).findFirst().get();
        assertEquals(69, entityDataScoreSnapshot.getScore());
        assertNotNull(entityDataScoreSnapshot.getComputedOn());
        assertEquals(def.getId(), entityDataScoreSnapshot.getEntityDefId());

        List<FieldDataScoreSnapshot> fieldDataScoreSnapshot = fieldSnapshotRepo.findAll().stream().filter(s -> s.getEntityDefId().equalsIgnoreCase(def.getId())).collect(Collectors.toList());
        List<FieldDataScoreSnapshot> nameScores = fieldDataScoreSnapshot.stream().filter(f -> f.getFieldName().equalsIgnoreCase("name")).collect(Collectors.toList());
        FieldDataScoreSnapshot nameNotEmpty = nameScores.stream().filter(f -> f.getRuleName().equalsIgnoreCase("Has Value")).findFirst().get();
        assertEquals(67, nameNotEmpty.getAverageScore().intValue());
        assertNotNull(nameNotEmpty.getComputedOn());
        assertEquals(def.getId(), nameNotEmpty.getEntityDefId());
        assertEquals("Has value", nameNotEmpty.getRuleName());
        FieldDataScoreSnapshot nameCamelCase = nameScores.stream().filter(f -> f.getRuleName().equalsIgnoreCase("Is camel cased")).findFirst().get();
        assertEquals(70, nameCamelCase.getAverageScore().intValue());
        assertNotNull(nameCamelCase.getComputedOn());
        assertEquals(def.getId(), nameCamelCase.getEntityDefId());
        assertEquals("Is camel cased", nameCamelCase.getRuleName());

        EntityScoreWrapper worseFieldsByScore = service.getTop3AvgScores(def.getId());
        assertEquals(3, worseFieldsByScore.getFieldScores().size());
        assertEquals(67, worseFieldsByScore.getFieldScores().get(0).getAverageScore().intValue());
        assertEquals("Name", worseFieldsByScore.getFieldScores().get(0).getFieldName());
    }

    @Test
    public void getDfiTrend() {
        EntityDefinition def = schemaService.getEntityByName(connectorService.getSyncariConnector().getId(), "account").get();
        accountNameFieldScore();

        service.snapshotScore();
        List<EntityDataScoreSnapshot> all = entitySnapshotRepo.findAll();
        all.forEach(a -> {
            a.setId(null);
            a.setComputedOn(a.getComputedOn().minus(1, ChronoUnit.DAYS));
            a.setScore(a.getScore()-5);
        });
        entitySnapshotRepo.saveAll(all);

        Map<String, Integer> dfiTrend = service.getDfiTrend(def.getId(), 30);
        assertEquals(2, dfiTrend.size());
        assertEquals("69", dfiTrend.get(util.formatDate(Instant.now(), DateUtil.dateOnlyFormat2)).toString());
        assertEquals("64", dfiTrend.get(util.formatDate(Instant.now().minus(1, ChronoUnit.DAYS), DateUtil.dateOnlyFormat2)).toString());

        EntityScoreWrapper avgScores = scoreImpl.getAvgScores(def, Optional.of(3), Optional.empty());
        assertEquals(69, avgScores.getEntityScore().getScore());
        assertEquals(3, avgScores.getFieldScores().size());
        assertEquals(67, avgScores.getFieldScores().get(0).getAverageScore().intValue());
        assertEquals(RuleConstants.IS_NOT_EMPTY, avgScores.getFieldScores().get(0).getConditionName());
        assertEquals("Name", avgScores.getFieldScores().get(0).getFieldName());
        assertEquals(67, avgScores.getFieldScores().get(1).getAverageScore().intValue());
        assertEquals(RuleConstants.IS_NOT_EMPTY, avgScores.getFieldScores().get(1).getConditionName());
        assertEquals("BillingCity", avgScores.getFieldScores().get(1).getFieldName());
        assertEquals(67, avgScores.getFieldScores().get(2).getAverageScore().intValue());
        assertEquals(RuleConstants.IS_NOT_EMPTY, avgScores.getFieldScores().get(2).getConditionName());
        assertEquals("BillingState", avgScores.getFieldScores().get(2).getFieldName());
    }

    @Test
    public void getDfiTrendNotComputedYet() {
        EntityDefinition def = schemaService.getEntityByName(connectorService.getSyncariConnector().getId(), "account").get();
        accountNameFieldScore();

        service.snapshotScore();
        List<EntityDataScoreSnapshot> all = entitySnapshotRepo.findAll();
        all.forEach(a -> {
            a.setId(null);
            a.setComputedOn(a.getComputedOn().minus(1, ChronoUnit.DAYS));
            a.setScore(a.getScore()-5);
        });
        entitySnapshotRepo.deleteAll();
        entitySnapshotRepo.saveAll(all);
        List<FieldDataScoreSnapshot> allFields = fieldSnapshotRepo.findAll();
        allFields.forEach(a -> {
            a.setId(null);
            a.setComputedOn(a.getComputedOn().minus(1, ChronoUnit.DAYS));
            a.setAverageScore((a.getAverageScore() == null ? 0 : a.getAverageScore())-5);
        });
        fieldSnapshotRepo.deleteAll();
        fieldSnapshotRepo.saveAll(allFields);

        Map<String, Integer> dfiTrend = service.getDfiTrend(def.getId(), 30);
        assertEquals(1, dfiTrend.size());
        assertNull(dfiTrend.get(util.formatDate(Instant.now(), DateUtil.dateOnlyFormat2)));
        assertEquals("64", dfiTrend.get(util.formatDate(Instant.now().minus(1, ChronoUnit.DAYS), DateUtil.dateOnlyFormat2)).toString());

        EntityScoreWrapper avgScores = scoreImpl.getAvgScores(def, Optional.of(3), Optional.empty());
        assertEquals(64, avgScores.getEntityScore().getScore());
        assertEquals(3, avgScores.getFieldScores().size());
    }

    @Test
    public void getDfiScoresWithoutReCalculating() {
        
        EntityDefinition userEntity = schemaService.getEntityByName(connectorService.getSyncariConnector().getId(), "user").get();

        DfiRuleAssignment draft = dfiRuleAssignmentService.findOrCreateDraft(userEntity.getId());
        draft.setRules(Set.of(DfiRuleAssignmentServiceTest.getUserFirstNameRuleAssignment(userEntity)));
        draft = dfiRuleAssignmentService.saveDraft(draft);
        dfiRuleAssignmentService.publish(draft);

        entityRepo.createCollection(userEntity);

        EntityData data = new EntityData("user");
        data.addValue("FirstName", "test firstname");
        data.addValue("LastName", "test lastname");
        data = entityRepo.save(data);
        EntityData data1 = new EntityData("user");
        data1.addValue("FirstName", "test firstname 1");
        data1.addValue("LastName", "test lastname 1");
        data1 = entityRepo.save(data1);

        service.computeScore(List.of(data, data1), userEntity.getApiName());

        service.snapshotScore();

        EntityRepoService spy = Mockito.spy(service);
        when(spy.getLiveFieldScoreAggThreshold()).thenReturn(-1);

        EntityScoreWrapper avgScores = spy.getAllAvgScores(userEntity.getId());
        assertEquals(100, avgScores.getEntityScore().getScore());
        assertEquals(1, avgScores.getFieldScores().size());
        assertEquals(RuleConstants.IS_NOT_EMPTY, avgScores.getFieldScores().get(0).getConditionName());

        // Add a condition, and even without recomputing the results should return the condition.
        ConditionAssignment conditionV = new ConditionAssignment().setName(RuleConstants.WITHIN_NUMERIC_RANGE).setConditionMatches(true)
            .setImpact(Impact.HIGH).setType(RuleType.INT_RANGE).setConditionValues(List.of("1", "10"));
        draft = dfiRuleAssignmentService.findOrCreateDraft(userEntity.getId());
        draft.getRules().iterator().next().getConditions().add(conditionV);
        dfiRuleAssignmentService.publish(draft);
        
        avgScores = spy.getAllAvgScores(userEntity.getId());
        assertEquals(100, avgScores.getEntityScore().getScore());
        assertEquals(2, avgScores.getFieldScores().size());
        assertEquals("FirstName", avgScores.getFieldScores().get(0).getFieldName());
        assertEquals(RuleConstants.WITHIN_NUMERIC_RANGE, avgScores.getFieldScores().get(0).getConditionName());
        assertEquals(RuleConstants.IS_NOT_EMPTY, avgScores.getFieldScores().get(1).getConditionName());

        // Add a rule on new field, and even without recomputing the results should return the condition.
        draft = dfiRuleAssignmentService.findOrCreateDraft(userEntity.getId());
        draft.getRules().add(DfiRuleAssignmentServiceTest.getUserLastNameRuleAssignment(userEntity));
        dfiRuleAssignmentService.publish(draft);

        avgScores = spy.getAllAvgScores(userEntity.getId());
        assertEquals(100, avgScores.getEntityScore().getScore());
        assertEquals(3, avgScores.getFieldScores().size());
        assertEquals("FirstName", avgScores.getFieldScores().get(0).getFieldName());
        assertEquals("LastName", avgScores.getFieldScores().get(1).getFieldName());
        assertEquals("FirstName", avgScores.getFieldScores().get(2).getFieldName());
        assertEquals(RuleConstants.WITHIN_NUMERIC_RANGE, avgScores.getFieldScores().get(0).getConditionName());
        assertEquals(RuleConstants.IS_NOT_EMPTY, avgScores.getFieldScores().get(1).getConditionName());
        assertEquals(RuleConstants.IS_NOT_EMPTY, avgScores.getFieldScores().get(2).getConditionName());

        // This is just for testing purposes, so we can test with less number of fieldScores.
        avgScores = spy.getTopNAvgScores(userEntity.getId(), 1);
        assertEquals(100, avgScores.getEntityScore().getScore());
        assertEquals(1, avgScores.getFieldScores().size());
        assertEquals("FirstName", avgScores.getFieldScores().get(0).getFieldName());
        assertEquals(RuleConstants.WITHIN_NUMERIC_RANGE, avgScores.getFieldScores().get(0).getConditionName());

        // Drop a rule, and even without recomputing the results should not return the deleted rule.
        draft = dfiRuleAssignmentService.findOrCreateDraft(userEntity.getId());
        draft.setRules(Set.of(DfiRuleAssignmentServiceTest.getUserLastNameRuleAssignment(userEntity)));
        dfiRuleAssignmentService.publish(draft);

        avgScores = spy.getAllAvgScores(userEntity.getId());
        assertEquals(100, avgScores.getEntityScore().getScore());
        assertEquals(1, avgScores.getFieldScores().size());
        assertEquals("LastName", avgScores.getFieldScores().get(0).getFieldName());
        assertEquals(RuleConstants.IS_NOT_EMPTY, avgScores.getFieldScores().get(0).getConditionName());

        dfiRuleAssignmentRepo.deleteByEntityId(userEntity.getId());
    }

    @Test
    public void initialize() {
        assertEquals(0, service.getCount("account"));
        List<EntityData> list = new ArrayList<>();

        for (int i = 0; i < 3270; i++) {
            EntityData data = new EntityData("account");
            data.addValue("name", "test account"+i);
            data.addValue("phone", "6505554545");
            data.addValue("website", "www.test.com");
            data.addValue("NumberOfEmployees", 10);
            data.addValue("Domain", "test account");
            data.addValue("billingCity", "Foster");
            data.addValue("billingCountry", "USA");
            data.addValue("billingState", "CA");
            data.addValue("billingPostalCode", "123456");
            data.addValue("AnnualRevenue", "123456");
            list.add(data);
        }
        entityRepo.saveAll(list);
        assertEquals(3270, service.getCount("account"));
        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();

        service.initializeScore();
        Slice<EntityData> data = entityRepo.find(entityDefinition, Instant.EPOCH, new PageRequest(1, 3275));
        for (EntityData entityData : data) {
            assertTrue(entityData.getSyncariScore() != null);
            assertTrue(entityData.getSyncariScore().getRecordScore() > 10);
        }
    }

    @Test
    public void testEmbeddedDocument() {
        List<EntityData> list = new ArrayList<>();

        Map<String, Object> shippingAddress = Map.of("shippingCity", "Foster", "shippingState", "CA", "shippingCountry", "USA");
        EntityData data = new EntityData("account");
        data.addValue("name", "test account");
        data.addValue("phone", "6505554545");
        data.addValue("website", "www.test.com");
        data.addValue("NumberOfEmployees", 10);
        data.addValue("Domain", "test account");
        data.addValue("billingCity", "Foster");
        data.addValue("billingCountry", "USA");
        data.addValue("billingState", "CA");
        data.addValue("billingPostalCode", "123456");
        data.addValue("AnnualRevenue", "123456");
        data.addValue("shippingAddress", new Document(shippingAddress));
        list.add(data);
        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        entityDefinition.addField(new AttributeDefinition().setEntityId(entityDefinition.getId()).setApiName("shippingAddress").setDataType(ObjectType.VALUE));
        entityRepo.saveAll(entityDefinition, list);
        assertEquals(1, service.getCount("account"));

        Slice<EntityData> res = entityRepo.find(entityDefinition, Instant.EPOCH, PageRequest.of(0, 10));
        for (EntityData entityData : res) {
            assertTrue(shippingAddress.equals(entityData.getValue("shippingAddress")));
        }
    }
    
    @Test
    public void purgeRecords() throws JsonProcessingException {
        assertEquals(0, service.getCount("account"));

        EntityData data1 = new EntityData("account");
        data1.addValue("Name", "test account");
        data1.addValue("Website", "www.test1.com");
        data1.addValue("test_field", "test value1");
        data1.addValue("watermark", 6);
        data1 = entityRepo.save(data1);
        EntityData data2 = new EntityData("account");
        data2.addValue("Name", "test account");
        data2.addValue("Website", "www.test2.com");
        data2.addValue("test_field", "test value2");
        data2.addValue("watermark", 7);
        data2 = entityRepo.save(data2);

        IdMapping idMapping = new IdMapping().setSyncariId(data1.getSyncariEntityId())
            .addMapping("123", data1.getId(), "123").setEntityName("account");
        mappingService.save(idMapping);
        assertEquals(1, mappingRepo.count());

        assertNotNull(entityRepo.findByIds("account", Set.of(data1.getId())));

        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        // purge delete
        Batch batch = new Batch().setEntityId(entityDefinition.getId());
		service.deleteAllForEntity(entityDefinition.getId(), batchRepo.save(batch));
        assertEquals(0, entityRepo.count(entityDefinition.getApiName(), false));
        assertEquals(0, mappingRepo.count());
    }

    @Test
    public void updateReferenceEntities() {
        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName("account").get();
        String entityId = entityDefinition.getId();

        List<String> syncariIds = IntStream.range(0, 500).mapToObj(i -> ObjectId.get().toHexString()).collect(Collectors.toList());

        SchemaService mockSchemaService = mock(SchemaService.class);

        EntityDefinition contactDef = schemaService.getSyncariEntityByName("contact").get();
        EntityDefinition opportunityDef = schemaService.getSyncariEntityByName("opportunity").get();

        // for reference entity add 500 records
        var contacts = syncariIds.stream().map(syncariId -> new EntityData("contact").setSyncariEntityId(syncariId).setId(syncariId).addValue("AccountId", syncariId)).collect(Collectors.toList());
        var opportunities = syncariIds.stream().map(syncariId -> new EntityData("opportunity").setSyncariEntityId(syncariId).setId(syncariId).addValue("AccountId", syncariId)).collect(Collectors.toList());

        entityRepo.saveAll(contactDef, contacts);
        entityRepo.saveAll(opportunityDef, opportunities);

        long currentTimestamp = System.currentTimeMillis();

        service.updateReferringEntities(entityId, syncariIds);

        entityRepo.findByIds(contactDef, new HashSet<>(syncariIds)).forEach(entityData -> {
            assertTrue(entityData.getSyncariTimestamp() > currentTimestamp);
        });

        entityRepo.findByIds(opportunityDef, new HashSet<>(syncariIds)).forEach(entityData -> {
            assertTrue(entityData.getSyncariTimestamp() > currentTimestamp);
        });
    }
}
