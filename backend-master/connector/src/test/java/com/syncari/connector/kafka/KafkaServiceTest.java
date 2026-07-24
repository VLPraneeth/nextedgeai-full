package com.syncari.connector.kafka;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.NonRetriableException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ConnectorConfig.class, TestConfig.class})
@TestPropertySource("classpath:test_application.properties")
public class KafkaServiceTest extends AbstractConnectorTest {

    @Autowired
    private KafkaService kafkaService;

    @Mock
    private ConnectorInfo connectorInfo;

    @Before
    public void setup() {
        connectorInfo = new ConnectorInfo();
        connectorInfo.setId("test-kafka-connector");
        connectorInfo.setName("Test Kafka Connector");

        Map<String, Object> metaConfig = new HashMap<>();
        metaConfig.put(KafkaClient.BOOTSTRAP_SERVERS, "pkc-lgk0v.us-west1.gcp.confluent.cloud:9092");
        connectorInfo.setAuthConfig(new AuthConfig());
        connectorInfo.getAuthConfig().setUserName("HTP5NRGAT6NZ4KJZ");
        connectorInfo.getAuthConfig().setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
        connectorInfo.setMetaConfig(metaConfig);
    }

    @Test
    public void testGetName() {
        assertEquals("kafka", kafkaService.getName());
    }

    @Test
    public void testGetCategory() {
        assertEquals("Messaging", kafkaService.getCategory());
    }

    @Test
    public void testGetUIMetadata() {
        UIMetadata metadata = kafkaService.getUIMetadata();
        assertNotNull(metadata);
        assertEquals("Apache Kafka", metadata.getDisplayName());
        assertEquals("#000000", metadata.getBackgroundColor());
    }

    @Test
    public void testIsSink() {
        assertFalse(kafkaService.isSink());
    }

    @Test
    public void testGetCapabilities() {
        List<Capability> capabilities = kafkaService.getCapabilities();
        assertNotNull(capabilities);
        assertTrue(capabilities.contains(Capability.getByWatermark));
        assertTrue(capabilities.contains(Capability.schemaCreateField));
        assertTrue(capabilities.contains(Capability.schemaEditInSyncari));
    }

    @Test
    public void testGetSupportedAuthTypes() {
        List<AuthMetadata> authTypes = kafkaService.getSupportedAuthTypes();
        assertNotNull(authTypes);
        assertEquals(1, authTypes.size());
    }

    @Test()
    public void testCreateReadDescribe() {
        SyncRequest request = new SyncRequest();
        request.setEntitySchema(new EntitySchema("lead"));
        request.setPipeline(new Pipeline("lead_pipeline","APPROVED","GH67GH"));
        request.setConnector(connectorInfo);
        EntityData ed = new EntityData("lead");
        ed.addValue("name", "John");
        ed.addValue("city", "SFO");
        request.addData(connectorInfo.getId(), ed);
        kafkaService.create(request);

        // test getByWatermark
        WatermarkInfo watermark = new WatermarkInfo(1697964053000L, Instant.now().toEpochMilli(), true, 0);
        request.setWatermark(watermark);
        FetchResponse response = kafkaService.getByWatermark(request);
        assertTrue(response.getIterator().hasNext());
        EntityData entityData = response.getIterator().next().get(0);
        assertEquals("John", entityData.getValueAsString("name"));
        assertNotNull(entityData.getId());
        assertNotNull(entityData.getLastModified());
        assertEquals("Watermark should be the same", watermark, response.getWatermark());

        // test getById
        request.getData().get(connectorInfo.getId()).get(0).setId(entityData.getId());
        List<EntityData> result = kafkaService.getByIds(request);
        assertEquals("John", result.get(0).getValueAsString("name"));
        assertNotNull(result.get(0).getId());
        assertNotNull(result.get(0).getLastModified());

        // test describe
        DescribeRequest descRequest = new DescribeRequest(connectorInfo, "lead");
        Optional<EntitySchema> schema = kafkaService.describe(descRequest);
        assertTrue(schema.isPresent());
        assertEquals("lead", schema.get().getApiName());
        assertEquals(5, schema.get().getAttributes().size());
        assertEquals("id", schema.get().getIdField().getApiName());
        assertEquals(Constants.SYNCARI_FABRICATED_WATERMARKFIELD, schema.get().getWatermarkField().getApiName());
    }

    @Test(expected = NonRetriableException.class)
    public void testUpdate() {
        SyncRequest request = new SyncRequest();
        request.setConnector(connectorInfo);

        // Call update - should throw NonRetriableException
        kafkaService.update(request);
    }

    @Test(expected = NonRetriableException.class)
    public void testDelete() {
        SyncRequest request = new SyncRequest();
        request.setConnector(connectorInfo);
        
        // Call delete - should throw NonRetriableException
        kafkaService.delete(request);
    }

}