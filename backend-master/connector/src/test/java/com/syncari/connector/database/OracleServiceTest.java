package com.syncari.connector.database;

import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@Slf4j
@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@Ignore
public class OracleServiceTest extends AbstractConnectorTest implements DataServiceTest {
    private static final String SID = "gd1e3c847d9b8de_ccm97uk3mmxvuaqy_high.adb.oraclecloud.com";
    private static final String PASSWORD = System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME");
    private static final String USER = "SYNCARIDEV";
    private static final String SCHEMA_NAME = "SYNCARIDEV";
    private static final String HOST = "adb.us-sanjose-1.oraclecloud.com";
    private static final String PORT = "1521";

    @Autowired
    @Qualifier(Constants.ORACLE)
    OracleService service;

    @Autowired
    DateUtil dateUtil;

    private ConnectorInfo createConnector() {
        ConnectorInfo connector = new ConnectorInfo("123", "oracle", null, "HR", USER, PASSWORD);
        connector.getMetaConfig().put(Constants.CLUSTER_NAME, HOST);
        connector.getMetaConfig().put(Constants.SCHEMA_NAME, SCHEMA_NAME);
        connector.getMetaConfig().put(Constants.DATABASE_NAME, SID);
        connector.getMetaConfig().put(Constants.PORT, PORT);
        connector.getMetaConfig().put(RedshiftService.TIME_ZONE_ID, "America/Los_Angeles");
        connector.getAuthConfig().addHeader("useSsl", "true");
        return connector;
    }


    @Test
    public void validate() throws SQLException, ClassNotFoundException {
        ConnectorInfo connector = createConnector();
        //connector.getAuthConfig().addHeader("useSsl", "false");
        new OracleService().getConnection(connector);
    }

