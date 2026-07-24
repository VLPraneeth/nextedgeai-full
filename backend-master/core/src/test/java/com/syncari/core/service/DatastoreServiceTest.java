package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.syncari.connector.datastore.SyncariDatastoreService;
import com.syncari.core.model.misc.ConnectorStatus;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.database.DatastoreFactory;
import com.syncari.connector.datastore.Datastore;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.datatype.IdType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.ReferenceType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.DatastoreWatermark;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.Feature;
import com.syncari.core.model.ResourceType;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.misc.FeatureStage;
import com.syncari.core.model.misc.FeatureStatus;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.FeatureRepo;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.DataStoreConfig;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.schema.Schema;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

@Ignore
@Slf4j
public class DatastoreServiceTest extends AbstractSyncariTest {
    @Autowired
    DatastoreService datastoreService;
    @Autowired
    ConnectorRepo connectorRepo;
    @Autowired
    DataServiceFactory factory;
    @Autowired
    DatastoreFactory dsFactory;
    @Autowired
    ConnectorService connService;
    @Autowired
    DataTransformer transformer;
    @Autowired
    FeatureRepo featureRepo;
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    @Autowired
    AttributeRepo attributeProxyRepo;
    @Autowired
    SchemaService schemaService;
    @Autowired
    SyncariDatastoreService synapseService;
    @Autowired
    AppConfig appConfig;
    
    @Override
    public void setUp() {
        super.setUp();
        featureRepo.save(new Feature(Features.Datastore.name(), FeatureStage.GA, FeatureStatus.active));
    }

    @After
    public void tearDown() {
        entityRepo.deleteAll("account");
        super.tearDown();
    }
    
