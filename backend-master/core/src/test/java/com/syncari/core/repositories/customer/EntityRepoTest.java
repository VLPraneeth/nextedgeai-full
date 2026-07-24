package com.syncari.core.repositories.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.EntityData;
import com.syncari.connector.EntityScore;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.Index;
import com.syncari.core.datatype.ChildType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.dfi.RuleConstants;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.*;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.IterableUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class EntityRepoTest extends AbstractSyncariTest {
    @Autowired
    private EntityRepo entityRepo;

    @Autowired
    private EntityDatabaseRepo entityDatabaseRepo;


    @Autowired
    MongoTemplate customerMongoTemplate;
    @Autowired
    private CustomerMongoUtils customerMongoUtils;
    @Autowired
    private TokenHelper tokenHelper;
    private EntityDefinition entityDefinition;

    @Test
    public void saveEntity() {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("some", ZonedDateTime.now());
        EntityData saved = entityRepo.save(entityDefinition, entity);
        assertNotNull(saved.getSyncariEntityId());
        EntityData retrieved = entityRepo.findById(entityDefinition, saved.getSyncariEntityId()).get();
        assertEquals(((ZonedDateTime)saved.getValues().get("some")).toEpochSecond(), ((ZonedDateTime)retrieved.getValues().get("some")).toInstant().getEpochSecond());

    }

    @Test
    public void hasChangeWithSavedEntity() throws JsonProcessingException {

        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        List<Map<String, Object>> in = List.of(Map.of("key1", "v1"));
        entity.addValue("obj", in);
        EntityData saved = entityRepo.save(entityDefinition, entity);
        ObjectMapper mapper = new ObjectMapper();
        final String s = mapper.writeValueAsString(entity);
        EntityData read = mapper.readValue(s, EntityData.class);

        EntityData retrieved = entityRepo.findById(entityDefinition, saved.getSyncariEntityId()).get();
        assertFalse(retrieved.hasChanges("obj", read.getValue("obj")));

    }

    @Test
    public void saveEntityBatch() {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("some", ZonedDateTime.now());
        var syncariId = ObjectId.get().toHexString();
        entity.setSyncariEntityId(syncariId);
        IdMapping idMapping = new IdMapping().setSyncariId(syncariId).setEntityName("account").addMapping("sfdc_id", "123", "sfdc_account_id");

        entityRepo.saveEntityBatch(entityDefinition, List.of(entity), List.of(idMapping));
        EntityData retrieved = entityRepo.findById(entityDefinition, syncariId).get();
        assertTrue(retrieved != null);
        assertEquals(((ZonedDateTime)entity.getValues().get("some")).toEpochSecond(), ((ZonedDateTime)retrieved.getValues().get("some")).toInstant().getEpochSecond());
    }

    @Test
    public void saveMixedEntityBatch() {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("some", ZonedDateTime.now());
        EntityData savedEntity = entityRepo.save(entityDefinition, entity);
        var syncariId = savedEntity.getSyncariEntityId();
        IdMapping idMapping1 = new IdMapping().setSyncariId(syncariId).setEntityName("account").addMapping("sfdc_id", "123", "sfdc_account_id");

        var entity2 = new EntityData("account");
        entity2.addValue("name", "Test Account 1");
        entity2.addValue("address", "some address 1");
        entity2.addValue("some", ZonedDateTime.now());
        var syncariId2 = ObjectId.get().toHexString();
        entity2.setSyncariEntityId(syncariId2);
        IdMapping idMapping2 = new IdMapping().setSyncariId(syncariId2).setEntityName("account").addMapping("sfdc_id", "456", "sfdc_account_id");

        entityRepo.saveEntityBatch(entityDefinition, List.of(savedEntity, entity2), List.of(idMapping1, idMapping2));
        EntityData retrieved = entityRepo.findById(entityDefinition, syncariId).get();
        assertTrue(retrieved != null);
        assertEquals(((ZonedDateTime)entity.getValues().get("some")).toEpochSecond(), ((ZonedDateTime)retrieved.getValues().get("some")).toInstant().getEpochSecond());
        assertTrue(retrieved.getSyncariTimestamp() != 0l);

        EntityData retrieved2 = entityRepo.findById(entityDefinition, syncariId2).get();
        assertTrue(retrieved2 != null);
        assertEquals(((ZonedDateTime)entity2.getValues().get("some")).toEpochSecond(), ((ZonedDateTime)retrieved2.getValues().get("some")).toInstant().getEpochSecond());
        assertTrue(retrieved.getSyncariTimestamp() != 0l);
    }

    @Test
    public void saveEntityWithChildRecords() {
        var entity = new EntityData("account");
        entityDefinition.addField(new AttributeDefinition().setApiName("singleValuedChild").setDataType(ChildType.VALUE).setStatus(Status.ACTIVE));
        entityDefinition.addField(new AttributeDefinition().setApiName("multiValuedChild").setDataType(ChildType.VALUE).setMultiValueField(true).setStatus(Status.ACTIVE));
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("some", ZonedDateTime.now());
        entity.addValue("singleValuedChild", new EntityData("childSchema1").addValue("k1","v1").setSyncariEntityId("syncariId1"));
        ZonedDateTime childTime = ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        entity.addValue("multiValuedChild",List.of(
                new EntityData("childSchema2").addValue("k1","v2").addValue("childdate", childTime).setSyncariEntityId("syncariId2"),
                new EntityData("childSchema2").addValue("k1","v3").addValue("childdate", childTime).setSyncariEntityId("syncariId3")
                )
        );
        EntityData saved = entityRepo.save(entityDefinition, entity);
        assertNotNull(saved.getSyncariEntityId());
        EntityData retrieved = entityRepo.findById(entityDefinition, saved.getSyncariEntityId()).get();
        assertEquals(((ZonedDateTime)saved.getValues().get("some")).toEpochSecond(), ((ZonedDateTime)retrieved.getValues().get("some")).toInstant().getEpochSecond());
        EntityData singleValuedChild = retrieved.getTypedValue("singleValuedChild");
        List<EntityData> multiValuedChild = retrieved.getTypedValue("multiValuedChild");
        assertEquals("childSchema1",singleValuedChild.getName());
        assertEquals("v1",singleValuedChild.getValue("k1"));
        assertEquals("syncariId1",singleValuedChild.getSyncariEntityId());

        assertEquals("childSchema2",multiValuedChild.get(0).getName());
        assertEquals("syncariId2",multiValuedChild.get(0).getSyncariEntityId());
        assertEquals("v2",multiValuedChild.get(0).getValue("k1"));
        assertEquals(childTime.format(DateTimeFormatter.ofPattern("YYYY-MM-DD HH:mm:ss.SSSZ")),
                ((ZonedDateTime)multiValuedChild.get(0).getValue("childdate")).format(DateTimeFormatter.ofPattern("YYYY-MM-DD HH:mm:ss.SSSZ")));

        assertEquals("childSchema2",multiValuedChild.get(1).getName());
        assertEquals("syncariId3",multiValuedChild.get(1).getSyncariEntityId());
        assertEquals("v3",multiValuedChild.get(1).getValue("k1"));
        assertEquals(childTime.format(DateTimeFormatter.ofPattern("YYYY-MM-DD HH:mm:ss.SSSZ")),
                ((ZonedDateTime)multiValuedChild.get(1).getValue("childdate")).format(DateTimeFormatter.ofPattern("YYYY-MM-DD HH:mm:ss.SSSZ")));
    }
    @Test
    public void attachExternalIdsToChildren() throws InterruptedException {
        var entity = new EntityData("account");
        entityDefinition.addField(new AttributeDefinition().setApiName("singleValuedChild").setDataType(ChildType.VALUE).setStatus(Status.ACTIVE));
        entityDefinition.addField(new AttributeDefinition().setApiName("multiValuedChild").setDataType(ChildType.VALUE).setMultiValueField(true).setStatus(Status.ACTIVE));
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("some", ZonedDateTime.now());
        entity.addValue("singleValuedChild", new EntityData("childSchema1").addValue("k1","v1").setSyncariEntityId("syncariId1"));
        entity.addValue("multiValuedChild",List.of(
                new EntityData("childSchema2").addValue("k1","v2").setSyncariEntityId("syncariId2"),
                new EntityData("childSchema2").addValue("k1","v3").setSyncariEntityId("syncariId3")
                )
        );
        EntityData saved = entityRepo.save(entityDefinition, entity);


        EntityData retrieved = entityRepo.findById(entityDefinition, saved.getSyncariEntityId()).get();
        EntityData singleValuedChild = retrieved.getTypedValue("singleValuedChild");


        List<EntityData> multiValuedChild = retrieved.getTypedValue("multiValuedChild");
        assertEquals("childSchema1",singleValuedChild.getName());
        assertEquals("v1",singleValuedChild.getValue("k1"));
        assertEquals("syncariId1",singleValuedChild.getSyncariEntityId());

        assertEquals("childSchema2",multiValuedChild.get(0).getName());
        assertEquals("syncariId2",multiValuedChild.get(0).getSyncariEntityId());
        assertEquals("v2",multiValuedChild.get(0).getValue("k1"));

        assertEquals("childSchema2",multiValuedChild.get(1).getName());
        assertEquals("syncariId3",multiValuedChild.get(1).getSyncariEntityId());
        assertEquals("v3",multiValuedChild.get(1).getValue("k1"));

    }


    @Test
    public void saveAndFindEntity() throws InterruptedException {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("some", ZonedDateTime.now());
        EntityData saved1 = entityRepo.save(entityDefinition, entity);
        assertNotNull(saved1.getSyncariEntityId());
        assertNotNull(saved1.getId());
        Thread.sleep(1000);
        EntityData saved2 = entityRepo.save(entityDefinition, new EntityData("account").addValue("name","Second").setLastTransactionLogId("TxLog1"));

        //Resave object
        saved2 = entityRepo.save(entityDefinition, saved2);
        assertEquals("TxLog1",saved2.getLastTransactionLogId());
        assertNotNull(saved2.getSyncariEntityId());
        assertNotNull(saved2.getId());
        var retrieved = entityRepo.find("account", Instant.now().minusMillis(800),Pageable.unpaged());
        assertEquals(retrieved.getContent().get(0).getId(),saved2.getId());
        assertEquals(retrieved.getContent().get(0).getSyncariEntityId(),saved2.getSyncariEntityId());
        assertNotNull(saved1.getId());
        assertEquals(1, retrieved.getContent().size());
        assertEquals("Second",retrieved.getContent().get(0).getValue("name"));
        assertEquals("TxLog1",retrieved.getContent().get(0).getLastTransactionLogId());

    }

    @Test
    public void saveAndFindEntityIsSorted() throws InterruptedException {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("some", ZonedDateTime.now());
        EntityData saved1 = entityRepo.save(entityDefinition,entity);
        assertNotNull(saved1.getSyncariEntityId());
        assertNotNull(saved1.getId());
        Thread.sleep(500);
        EntityData saved2 = entityRepo.save(entityDefinition,new EntityData("account").addValue("name","Second").setLastTransactionLogId("TxLog1"));

        //Resave object
        saved2 = entityRepo.save(entityDefinition,saved2);
        assertEquals("TxLog1",saved2.getLastTransactionLogId());
        assertNotNull(saved2.getSyncariEntityId());
        assertNotNull(saved2.getId());
        var retrieved = entityRepo.find("account", Instant.now().minusMillis(5000),Pageable.unpaged());
        assertEquals(retrieved.getContent().get(0).getId(),saved1.getId());
        assertEquals(retrieved.getContent().get(1).getId(),saved2.getId());
    }

    @Test
    public void paginatedFind() throws InterruptedException {
        final long startingTimestamp = System.currentTimeMillis();
        final List<EntityData> entitiesWithTimeStamps = createEntitiesWithTimeStamps(startingTimestamp, 10, entityDefinition);

        final List<EntityData> saved = entitiesWithTimeStamps.stream().map(record -> {
            sleep(10l);
            return entityRepo.save(entityDefinition, record);
        }).collect(Collectors.toList());

        final List<EntityData> entityData = entityRepo.find(entityDefinition, Instant.ofEpochMilli(startingTimestamp - 1), new PageCursor("", PageDirection.next, 5));
        assertEquals(5, entityData.size());
        //update an earlier record with a later timestamp
        sleep(10l);
        entityRepo.save(entityDefinition, entityData.get(0));
        EntityData lastRecord = entityData.get(entityData.size() - 1);

        String cursor = lastRecord.getSyncariTimestamp() + "_" + lastRecord.getSyncariEntityId();
        PageCursor nextPage = new PageCursor(cursor, PageDirection.next, 10);
        final List<EntityData> nextPageWithUpdatedRecords = entityRepo.find(entityDefinition, Instant.ofEpochMilli(lastRecord.getSyncariTimestamp()), nextPage);
        //5 remaining records from the last timestamp and 1 updated record
        assertEquals(6, nextPageWithUpdatedRecords.size());
    }

    private static void sleep(long sleepMs) {
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private List<EntityData> createEntitiesWithTimeStamps(long startingTimestamp, int numRecords, EntityDefinition entityDefinition) {
        List<EntityData> records = new ArrayList<>();
        for (int i = 0; i < numRecords; i++) {
            records.add(new EntityData(entityDefinition.getApiName())
                    .addValue("name", "Test Account " + i)
                    .setSyncariTimestamp(startingTimestamp++)
            );
        }
        return records;
    }

    @Test
    public void saveFindAndUpdate() throws InterruptedException {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        ZonedDateTime now = ZonedDateTime.now();
        entity.addValue("some", now.toEpochSecond());
        EntityData saved1 = entityRepo.save(entityDefinition, entity);
        EntityData saved2 = entityRepo.save(entityDefinition,new EntityData("account").addValue("field4","Value 4")
                .addValue("address","new address")
                .setSyncariEntityId(saved1.getSyncariEntityId())
                .setId(saved1.getId()));
        assertEquals("Test Account",saved2.getValue("name"));
        assertEquals("new address",saved2.getValue("address"));
        assertEquals(now.toEpochSecond(),saved2.getValue("some"));
        assertEquals("Value 4",saved2.getValue("field4"));


    }

    @Test
    public void searchWithCount() {
        int pageSize = 5;
        // Generate three pages worth of records
        int recordsCount = 3 * pageSize;

        List<EntityData> records = new ArrayList<>();
        for (int i = 1; i <= recordsCount; i++) {
            records.add(new EntityData("account"));
        }
        entityRepo.saveAll(entityDefinition,records);
        
        PageCursor pageInfo = new PageCursor(null, PageDirection.next, pageSize);
        EntityDefinition entityDefinition = new EntityDefinition("account", "account");

        // Fetch first pagae of results with no cursor
        var search = entityRepo.searchWithCount(entityDefinition, Optional.empty(), pageInfo, Optional.empty(),true);

        assertEquals(search.getRecords().size(), pageSize);

        List<EntityData> results = search.getRecords();
        String firstPageEndRecordId = results.get(results.size() - 1).getId();

        pageInfo.setCursor(firstPageEndRecordId);

        // Fetch second page of results
        search = entityRepo.searchWithCount(entityDefinition, Optional.empty(), pageInfo, Optional.empty(),true);

        results = search.getRecords();
        String secondPageEndRecordId = results.get(results.size() - 1).getId();
        List<String> secondpageResultKeys = results
            .stream()
            .map(record -> record.getName() + record.getId())
            .collect(Collectors.toList());

        pageInfo.setCursor(secondPageEndRecordId);

        // Fetch third page of results
        search = entityRepo.searchWithCount(entityDefinition, Optional.empty(), pageInfo, Optional.empty(),true);

        results = search.getRecords();
        String thirdPageStartRecordId = results.get(0).getId();

        pageInfo.setCursor(thirdPageStartRecordId);
        pageInfo.setDirection(PageDirection.previous);

        // Fetch second page of results using third page cursor and "previous" direction
        search = entityRepo.searchWithCount(entityDefinition, Optional.empty(), pageInfo, Optional.empty(),true);

        results = search.getRecords();
        List<String> secondpageResultKeysFromPrevious = results
        .stream()
        .map(record -> record.getName() + record.getId())
        .collect(Collectors.toList());

        // The IDs for the all second page records should be the same if coming
        // from the first page or the third page
        assertEquals(secondpageResultKeys, secondpageResultKeysFromPrevious);
    }

    @Test
    public void searchWithCount_ExceedingCountThreshold() {
        var orgCustomerMongoUtils = entityDatabaseRepo.customerMongoUtils;
        try{
            int pageSize = 5;
            PageCursor pageInfo = new PageCursor(null, PageDirection.next, pageSize);
            EntityDefinition entityDefinition = new EntityDefinition("account", "account");

            Optional<Expression> expr = Optional.of(Expression.eq(Expression.var("field1"), Expression.lit("Value1")));
            Optional<Bson> filter = expr.map(i -> new DataCriteriaVisitor(i, Map.of(), Optional.empty())).map(v -> v.createCriteria());
            CustomerMongoUtils mockMongoUtils = mock(CustomerMongoUtils.class);
            entityDatabaseRepo.customerMongoUtils = mockMongoUtils;

            // total document count over threshold
            doReturn(1000L).when(mockMongoUtils).count(eq("syncari_"+entityDefinition.getApiName()), any(Optional.class));
            doReturn(EntityDatabaseRepo.COUNT_THRESHOLD + 1).when(mockMongoUtils).count("syncari_"+entityDefinition.getApiName(), Optional.empty());
            var search = entityRepo.searchWithCount(entityDefinition, expr, pageInfo, Optional.empty(),true);
            assertEquals(EntityDatabaseRepo.COUNT_THRESHOLD + 1 ,search.getPageInfo().getTotalCount());
            assertEquals(0 ,search.getPageInfo().getFilteredCount());
            assertEquals("Filtered Record count is unavailable due to large dataset size." ,search.getPageInfo().getMessage());

            // total document count within threshold
            doReturn(1000L).when(mockMongoUtils).count(eq("syncari_"+entityDefinition.getApiName()), any(Optional.class));
            doReturn(EntityDatabaseRepo.COUNT_THRESHOLD - 1).when(mockMongoUtils).count("syncari_"+entityDefinition.getApiName(), Optional.empty());
            search = entityRepo.searchWithCount(entityDefinition, expr, pageInfo, Optional.empty(),true);
            assertEquals(EntityDatabaseRepo.COUNT_THRESHOLD - 1 ,search.getPageInfo().getTotalCount());
            assertEquals(1000L ,search.getPageInfo().getFilteredCount());

        } finally {
            entityDatabaseRepo.customerMongoUtils = orgCustomerMongoUtils;
        }
    }

    @Test
    public void searchBySingleField() {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("email", "test@example.com");
        EntityData saved = entityRepo.save(entityDefinition,entity);
        Slice<EntityData> search = entityRepo.search("account",
                SearchCriteria.with("name", "Test Account").setCaseSensitive(true)
                , PageRequest.of(0, 100));
        assertEquals(1, search.getNumberOfElements());
        EntityData searchResult = search.getContent().get(0);
        assertEquals(searchResult.getName(), entity.getName());
        assertEquals("Test Account", searchResult.getValues().get("name"));
        assertEquals("some address", searchResult.getValues().get("address"));
        assertEquals("test@example.com", searchResult.getValues().get("email"));

    }

    @Test
    public void searchExcludesDeletedRecords() {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("email", "test@example.com");
        entity.setDeleted(true);
        EntityData saved = entityRepo.save(entityDefinition,entity);
        Slice<EntityData> search = entityRepo.search("account",
                SearchCriteria.with("name", "Test Account").setCaseSensitive(true)
                , PageRequest.of(0, 100));
        assertEquals(0, search.getNumberOfElements());
        assertFalse(search.hasNext());
        assertFalse(search.hasContent());

    }

    @Test
    public void searchByEmptyValuesShouldReturnNoResults() {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("email", "test@example.com");
        EntityData saved = entityRepo.save(entityDefinition, entity);
        Slice<EntityData> search = entityRepo.search("account",
                SearchCriteria.with("what", null).setCaseSensitive(true)
                , PageRequest.of(0, 100));
        assertEquals(0, search.getNumberOfElements());
        assertFalse(search.hasNext());
        assertFalse(search.hasContent());

    }

    @Test
    public void searchByMultipleFields() {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("email", "test@example.com");
        EntityData saved = entityRepo.save(entityDefinition,entity);
        Slice<EntityData> search = entityRepo.search("account",
                SearchCriteria.with("name", "Test Account").and("address", "some address").setCaseSensitive(true),
                PageRequest.of(0, 100));
        assertEquals(1, search.getNumberOfElements());
        EntityData searchResult = search.getContent().get(0);
        assertEquals(searchResult.getName(), entity.getName());
        assertEquals("Test Account", searchResult.getValues().get("name"));
        assertEquals("some address", searchResult.getValues().get("address"));
        assertEquals("test@example.com", searchResult.getValues().get("email"));

    }

    @Test
    public void searchByMultipleFieldsNoMatches() {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("email", "test@example.com");
        EntityData saved = entityRepo.save(entityDefinition, entity);
        Slice<EntityData> search = entityRepo.search("account", SearchCriteria.with("name", "Test Account")
                .and("address", "some address2").setCaseSensitive(true), PageRequest.of(0, 100));
        assertEquals(0, search.getNumberOfElements());
        assertFalse(search.hasNext());
        assertFalse(search.hasContent());
    }

    @Test
    public void searchByMultipleFieldsCaseSensitive() {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("email", "test@example.com");
        EntityData saved = entityRepo.save(entityDefinition,entity);
        Slice<EntityData> search = entityRepo.search("account", SearchCriteria.with("name", "Test Account")
                .and("address", "Some Address").setCaseSensitive(true), PageRequest.of(0, 100));
        assertEquals(0, search.getNumberOfElements());
        assertFalse(search.hasNext());
        assertFalse(search.hasContent());
    }

    @Test
    public void searchByMultipleFieldsCaseInsensitive() {
        customerMongoUtils.createCollection("syncari_account", List.of("name", "address", "email"));
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("email", "test@example.com");
        EntityData saved = entityRepo.save(entityDefinition,entity);

        Slice<EntityData> search = entityRepo.search("account", SearchCriteria.with("name", "Test Account")
                .and("address", "Some Address"), PageRequest.of(0, 100));
        assertEquals(1, search.getNumberOfElements());
        EntityData searchResult = search.getContent().get(0);
        assertEquals(searchResult.getName(), entity.getName());
        assertEquals("Test Account", searchResult.getValues().get("name"));
        assertEquals("some address", searchResult.getValues().get("address"));
        assertEquals("test@example.com", searchResult.getValues().get("email"));
    }

    @Test
    public void searchByMultipleFieldsTrimsSearchCriteria() {
        customerMongoUtils.createCollection("syncari_account", List.of("name", "address", "email"));
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("email", "test@example.com");
        EntityData saved = entityRepo.save(entityDefinition, entity);

        Slice<EntityData> search = entityRepo.search("account", SearchCriteria.with("name", "  Test Account  ")
                .and("address", "Some Address"), PageRequest.of(0, 100));
        assertEquals(1, search.getNumberOfElements());
        EntityData searchResult = search.getContent().get(0);
        assertEquals(searchResult.getName(), entity.getName());
        assertEquals("Test Account", searchResult.getValues().get("name"));
        assertEquals("some address", searchResult.getValues().get("address"));
        assertEquals("test@example.com", searchResult.getValues().get("email"));
    }

    @Test
    public void searchByMultipleFieldsIgnoreSpecialChars() {
        customerMongoUtils.createCollection("syncari_account", List.of("name", "address", "email"));
        var entity = new EntityData("account");
        entity.addValue("name", "Test-Account");
        entity.addValue("address", "some      address");
        entity.addValue("address1", "some_other_address");
        entity.addValue("email", "test@example.com");
        EntityData saved = entityRepo.save(entityDefinition, entity);

        Slice<EntityData> search = entityRepo.search("account", SearchCriteria.with("name", "Test Account")
                .and("address", "Some Address").and("address1", "some other Address").setIgnoreDelimiters(true)
                , PageRequest.of(0, 100));
        assertEquals(1, search.getNumberOfElements());
        EntityData searchResult = search.getContent().get(0);
        assertEquals(searchResult.getName(), entity.getName());
        assertEquals("Test-Account", searchResult.getValues().get("name"));
        assertEquals("some      address", searchResult.getValues().get("address"));
        assertEquals("test@example.com", searchResult.getValues().get("email"));
    }

    @Test
    public void searchTestsAllColumnsCaseIndexed() {
        customerMongoUtils.dropCollection("syncari_account");
        customerMongoUtils.createCollection("syncari_account", List.of("index_field", "ig_index_field", "non_index_field", "isDeleted"));
        
        MongoUtils.createIndexes(customerMongoTemplate, "syncari_account", List.of(
                new Index("test_index_collection_case_sensitive", false, true, "index_field")
        ));
        MongoUtils.createIndexes(customerMongoTemplate, "syncari_account", List.of(
                new Index("test_index_collection_case_insensitive", false, false, "ig_index_field", "isDeleted")
        ));
        
        var entity = new EntityData("account").addValue("index_field", "CaseSensitiveValue").addValue("ig_index_field", "CaseInsensitiveValue")
            .addValue("non_index_field", "NonIndexValue");
        EntityData saved = entityRepo.save(entityDefinition, entity);
        entity = new EntityData("account").addValue("index_field", "casesensitivevalue").addValue("ig_index_field", "caseinsensitivevalue")
            .addValue("non_index_field", "nonindexvalue");
        saved = entityRepo.save(entityDefinition, entity);
        entity = new EntityData("account").addValue("index_field", "CASESENSITIVEVALUE").addValue("ig_index_field", "CASEINSENSITIVEVALUE")
            .addValue("non_index_field", "NONINDEXVALUE");
        saved = entityRepo.save(entityDefinition, entity);

        assertValueFound("account", "index_field", "CaseSensitiveValue", 1, true);
        assertValueFound("account", "index_field", "casesensitivevalue", 1, true);
        assertValueFound("account", "index_field", "CASESENSITIVEVALUE", 1, true);
        // Check 'Equals Ignore Case' but without the case insensitive index.
        assertValueFound("account", "index_field", "CaseSensitiveValue", 3, false);
        assertValueFound("account", "index_field", "casesensitivevalue", 3, false);
        assertValueFound("account", "index_field", "CASESENSITIVEVALUE", 3, false);
        assertValueNotFound("account", "index_field", "xyz");

        // Turn off case sensitive search "Equals Ignore Case" scenario
        assertValueFound("account", "ig_index_field", "CaseInsensitiveValue", 3, false);
        assertValueFound("account", "ig_index_field", "caseinsensitivevalue", 3, false);
        assertValueFound("account", "ig_index_field", "CASEINSENSITIVEVALUE", 3, false);
        assertValueNotFound("account", "ig_index_field", "xyz");
        // Turn on case sensitive search "Equals" scenario
        assertValueFound("account", "ig_index_field", "CaseInsensitiveValue", 1, true);
        assertValueFound("account", "ig_index_field", "caseinsensitivevalue", 1, true);
        assertValueFound("account", "ig_index_field", "CASEINSENSITIVEVALUE", 1, true);
        assertValueNotFound("account", "ig_index_field", "Xyz");

        assertValueFound("account", "non_index_field", "NonIndexValue", 1, true);
        assertValueFound("account", "non_index_field", "nonindexvalue", 1, true);
        assertValueFound("account", "non_index_field", "NONINDEXVALUE", 1, true);
        assertValueNotFound("account", "non_index_field", "Xyz");
    }

    @Test
    public void searchTestsOneColumnCaseInsensitiveIndexed() {
        customerMongoUtils.dropCollection("syncari_account");
        customerMongoUtils.createCollection("syncari_account", List.of("index_field", "ig_index_field", "non_index_field", "isDeleted"));
        
        MongoUtils.createIndexes(customerMongoTemplate, "syncari_account", List.of(
                new Index("test_index_collection_case_sensitive", false, true, "index_field")
        ));
        MongoUtils.createIndexes(customerMongoTemplate, "syncari_account", List.of(
                new Index("test_index_collection_case_insensitive", false, false, "ig_index_field")
        ));
        
        var entity = new EntityData("account").addValue("index_field", "CaseSensitiveValue").addValue("ig_index_field", "CaseInsensitiveValue")
            .addValue("non_index_field", "NonIndexValue");
        EntityData saved = entityRepo.save(entityDefinition, entity);
        entity = new EntityData("account").addValue("index_field", "casesensitivevalue").addValue("ig_index_field", "caseinsensitivevalue")
            .addValue("non_index_field", "nonindexvalue");
        saved = entityRepo.save(entityDefinition, entity);
        entity = new EntityData("account").addValue("index_field", "CASESENSITIVEVALUE").addValue("ig_index_field", "CASEINSENSITIVEVALUE")
            .addValue("non_index_field", "NONINDEXVALUE");
        saved = entityRepo.save(entityDefinition, entity);

        assertValueFound("account", "index_field", "CaseSensitiveValue", 1, true);
        assertValueFound("account", "index_field", "casesensitivevalue", 1, true);
        assertValueFound("account", "index_field", "CASESENSITIVEVALUE", 1, true);
        // Check 'Equals Ignore Case' but without the case insensitive index.
        assertValueFound("account", "index_field", "CaseSensitiveValue", 3, false);
        assertValueFound("account", "index_field", "casesensitivevalue", 3, false);
        assertValueFound("account", "index_field", "CASESENSITIVEVALUE", 3, false);
        assertValueNotFound("account", "index_field", "xyz");

        // Turn off case sensitive search "Equals Ignore Case" scenario
        assertValueFound("account", "ig_index_field", "CaseInsensitiveValue", 3, false);
        assertValueFound("account", "ig_index_field", "caseinsensitivevalue", 3, false);
        assertValueFound("account", "ig_index_field", "CASEINSENSITIVEVALUE", 3, false);
        assertValueNotFound("account", "ig_index_field", "xyz");
        // Turn on case sensitive search "Equals" scenario
        assertValueFound("account", "ig_index_field", "CaseInsensitiveValue", 1, true);
        assertValueFound("account", "ig_index_field", "caseinsensitivevalue", 1, true);
        assertValueFound("account", "ig_index_field", "CASEINSENSITIVEVALUE", 1, true);
        assertValueNotFound("account", "ig_index_field", "Xyz");

        assertValueFound("account", "non_index_field", "NonIndexValue", 1, true);
        assertValueFound("account", "non_index_field", "nonindexvalue", 1, true);
        assertValueFound("account", "non_index_field", "NONINDEXVALUE", 1, true);
        assertValueNotFound("account", "non_index_field", "Xyz");
    }

    private void assertValueFound(String collName, String fieldName, String value, int expectedCount, boolean caseSensitiveSearch) {
        Slice<EntityData> search = 
            entityRepo.search(collName, SearchCriteria.with(fieldName, value).setCaseSensitive(caseSensitiveSearch), PageRequest.of(0, 5));
        assertEquals(expectedCount, search.getNumberOfElements());

        // Also check that the LookupCriteriaVisitor implementation works.
        Expression expression = null;
        if (!caseSensitiveSearch) {
            expression = Expression.ieq(Expression.var(fieldName), Expression.lit(value));
        } else {
            expression = Expression.eq(Expression.var(fieldName), Expression.lit(value));
        }
        Optional<LookupCriteriaVisitor> mongoCriteria = Optional.of(new LookupCriteriaVisitor(new GraphContext(), expression, tokenHelper,
                entityDefinition.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entityDefinition, key)));
        List<EntityData> searchList = entityRepo.search(entityDefinition, mongoCriteria,
                /*Arbitrary number to ensure returned results are lesser*/ 10).getRecords();
        assertEquals(expectedCount, searchList.size());
        //Test search method that takes cursor
        List<EntityData> searchListByCursor = entityRepo.search(entityDefinition, mongoCriteria, new PageCursor("", PageDirection.next, 10)).getRecords();
        assertEquals(expectedCount, searchListByCursor.size());

    }

    private void assertValueNotFound(String collName, String fieldName, String value) {
        Slice<EntityData> search = entityRepo.search(collName, SearchCriteria.with(fieldName, value).setCaseSensitive(true), PageRequest.of(0, 5));
        assertTrue(search.isEmpty());
    }

    @Test
    public void deleteByIds() {
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("email", "test@example.com");
        EntityData saved = entityRepo.save(entityDefinition, entity);

        assertNotNull(entityRepo.findByIds("account", Set.of(saved.getId())).iterator().next());
        entityRepo.delete(List.of(saved.getId()),"account");
        assertFalse(entityRepo.findByIds("account", Set.of(saved.getId())).iterator().hasNext());
    }

    @Test
    public void readEntityScore() {
        Document object = Document.parse("{\"recordScore\":80, \"fieldScores\":{\"BillingCountry\":{\"fieldScore\":100, \"byRuleScores\":{\"isNotEmpty\":100}}}}");
        EntityScore score = entityRepo.getScore(object);
        verifyEntityScore(score);

        object = Document.parse("{\"name\":\"account\", \"connectorId\":\"null\", \"lastModified\":1618967274000, \"createdAt\":1554768893000}");
        score = entityRepo.getScore(object.get("syncariScore"));
        assertEquals(0, score.getRecordScore());
        assertTrue(MapUtils.isEmpty(score.getFieldScores()));

        object = Document.parse("{\"name\":\"account\", \"connectorId\":\"null\", \"lastModified\":1618967274000, \"createdAt\":1554768893000, \"syncariScore\":{\"recordScore\":80, \"fieldScores\":{\"BillingCountry\":{\"fieldScore\":100, \"byRuleScores\":{\"isNotEmpty\":100}}}}}");
        score = entityRepo.getScore(object.get("syncariScore"));
        verifyEntityScore(score);
    }

    @Test
    public void count() {
        ZonedDateTime begin = ZonedDateTime.now();
        EntityDefinition account = SchemaHelper.createEntityDefinition("account").string("name").string("address")
                .datetime("some").string("city").getEntityDefinition();
        for(int i=0;i<27;i++) {
            var entity = new EntityData("account");
            entity.addValue("name", "Test Account");
            entity.addValue("address", "some address");
            entity.addValue("some", ZonedDateTime.now());
            entity.addValue("city", "Fremont");
            EntityData saved = entityRepo.save(entityDefinition, entity);
            assertNotNull(saved.getSyncariEntityId());
        }
        for(int i=0;i<12;i++) {
            var entity = new EntityData("account");
            entity.addValue("name", "Skip these Accounts");
            entity.addValue("address", "some address");
            entity.addValue("some", ZonedDateTime.now());
            entity.addValue("city", "fremont");
            EntityData saved = entityRepo.save(entityDefinition, entity);
            assertNotNull(saved.getSyncariEntityId());
        }
        final Expression expression = Expression.startsWith(Expression.var(account.getFieldByName("name").getId()), Expression.lit("Test"));
        final long count = entityRepo.count(account, Optional.of(new LookupCriteriaVisitor(new GraphContext(), expression, tokenHelper,
            account.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(account, key))));
        assertEquals(27,count);

        final Expression expression2 = Expression.gte(Expression.var(account.getFieldByName("some").getId()), Expression.lit(begin));
        final long count2 = entityRepo.count(account, Optional.of(new LookupCriteriaVisitor(new GraphContext(), expression2, tokenHelper,
            account.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(account, key))));
        assertEquals(39,count2);

        final Expression expression3 = Expression.ieq(Expression.var(account.getFieldByName("city").getId()), Expression.lit("FREMONT"));
        var criteria = new LookupCriteriaVisitor(new GraphContext(), expression3, tokenHelper,
                account.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(account, key)).setHasCaseInSensitiveIndexOnField(true);

        final long count1 = entityRepo.count(account, Optional.of(criteria));
        assertEquals(39,count1);


    }

    @Test
    public void aggregateFunctions() {
        EntityDefinition account = SchemaHelper.createEntityDefinition("account").string("name").string("address").dbl("revenue")
                .datetime("some").getEntityDefinition();
        double sum = 0.0d;
        List<Double> revenues= new ArrayList<>();
        for(int i=0;i<27;i++) {
            var entity = new EntityData("account");
            entity.addValue("name", "Test Account");
            entity.addValue("address", "some address");
            entity.addValue("some", ZonedDateTime.now());
            //don't set revenue on every third record
            final double revenue = Math.random() * 1000000d;
            entity.addValue("revenue", revenue);
            sum+=revenue;
            revenues.add(revenue);
            EntityData saved = entityRepo.save(entityDefinition, entity);
            assertNotNull(saved.getSyncariEntityId());
        }
        for(int i=0;i<12;i++) {
            var entity = new EntityData("account");
            entity.addValue("name", "Skip these Accounts");
            entity.addValue("address", "some address");
            entity.addValue("some", ZonedDateTime.now());
            EntityData saved = entityRepo.save(entityDefinition, entity);
            assertNotNull(saved.getSyncariEntityId());
        }
        final Expression expression = Expression.startsWith(Expression.var(account.getFieldByName("name").getId()), Expression.lit("Test"));
        final double avgOnAddress = entityRepo.avg(account.getApiName(),"address",new LookupCriteriaVisitor(new GraphContext(), expression,
            tokenHelper, account.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(account, key)).createCriteria());
        final double sumOnAddress = entityRepo.sum(account.getApiName(),"address",new LookupCriteriaVisitor(new GraphContext(), expression,
            tokenHelper, account.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(account, key)).createCriteria());
        final double stdDevOnAddress = entityRepo.stdDev(account.getApiName(),"address",new LookupCriteriaVisitor(new GraphContext(), expression,
            tokenHelper, account.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(account, key)).createCriteria());
        assertEquals(0.0d,avgOnAddress,0.001d);
        assertEquals(0.0d,sumOnAddress,0.001d);
        assertEquals(0.0d,stdDevOnAddress,0.001d);

        final double avgOnRevenue = entityRepo.avg(account.getApiName(),"revenue",new LookupCriteriaVisitor(new GraphContext(), expression,
            tokenHelper, account.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(account, key)).createCriteria());
        final double sumOnRevenue = entityRepo.sum(account.getApiName(),"revenue",new LookupCriteriaVisitor(new GraphContext(), expression,
            tokenHelper, account.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(account, key)).createCriteria());
        final double stdDevOnRevenue = entityRepo.stdDev(account.getApiName(),"revenue",new LookupCriteriaVisitor(new GraphContext(), expression,
            tokenHelper, account.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(account, key)).createCriteria());
        assertEquals(sum/27,avgOnRevenue,0.001d);
        assertEquals(sum,sumOnRevenue,0.001d);
        final double stdDev = Math.sqrt(revenues.stream().map(r -> r - avgOnRevenue).map(r -> r * r).reduce((a, b) -> a + b).orElse(0.0d) / 27.0);
        assertEquals(stdDev,stdDevOnRevenue,0.001d);

    }
    private void verifyEntityScore(EntityScore score) {
        assertNotNull(score);
        assertEquals(80, score.getRecordScore());
        assertTrue(score.getFieldScores().containsKey("BillingCountry"));
        assertEquals(100, score.getFieldScores().get("BillingCountry").getFieldScore());
        assertNotNull(score.getFieldScores().get("BillingCountry").getByRuleScores());
        assertTrue(score.getFieldScores().get("BillingCountry").getByRuleScores().containsKey(RuleConstants.IS_NOT_EMPTY));
        assertNotNull(score.getFieldScores().get("BillingCountry").getByRuleScores().get(RuleConstants.IS_NOT_EMPTY));
    }

    @Test
    public void testContainsSearch() {

        entityDefinition.addField(new AttributeDefinition().setApiName("singleValuedString").setDataType(StringType.VALUE).setStatus(Status.ACTIVE));
        entityDefinition.addField(new AttributeDefinition().setApiName("multiValuedString").setDataType(StringType.VALUE).setMultiValueField(true).setStatus(Status.ACTIVE));
        entityDefinition.addField(new AttributeDefinition().setApiName("multiValuedNumber").setDataType(IntegerType.VALUE).setMultiValueField(true).setStatus(Status.ACTIVE));

        var entity1 = new EntityData("account");
        var entity2 = new EntityData("account");

        entity1.addValue("name", "Test Account1");
        entity1.addValue("address", "some address");
        entity1.addValue("singleValuedString", "test string");
        entity1.addValue("multiValuedString", List.of("test1", "test2"));
        entity1.addValue("multiValuedNumber", List.of(1, 2, 3, 4));

        entity2.addValue("name", "Test Account2");
        entity2.addValue("address", "some address");
        entity2.addValue("singleValuedString", "string test");
        entity2.addValue("multiValuedString", List.of("test1", "test3"));
        entity2.addValue("multiValuedNumber", List.of(3, 4, 5, 6));

        entityRepo.saveAll(entityDefinition, List.of(entity1, entity2));

        // Also check that the LookupCriteriaVisitor implementation works.
        Expression expression1 = Expression.contains(Expression.var("singleValuedString"), Expression.lit("test"));
        Expression expression2 = Expression.contains(Expression.var("multiValuedString"), Expression.lit("test2"));
        Expression expression3 = Expression.contains(Expression.var("multiValuedNumber"), Expression.lit(4));
        Expression expression4 = Expression.notContains(Expression.var("singleValuedString"), Expression.lit("test"));
        Expression expression5 = Expression.notContains(Expression.var("multiValuedString"), Expression.lit("test1"));
        Expression expression6 = Expression.contains(Expression.var("multiValuedNumber"), Expression.lit(6));


        Optional<LookupCriteriaVisitor> mongoCriteria = Optional.of(new LookupCriteriaVisitor(new GraphContext(), expression1, tokenHelper,
                entityDefinition.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entityDefinition, key)));

        List<EntityData> searchList = entityRepo.search(entityDefinition, mongoCriteria,10).getRecords();
        assertEquals(2, searchList.size());
        assertTrue(searchList.stream().anyMatch(s -> s.getTypedValue("singleValuedString").toString().equals("test string")));
        assertTrue(searchList.stream().anyMatch(s -> s.getTypedValue("singleValuedString").toString().equals("string test")));

        mongoCriteria = Optional.of(new LookupCriteriaVisitor(new GraphContext(), expression2, tokenHelper,
                entityDefinition.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entityDefinition, key)));
        searchList = entityRepo.search(entityDefinition, mongoCriteria,10).getRecords();
        assertEquals(1, searchList.size());
        assertThat(List.of("test1", "test2"), is((List)searchList.get(0).getTypedValue("multiValuedString")));

        mongoCriteria = Optional.of(new LookupCriteriaVisitor(new GraphContext(), expression3, tokenHelper,
                entityDefinition.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entityDefinition, key)));
        searchList = entityRepo.search(entityDefinition, mongoCriteria,10).getRecords();
        assertEquals(2, searchList.size());

        mongoCriteria = Optional.of(new LookupCriteriaVisitor(new GraphContext(), expression4, tokenHelper,
                entityDefinition.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entityDefinition, key)));
        searchList = entityRepo.search(entityDefinition, mongoCriteria,10).getRecords();
        assertEquals(0, searchList.size());

        mongoCriteria = Optional.of(new LookupCriteriaVisitor(new GraphContext(), expression5, tokenHelper,
                entityDefinition.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entityDefinition, key)));
        searchList = entityRepo.search(entityDefinition, mongoCriteria,10).getRecords();
        assertEquals(0, searchList.size());

        mongoCriteria = Optional.of(new LookupCriteriaVisitor(new GraphContext(), expression6, tokenHelper,
                entityDefinition.getIdToAttribMap(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entityDefinition, key)));
        searchList = entityRepo.search(entityDefinition, mongoCriteria,10).getRecords();
        assertEquals(1, searchList.size());

        entityRepo.deleteAll(entityDefinition, List.of(entity1, entity2));
    }

    @Test
    public void markDeleted()  {
        var records = List.of(
                new EntityData("account").addValue("name", "Test Account1").addValue("address", "some address"),
                new EntityData("account").addValue("name", "Test Account2").addValue("address", "some address"),
                new EntityData("account").addValue("name", "Test Account3").addValue("address", "some address")
                );
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("name").string("address").getEntityDefinition();
        final List<EntityData> saved = entityRepo.saveAll(entityDefinition, records);
         entityRepo.save(entityDefinition,  new EntityData("account").addValue("name", "Test Account4").addValue("address", "some address"));
        assertEquals(4,entityRepo.count("account",false));
        entityRepo.markDeleted(saved.stream().map(s->s.getSyncariEntityId()).collect(Collectors.toList()), "account");

        assertEquals(3,entityRepo.count("account",true));
        assertEquals(4,entityRepo.count("account",false));

    }

    @Test
    public void basicUpdateValues()  {
        var records = List.of(
                new EntityData("account").addValue("name", "Test Account1").addValue("address", "some address"),
                new EntityData("account").addValue("name", "Test Account2").addValue("address", "some address"),
                new EntityData("account").addValue("name", "Test Account3").addValue("address", "some address")
        );
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("name").string("address").string("customField").getEntityDefinition();
        final List<EntityData> saved = entityRepo.saveAll(entityDefinition, records);
        assertEquals(3,entityRepo.count("account",false));
        assertTrue(saved.stream().allMatch(e->e.getValue("customField")==null));
        final List<EntityData> updates = saved.stream().map(e -> new EntityData("account").setSyncariEntityId(e.getSyncariEntityId()).addValue("customField", "customValue" + e.getSyncariEntityId())).collect(Collectors.toList());
        entityRepo.updateValues(entityDefinition,updates);
        final Iterable<EntityData> byIds = entityRepo.findByIds(entityDefinition, saved.stream().map(e -> e.getSyncariEntityId()).collect(Collectors.toSet()));
        byIds.forEach(e-> assertNotNull(e.getValue("customField")));
    }

    @Test
    public void updateValues_skips_deleted()  {
        var records = List.of(
                new EntityData("account").addValue("name", "Test Account1").addValue("address", "some address"),
                new EntityData("account").addValue("name", "Test Account2").addValue("address", "some address"),
                new EntityData("account").addValue("name", "Test Account3").addValue("address", "some address").setDeleted(true)
        );
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("name").string("address").string("customField").getEntityDefinition();
        final List<EntityData> saved = entityRepo.saveAll(entityDefinition, records);
        assertEquals(3,entityRepo.count("account",false));
        assertEquals(1,entityRepo.count("account",true));
        assertTrue(saved.stream().allMatch(e->e.getValue("customField")==null));
        final List<EntityData> updates = saved.stream().map(e -> new EntityData("account").setSyncariEntityId(e.getSyncariEntityId()).addValue("customField", "customValue" + e.getSyncariEntityId())).collect(Collectors.toList());
        entityRepo.updateValues(entityDefinition,updates);
        final Iterable<EntityData> byIds = entityRepo.findByIds(entityDefinition, saved.stream().map(e -> e.getSyncariEntityId()).collect(Collectors.toSet()));
        byIds.forEach(e-> {
            if(!e.isDeleted()) {
                assertNotNull(e.getValue("customField"));
            }else{
                assertNull(e.getValue("customField"));
            }
        });
    }

    @Test
    public void updateValues_skips_empty_records()  {
        var records = List.of(
                new EntityData("account").addValue("name", "Test Account1").addValue("address", "some address"),
                new EntityData("account").addValue("name", "Test Account2").addValue("address", "some address")
        );
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("name").string("address").string("customField").getEntityDefinition();
        final List<EntityData> saved = entityRepo.saveAll(entityDefinition, records);
        assertEquals(2,entityRepo.count("account",false));
        assertTrue(saved.stream().allMatch(e->e.getValue("customField")==null));
        final List<EntityData> updates = List.of(
                new EntityData("account").setSyncariEntityId(saved.get(0).getSyncariEntityId()).addValue("customField", "customValue"),
                new EntityData("account").setSyncariEntityId(saved.get(1).getSyncariEntityId())
        );
        entityRepo.updateValues(entityDefinition,updates);
        final List<EntityData> byIds = IterableUtils.toList(entityRepo.findByIds(entityDefinition, saved.stream().map(e -> e.getSyncariEntityId()).collect(Collectors.toSet())));
        Map<String, Object> retrieved = new HashMap<>();
        byIds.forEach(e-> retrieved.put(e.getSyncariEntityId(),e));
        assertEquals(2,retrieved.size());
        EntityData retrieved1= entityRepo.findById(entityDefinition, saved.get(0).getSyncariEntityId()).get();
        EntityData retrieved2= entityRepo.findById(entityDefinition, saved.get(1).getSyncariEntityId()).get();
        assertTrue(retrieved1.getSyncariTimestamp() <= byIds.get(0).getSyncariTimestamp());
        assertEquals("customValue",byIds.get(0).getValueAsString("customField"));
        assertNull(byIds.get(1).getValueAsString("customField"));
        //no change in timestamp
        assertEquals(retrieved2.getSyncariTimestamp(), byIds.get(1).getSyncariTimestamp());
    }

    @Override
    public void tearDown() {
        entityRepo.deleteAll("account");
        entityRepo.deleteAll("contact");
    }

    @Override
    public void setUp() {
        super.setUp();
        entityDefinition = SchemaHelper.createEntityDefinition("account").
                string("name")
                .string("address")
                .string("email")
                .datetime("some")
                .field("obj", ObjectType.VALUE, true)
                .getEntityDefinition();
        entityRepo.deleteAll("account");
        entityRepo.deleteAll("contact");
    }
}