    @Test
    public void getByWatermarkRecent() {
        EntitySchema entitySchema = new EntitySchema("CONTACT");
        AttributeSchema attributeSchema = new AttributeSchema("LASTMODIFIED", "timestamp");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("ID", "numeric");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id));
        ConnectorInfo connector = createConnector();

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = DateUtil.convertDate(ZonedDateTime.class, "2023-01-01T10:30:00").toInstant().toEpochMilli();
        long end = Instant.now().toEpochMilli();
        request.setWatermark(new WatermarkInfo(start, end, true, 0));
        request.setPageSize(10);

        FetchResponse resp = service.getByWatermark(request);
        List<EntityData> data = new ArrayList<>();
        resp.getIterator().forEachRemaining(data::addAll);
        assertFalse(data.isEmpty());
        assertThat(data.size(), greaterThanOrEqualTo(2));
    }

    @Test
    public void getByWatermarkWithLimit() {
        EntitySchema entitySchema = new EntitySchema("CONTACT");
        AttributeSchema attributeSchema = new AttributeSchema("LASTMODIFIED", "timestamp");
        attributeSchema.setWatermarkField(true);
        AttributeSchema id = new AttributeSchema("ID", "numeric");
        id.setIdField(true);
        entitySchema.setAttributes(List.of(attributeSchema, id));
        ConnectorInfo connector = createConnector();

        SyncRequest request = new SyncRequest().Builder(connector, entitySchema);
        long start = DateUtil.convertDate(ZonedDateTime.class, "2023-01-01T10:30:00").toInstant().toEpochMilli();
        long end = Instant.now().toEpochMilli();
        request.setWatermark(new WatermarkInfo(start, end, true, 0));
        request.setPageSize(100);
        request.getWatermark().setLimit(1);

        FetchResponse resp = service.getByWatermark(request);
        List<EntityData> data = new ArrayList<>();
        resp.getIterator().forEachRemaining(data::addAll);
        assertFalse(data.isEmpty());
        assertThat(data, hasSize(1));
    }

    @Override
    public ConnectorInfo getConnector() {
        return createConnector();
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return service;
    }

    @Override
    public MetadataService getMetadataService() {
        return service;
    }

    @Override
    public CommonDataService getDataService() {
        return service;
    }

    @Override
    public String getDescribeObject() {
        return "test";
    }

    @Override
    public void testConnectionTest() {
        ConnectorInfo connector = getConnector();
        TestConnectionResponse success = service.testConnection(connector, null);
        assertTrue(success.isSuccess());
        connector.getAuthConfig().setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
        TestConnectionResponse failure = service.testConnection(connector, null);
        assertFalse(failure.isSuccess());
    }

    @Test
    public void describeAllTest() {
        DescribeAllRequest request = new DescribeAllRequest(createConnector(), List.of());
        List<EntitySchema> entities = service.describeAll(request);
        assertTrue(entities.size() > 0);
        List<String> names = entities.stream().map(e -> e.getApiName()).collect(Collectors.toList());
        assertTrue(names.contains("CONTACT"));
        assertTrue(entities.get(0).getAttributes().size() >= 1);
        assertTrue(entities.stream().filter(e -> e.getApiName().equalsIgnoreCase("CONTACT")).findFirst().isPresent());
        assertTrue(entities.stream().filter(e -> e.getApiName().equalsIgnoreCase("CONTACT"))
                .findFirst().get().getField("ID").isPresent());
    }

    @Test
    public void describeTest() {
        DescribeRequest request = new DescribeRequest(createConnector(), "CONTACT");
        Optional<EntitySchema> entities = service.describe(request);
        assertTrue(entities.isPresent());
        assertTrue(entities.get().getAttributes().size() >= 1);
        AttributeSchema wm = entities.get().getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase("LASTMODIFIED")).findAny().get();
        assertTrue(wm.getDataType().equalsIgnoreCase("timestamp"));
    }

    @Test
    public void crudWithId() {
        SyncResponse response = null;
        EntityData entityData1 = null;
        EntityData entityData2 = null;
        String id1 = "111111";
        String id2 = "222222";
        ConnectorInfo connector = createConnector();

        Optional<EntitySchema> entitySchemaOpt =  service.describe(new DescribeRequest(connector,"CONTACT"));
        // Create a new table
        entitySchemaOpt.get().getField("ID").get().setIdField(true);
        entitySchemaOpt.get().getField("LASTMODIFIED").get().setWatermarkField(true);

        try{
            // Insert a single row
            SyncRequest request = new SyncRequest().Builder(connector, entitySchemaOpt.get());

            entityData1 = new EntityData("contact1");
            entityData1.setId(id1);
            entityData1.addValue("FIRST_NAME", "FName111111");
            entityData1.addValue("LAST_NAME", "LName111111");
            entityData1.addValue("EMAIL", "FName111111.LName111111@email.com");
            entityData1.addValue("ADDRESS", "Address111111");
            entityData1.addValue("TS_01", Instant.now());
            request.addData(connector.getId(), entityData1);

            entityData2 = new EntityData("contact2");
            entityData2.setId(id2);
            entityData2.addValue("FIRST_NAME", "FName222222");
            entityData2.addValue("LAST_NAME", "LName222222");
            entityData2.addValue("EMAIL", "FName222222.LName222222@email.com");
            entityData2.addValue("ADDRESS", "Address222222");
            entityData2.addValue("TS_01", Instant.now());
            request.addData(connector.getId(), entityData2);

            response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().size() == 2);
            assertNotNull(response.getResults().get(0).getId());
            assertEquals(id1, response.getResults().get(0).getId());
            assertNotNull(response.getResults().get(1).getId());
            assertEquals(id2, response.getResults().get(1).getId());


            EntityData getEntityData = new EntityData("getContact");;
            getEntityData.setId(id2);
            SyncRequest getRequest = new SyncRequest().Builder(connector, entitySchemaOpt.get());
            getRequest.addData(connector.getId(), getEntityData);
            List<EntityData> next = service.getByIds(getRequest);
            assertTrue(next.size() == 1);
            assertEquals(id2, next.get(0).getId());
            assertEquals("FName222222", next.get(0).getValue("FIRST_NAME"));
            assertEquals("LName222222", next.get(0).getValue("LAST_NAME"));
            assertEquals("FName222222.LName222222@email.com", next.get(0).getValue("EMAIL"));
            assertEquals("Address222222", next.get(0).getValue("ADDRESS"));

            getEntityData.setId(id1);
            getRequest = new SyncRequest().Builder(connector, entitySchemaOpt.get());
            getRequest.addData(connector.getId(), getEntityData);
            next = service.getByIds(getRequest);
            assertTrue(next.size() == 1);
            assertEquals(id1, next.get(0).getId());
            assertEquals("FName111111", next.get(0).getValue("FIRST_NAME"));
            assertEquals("LName111111", next.get(0).getValue("LAST_NAME"));
            assertEquals("FName111111.LName111111@email.com", next.get(0).getValue("EMAIL"));
            assertEquals("Address111111", next.get(0).getValue("ADDRESS"));

            SyncRequest updateRequest = new SyncRequest().Builder(connector, entitySchemaOpt.get());

            EntityData updateEntityData = new EntityData("updateContact");
            updateEntityData.setId(id1);
            updateEntityData.addValue("FIRST_NAME", "FName333333");
            updateEntityData.addValue("LAST_NAME", "LName333333");
            updateEntityData.addValue("EMAIL", "FName333333.LName333333@email.com");
            updateEntityData.addValue("ADDRESS", "Address333333");
            updateEntityData.addValue("TS_01", Instant.now());
            updateRequest.addData(connector.getId(), updateEntityData);

            response = service.update(updateRequest);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().size() == 1);
            assertNotNull(response.getResults().get(0).getId());
            assertEquals(id1, response.getResults().get(0).getId());

            getRequest = new SyncRequest().Builder(connector, entitySchemaOpt.get());
            getRequest.addData(connector.getId(), getEntityData);
            next = service.getByIds(getRequest);
            assertTrue(next.size() > 0);
            assertEquals(id1, next.get(0).getId());
            assertEquals("FName333333", next.get(0).getValue("FIRST_NAME"));
            assertEquals("LName333333", next.get(0).getValue("LAST_NAME"));
            assertEquals("FName333333.LName333333@email.com", next.get(0).getValue("EMAIL"));
            assertEquals("Address333333", next.get(0).getValue("ADDRESS"));


        }finally {
            SyncRequest requestToDel = new SyncRequest().Builder(connector,entitySchemaOpt.get());
            requestToDel.addData(connector.getId(), new EntityData("deleteData1").setId(id1));
            requestToDel.addData(connector.getId(), new EntityData("deleteData2").setId(id2));
            service.delete(requestToDel);
        }

    }


    @Test
    public void crudWitOutId() {
        SyncResponse response = null;
        EntityData entityData1 = null;
        EntityData entityData2 = null;
        String id1 = null;
        String id2 = null;
        ConnectorInfo connector = createConnector();

        Optional<EntitySchema> entitySchemaOpt =  service.describe(new DescribeRequest(connector,"PERSON"));
        // Create a new table
        entitySchemaOpt.get().getField("ID").get().setIdField(true);
        entitySchemaOpt.get().getField("LASTMODIFIED").get().setWatermarkField(true);

        try{
            // Insert a single row
            SyncRequest request = new SyncRequest().Builder(connector, entitySchemaOpt.get());

            entityData1 = new EntityData("contact1");
            entityData1.addValue("FIRST_NAME", "FName111111");
            entityData1.addValue("LAST_NAME", "LName111111");
            entityData1.addValue("EMAIL", "FName111111.LName111111@email.com");
            entityData1.addValue("ADDRESS", "Address111111");
            request.addData(connector.getId(), entityData1);

            entityData2 = new EntityData("contact2");
            entityData2.addValue("FIRST_NAME", "FName222222");
            entityData2.addValue("LAST_NAME", "LName222222");
            entityData2.addValue("EMAIL", "FName222222.LName222222@email.com");
            entityData2.addValue("ADDRESS", "Address222222");
            request.addData(connector.getId(), entityData2);

            response = service.create(request);
            assertTrue(response.isSuccess());
            assertTrue(response.getResults().size() == 2);
            assertNotNull(response.getResults().get(0).getId());
            id1 = response.getResults().get(0).getId();
            assertNotNull(response.getResults().get(1).getId());
            id2 = response.getResults().get(1).getId();


            EntityData getEntityData = new EntityData("getContact");;
            getEntityData.setId(id2);
            SyncRequest getRequest = new SyncRequest().Builder(connector, entitySchemaOpt.get());
            getRequest.addData(connector.getId(), getEntityData);
            List<EntityData> next = service.getByIds(getRequest);
            assertTrue(next.size() == 1);
            assertEquals(id2, next.get(0).getId());
            assertEquals("FName222222", next.get(0).getValue("FIRST_NAME"));
            assertEquals("LName222222", next.get(0).getValue("LAST_NAME"));
            assertEquals("FName222222.LName222222@email.com", next.get(0).getValue("EMAIL"));
            assertEquals("Address222222", next.get(0).getValue("ADDRESS"));

            getEntityData.setId(id1);
            getRequest = new SyncRequest().Builder(connector, entitySchemaOpt.get());
            getRequest.addData(connector.getId(), getEntityData);
            next = service.getByIds(getRequest);
            assertTrue(next.size() == 1);
            assertEquals(id1, next.get(0).getId());
            assertEquals("FName111111", next.get(0).getValue("FIRST_NAME"));
            assertEquals("LName111111", next.get(0).getValue("LAST_NAME"));
            assertEquals("FName111111.LName111111@email.com", next.get(0).getValue("EMAIL"));
            assertEquals("Address111111", next.get(0).getValue("ADDRESS"));

            SyncRequest updateRequest = new SyncRequest().Builder(connector, entitySchemaOpt.get());

            EntityData updateEntityData = new EntityData("updateContact");
            updateEntityData.setId(id1);
            updateEntityData.addValue("FIRST_NAME", "FName333333");
            updateEntityData.addValue("LAST_NAME", "LName333333");
            updateEntityData.addValue("EMAIL", "FName333333.LName333333@email.com");
            updateEntityData.addValue("ADDRESS", "Address333333");
            updateRequest.addData(connector.getId(), updateEntityData);

            SyncResponse updateResponse = service.update(updateRequest);
            assertTrue(updateResponse.isSuccess());
            assertTrue(updateResponse.getResults().size() == 1);
            assertNotNull(updateResponse.getResults().get(0).getId());
            assertEquals(id1, updateResponse.getResults().get(0).getId());

            getRequest = new SyncRequest().Builder(connector, entitySchemaOpt.get());
            getRequest.addData(connector.getId(), getEntityData);
            next = service.getByIds(getRequest);
            assertTrue(next.size() > 0);
            assertEquals(id1, next.get(0).getId());
            assertEquals("FName333333", next.get(0).getValue("FIRST_NAME"));
            assertEquals("LName333333", next.get(0).getValue("LAST_NAME"));
            assertEquals("FName333333.LName333333@email.com", next.get(0).getValue("EMAIL"));
            assertEquals("Address333333", next.get(0).getValue("ADDRESS"));


        }finally {
            SyncRequest requestToDel = new SyncRequest().Builder(connector,entitySchemaOpt.get());
            response.getResults().forEach(result -> {
                requestToDel.addData(connector.getId(), new EntityData("delete").setId(result.getId()));
            });
            service.delete(requestToDel);
        }

    }

    @Override
    public void getByWatermarkSinceEpoch() {

    }

    @Override
    public void getByWatermarkResultsOrdered() {

    }

    @Override
    public void getByIds() {

    }

    @Override
    public void getDeletedByWatermark() {

    }

    @Override
    public void createTest() {

    }

    @Override
    public void updateTest() {

    }

    @Override
    public void deleteTest() {

    }

    @Override
    public void batchCreateTest() {

    }

    @Override
    public void batchUpdateTest() {

    }

    @Override
    public void batchDeleteTest() {

    }

    @Override
    public void createCustomObjectTest() {

    }

    @Override
    public void updateCustomObjectTest() {

    }

    @Override
    public void deleteCustomObjectTest() {

    }

    @Override
    public void mixedBatchCreateFailuresTest() {

    }

    @Override
    public void mixedBatchUpdateFailuresTest() {

    }

    @Override
    public void mixedBatchDeleteFailuresTest() {

    }

    @Override
    public void allDataTypesTest() {

    }

    @Override
    public void referencesTest() {

    }

    @Override
    public void rateLimitTest() {

    }
}