    @Test
    public void provisionValidations() {
        try {
            datastoreService.provision(null);
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("A schema name is required to provision Syncari datastore"));
        }
        try {
            datastoreService.provision("");
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("A schema name is required to provision Syncari datastore"));
        }
    }

    @Test
    
    public void provisionValidSubscription() {
        String schema = SyncariContext.getSyncariId();
        try {

            long before = connectorRepo.count();
            datastoreService.provision(schema);
            long after = connectorRepo.count();
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            Datastore dataService = dsFactory.getService(connectorInfo);
            EntitySchema entitySchema = new EntitySchema("account");
            AttributeSchema attributeSchema = new AttributeSchema("name", "string");
            attributeSchema.setWatermarkField(true);
            entitySchema.setAttributes(List.of(attributeSchema));
            DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(connector), List.of());
            List<EntitySchema> describeAll = dataService.describeAll(request);
            assertEquals(before + 1, after);
            assertTrue(describeAll.size() > 0);
            describeAll.forEach(e -> {
                assertTrue(e.getAttributes().size() > 0);
                assertTrue(e.hasField("syncariid"));
            });
            assertTrue(SyncariContext.getInstance().getResource(ResourceType.DATASTORE).isPresent());
            assertTrue(SyncariContext.getInstance().getResourceConfig(ResourceType.DATASTORE, DatastoreService.DATASTORE_USER_NAME).isPresent());
            assertTrue(SyncariContext.getInstance().getResourceConfig(ResourceType.DATASTORE, DatastoreService.DATASTORE_PASSWORD).isPresent());
        } catch(Exception e) {
            log.error(e.getMessage());
            fail();
        } finally {
            datastoreService.deprovision(schema);
        }
    }
    
    @Test
    public void provisionIsIdempotent() {
        String schema = SyncariContext.getSyncariId();
        try {
            long before = connectorRepo.count();
            datastoreService.provision(schema);
            datastoreService.provision(schema);
            long after = connectorRepo.count();
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            Datastore dataService = dsFactory.getService(connectorInfo);
            EntitySchema entitySchema = new EntitySchema("account");
            AttributeSchema attributeSchema = new AttributeSchema("name", "string");
            attributeSchema.setWatermarkField(true);
            entitySchema.setAttributes(List.of(attributeSchema));
            DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(connector), List.of());
            List<EntitySchema> describeAll = dataService.describeAll(request);
            assertEquals(before + 1, after);
            assertTrue(describeAll.size() > 0);
            describeAll.forEach(e -> {
                assertTrue(e.getAttributes().size() > 0);
                assertTrue(e.hasField("syncariid"));
            });
        } catch(Exception e) {
            log.error(e.getMessage());
            fail();
        } finally {
            datastoreService.deprovision(schema);
        }
    }

    @Test
    public void getMaxIterationsNeeded() {
        String schema = SyncariContext.getSyncariId();
        WatermarkService origWMService = datastoreService.watermarkService;
        try {
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            EntityDefinition entityDefinition = schemaService.getEntity(account.getId());
            // No datastore provisioned, simply return default page value.
            assertEquals(100, datastoreService.getMaxIterationsNeeded(entityDefinition, 150));
            datastoreService.provision(schema);
            assertEquals(100, datastoreService.getMaxIterationsNeeded(entityDefinition, 0));
            assertEquals(100, datastoreService.getMaxIterationsNeeded(entityDefinition, 10));
            assertEquals(100, datastoreService.getMaxIterationsNeeded(entityDefinition, DatastoreService.PAGE_SIZE));
            assertEquals(100, datastoreService.getMaxIterationsNeeded(entityDefinition, DatastoreService.PAGE_SIZE * 2));
            assertEquals(100, datastoreService.getMaxIterationsNeeded(entityDefinition, DatastoreService.PAGE_SIZE * 2 + 99));
            assertEquals(100, datastoreService.getMaxIterationsNeeded(entityDefinition, DatastoreService.PAGE_SIZE * 20));
            assertEquals(100, datastoreService.getMaxIterationsNeeded(entityDefinition, 2001));
            assertEquals(103, datastoreService.getMaxIterationsNeeded(entityDefinition, 10221));
            // Mock datastore watermark.
            DatastoreWatermark dsWM = mock(DatastoreWatermark.class);
            datastoreService.watermarkService = mock(WatermarkService.class);
            when(datastoreService.watermarkService.getDatastoreWatermark(any())).thenReturn(Optional.of(dsWM));
            when(dsWM.getIterationsPerCycle()).thenReturn(100l);
            assertEquals(100l, datastoreService.getMaxIterationsNeeded(entityDefinition, 20));
            when(dsWM.getIterationsPerCycle()).thenReturn(200l);
            assertEquals(200l, datastoreService.getMaxIterationsNeeded(entityDefinition, 20));
            when(dsWM.getIterationsPerCycle()).thenReturn(201l);
            assertEquals(200l, datastoreService.getMaxIterationsNeeded(entityDefinition, 20));
            when(dsWM.getIterationsPerCycle()).thenReturn(100l);
            assertEquals(100l, datastoreService.getMaxIterationsNeeded(entityDefinition, 20000));
        } catch(Exception e) {
            log.error(e.getMessage());
            fail();
        } finally {
            datastoreService.watermarkService = origWMService;
            datastoreService.deprovision(schema);
        }
    }
    
    @Test
    public void addAndDeleteField() {
        String schema = SyncariContext.getSyncariId();
        try {
            long before = connectorRepo.count();
            datastoreService.provision(schema);
            long after = connectorRepo.count();
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            Datastore dataService = dsFactory.getService(connectorInfo);
            DescribeAllRequest request = new DescribeAllRequest(connectorInfo, List.of());
            List<EntitySchema> describeAll = dataService.describeAll(request);
            assertEquals(before + 1, after);
            assertTrue(describeAll.size() > 0);
            describeAll.forEach(e -> {
                assertTrue(e.getAttributes().size() > 0);
                assertTrue(e.hasField("syncariid"));
            });
            
            // Add new field to account while insert
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            EntityDefinition entityDefinition = schemaService.getEntity(account.getId());
            AttributeDefinition testField = new AttributeDefinition();
            testField.setEntityId(entityDefinition.getId());
            testField.setApiName("test_field");
            testField.setDisplayName("test_field");
            testField.setDataType(new StringType());
            testField = attributeProxyRepo.save(testField);
            entityDefinition.addField(testField);
            
            AttributeDefinition wmField = new AttributeDefinition();
            wmField.setEntityId(entityDefinition.getId());
            wmField.setApiName("watermark");
            wmField.setDisplayName("watermark");
            wmField.setDataType(new IntegerType());
            wmField.setWatermarkField(true);
            wmField.setStatus(Status.ACTIVE);
            wmField = attributeProxyRepo.save(wmField);
            entityDefinition.addField(wmField);
            EntitySchema accountSchema = transformer.toEntitySchema(entityDefinition, connector);
            // Insert first record
            entityRepo.createCollection(entityDefinition);
            EntityData data = new EntityData(account.getApiName());
            data.addValue("name", "test account");
            data.addValue("website", "www.test.com");
            data.addValue("test_field", "test value");
            data.addValue("watermark", 5);
            entityRepo.save(data);
            //Simulate pipeline sequence
            datastoreService.createEntity(entityDefinition);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
            
            // Insert second record
            EntityData data1 = new EntityData(account.getApiName());
            data1.addValue("name", "test account1");
            data1.addValue("website", "www.test1.com");
            data1.addValue("test_field", "test value1");
            data1.addValue("watermark", 6);
            entityRepo.save(data1);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
            
            // Verify new column and its data exists
            SyncRequest query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertNotNull(next.get(0).getValue("test_field"));
            assertNotNull(next.get(1).getValue("test_field"));
//            assertNotNull(next.get(0).getValue("syncariid"));
//            assertNotNull(next.get(1).getValue("syncariid"));
            
            // Delete the new field from account while insert
            attributeProxyRepo.delete(testField);
            entityDefinition = schemaService.getEntity(account.getId());
            accountSchema = transformer.toEntitySchema(entityDefinition, connector);
            
            entityRepo.createCollection(entityDefinition);
            data = new EntityData(account.getApiName());
            data.addValue("name", "test account");
            data.addValue("website", "www.test.com");
            data.addValue("watermark", 5);
            entityRepo.save(data);

            //Simulate pipeline sequence
            datastoreService.createEntity(entityDefinition);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
            
            // Verify new column doesnt exist anymore
            query.setEntitySchema(accountSchema);
            resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertFalse(next.get(0).getValues().containsKey("test_field"));
            assertNull(next.get(0).getValue("test_field"));
            assertFalse(next.get(1).getValues().containsKey("test_field"));
            assertNull(next.get(1).getValue("test_field"));
            
        } finally {
            datastoreService.deprovision(schema);
        }
    }

    @Test
    public void paginationWithOffset() {
        String schema = SyncariContext.getSyncariId();
        try {
            datastoreService.provision(schema);
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            EntitySchema accountSchema = transformer.toEntitySchema(account, connector);
            EntityDefinition entityDefinition = transformer.toEntityDefinition(accountSchema, connector);
            entityDefinition.getField("NumberOfEmployees").get().setWatermarkField(true);
            accountSchema.getField("NumberOfEmployees").get().setWatermarkField(true);
            accountSchema.addField(new AttributeSchema("syncariid", "string").setIdField(true));

            // Insert 5K records
            entityRepo.createCollection(entityDefinition);

            long currentTS = Instant.now().toEpochMilli()-24*60*60*1000;

            List<EntityData> entityDataList= new ArrayList<>();
            for (int i=10000; i<15000; i++){
                EntityData data = new EntityData(account.getApiName());
                data.addValue("Name", "test account"+i);
                data.addValue("Industry", "www.test"+i+".com");
                data.addValue("NumberOfEmployees", 5);
                data.addValue("syncariTimestamp", currentTS);
                entityDataList.add(data);
            }
            entityRepo.saveAll(null, entityDataList, false);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);

            // Verify data exists
            SyncRequest query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertFalse(resp.getIterator().hasNext());


            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
            resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertFalse(resp.getIterator().hasNext());

            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
            resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertFalse(resp.getIterator().hasNext());
        } finally {
            datastoreService.deprovision(schema);
        }
    }

    @Test
    public void addAndDeleteRecord() {
        String schema = SyncariContext.getSyncariId();
        try {
            long before = connectorRepo.count();
            datastoreService.provision(schema);
            long after = connectorRepo.count();
            String dsId = connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId();
            connService.setStatus(dsId, ConnectorStatus.ERROR, "Error","Error");
            datastoreService.activate(dsId);
            Connector connector = connService.find(dsId).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            Datastore dataService = dsFactory.getService(connectorInfo);
            DescribeAllRequest request = new DescribeAllRequest(connectorInfo, List.of());
            List<EntitySchema> describeAll = dataService.describeAll(request);
            assertEquals(before + 1, after);
            assertTrue(describeAll.size() > 0);
            describeAll.forEach(e -> {
                assertTrue(e.getAttributes().size() > 0);
                assertTrue(e.hasField("syncariid"));
            });

            // Add new field to account while insert
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            EntityDefinition entityDefinition = schemaService.getEntity(account.getId());
            AttributeDefinition testField = new AttributeDefinition();
            testField.setEntityId(entityDefinition.getId());
            testField.setApiName("test_field");
            testField.setDisplayName("test_field");
            testField.setDataType(new StringType());
            testField = attributeProxyRepo.save(testField);
            entityDefinition.addField(testField);

            AttributeDefinition wmField = new AttributeDefinition();
            wmField.setEntityId(entityDefinition.getId());
            wmField.setApiName("watermark");
            wmField.setDisplayName("watermark");
            wmField.setDataType(new IntegerType());
            wmField.setWatermarkField(true);
            wmField.setStatus(Status.ACTIVE);
            wmField = attributeProxyRepo.save(wmField);
            entityDefinition.addField(wmField);
            EntitySchema accountSchema = transformer.toEntitySchema(entityDefinition, connector);
            // Insert first record
            entityRepo.createCollection(entityDefinition);
            EntityData data = new EntityData(account.getApiName());
            data.addValue("name", "test account");
            data.addValue("website", "www.test.com");
            data.addValue("test_field", "test value");
            data.addValue("watermark", 5);
            entityRepo.save(data);
            //Simulate pipeline sequence
            datastoreService.createEntity(entityDefinition);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
            datastoreService.delete(entityDefinition,connector, data);

            Map<String, List<EntityData>> alreadyDeleted = new HashMap<>();
            alreadyDeleted.putIfAbsent(connector.getId(), List.of(data));

            SyncRequest queryAfterDelete = new SyncRequest().Builder(connectorInfo, accountSchema);
            queryAfterDelete.setData(alreadyDeleted);
            List<EntityData> datalist = synapseService.getByIds(queryAfterDelete);
            assertTrue(datalist.isEmpty());

        } finally {
            datastoreService.deprovision(schema);
        }
    }
    
    @Test
    public void createEntity() {
        String schema = SyncariContext.getSyncariId();
        try {
            datastoreService.provision(schema);
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);

            EntityDefinition entity = new EntityDefinition("14taqhfz_obws_vikbnvl7oibvl6gsc2o", "Some Entity");
            entity.setId("123");
            AttributeDefinition field = new AttributeDefinition();
            field.setApiName("somefield").setDataType(new StringType()).setIdField(true);
            entity.addField(field);
            entity = entityProxyRepo.save(entity);
            datastoreService.createEntity(entity);

            // Rename entity
            entity.setDataStoreName("some_entity");
            entity = entityProxyRepo.save(entity);
            assertTrue(entity.isDsNameAltered());
            datastoreService.createEntity(entity);
            
            // Ensure the old entity is replaced in the datastore.
            assertFalse(synapseService.describe(new DescribeRequest(connectorInfo, "14taqhfz_obws_vikbnvl7oibvl6gsc2o")).isPresent());
            assertTrue(synapseService.describe(new DescribeRequest(connectorInfo, "some_entity")).isPresent());
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            fail();
        } finally {
            datastoreService.deprovision(schema);
        }
    }
    
    @Test
    
    public void renameField() {
        String schema = SyncariContext.getSyncariId();
        try {
            datastoreService.provision(schema);
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            EntitySchema accountSchema = transformer.toEntitySchema(account, connector);
            EntityDefinition entityDefinition = transformer.toEntityDefinition(accountSchema, connector);
            
            // Rename TwitterHandle
            AttributeDefinition fieldByName = entityDefinition.getFieldByName("TwitterHandle");
            fieldByName.setEntityId(entityDefinition.getId());
            fieldByName.setDataStoreName("TwitterHandle_changed");
            attributeProxyRepo.save(fieldByName);
            assertTrue(entityDefinition.getFieldByName("TwitterHandle").isDsNameAltered());
            datastoreService.createEntity(entityDefinition);
            entityDefinition = schemaService.getEntity(entityDefinition.getId());
            assertFalse(entityDefinition.getFieldByName("TwitterHandle").isDsNameAltered());
            
            SyncRequest query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            EntitySchema resp = synapseService.describe(new DescribeRequest(connectorInfo, "account")).get();
            assertFalse(resp.hasField("TwitterHandle"));
            assertTrue(resp.hasField("TwitterHandle_changed"));

            // Make sure the name is in lower case
            assertEquals("TwitterHandle_changed".toLowerCase(), resp.getField("TwitterHandle_changed").get().getApiName());

            
            // Rename back
            fieldByName = entityDefinition.getFieldByName("TwitterHandle");
            fieldByName.setDataStoreName("TwitterHandle");
            attributeProxyRepo.save(fieldByName);
            assertTrue(entityDefinition.getFieldByName("TwitterHandle").isDsNameAltered());
            datastoreService.createEntity(entityDefinition);
            entityDefinition = schemaService.getEntity(entityDefinition.getId());
            assertFalse(entityDefinition.getFieldByName("TwitterHandle").isDsNameAltered());
            
            query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            resp = synapseService.describe(new DescribeRequest(connectorInfo, "account")).get();
            assertFalse(resp.hasField("TwitterHandle_changed"));
            assertTrue(resp.hasField("TwitterHandle"));

            // Make sure the name is in lower case
            assertEquals("TwitterHandle".toLowerCase(), resp.getField("TwitterHandle").get().getApiName());

        } finally {
            datastoreService.deprovision(schema);
        }
    }
    
    @Test
    public void renameFieldInsertData() {
        String schema = SyncariContext.getSyncariId();
        try {
            datastoreService.provision(schema);
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            EntitySchema accountSchema = transformer.toEntitySchema(account, connector);
            EntityDefinition entityDefinition = transformer.toEntityDefinition(accountSchema, connector);
            entityDefinition.getField("NumberOfEmployees").get().setWatermarkField(true);
            accountSchema.getField("NumberOfEmployees").get().setWatermarkField(true);
            accountSchema.addField(new AttributeSchema("syncariid", "string").setIdField(true));
            
            // Insert first record
            entityRepo.createCollection(entityDefinition);
            EntityData data = new EntityData(account.getApiName());
            data.addValue("Name", "test account");
            data.addValue("Industry", "www.test.com");
            data.addValue("NumberOfEmployees", 5);
            entityRepo.save(data);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
            

            // Verify data exists
            SyncRequest query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertNotNull(next.get(0).getValue("Industry"));
            entityDefinition = schemaService.getEntity(entityDefinition.getId());
            entityDefinition.getField("NumberOfEmployees").get().setWatermarkField(true);
            
            // Rename Industry
            AttributeDefinition fieldByName = entityDefinition.getFieldByName("Industry");
            fieldByName.setEntityId(entityDefinition.getId());
            fieldByName.setDataStoreName("Industry_changed");
            attributeProxyRepo.save(fieldByName);
            assertTrue(entityDefinition.getFieldByName("Industry").isDsNameAltered());
            datastoreService.createEntity(entityDefinition);
            entityDefinition = schemaService.getEntity(entityDefinition.getId());
            assertFalse(entityDefinition.getFieldByName("Industry").isDsNameAltered());
            
            entityDefinition = schemaService.getEntity(entityDefinition.getId());
            entityDefinition.getField("NumberOfEmployees").get().setWatermarkField(true);
            
            accountSchema.getField("Industry").get().setApiName("Industry_changed");
            // Verify data exists
            query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertNotNull(next.get(0).getValue("Industry_changed"));
            assertNotNull(next.get(0).getValue("syncariid"));
            
            // Insert second record
            data = new EntityData(account.getApiName());
            data.addValue("Name", "test account1");
            data.addValue("Industry", "www.test1.com");
            data.addValue("NumberOfEmployees", 3);
            data = entityRepo.save(data);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
            
            // Verify data exists
            query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertNotNull(next.get(0).getValue("Industry_changed"));
            assertNotNull(next.get(0).getValue("syncariid"));
            
            // update record
            data.addValue("Name", "test-changed");
            data = entityRepo.save(data);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
            query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() == 2);
            //reliably find the modified record.
            EntityData changed = next.stream()
                .filter(x -> x.has("Industry_changed") && "www.test1.com".equalsIgnoreCase(x.getValueAsString("Industry_changed")))
                .findAny().map(x -> x).orElse(null);
            assertNotNull(changed);
            assertEquals("test-changed", changed.getValue("Name"));
            assertNotNull(changed.getValue("syncariid"));
            
            // delete record
            data.setDeleted(true);
            entityRepo.save(data);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
            query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            next = resp.getIterator().next();
            assertTrue(next.size() == 1);
            
            // reset industry
            fieldByName = entityDefinition.getFieldByName("Industry");
            fieldByName.setEntityId(entityDefinition.getId());
            fieldByName.setDataStoreName("Industry");
            attributeProxyRepo.save(fieldByName);
            assertTrue(entityDefinition.getFieldByName("Industry").isDsNameAltered());
            datastoreService.createEntity(entityDefinition);
            entityDefinition = schemaService.getEntity(entityDefinition.getId());
            assertFalse(entityDefinition.getFieldByName("Industry").isDsNameAltered());
        } finally {
            datastoreService.deprovision(schema);
        }
    }
    
    @Test
    public void valueTooLong() {
        String schema = SyncariContext.getSyncariId();
        try {
            datastoreService.provision(schema);
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            EntitySchema accountSchema = transformer.toEntitySchema(account, connector);
            EntityDefinition entityDefinition = transformer.toEntityDefinition(accountSchema, connector);
            entityRepo.createCollection(entityDefinition);
            EntityData data1 = new EntityData(account.getApiName());
            data1.addValue("Name", "test account");
            data1.addValue("Industry", "www.test.com");
            data1.addValue("NumberOfEmployees", 5);
            data1.addValue("ShippingCity", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            data1 = entityRepo.save(data1);
            EntityData data = new EntityData(account.getApiName());
            data.addValue("Name", "test account");
            data.addValue("Industry", "www.test.com");
            data.addValue("NumberOfEmployees", 5);
            data.addValue("ShippingCity", "ssssssssssssssssssssssssssssssssssssssssssssssssssssss");
            data = entityRepo.save(data);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
            
            //update value
            data.addValue("ShippingCity", "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd");
            data = entityRepo.save(data);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
        } catch (Exception e) {
            fail();
        } finally {
            datastoreService.deprovision(schema);
        }
    }
    
    @Test
    public void insertNullText() {
        String schema = SyncariContext.getSyncariId();
        try {
            datastoreService.provision(schema);
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            EntitySchema accountSchema = transformer.toEntitySchema(account, connector);
            EntityDefinition entityDefinition = transformer.toEntityDefinition(accountSchema, connector);
            entityRepo.createCollection(entityDefinition);
            EntityData data1 = new EntityData(account.getApiName());
            data1.addValue("Name", "test account");
            data1.addValue("Industry", "0x00");
            data1.addValue("NumberOfEmployees", 5);
            data1.addValue("ShippingCity", "This is \u0000 char");
            data1 = entityRepo.save(data1);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
        } catch (Exception e) {
            fail();
        } finally {
            datastoreService.deprovision(schema);
        }
    }
    
    @Test
    public void renameObject() throws InterruptedException {
        String schema = SyncariContext.getSyncariId();
        try {
            datastoreService.provision(schema);
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            EntitySchema accountSchema = transformer.toEntitySchema(account, connector);
            
            // Rename entity
            EntityDefinition entityDefinition = entityProxyRepo.findById(accountSchema.getId()).get();
            entityDefinition.setDataStoreName("account_changed");
            entityProxyRepo.save(entityDefinition);
            assertTrue(entityDefinition.isDsNameAltered());
            datastoreService.createEntity(entityDefinition);
            entityDefinition = schemaService.getEntity(entityDefinition.getId());
            assertFalse(entityDefinition.isDsNameAltered());
            
            SyncRequest query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            assertFalse(synapseService.describe(new DescribeRequest(connectorInfo, "account")).isPresent());
            assertTrue(synapseService.describe(new DescribeRequest(connectorInfo, "account_changed")).isPresent());
            
            // Rename entity back
            entityDefinition = entityProxyRepo.findById(entityDefinition.getId()).get();
            entityDefinition.setDataStoreName("account");
            entityProxyRepo.save(entityDefinition);
            assertEquals("account_changed", entityDefinition.getDataStoreOldName());
            assertTrue(entityDefinition.isDsNameAltered());
            datastoreService.createEntity(entityDefinition);
            entityDefinition = schemaService.getEntity(entityDefinition.getId());
            assertFalse(entityDefinition.isDsNameAltered());
            
            query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            assertFalse(synapseService.describe(new DescribeRequest(connectorInfo, "account_changed")).isPresent());
            assertTrue(synapseService.describe(new DescribeRequest(connectorInfo, "account")).isPresent());
        } finally {
            datastoreService.deprovision(schema);
        }
    }

    @Test
    public void renameObjectAndField() throws InterruptedException {
        String schema = SyncariContext.getSyncariId();
        try {
            datastoreService.provision(schema);
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            EntitySchema accountSchema = transformer.toEntitySchema(account, connector);
            
            // Rename entity
            EntityDefinition entityDefinition = entityProxyRepo.findById(accountSchema.getId()).get();
            datastoreService.createEntity(entityDefinition);
            entityDefinition = schemaService.getEntity(entityDefinition.getId());
            entityDefinition.setDataStoreName("account_changed");
            entityProxyRepo.save(entityDefinition);
            assertTrue(entityDefinition.isDsNameAltered());
            accountSchema.getField("NumberOfEmployees").get().setWatermarkField(true);
            // Rename Industry
            AttributeDefinition fieldByName = entityDefinition.getFieldByName("BillingCity");
            fieldByName.setEntityId(entityDefinition.getId());
            fieldByName.setDataStoreName("BillingCity_changed");
            attributeProxyRepo.save(fieldByName);
            assertTrue(entityDefinition.getFieldByName("BillingCity").isDsNameAltered());

            datastoreService.createEntity(entityDefinition);
            entityDefinition = schemaService.getEntity(entityDefinition.getId());
            assertFalse(entityDefinition.getFieldByName("BillingCity").isDsNameAltered());
            assertFalse(entityDefinition.isDsNameAltered());
            assertFalse(synapseService.describe(new DescribeRequest(connectorInfo, "account")).isPresent());
            Optional<EntitySchema> describe = synapseService.describe(new DescribeRequest(connectorInfo, "account_changed"));
            assertTrue(describe.isPresent());
            assertTrue(describe.get().getField("BillingCity_changed").isPresent());
            assertFalse(describe.get().getField("BillingCity").isPresent());
            
            // Rename entity back
            entityDefinition = entityProxyRepo.findById(entityDefinition.getId()).get();
            entityDefinition.setDataStoreName("account");
            entityProxyRepo.save(entityDefinition);
            assertEquals("account_changed", entityDefinition.getDataStoreOldName());
            assertTrue(entityDefinition.isDsNameAltered());
            datastoreService.createEntity(entityDefinition);
            entityDefinition = schemaService.getEntity(entityDefinition.getId());
            assertFalse(entityDefinition.isDsNameAltered());
            assertFalse(synapseService.describe(new DescribeRequest(connectorInfo, "account_changed")).isPresent());
            assertTrue(synapseService.describe(new DescribeRequest(connectorInfo, "account")).isPresent());
        } finally {
            datastoreService.deprovision(schema);
        }
    }
    
    @Test
    public void changeFieldLength() {
        String schema = SyncariContext.getSyncariId();
        retry(() -> {
            try {
                datastoreService.provision(schema);
                Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
                ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
                Schema syncariSchema = schemaService.getSyncariSchema();
                EntityDef account = syncariSchema.findEntityByName("account").get();
                EntitySchema accountSchema = transformer.toEntitySchema(account, connector);
                EntityDefinition entityDefinition = transformer.toEntityDefinition(accountSchema, connector);
                
                // Change length
                AttributeDefinition fieldByName = entityDefinition.getFieldByName("ShippingCountry");
                fieldByName.setEntityId(entityDefinition.getId());
                fieldByName.setLength(3200);
                attributeProxyRepo.save(fieldByName);
                datastoreService.createEntity(entityDefinition);
                
                SyncRequest query = new SyncRequest().Builder(connectorInfo, accountSchema);
                query.setWatermark(new WatermarkInfo(0, 10, true, 0));
                EntitySchema resp = synapseService.describe(new DescribeRequest(connectorInfo, "account")).get();
                assertEquals(3200, resp.getField("ShippingCountry").get().getLength());
                
                // Change length to lower value not allowed
                fieldByName = entityDefinition.getFieldByName("ShippingCountry");
                fieldByName.setEntityId(entityDefinition.getId());
                fieldByName.setLength(200);
                attributeProxyRepo.save(fieldByName);
                datastoreService.createEntity(entityDefinition);
                
                query = new SyncRequest().Builder(connectorInfo, accountSchema);
                query.setWatermark(new WatermarkInfo(0, 10, true, 0));
                resp = synapseService.describe(new DescribeRequest(connectorInfo, "account")).get();
                assertEquals(3200, resp.getField("ShippingCountry").get().getLength());
                
            } finally {
                datastoreService.deprovision(schema);
            }
        });
    }

    @Test
    public void datetimeFieldConversion() {
        String schema = SyncariContext.getSyncariId();
        try {
            long before = connectorRepo.count();
            datastoreService.provision(schema);
            long after = connectorRepo.count();
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            Datastore dataService = dsFactory.getService(connectorInfo);
            DescribeAllRequest request = new DescribeAllRequest(connectorInfo, List.of());
            List<EntitySchema> describeAll = dataService.describeAll(request);
            assertEquals(before + 1, after);
            assertTrue(describeAll.size() > 0);
            describeAll.forEach(e -> {
                assertTrue(e.getAttributes().size() > 0);
                assertTrue(e.hasField("syncariid"));
            });

            // Add new field to account while insert
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            AttributeDef testField = new AttributeDef("test_field", "test_field");
            testField.setDataType("string");
            account.getFields().add(testField);
            AttributeDef wmField = new AttributeDef("watermark", "watermark");
            wmField.setDataType("integer");
            account.getFields().add(wmField);
            EntitySchema accountSchema = transformer.toEntitySchema(account, connector);
            accountSchema.getField("watermark").get().setWatermarkField(true);
            EntityDefinition entityDefinition = transformer.toEntityDefinition(accountSchema, connector);

            // Insert first record
            entityRepo.createCollection(entityDefinition);
            EntityData data = new EntityData(account.getApiName());
            data.addValue("name", "test account");
            data.addValue("website", "www.test.com");
            data.addValue("test_field", "test value");
            data.addValue("watermark", 5);
            long created = Instant.now().minusSeconds(DatastoreService.PAGE_SIZE).toEpochMilli();
            data.addValue("CreatedDate", created);
            entityRepo.save(data);

            // Insert second record
            EntityData data1 = new EntityData(account.getApiName());
            data1.addValue("name", "test account1");
            data1.addValue("website", "www.test1.com");
            data1.addValue("test_field", "test value1");
            data1.addValue("watermark", 6);
            long created1 = Instant.now().minusSeconds(500).toEpochMilli();
            data1.addValue("CreatedDate", created1);
            entityRepo.save(data1);

            datastoreService.createEntity(entityDefinition);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);

            // Verify new column and its data exists
            SyncRequest query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertEquals(created, ((ZonedDateTime)next.get(0).getValue("CreatedDate")).toInstant().toEpochMilli());
            assertEquals(created1, ((ZonedDateTime)next.get(1).getValue("CreatedDate")).toInstant().toEpochMilli());

        } finally {
            datastoreService.deprovision(schema);
        }
    }

    @Test
    public void multivaluedFields() {
        String schema = SyncariContext.getSyncariId();
        try {
            datastoreService.provision(schema);
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            EntitySchema accountSchema = transformer.toEntitySchema(account, connector);
            EntityDefinition entityDefinition = transformer.toEntityDefinition(accountSchema, connector);
            entityDefinition.getField("NumberOfEmployees").get().setWatermarkField(true);
            entityDefinition.addField(new AttributeDefinition().setDataType(ReferenceType.VALUE).setMultiValueField(true).setApiName("multi_contacts"));
            datastoreService.createEntity(entityDefinition);
            // Insert first record
            entityRepo.createCollection(entityDefinition);
            EntityData data = new EntityData(account.getApiName());
            data.addValue("Name", "test account");
            data.addValue("Industry", "www.test.com");
            data.addValue("NumberOfEmployees", 5);
            data.addValue("multi_contacts", List.of("record1","record2","record3"));
            entityRepo.save(data);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);

            accountSchema.getField("NumberOfEmployees").get().setWatermarkField(true);
            accountSchema.addField(new AttributeSchema("syncariid", "string").setIdField(true));
            accountSchema.addField(new AttributeSchema("multi_contacts", "reference").setMultiValueField(true));

            // Verify data exists
            SyncRequest query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertNotNull(next.get(0).getValue("Industry"));
            assertEquals("[\"record1\",\"record2\",\"record3\"]",next.get(0).getValue("multi_contacts"));

        } finally {
            datastoreService.deprovision(schema);
        }
    }
    
    @Test
    public void deleteField() {
        String schema = SyncariContext.getSyncariId();
        try {
            datastoreService.provision(schema);
            Connector connector = connService.find(connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get().getId()).get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            Schema syncariSchema = schemaService.getSyncariSchema();
            EntityDef account = syncariSchema.findEntityByName("account").get();
            EntitySchema accountSchema = transformer.toEntitySchema(account, connector);
            EntityDefinition entityDefinition = transformer.toEntityDefinition(accountSchema, connector);
            entityDefinition.getField("NumberOfEmployees").get().setWatermarkField(true);
            entityDefinition.addField(new AttributeDefinition().setDataType(ReferenceType.VALUE).setMultiValueField(true).setApiName("multi_contacts"));
            datastoreService.createEntity(entityDefinition);
            // Insert first record
            entityRepo.createCollection(entityDefinition);
            EntityData data = new EntityData(account.getApiName());
            data.addValue("Name", "test account");
            data.addValue("Industry", "www.test.com");
            data.addValue("NumberOfEmployees", 5);
            data.addValue("multi_contacts", List.of("record1","record2","record3"));
            entityRepo.save(data);
            datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE);
            
            accountSchema.getField("NumberOfEmployees").get().setWatermarkField(true);
            accountSchema.addField(new AttributeSchema("syncariid", "string").setIdField(true));
            accountSchema.addField(new AttributeSchema("multi_contacts", "reference").setMultiValueField(true));
            
            // Verify data exists
            SyncRequest query = new SyncRequest().Builder(connectorInfo, accountSchema);
            query.setWatermark(new WatermarkInfo(0, 10, true, 0));
            FetchResponse resp = synapseService.getByWatermark(query);
            assertTrue(resp.getIterator().hasNext());
            List<EntityData> next = resp.getIterator().next();
            assertTrue(next.size() > 0);
            assertNotNull(next.get(0).getValue("Industry"));
            assertEquals("[\"record1\",\"record2\",\"record3\"]",next.get(0).getValue("multi_contacts"));
            
            Optional<EntitySchema> entity = synapseService.describe(new DescribeRequest(connectorInfo, accountSchema.getApiName()));
            assertTrue(entity.get().hasField("Industry"));
            assertEquals(40, entity.get().getAttributes().size());
            
            datastoreService.deleteField(accountSchema.getApiName(), accountSchema.getField("Industry").get());
            
            entity = synapseService.describe(new DescribeRequest(connectorInfo, accountSchema.getApiName()));
            assertFalse(entity.get().hasField("Industry"));
            assertEquals(39, entity.get().getAttributes().size());
            
            AttributeSchema attr = accountSchema.getField("Name").get();
            attr.setApiName("unknown");
            attr.setDisplayName("unknown");
            datastoreService.deleteField(accountSchema.getApiName(), attr);
            entity = synapseService.describe(new DescribeRequest(connectorInfo, accountSchema.getApiName()));
            assertFalse(entity.get().hasField("unknown"));
            assertEquals(39, entity.get().getAttributes().size());
            
        } finally {
            datastoreService.deprovision(schema);
        }
    }

    @Test
    public void negativeTestNoDataStore_ExecuteShouldGracefullyReturn() {
        Schema syncariSchema = schemaService.getSyncariSchema();
        EntityDef account = syncariSchema.findEntityByName("account").get();
        EntityDefinition entityDefinition = schemaService.getEntity(account.getId());
        assertFalse(CollectionUtils.isEmpty(datastoreService.execute(entityDefinition, DatastoreService.PAGE_SIZE)));
    }

    @Test
    public void possibleDuplicateFieldApiNamesFromSourceSchemaHandled() {
        String schema = SyncariContext.getSyncariId();
        try {
            datastoreService.provision(schema);
            EntityDefinition ed = new EntityDefinition("DupFieldApiNameTest", "DupFieldApiNameTest_DisplayName").setConnectorId("c1");
            AttributeDefinition attr1 = new AttributeDefinition().setApiName("apiName").setDataType(new StringType());
            attr1.setId((new ObjectId()).toHexString());
            ed.addField(attr1);
            AttributeDefinition attr2 = new AttributeDefinition().setApiName("apiName").setDataType(new StringType());
            attr2.setId((new ObjectId()).toHexString());
            ed.addField(attr2);
            EntitySchema sc = datastoreService.toEntitySchema(ed, datastoreService.connectorService.getSyncariDatastore().get());
            assertEquals("apiName", sc.getAttributes().get(0).getApiName());
            assertEquals("apiName_1", sc.getAttributes().get(1).getApiName());

            ed = new EntityDefinition("DupFieldApiNameTest", "DupFieldApiNameTest_DisplayName").setConnectorId("c1");
            DataStoreConfig config1 = new DataStoreConfig();
            config1.setNewName("Id");
            AttributeDefinition attr3 = new AttributeDefinition().setApiName("Id").setDataType(new IdType()).setIdField(true).withStoreConfig(config1);
            attr3.setId((new ObjectId()).toHexString());
            ed.addField(attr3);
            
            DataStoreConfig config2 = new DataStoreConfig();
            config2.setNewName("id");
            AttributeDefinition attr4 = new AttributeDefinition().setApiName("id_2").setDataType(new IntegerType()).withStoreConfig(config2);
            attr4.setId((new ObjectId()).toHexString());
            ed.addField(attr4);
            AttributeDefinition attr5 = new AttributeDefinition().setApiName("id_3").setDataType(new IntegerType()).withStoreConfig(config2);
            attr5.setId((new ObjectId()).toHexString());
            ed.addField(attr5);
            sc = datastoreService.toEntitySchema(ed, datastoreService.connectorService.getSyncariDatastore().get());
            assertEquals("Id", sc.getAttributes().get(0).getApiName());
            assertEquals("id_1", sc.getAttributes().get(1).getApiName());
            assertEquals("id_2", sc.getAttributes().get(2).getApiName());
        }  finally {
            datastoreService.deprovision(schema);
        }
    }
    
    @Test
    public void errorStatus() {
        String schema = SyncariContext.getSyncariId();
        try {
            datastoreService.provision(schema);
            connectorRepo.findByName(DatastoreService.DATASTORE_NAME).ifPresent(connector -> {
            	Map<String, Object> metaconfig = connector.getMetaConfig();
            	metaconfig.put("schemaName", "invalid");
            	connectorRepo.save(connector);
            });
            EntityDefinition entity = new EntityDefinition("14taqhfz_obws_vikbnvl7oibvl6gsc2o", "Some Entity");
            entity.setId("123");
            AttributeDefinition field = new AttributeDefinition();
            field.setApiName("somefield").setDataType(new StringType()).setIdField(true);
            entity.addField(field);
            entity = entityProxyRepo.save(entity);
            datastoreService.createEntity(entity);
            fail();
        } catch (Exception e) {
        	var connector = connectorRepo.findByName(DatastoreService.DATASTORE_NAME).get();
        	assertEquals(ConnectorStatus.ERROR, connector.getStatus());
        	assertNotNull(connector.getErrorMessage());
        } finally {
            datastoreService.deprovision(schema);
        }
    }

}
