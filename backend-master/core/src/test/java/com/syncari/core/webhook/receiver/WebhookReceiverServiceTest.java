package com.syncari.core.webhook.receiver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.WebhookConfigInfo;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.data.WebhookRequest;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.core.http.source.AbstractConnectorTest;
import com.syncari.core.http.source.DataServiceTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
public class WebhookReceiverServiceTest extends AbstractConnectorTest implements DataServiceTest {

  private ConnectorInfo connector;

  @Autowired
  private WebhookReceiverService webhookService;

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() {
  }

  @Override
  public ConnectorInfo getConnector() {
    if (connector == null)
      connector = createConnector();
    return connector;
  }

  @Override
  public AuthenticationService getAuthenticationService() {
    return webhookService;
  }

  @Override
  public MetadataService getMetadataService() {
    return webhookService;
  }

  @Override
  public CommonDataService getDataService() {
    return webhookService;
  }

  @Override
  public String getDescribeObject() {
    return "wh1";
  }

  @Override
  public void referencesTest() {
    // Not applicable
  }

  @Override
  @Test
  public void testConnectionTest() {
    verifyTestConnection();
  }

  @Override
  public List<String> skipPickListVerificationObjects() {
    return List.of();
  }

  @Override
  @Test
  public void describeAllTest() {
    describeAll(null);
  }

  // describe Test for wh1
  @Override
  @Test
  public void describeTest() {
    describe("wh1", null);
  }

  @Test
  @Override
  public void getByWatermarkSinceEpoch() {
    //Webhook synapse
  }

  @Test
  @Override
  public void getByWatermarkRecent() {
    //Webhook synapse
  }

  @Test
  @Override
  public void getByWatermarkWithLimit() {
    //Webhook synapse
  }

  @Test
  @Override
  public void getByWatermarkResultsOrdered() {
    //Webhook synapse
  }

  @Test
  @Override
  public void getByIds() {
    //NA
  }

  @Override
  public void getDeletedByWatermark() {
    // Readonly synapse
  }

  @Override
  public void createTest() {
    // Readonly synapse
  }

  @Override
  public void updateTest() {
    // Readonly synapse
  }

  @Override
  public void deleteTest() {
    // Readonly synapse
  }

  @Override
  public void batchCreateTest() {
    // Readonly synapse
  }

  @Override
  public void batchUpdateTest() {
    // Readonly synapse
  }

  @Override
  public void batchDeleteTest() {
    // Readonly synapse
  }

  @Override
  public void createCustomObjectTest() {
    // Readonly synapse
  }

  @Override
  public void updateCustomObjectTest() {
    // Readonly synapse
  }

  @Override
  public void deleteCustomObjectTest() {
    // Readonly synapse
  }

  @Override
  public void mixedBatchCreateFailuresTest() {
    // Readonly synapse
  }

  @Override
  public void mixedBatchUpdateFailuresTest() {
    // Readonly synapse
  }

  @Override
  public void mixedBatchDeleteFailuresTest() {
    // Readonly synapse
  }

  @Override
  public void allDataTypesTest() {

  }

  @Override
  public void rateLimitTest() {
    // This is a custom synapse we dont have a standard way to handle rate limit
  }
  
  @Test
  public void testParseEventData() throws JsonProcessingException {
    ConnectorInfo connector = createConnector();
    WebhookRequest req = new WebhookRequest();
    req.setConfig(connector);
    req.setHeaders(Map.of());
    req.setParams(Map.of());
    req.setBody("[{\"id\": 1, \"name\": \"Name 1\", \"age\": 21, \"address\": {\"street\": \"1 Example St\", \"city\": \"City 1\", \"state\": \"State 1\", \"country\": \"Country 1\"}, \"updatedAt\": \"2024-10-10T09:38:19.2175439+05:30\"}, {\"id\": 2, \"name\": \"Name 2\", \"age\": 22, \"address\": {\"street\": \"2 Example St\", \"city\": \"City 2\", \"state\": \"State 2\", \"country\": \"Country 2\"}, \"updatedAt\": \"2024-10-10T09:38:19.2175439+05:30\"}]");
    
    var eventData = webhookService.parseEventData(req);
    assertNotNull(eventData);
    assertFalse(eventData.isEmpty());
    assertEquals(2, eventData.size());
    assertEquals("webhook", eventData.get(0).getData().getName());
    
    req.setActiveEntities(List.of("active1"));
    eventData = webhookService.parseEventData(req);
    assertNotNull(eventData);
    assertFalse(eventData.isEmpty());
    assertEquals(2, eventData.size());
    assertEquals("active1", eventData.get(0).getData().getName());
    
    //Unlikely scenario. If more than one active entity exist
    req.setActiveEntities(List.of("active1", "active2"));
    eventData = webhookService.parseEventData(req);
    assertNotNull(eventData);
    assertFalse(eventData.isEmpty());
    assertEquals(4, eventData.size());
    Set<String> entities = eventData.stream().map(e -> e.getData().getName()).collect(Collectors.toSet());
    assertTrue(entities.contains("active1"));
    assertTrue(entities.contains("active2"));

    req.setActiveEntities(List.of("active1"));
    List<Map<String, Object>> records = List.of(
            Map.of("id", 1, "others", List.of(Map.of("name", "Name 1", "age", 2)), "name", "Name 1", "age", 21, "address", Map.of("street", "1 Example St", "city", "City 1", "state", "State 1"))
    );

    req.setBody(webhookService.mapper.writeValueAsString(records));
    eventData = webhookService.parseEventData(req);
    assertEquals(1, eventData.size());
    assertEquals("active1", eventData.get(0).getData().getName());
    assertEquals(1, List.class.cast(eventData.get(0).getData().getValues().get("others")).size());

  }
  
