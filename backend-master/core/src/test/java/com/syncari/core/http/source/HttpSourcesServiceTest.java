package com.syncari.core.http.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import org.json.JSONArray;
import org.json.JSONObject;
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

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
public class HttpSourcesServiceTest extends AbstractConnectorTest implements DataServiceTest {

  private static String urlPrefix = "http://localhost:8089";
  private ConnectorInfo connector;
  private WireMockServer wireMockServer;

  @Autowired
  private HttpSourcesService httpService;

  @Autowired
  ObjectMapper mapper;

  @Before
  public void setUp() throws Exception {
    wireMockServer = new WireMockServer(
        new WireMockConfiguration().port(8089).extensions(new LimitOffsetGenerator(),
            new PageNumberGenerator(), new CursorParamGenerator(), new CursorUrlGenerator()));
    wireMockServer.start();

    wireMockServer.stubFor(get(urlEqualTo("/account")).willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(
            "[{\"id\":\"user_001\",\"updatedAt\":\"2024-07-01T12:34:56Z\",\"name\":\"John Doe\",\"age\":30,\"profession\":\"Software Engineer\",\"address\":{\"street\":\"123 Main St\",\"city\":\"Springfield\",\"state\":\"IL\",\"zipCode\":\"62701\"}},{\"id\":\"user_002\",\"updatedAt\":\"2024-07-02T15:22:10Z\",\"name\":\"Jane Smith\",\"age\":28,\"profession\":\"Graphic Designer\",\"address\":{\"street\":\"456 Oak Ave\",\"city\":\"Rivertown\",\"state\":\"CA\",\"zipCode\":\"90210\"}}]")
        .withStatus(200)));
    wireMockServer.stubFor(get(urlEqualTo("/auth-test"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(401)));
    wireMockServer.stubFor(get(urlPathEqualTo("/contact"))
        .withQueryParam("limit", matching("[0-9]+")).withQueryParam("offset", matching("[0-9]+"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json")
            .withTransformers("LimitOffsetGenerator").withStatus(200)));
    wireMockServer
        .stubFor(get(urlPathEqualTo("/contact1")).withQueryParam("pageNumber", matching("[0-9]+"))
            .withQueryParam("pageSize", matching("[0-9]+"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withTransformers("PageNumberGenerator").withStatus(200)));
    wireMockServer.stubFor(get(urlPathEqualTo("/contact2"))
        .withQueryParam("cursor", matching("[0-9]+")).withQueryParam("pageSize", matching("[0-9]+"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json")
            .withTransformers("CursorParamGenerator").withStatus(200)));
    wireMockServer.stubFor(get(urlPathEqualTo("/contact3"))
        .withQueryParam("cursor", matching("[0-9]+")).withQueryParam("pageSize", matching("[0-9]+"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json")
            .withTransformers("CursorUrlGenerator").withStatus(200)));

    wireMockServer.stubFor(post(urlEqualTo("/oauth/accessToken"))
            .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(mapper.writeValueAsString(Map.of("access_token", "123access"
                            , "refresh_token", "12refresh"
                            , "expires_in", 3600,
                            "token_type", "Bearer")))
                    .withStatus(200)));

  }

  @Test
  public void accessAndRefreshTokenSetsLastRefreshTime() {
    ConnectorInfo conn = createConnectorOAuthAuthorized();
    conn.getAuthConfig().setAccessTokenEndpoint(urlPrefix + "/oauth/accessToken");
    final AuthConfig accessToken = httpService.getAccessToken(
            new OAuthRequest(conn.getEndpoint(), conn.getOAuthRedirectUrl(),
                    conn.getAuthConfig(), conn.getMetaConfig())
    ).clone();
    assertNotNull(accessToken.getAccessToken());
    assertNotNull(accessToken.getRefreshToken());
    assertNotNull(accessToken.getLastRefreshed());
    assertNotNull(accessToken.getExpiresIn());

    final AuthConfig refreshToken = httpService.refreshToken(conn);
    assertNotNull(refreshToken.getAccessToken());
    assertNotNull(refreshToken.getRefreshToken());
    assertNotNull(refreshToken.getLastRefreshed());
    assertTrue(refreshToken.getLastRefreshed().toEpochMilli() >= accessToken.getLastRefreshed().toEpochMilli());
    assertNotNull(refreshToken.getExpiresIn());

  }

  @After
  public void tearDown() {
    wireMockServer.stop();
  }

  @Override
  public ConnectorInfo getConnector() {
    if (connector == null)
      connector = createConnector();
    return connector;
  }

  @Override
  public AuthenticationService getAuthenticationService() {
    return httpService;
  }

  @Override
  public MetadataService getMetadataService() {
    return httpService;
  }

  @Override
  public CommonDataService getDataService() {
    return httpService;
  }

  @Override
  public String getDescribeObject() {
    return Constants.ACCOUNT.toLowerCase();
  }

  @Override
  public void referencesTest() {
    // Not applicable
  }

  @Override
  @Test
  public void testConnectionTest() {
    ConnectorInfo conn = createConnectorUnAuthorized();
    TestConnectionResponse resp = getAuthenticationService().testConnection(conn, List.of());
    assertFalse(resp.isSuccess());
    assertTrue(resp.getMessage().startsWith("Error when testing the authenticated connection"));
    assertFalse(resp.getErrors().isEmpty());
    
    conn = createConnectorOAuthAuthorized();
    httpService.tokenHandler = new DefaultAuthTokenHandler() {
      public AuthConfig getAccessToken(String endpoint, java.util.Map<String,String> dataMap, java.util.Map<String,String> headersMap) {
        assertEquals("http://localhost:80/oauth/token", endpoint);
        assertEquals("Basic " + Base64.getEncoder().encodeToString("c1:c2".getBytes()), headersMap.get("Authorization"));
        return new AuthConfig().setAccessToken("token1");
      };

      public AuthConfig getAccessToken(String endpoint, Map<String, String> map) {
        throw new RetriableException(ErrorCodes.UNKNOWN_ERROR, "error", "500");
      }
    };
    resp = getAuthenticationService().testConnection(conn, List.of());
    assertTrue(resp.isSuccess());
    assertEquals("token1", resp.getAuthConfig().getAccessToken());
    
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

  // describe Test for Account
  @Override
  @Test
  public void describeTest() {
    describe(Constants.ACCOUNT.toLowerCase(), null);
  }

  @Test
  @Override
  public void getByWatermarkSinceEpoch() {
    verifyGetByWatermarkSinceEpoch(Constants.ACCOUNT.toLowerCase());
  }

  @Test
  @Override
  public void getByWatermarkRecent() {
    verifyGetByWatermarkRecent(Constants.ACCOUNT.toLowerCase());
  }

  @Test
  @Override
  public void getByWatermarkWithLimit() {
    verifyGetByWatermarkWithLimit(Constants.ACCOUNT.toLowerCase(), 2);
  }

  @Test
  @Override
  public void getByWatermarkResultsOrdered() {
    verifyGetByWatermarkResultsOrdered(Constants.ACCOUNT.toLowerCase());
  }

  private void performWatermarkPaginationTest(String entitySchemaName) {
    Optional<EntitySchema> entitySchema = describe(entitySchemaName.toLowerCase(), null);
    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
    WatermarkInfo watermark =
        new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
    syncRequest.setWatermark(watermark);
    FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);

    // Assert the fetched data
    assertTrue(byWatermark.getIterator().hasNext());
    List<EntityData> data = byWatermark.getIterator().next();
    assertNotNull(data);
    assertTrue(data.size() > 0);
    assertNotNull(data.get(0).getId());
    assertNotNull(data.get(0).getLastModified());
    assertNotNull(data.get(0).getCreatedAt());
  }

  @Test
  public void getByWatermarkLimitOffset() {
    performWatermarkPaginationTest(Constants.CONTACT);
  }

  @Test
  public void getByWatermarkPageNumber() {
    performWatermarkPaginationTest(Constants.CONTACT + "1");
  }

  @Test
  public void getByWatermarkCursorParam() {
    performWatermarkPaginationTest(Constants.CONTACT + "2");
  }

  @Test
  public void getByWatermarkCursorLink() {
    performWatermarkPaginationTest(Constants.CONTACT + "3");
  }

  @Test
  @Override
  public void getByIds() {
    String entityName = Constants.ACCOUNT.toLowerCase();
    Optional<EntitySchema> entitySchema = describe(entityName, null);
    SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
    WatermarkInfo watermark =
        new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
    watermark.setLimit(2);
    syncRequest.setWatermark(watermark);

    FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
    assertTrue("Found no records for entity: " + entityName, byWatermark.getIterator().hasNext());

    SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
    List<EntityData> data = byWatermark.getIterator().next();
    for (EntityData ed : data) {
      getByIdRequest.addData(getConnector().getId(), ed);
    }
    try {
      data = getDataService().getByIds(getByIdRequest);
      fail("Get by id not supported. But is successful");
    } catch (Exception e) {
      assertEquals("Http Sources does not support getbyids", e.getMessage());
    }

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
  public void updateSelectorXPaths() {
    HttpSourceConfigInfo config = null;
    assertNull(httpService.updateSelectorXPaths(config));
    
    config = new HttpSourceConfigInfo();
    config.setRecordSelector("/records");
    config.setIdSelector("/id");
    config.setWmSelector("/wm");
    
    HttpSourceConfigInfo updConfig =  httpService.updateSelectorXPaths(config);
    assertEquals("/records", updConfig.getRecordSelector());
    assertEquals("/id", updConfig.getIdSelector());
    assertEquals("/wm", updConfig.getWmSelector());
    
    config = new HttpSourceConfigInfo();
    config.setRecordSelector("records");
    config.setIdSelector("id");
    config.setWmSelector("wm");
    
    updConfig =  httpService.updateSelectorXPaths(config);
    assertEquals("/records", updConfig.getRecordSelector());
    assertEquals("/id", updConfig.getIdSelector());
    assertEquals("/wm", updConfig.getWmSelector());
  }

  private ConnectorInfo createConnector() {
    ConnectorInfo connector = new ConnectorInfo();
    connector.setId("1234");
    connector.setName("httpsources");
    connector.setAuthType(AuthType.None);
    connector.setAuthConfig(new AuthConfig());
    connector.setHttpSourceConfig(List.of(new HttpSourceConfigInfo()
        .setApiName(Constants.ACCOUNT.toLowerCase()).setEndpoint(urlPrefix + "/account")
        .setMethod("GET")
        .setSchema(
            "{\"$schema\": \"http://json-schema.org/draft-07/schema#\", \"type\": \"object\", \"properties\": {\"id\": { \"type\": \"string\" }, \"updatedAt\": { \"type\": \"string\", \"format\": \"date-time\" }, \"name\": { \"type\": \"string\" }, \"age\": { \"type\": \"number\" }, \"profession\": { \"type\": \"string\" }, \"address\": { \"type\": \"object\", \"properties\": { \"street\": { \"type\": \"string\" }, \"city\": { \"type\": \"string\" }, \"state\": { \"type\": \"string\" }, \"zipCode\": { \"type\": \"string\" } } } }}")
        .setIdSelector("/id").setWmSelector("/updatedAt").setType(PaginationType.NO_PAGINATION)
        .setHeaders(Map.of()),

        new HttpSourceConfigInfo().setApiName(Constants.CONTACT.toLowerCase())
            .setEndpoint(urlPrefix + "/contact?limit={{limit}}&offset={{offset}}").setMethod("GET")
            .setSchema(
                "{\"$schema\": \"http://json-schema.org/draft-07/schema#\", \"type\": \"object\", \"properties\": {\"id\": { \"type\": \"string\" }, \"updatedAt\": { \"type\": \"string\", \"format\": \"date-time\" }, \"name\": { \"type\": \"string\" }, \"age\": { \"type\": \"number\" }, \"profession\": { \"type\": \"string\" }, \"address\": { \"type\": \"object\", \"properties\": { \"street\": { \"type\": \"string\" }, \"city\": { \"type\": \"string\" }, \"state\": { \"type\": \"string\" }, \"zipCode\": { \"type\": \"string\" } } } }}")
            .setIdSelector("/id").setWmSelector("/updatedAt").setType(PaginationType.LIMIT_OFFSET)
            .setLimitValue(10).setOffsetValue(0).setLimitParam("limit").setOffsetParam("offset")
            .setHeaders(Map.of()),

        new HttpSourceConfigInfo().setApiName(Constants.CONTACT.toLowerCase() + "1")
            .setEndpoint(urlPrefix + "/contact1?pageNumber={{pageNumber}}&pageSize={{pageSize}}")
            .setMethod("GET")
            .setSchema(
                "{\"$schema\": \"http://json-schema.org/draft-07/schema#\", \"type\": \"object\", \"properties\": {\"id\": { \"type\": \"string\" }, \"updatedAt\": { \"type\": \"string\", \"format\": \"date-time\" }, \"name\": { \"type\": \"string\" }, \"age\": { \"type\": \"number\" }, \"profession\": { \"type\": \"string\" }, \"address\": { \"type\": \"object\", \"properties\": { \"street\": { \"type\": \"string\" }, \"city\": { \"type\": \"string\" }, \"state\": { \"type\": \"string\" }, \"zipCode\": { \"type\": \"string\" } } } }}")
            .setIdSelector("/id").setWmSelector("/updatedAt").setType(PaginationType.PAGE_NUMBER)
            .setPageNumberParam("pageNumber").setPageNumberValue(1).setPageSizeParam("pageSize")
            .setPageSize(10).setHeaders(Map.of()),

        new HttpSourceConfigInfo().setApiName(Constants.CONTACT.toLowerCase() + "2")
            .setEndpoint(urlPrefix + "/contact2?cursor={{cursor}}&pageSize={{pageSize}}")
            .setMethod("GET")
            .setSchema(
                "{\"$schema\": \"http://json-schema.org/draft-07/schema#\", \"type\": \"object\", \"properties\": {\"id\": { \"type\": \"string\" }, \"updatedAt\": { \"type\": \"string\", \"format\": \"date-time\" }, \"name\": { \"type\": \"string\" }, \"age\": { \"type\": \"number\" }, \"profession\": { \"type\": \"string\" }, \"address\": { \"type\": \"object\", \"properties\": { \"street\": { \"type\": \"string\" }, \"city\": { \"type\": \"string\" }, \"state\": { \"type\": \"string\" }, \"zipCode\": { \"type\": \"string\" } } } }}")
            .setRecordSelector("/records").setIdSelector("/id").setWmSelector("/updatedAt")
            .setType(PaginationType.CURSOR).setCursorType("parameter")
            .setNextCursorSelector("/nextCursor").setStartValue("0").setNextCursorParam("cursor")
            .setPageSizeParam("pageSize").setPageSize(10).setHeaders(Map.of()),
        new HttpSourceConfigInfo().setApiName(Constants.CONTACT.toLowerCase() + "3")
            .setEndpoint(urlPrefix + "/contact2?cursor=0&pageSize=10").setMethod("GET")
            .setSchema(
                "{\"$schema\": \"http://json-schema.org/draft-07/schema#\", \"type\": \"object\", \"properties\": {\"id\": { \"type\": \"string\" }, \"updatedAt\": { \"type\": \"string\", \"format\": \"date-time\" }, \"name\": { \"type\": \"string\" }, \"age\": { \"type\": \"number\" }, \"profession\": { \"type\": \"string\" }, \"address\": { \"type\": \"object\", \"properties\": { \"street\": { \"type\": \"string\" }, \"city\": { \"type\": \"string\" }, \"state\": { \"type\": \"string\" }, \"zipCode\": { \"type\": \"string\" } } } }}")
            .setRecordSelector("/records").setIdSelector("/id").setWmSelector("/updatedAt")
            .setType(PaginationType.CURSOR).setCursorType("link_in_body")
            .setNextCursorSelector("/nextCursor").setHeaders(Map.of())));
    return connector;
  }

  private ConnectorInfo createConnectorUnAuthorized() {
    ConnectorInfo connector = new ConnectorInfo();
    connector.setId("1234");
    connector.setName("httpsources");
    connector.setAuthType(AuthType.ApiSecretKey);
    connector.setAuthConfig(new AuthConfig());
    connector.setHttpSourceConfig(List.of(new HttpSourceConfigInfo()
        .setApiName(Constants.ACCOUNT.toLowerCase()).setEndpoint(urlPrefix + "/auth-test")
        .setMethod("GET")
        .setSchema(
            "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"type\":\"object\",\"properties\":{\"records\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},\"updatedAt\":{\"type\":\"string\",\"format\":\"date-time\"},\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"number\"},\"profession\":{\"type\":\"string\"},\"address\":{\"type\":\"object\",\"properties\":{\"street\":{\"type\":\"string\"},\"city\":{\"type\":\"string\"},\"state\":{\"type\":\"string\"},\"zipCode\":{\"type\":\"string\"}}}}}},\"nextCursor\":{\"type\":\"integer\"}}}")
        .setIdSelector("/id").setWmSelector("/updatedAt").setType(PaginationType.NO_PAGINATION)
        .setHeaders(Map.of())));
    return connector;
  }
  
  private ConnectorInfo createConnectorOAuthAuthorized() {
    ConnectorInfo connector = new ConnectorInfo();
    connector.setId("1234");
    connector.setName("httpsources");
    connector.setAuthType(AuthType.SimpleOAuth);
    connector.setAuthConfig(new AuthConfig().setClientId("c1").setClientSecret("c2").setAccessTokenEndpoint("http://localhost:80/oauth/token"));
    connector.setHttpSourceConfig(List.of(new HttpSourceConfigInfo()
        .setApiName(Constants.ACCOUNT.toLowerCase()).setEndpoint(urlPrefix + "/auth-test")
        .setMethod("GET")
        .setSchema(
            "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"type\":\"object\",\"properties\":{\"records\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},\"updatedAt\":{\"type\":\"string\",\"format\":\"date-time\"},\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"number\"},\"profession\":{\"type\":\"string\"},\"address\":{\"type\":\"object\",\"properties\":{\"street\":{\"type\":\"string\"},\"city\":{\"type\":\"string\"},\"state\":{\"type\":\"string\"},\"zipCode\":{\"type\":\"string\"}}}}}},\"nextCursor\":{\"type\":\"integer\"}}}")
        .setIdSelector("/id").setWmSelector("/updatedAt").setType(PaginationType.NO_PAGINATION)
        .setHeaders(Map.of())));
    return connector;
  }

  static class LimitOffsetGenerator extends ResponseTransformer {

    @Override
    public String getName() {
      return "LimitOffsetGenerator";
    }

    @Override
    public boolean applyGlobally() {
      return false;
    }

    @Override
    public Response transform(Request request, Response response, FileSource files,
        Parameters parameters) {
      int limit = Integer.parseInt(request.queryParameter("limit").firstValue());
      int offset = Integer.parseInt(request.queryParameter("offset").firstValue());

      // Generate JSON response based on limit and offset
      JSONArray responseBody = generateResponseData(limit, offset);

      // Return transformed response
      return Response.Builder.like(response).body(responseBody.toString()).build();
    }

    private JSONArray generateResponseData(int limit, int offset) {
      JSONArray jsonArray = new JSONArray();

      if (offset <= 25) {
        offset = offset < 25 ? offset : 25;
        for (int i = offset; i < offset + limit; i++) {
          // Generate example data for each item
          JSONObject jsonObject = new JSONObject();
          jsonObject.put("id", "user_" + i);
          jsonObject.put("name", "John Doe " + i);
          jsonObject.put("age", 30 + i);
          jsonObject.put("profession", "Software Engineer");
          // Slight old timestamp to so that test will not filter the data
          jsonObject.put("updatedAt",
              ZonedDateTime.now().minusMinutes(2).format(DateTimeFormatter.ISO_INSTANT));

          // Example address object
          JSONObject addressObject = new JSONObject();
          addressObject.put("street", i + " Main St");
          addressObject.put("city", "Springfield");
          addressObject.put("state", "IL");
          addressObject.put("zipCode", "6270" + i);

          jsonObject.put("address", addressObject);

          jsonArray.put(jsonObject);
        }
      }
      return jsonArray;
    }

  }

  static class PageNumberGenerator extends ResponseTransformer {

    @Override
    public String getName() {
      return "PageNumberGenerator";
    }

    @Override
    public boolean applyGlobally() {
      return false;
    }

    @Override
    public Response transform(Request request, Response response, FileSource files,
        Parameters parameters) {
      int pageSize = Integer.parseInt(request.queryParameter("pageSize").firstValue());
      int pageNumber = Integer.parseInt(request.queryParameter("pageNumber").firstValue());

      // Generate JSON response based on limit and offset
      JSONArray responseBody = generateResponseData(pageSize, pageNumber);

      // Return transformed response
      return Response.Builder.like(response).body(responseBody.toString()).build();
    }

    private JSONArray generateResponseData(int pageSize, int pageNumber) {
      JSONArray jsonArray = new JSONArray();

      if (pageNumber <= 3) {
        pageNumber = pageNumber < 3 ? pageNumber : 3;
        for (int i = (pageNumber - 1) * pageSize; i <= ((pageNumber - 1) * pageSize)
            + pageSize; i++) {
          // Generate example data for each item
          JSONObject jsonObject = new JSONObject();
          jsonObject.put("id", "user_" + i);
          jsonObject.put("name", "John Doe " + i);
          jsonObject.put("age", 30 + i);
          jsonObject.put("profession", "Software Engineer");
          // Slight old timestamp to so that test will not filter the data
          jsonObject.put("updatedAt",
              ZonedDateTime.now().minusMinutes(2).format(DateTimeFormatter.ISO_INSTANT));

          // Example address object
          JSONObject addressObject = new JSONObject();
          addressObject.put("street", i + " Main St");
          addressObject.put("city", "Springfield");
          addressObject.put("state", "IL");
          addressObject.put("zipCode", "6270" + i);

          jsonObject.put("address", addressObject);

          jsonArray.put(jsonObject);
        }
      }
      return jsonArray;
    }

  }

  static class CursorParamGenerator extends ResponseTransformer {

    @Override
    public String getName() {
      return "CursorParamGenerator";
    }

    @Override
    public boolean applyGlobally() {
      return false;
    }

    @Override
    public Response transform(Request request, Response response, FileSource files,
        Parameters parameters) {
      int pageSize = Integer.parseInt(request.queryParameter("pageSize").firstValue());
      int cursor = Integer.parseInt(request.queryParameter("cursor").firstValue());

      // Generate JSON response based on limit and offset
      JSONObject responseBody = generateResponseData(pageSize, cursor);

      // Return transformed response
      return Response.Builder.like(response).body(responseBody.toString()).build();
    }

    private JSONObject generateResponseData(int pageSize, int cursor) {
      JSONArray jsonArray = new JSONArray();

      if (cursor <= 25) {
        cursor = cursor < 25 ? cursor : 25;
        for (int i = cursor; i < cursor + pageSize; i++) {
          // Generate example data for each item
          JSONObject jsonObject = new JSONObject();
          jsonObject.put("id", "user_" + i);
          jsonObject.put("name", "John Doe " + i);
          jsonObject.put("age", 30 + i);
          jsonObject.put("profession", "Software Engineer");
          // Slight old timestamp to so that test will not filter the data
          jsonObject.put("updatedAt",
              ZonedDateTime.now().minusMinutes(2).format(DateTimeFormatter.ISO_INSTANT));

          // Example address object
          JSONObject addressObject = new JSONObject();
          addressObject.put("street", i + " Main St");
          addressObject.put("city", "Springfield");
          addressObject.put("state", "IL");
          addressObject.put("zipCode", "6270" + i);

          jsonObject.put("address", addressObject);

          jsonArray.put(jsonObject);
        }
      }
      JSONObject finalRes = new JSONObject();
      finalRes.put("records", jsonArray);
      if (cursor + pageSize < 25) {
        finalRes.put("nextCursor", cursor + pageSize);
      }
      return finalRes;
    }

  }

  static class CursorUrlGenerator extends ResponseTransformer {

    @Override
    public String getName() {
      return "CursorUrlGenerator";
    }

    @Override
    public boolean applyGlobally() {
      return false;
    }

    @Override
    public Response transform(Request request, Response response, FileSource files,
        Parameters parameters) {
      int pageSize = Integer.parseInt(request.queryParameter("pageSize").firstValue());
      int cursor = Integer.parseInt(request.queryParameter("cursor").firstValue());

      // Generate JSON response based on limit and offset
      JSONObject responseBody = generateResponseData(pageSize, cursor);

      // Return transformed response
      return Response.Builder.like(response).body(responseBody.toString()).build();
    }

    private JSONObject generateResponseData(int pageSize, int cursor) {
      JSONArray jsonArray = new JSONArray();

      if (cursor <= 25) {
        cursor = cursor < 25 ? cursor : 25;
        for (int i = cursor; i < cursor + pageSize; i++) {
          // Generate example data for each item
          JSONObject jsonObject = new JSONObject();
          jsonObject.put("id", "user_" + i);
          jsonObject.put("name", "John Doe " + i);
          jsonObject.put("age", 30 + i);
          jsonObject.put("profession", "Software Engineer");
          // Slight old timestamp to so that test will not filter the data
          jsonObject.put("updatedAt",
              ZonedDateTime.now().minusMinutes(2).format(DateTimeFormatter.ISO_INSTANT));

          // Example address object
          JSONObject addressObject = new JSONObject();
          addressObject.put("street", i + " Main St");
          addressObject.put("city", "Springfield");
          addressObject.put("state", "IL");
          addressObject.put("zipCode", "6270" + i);

          jsonObject.put("address", addressObject);

          jsonArray.put(jsonObject);
        }
      }
      JSONObject finalRes = new JSONObject();
      finalRes.put("records", jsonArray);
      if (cursor + pageSize < 25) {
        finalRes.put("nextCursor", "http://localhost:8089/contact3?cursor=" + (cursor + pageSize)
            + "&pageSize=" + pageSize);
      }
      return finalRes;
    }

  }
}