  @Test
  public void testGetRecords() throws Exception{
    ConnectorInfo connector = createConnector();
    WebhookRequest req = new WebhookRequest();
    req.setConfig(connector);
    req.setHeaders(Map.of());
    req.setParams(Map.of());
    req.setBody("[{\"id\": 1, \"name\": \"Name 1\", \"age\": 21, \"address\": {\"street\": \"1 Example St\", \"city\": \"City 1\", \"state\": \"State 1\", \"country\": \"Country 1\"}, \"updatedAt\": \"2024-10-10T09:38:19.2175439+05:30\"}, {\"id\": 2, \"name\": \"Name 2\", \"age\": 22, \"address\": {\"street\": \"2 Example St\", \"city\": \"City 2\", \"state\": \"State 2\", \"country\": \"Country 2\"}, \"updatedAt\": \"2024-10-10T09:38:19.2175439+05:30\"}]");
    
    var eventData = webhookService.getRecords(req);
    assertNotNull(eventData);
    assertFalse(eventData.isEmpty());
    assertEquals(2, eventData.size());
    assertEquals("webhook", eventData.get(0).getName());
    
    req.setActiveEntities(List.of("active1"));
    eventData = webhookService.getRecords(req);
    assertNotNull(eventData);
    assertFalse(eventData.isEmpty());
    assertEquals(2, eventData.size());
    assertEquals("active1", eventData.get(0).getName());
    
    //Unlikely scenario. If more than one active entity exist
    req.setActiveEntities(List.of("active1", "active2"));
    eventData = webhookService.getRecords(req);
    assertNotNull(eventData);
    assertFalse(eventData.isEmpty());
    assertEquals(4, eventData.size());
    Set<String> entities = eventData.stream().map(e -> e.getName()).collect(Collectors.toSet());
    assertTrue(entities.contains("active1"));
    assertTrue(entities.contains("active2"));
  }

  private ConnectorInfo createConnector() {
    ConnectorInfo connector = new ConnectorInfo();
    connector.setId("1234");
    connector.setName("wh1");
    connector.setConnectorMetadataName("webhook");
    connector.setAuthType(AuthType.None);
    connector.setAuthConfig(new AuthConfig());
    connector.setWebhookConfig(new WebhookConfigInfo()
        .setIdSelector("id")
        .setRecordSelector("")
            .setSchema("{\"$schema\": \"http://json-schema.org/draft-07/schema#\", \"type\": \"object\", \"properties\": {\"id\": { \"type\": \"string\" },\"others\": {\n" +
                    "      \"type\": \"array\",\n" +
                    "      \"description\": \"An array of individual notification objects.\",\n" +
                    "      \"items\": {\n" +
                    "        \"type\": \"object\"\n" +
                    "      }\n" +
                    "    }, \"updatedAt\": { \"type\": \"string\", \"format\": \"date-time\" }, \"name\": { \"type\": \"string\" }, \"age\": { \"type\": \"number\" }, \"profession\": { \"type\": \"string\" }, \"address\": { \"type\": \"object\", \"properties\": { \"street\": { \"type\": \"string\" }, \"city\": { \"type\": \"string\" }, \"state\": { \"type\": \"string\" }, \"zipCode\": { \"type\": \"string\" } } } }}"));
    return connector;
  }

  private ConnectorInfo createConnectorUnAuthorized() {
    ConnectorInfo connector = new ConnectorInfo();
    connector.setId("1234");
    connector.setName("wh1");
    connector.setAuthType(AuthType.ApiSecretKey);
    connector.setAuthConfig(new AuthConfig());
    connector.setWebhookConfig(new WebhookConfigInfo()
        .setIdSelector("id")
        .setRecordSelector("")
        .setSchema("{\"$schema\": \"http://json-schema.org/draft-07/schema#\", \"type\": \"object\", \"properties\": {\"id\": { \"type\": \"string\" }, \"updatedAt\": { \"type\": \"string\", \"format\": \"date-time\" }, \"name\": { \"type\": \"string\" }, \"age\": { \"type\": \"number\" }, \"profession\": { \"type\": \"string\" }, \"address\": { \"type\": \"object\", \"properties\": { \"street\": { \"type\": \"string\" }, \"city\": { \"type\": \"string\" }, \"state\": { \"type\": \"string\" }, \"zipCode\": { \"type\": \"string\" } } } }}"));
    return connector;
  }
}
