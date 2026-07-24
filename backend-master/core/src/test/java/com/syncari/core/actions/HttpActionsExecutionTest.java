package com.syncari.core.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.rest.NetSuiteRestClient;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.NetSuiteService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.TestConfig;
import com.syncari.core.actions.http.AuthenticationInfo;
import com.syncari.core.actions.http.HTTPAction;
import com.syncari.core.actions.http.HttpActionProperties;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.CustomActionDefinitionRepoImpl;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.utils.Pair;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class HttpActionsExecutionTest extends AbstractSyncariTest {

    @Autowired
    HTTPAction httpAction;

    @Mock
    HTTPAction mockHttpAction;

    @Autowired
    EndSystemConfig config;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    ConnectorMetadataService connectorMetadataService;
    @MockBean
    DataServiceFactory dataServiceFactory;

    @MockBean
    CustomActionDefinitionRepoImpl customActionDefinitionRepoImpl;


    private static Connector apiKeyCredential;
    private static Connector oauthCredential;
    private static Connector netsuiteConnector = SchemaHelper.createConnector("Netsuite connector","Netsuite Co","zendeskConnectorId");

    @After
    public void tearDown() {
        super.tearDown();
    }

    @Before
    public void setUp() {
        super.setUp();
        connectorService.publisher = publisher;
        apiKeyCredential = new Connector("New Generic API Key", connectorService.describe("genericApiKey").getId(), "");
        apiKeyCredential.getAuthConfig().setToken(config.getHttpActionAPIKey());
        apiKeyCredential.setAuthType(AuthType.ApiKey);
        apiKeyCredential = connectorService.save(apiKeyCredential);
        connectorService.authenticated(apiKeyCredential.getId());

        oauthCredential = new Connector("New Generic OAuth", connectorService.describe("genericSimpleOAuth").getId(), "");
        oauthCredential.getAuthConfig().setClientId(config.getHttpTestSimpleOAuthClientId());
        oauthCredential.getAuthConfig().setClientSecret(config.getHttpTestSimpleOAuthClientSecret());
        oauthCredential.setAuthType(AuthType.SimpleOAuth);
        oauthCredential = connectorService.save(oauthCredential);
        connectorService.authenticated(oauthCredential.getId());
    }

    @Test
    public void testActionWithAPIKey() {

        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        Map<String, String> headers = Map.of("User-Agent", "Syncari/v1 HTTP Client", "X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");
        var body = "{\"name\" : \"John Doe\", \"email\" : \"jdoe@syncari.com\"}";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("https://example.com/rest/v1/actions/arg1/{{arg1}}/arg2/{{arg2}}")
                .setMethod(HttpMethod.POST)
                .setHeaders(headers)
                .setBody(body)
                .setBatch(false)
                .setBatchSize(0)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(apiKeyCredential.getId()));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("arg1", "5");
        configMap.put("arg2", "10");

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthConfig> captor3 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).postRaw(captor1.capture(), captor2.capture(), captor3.capture());

        assertEquals("https://example.com/rest/v1/actions/arg1/5/arg2/10?api_key=REPLACE_ME", captor1.getValue());
        assertEquals(body, captor2.getValue());

        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.putAll(headers);
        expectedHeaders.put("X-API-KEY", config.getHttpActionAPIKey());
        assertEquals(expectedHeaders, captor3.getValue().getAdditionalHeaders());
    }

    @Test
    public void testActionWithNoAuth() {

        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        Map<String, String> headers = Map.of("User-Agent", "Syncari/v1 HTTP Client", "X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");
        var body = "{\"name\" : \"John Doe\", \"email\" : \"jdoe@syncari.com\"}";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("https://example.com/rest/v1/actions/arg1/{{arg1}}/arg2/{{arg2}}")
                .setMethod(HttpMethod.POST)
                .setHeaders(headers)
                .setBody(body)
                .setBatchSize(0)
                .setBatch(false)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(""));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("arg1", "5");
        configMap.put("arg2", "10");

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthConfig> captor3 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).postRaw(captor1.capture(), captor2.capture(), captor3.capture());

        assertEquals("https://example.com/rest/v1/actions/arg1/5/arg2/10", captor1.getValue());
        assertEquals(body, captor2.getValue());

        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.putAll(headers);
        assertEquals(expectedHeaders, captor3.getValue().getAdditionalHeaders());
    }

    @Test
    public void testActionWithObjectVariables() {

        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        Map<String, String> headers = Map.of("X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");
        var body = "[{%for r in objectVar %} {\"name\" : \"{{r.values.name}}\"},{%endfor%}]";
        var expected = "[ {\"name\" : \"name1\"}, {\"name\" : \"name2\"},]";
        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("https://example.com/rest/v1/actions/arg1/{{arg1}}/arg2/{{arg2}}")
                .setMethod(HttpMethod.POST)
                .setHeaders(headers)
                .setBody(body)
                .setBatchSize(0)
                .setBatch(false)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(""));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("objectVar", "{{lookupRecords}}");
        configMap.put("arg1", "5");
        configMap.put("arg2", "10");
        CustomActionDefinition def = new CustomActionDefinition();

        def.setConfiguration(List.of(
                new FunctionConfiguration().setName("objectVar").setDatatype(ObjectType.VALUE),
                new FunctionConfiguration().setName("arg1").setDatatype(StringType.VALUE),
                new FunctionConfiguration().setName("arg2").setDatatype(StringType.VALUE)
        ));

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", def);
        graphContext.put("lookupRecords", List.of(new EntityData().addValue("name", "name1"), new EntityData().addValue("name", "name2")));
        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthConfig> captor3 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).postRaw(captor1.capture(), captor2.capture(), captor3.capture());

        assertEquals("https://example.com/rest/v1/actions/arg1/5/arg2/10", captor1.getValue());
        assertEquals(expected, captor2.getValue());

    }

    @Test
    public void testActionWithUnresolvedVariables() {

        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        Map<String, String> headers = Map.of("X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");
        var body = "{\"name\" : \"{{name}}\"}";
        var expected = "{\"name\" : \"\"}";
        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("https://example.com/rest/v1/actions/arg1/{{arg1}}/arg2/{{arg2}}")
                .setMethod(HttpMethod.POST)
                .setHeaders(headers)
                .setBody(body)
                .setBatchSize(0)
                .setBatch(false)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(""));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("arg1", "5");
        configMap.put("arg2", "10");
        configMap.put("name", "{{name}}");
        CustomActionDefinition def = new CustomActionDefinition();

        def.setConfiguration(List.of(
                new FunctionConfiguration().setName("name").setDatatype(StringType.VALUE),
                new FunctionConfiguration().setName("arg1").setDatatype(StringType.VALUE),
                new FunctionConfiguration().setName("arg2").setDatatype(StringType.VALUE)
        ));

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", def);
        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthConfig> captor3 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).postRaw(captor1.capture(), captor2.capture(), captor3.capture());

        assertEquals("https://example.com/rest/v1/actions/arg1/5/arg2/10", captor1.getValue());
        assertEquals(expected, captor2.getValue());

    }
    @Test
    public void testActionWithNewLineIsStripped() {

        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        Map<String, String> headers = Map.of("User-Agent", "Syncari/v1 HTTP Client", "X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");
        var body = "{\"name\" : \"John Doe\", \"email\" : \"jdoe@syncari.com\", \"note\" : \"{{arg3}}\" }";
        var expected = "{\"name\" : \"John Doe\", \"email\" : \"jdoe@syncari.com\", \"note\" : \"Some line \\n with new line\" }";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("https://example.com/rest/v1/actions/arg1/{{arg1}}/arg2/{{arg2}}")
                .setMethod(HttpMethod.POST)
                .setHeaders(headers)
                .setBody(body)
                .setBatch(false)
                .setBatchSize(0)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(apiKeyCredential.getId()));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("arg1", "5\n");
        configMap.put("arg2", "10");
        configMap.put("arg3", "Some line \n with new line");

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthConfig> captor3 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).postRaw(captor1.capture(), captor2.capture(), captor3.capture());

        assertEquals("https://example.com/rest/v1/actions/arg1/5/arg2/10?api_key=REPLACE_ME", captor1.getValue());
        assertEquals(expected, captor2.getValue());

        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.putAll(headers);
        expectedHeaders.put("X-API-KEY", config.getHttpActionAPIKey());
        assertEquals(expectedHeaders, captor3.getValue().getAdditionalHeaders());
    }

    @Test
    public void testActionWithEndpointToken() {
        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        var body = "{\"name\" : \"{{name}}\"}";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("{{endpoint}}")
                .setHeaders(Map.of())
                .setMethod(HttpMethod.POST)
                .setBody(body)
                .setBatchSize(0)
                .setBatch(false)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(""));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("endpoint", "{{test_endpoint}}");
        CustomActionDefinition def = new CustomActionDefinition();

        def.setConfiguration(List.of(
                new FunctionConfiguration().setName("endpoint").setDatatype(StringType.VALUE)
        ));

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        configMap.put("configId", "123");
        var graphContext = new GraphContext();
        graphContext.cache("customActionDefinition123", def);
        graphContext.put("test_endpoint", "http://syncari.salesforce.com");
        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthConfig> captor3 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).postRaw(captor1.capture(), captor2.capture(), captor3.capture());
        assertEquals("http://syncari.salesforce.com", captor1.getValue());

        graphContext.clear();
        graphContext.cache("customActionDefinition123", def);
        graphContext.put("test_endpoint", "http://syncari.salesforce.com/v1/");

        mockRestClient = mock(SyncariEntityDataRestClient.class);
        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        captor1 = ArgumentCaptor.forClass(String.class);
        captor2 = ArgumentCaptor.forClass(String.class);
        captor3 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).postRaw(captor1.capture(), captor2.capture(), captor3.capture());
        assertEquals("http://syncari.salesforce.com/v1/", captor1.getValue());

        graphContext.clear();
        graphContext.cache("customActionDefinition123", def);
        graphContext.put("test_endpoint", "http://syncari.salesforce.com/v|1/");

        mockRestClient = mock(SyncariEntityDataRestClient.class);
        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        captor1 = ArgumentCaptor.forClass(String.class);
        captor2 = ArgumentCaptor.forClass(String.class);
        captor3 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).postRaw(captor1.capture(), captor2.capture(), captor3.capture());
        assertEquals("http://syncari.salesforce.com/v%7C1/", captor1.getValue());
    }


    @Test
    public void testActionWithOAuth() {

        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        Map<String, String> headers = Map.of("User-Agent", "Syncari/v1 HTTP Client", "X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");
        var body = "{\"name\" : \"John Doe\", \"email\" : \"jdoe@syncari.com\"}";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("https://example.com/rest/v1/actions/arg1/{{arg1}}/arg2/{{arg2}}")
                .setMethod(HttpMethod.POST)
                .setHeaders(headers)
                .setBody(body)
                .setBatch(false)
                .setBatchSize(0)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(oauthCredential.getId()));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("arg1", "5");
        configMap.put("arg2", "10");

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthConfig> captor3 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).postRaw(captor1.capture(), captor2.capture(), captor3.capture());
        assertEquals("https://example.com/rest/v1/actions/arg1/5/arg2/10", captor1.getValue());
        assertEquals(body, captor2.getValue());
        assertEquals(headers, captor3.getValue().getAdditionalHeaders());
    }

    @Test
    public void testDeleteMethod() {

        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        Map<String, String> headers = Map.of("User-Agent", "Syncari/v1 HTTP Client", "X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");
        var body = "{}";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("https://example.com/rest/v1/actions/arg1/{{arg1}}/arg2/{{arg2}}")
                .setMethod(HttpMethod.DELETE)
                .setHeaders(headers)
                .setBody(body)
                .setBatchSize(0)
                .setBatch(false)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(""));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("arg1", "5");
        configMap.put("arg2", "10");

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).delete(anyString(), any(), any(AuthConfig.class));

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthConfig> captor3 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).delete(captor1.capture(), captor2.capture(), captor3.capture());

        assertEquals("https://example.com/rest/v1/actions/arg1/5/arg2/10", captor1.getValue());
        assertEquals(body, captor2.getValue());

        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.putAll(headers);
        assertEquals(expectedHeaders, captor3.getValue().getAdditionalHeaders());
    }

    @Test
    public void testActionWithOptionalValue() {

        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        Map<String, String> headers = Map.of("X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");
        var body = "{\"name\" : \"John Doe\", \"email\" : \"jdoe@syncari.com\"}";

        String endpoint = "https://example.com/rest/v1/actions/ticket/{{ticketId}}{% if user %}?id={{user}}{% endif %}";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint(endpoint)
                .setMethod(HttpMethod.POST)
                .setHeaders(headers)
                .setBody(body)
                .setBatch(false)
                .setBatchSize(0)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(oauthCredential.getId()));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("ticketId", "SYN-2324");
        configMap.put("user", null);

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        verify(mockRestClient).postRaw(captor.capture(), any(), any());
        assertEquals("https://example.com/rest/v1/actions/ticket/SYN-2324", captor.getValue());

        // add value to optional parameter
        configMap = new HashMap<String, Object>();
        configMap.put("ticketId", "SYN-2324");
        configMap.put("user", "john");

        mockRestClient = mock(SyncariEntityDataRestClient.class);
        genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        graphContext = new GraphContext();
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());
        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        captor = ArgumentCaptor.forClass(String.class);

        verify(mockRestClient).postRaw(captor.capture(), any(), any());
        assertEquals("https://example.com/rest/v1/actions/ticket/SYN-2324?id=john", captor.getValue());
    }

    private Connector createNetsuiteConnector() {
        var connectorMetadata = connectorMetadataService.findByName("netsuite");
        Connector netsuiteConnector = SchemaHelper.createConnector("Netsuite connector", ObjectId.get().toHexString(), connectorMetadata.get().getId());
        netsuiteConnector.setId(null);
        netsuiteConnector.setMetadataId(connectorMetadata.get().getId());
        netsuiteConnector.setMetadata(connectorMetadata.get());
        netsuiteConnector.setAuthType(AuthType.NetSuiteTokenBasedAuthentication);
        return connectorService.save(netsuiteConnector);
    }

    @Test
    public void testActionPostWithSynpase() {

        var mockRestClient = mock(NetSuiteRestClient.class);

        Map<String, String> headers = Map.of("User-Agent", "Syncari/v1 HTTP Client", "X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");
        var body = "{\"name\" : \"John Doe\", \"email\" : \"jdoe@syncari.com\"}";

        var netsuiteConnector = createNetsuiteConnector();

        var nsService=new NetSuiteService() {
            @Override
            protected NetSuiteRestClient getNetSuiteRestClient() {
                return mockRestClient;
            }
        };

        when(dataServiceFactory.getRestClientService(netsuiteConnector.getMetadata())).thenReturn(nsService);
        when(dataServiceFactory.isRestClientService(netsuiteConnector.getMetadata())).thenReturn(true);

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("https://netsuite.com/rest/v1/actions/arg1/{{arg1}}/arg2/{{arg2}}")
                .setMethod(HttpMethod.POST)
                .setHeaders(headers)
                .setBody(body)
                .setBatch(false)
                .setBatchSize(0)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Synapse).setCredentialId(netsuiteConnector.getId()));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("arg1", "5");
        configMap.put("arg2", "10");

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthConfig> captor3 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).postRaw(captor1.capture(), captor2.capture(), captor3.capture());
        assertEquals("https://netsuite.com/rest/v1/actions/arg1/5/arg2/10", captor1.getValue());
        assertEquals(body, captor2.getValue());
        assertEquals(body, captor2.getValue());
        assertEquals(headers, captor3.getValue().getAdditionalHeaders());
    }

    @Test
    public void testActionGetWithSynpase() {

        var mockRestClient = mock(NetSuiteRestClient.class);

        Map<String, String> headers = Map.of("User-Agent", "Syncari/v1 HTTP Client", "X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");
        var body = "{\"name\" : \"John Doe\", \"email\" : \"jdoe@syncari.com\"}";

        var netsuiteConnector = createNetsuiteConnector();

        var nsService=new NetSuiteService() {
            @Override
            protected NetSuiteRestClient getNetSuiteRestClient() {
                return mockRestClient;
            }
        };

        when(dataServiceFactory.getRestClientService(netsuiteConnector.getMetadata())).thenReturn(nsService);
        when(dataServiceFactory.isRestClientService(netsuiteConnector.getMetadata())).thenReturn(true);

        //netsuiteService

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("https://netsuite.com/rest/v1/actions/arg1/{{arg1}}/arg2/{{arg2}}")
                .setMethod(HttpMethod.GET)
                .setHeaders(headers)
                .setBody(body)
                .setBatch(false)
                .setBatchSize(0)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Synapse).setCredentialId(netsuiteConnector.getId()));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("arg1", "5");
        configMap.put("arg2", "10");

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).getResponse(any(), any());

        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthConfig> captor2 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).getResponse(captor1.capture(), captor2.capture());
        assertEquals("https://netsuite.com/rest/v1/actions/arg1/5/arg2/10", captor1.getValue());
        assertEquals(headers, captor2.getValue().getAdditionalHeaders());
    }

    @Test
    public void testJsonResponse() throws Exception {
        var mockRestClient = mock(SyncariEntityDataRestClient.class);
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, String> headers = Map.of("X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");

        String endpoint = "https://us-zipcode.api.smartystreets.com/lookup?city=Austin&state=TX&zipcode=73301&auth-id=REPLACE_ME&auth-token=REPLACE_ME";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint(endpoint)
                .setMethod(HttpMethod.GET)
                .setHeaders(headers)
                .setBatch(false)
                .setBatchSize(0)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(oauthCredential.getId()));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("ticketId", "SYN-2324");
        configMap.put("user", null);

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        graphContext.setCurrentNode(new MappingNode().setName("Custom Action"));
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        String responseString = "{\"name\": \"joe\", \"index\" : 0, \"address\" : {\"city\" : \"Dublin\", \"state\" : \"CA\"}}";

        Map<String, Object> responseObj = objectMapper.readValue(responseString, Map.class);

        ResponseEntity<String> response = new ResponseEntity<>(responseString, HttpStatus.OK);
        doReturn(response).when(mockRestClient).getNoRedirectResponse(any(), any());

        httpAction.setRestClient(mockRestClient);
        var actionResponse = httpAction.execute(genericActionConfig, graphContext);
        assertEquals(responseObj, actionResponse.getResult());
        assertEquals(200, graphContext.get("Status Code From Action Custom Action"));

        doReturn(new ResponseEntity<>("", HttpStatus.OK)).when(mockRestClient).getNoRedirectResponse(any(), any());
        httpAction.setRestClient(mockRestClient);
        actionResponse = httpAction.execute(genericActionConfig, graphContext);
        assertNull(actionResponse.getResult());
        assertEquals(200, graphContext.get("Status Code From Action Custom Action"));

    }

    @Test
    public void testResponseJsonArray() throws Exception {
        var mockRestClient = mock(SyncariEntityDataRestClient.class);
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, String> headers = Map.of("X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");

        String endpoint = "https://us-zipcode.api.smartystreets.com/lookup?city=Austin&state=TX&zipcode=73301&auth-id=REPLACE_ME&auth-token=REPLACE_ME";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint(endpoint)
                .setMethod(HttpMethod.GET)
                .setHeaders(headers)
                .setBatch(false)
                .setBatchSize(0)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(oauthCredential.getId()));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("ticketId", "SYN-2324");
        configMap.put("user", null);

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        graphContext.setCurrentNode(new MappingNode().setName("Custom Action"));
        configMap.put("configId", "123");
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        String responseArray = "[{\"input_index\":0,\"city_states\":[{\"city\":\"Austin\",\"state_abbreviation\":\"TX\",\"state\":\"Texas\",\"mailable_city\":true}],\"zipcodes\":[{\"zipcode\":\"73301\",\"zipcode_type\":\"U\",\"default_city\":\"Austin\",\"county_fips\":\"48453\",\"county_name\":\"Travis\",\"state_abbreviation\":\"TX\",\"state\":\"Texas\",\"latitude\":30.205,\"longitude\":-97.75808,\"precision\":\"Zip5\"}]}]";

        List<Object> responseObj = objectMapper.readValue(responseArray, List.class);

        ResponseEntity<String> response = new ResponseEntity<>(responseArray, HttpStatus.OK);
        doReturn(response).when(mockRestClient).getNoRedirectResponse(any(), any());

        httpAction.setRestClient(mockRestClient);
        var actionResponse = httpAction.execute(genericActionConfig, graphContext);
        assertEquals(responseObj, actionResponse.getResult());
        assertEquals(200, graphContext.get("Status Code From Action Custom Action"));
    }

    @Test
    public void testJTwigConditionalWithBooleanFalse() {
        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        Map<String, String> headers = Map.of("X-Custom-Header1", "Custom Value1");
        var body = "{\n    \"testkey3\": \"testvalue3\"{% if send %},\n    \"testkey4\": \"testvalue4\"{% endif %}\n}";
        var expectedFalse = "{\n    \"testkey3\": \"testvalue3\"\n}";
        var expectedTrue = "{\n    \"testkey3\": \"testvalue3\",\n    \"testkey4\": \"testvalue4\"\n}";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("https://example.com/test")
                .setMethod(HttpMethod.POST)
                .setHeaders(headers)
                .setBody(body)
                .setBatch(false)
                .setBatchSize(0)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(""));

        Map<String, Object> configMap = new HashMap<String, Object>();
        
        // Test with boolean false - should exclude conditional content
        configMap.put("send", false);
        configMap.put("configId", "123");

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_jtwig_conditional");
        var graphContext = new GraphContext();
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthConfig> captor3 = ArgumentCaptor.forClass(AuthConfig.class);

        verify(mockRestClient).postRaw(captor1.capture(), captor2.capture(), captor3.capture());

        assertEquals("https://example.com/test", captor1.getValue());
        
        System.out.println("Test with boolean false:");
        System.out.println("Expected: " + expectedFalse);
        System.out.println("Actual: " + captor2.getValue());
        
        // FIXED: evaluateVariables() now preserves boolean values instead of converting to string
        // Boolean false should be falsy and exclude conditional content
        System.out.println("Original configMap send value: " + configMap.get("send") + " (type: " + configMap.get("send").getClass() + ")");
        
        assertEquals(expectedFalse, captor2.getValue());
        
        // Test with boolean true - should include conditional content
        mockRestClient = mock(SyncariEntityDataRestClient.class);
        configMap.put("send", true);
        genericActionConfig.setConfigMap(configMap);
        
        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());
        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);
        
        ArgumentCaptor<String> captor4 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor5 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthConfig> captor6 = ArgumentCaptor.forClass(AuthConfig.class);
        
        verify(mockRestClient).postRaw(captor4.capture(), captor5.capture(), captor6.capture());
        
        System.out.println("\nTest with boolean true:");
        System.out.println("Expected: " + expectedTrue);
        System.out.println("Actual: " + captor5.getValue());
        
        // Boolean true should be truthy and include conditional content
        assertEquals(expectedTrue, captor5.getValue());
        
        System.out.println("\nFIX APPLIED:");
        System.out.println("HTTPAction.evaluateVariables() now preserves boolean types");
        System.out.println("Only string values containing tokens are resolved");
        System.out.println("Boolean values are passed directly to JTwig for correct conditional behavior");
    }

    @Test
    public void testEvaluateVariablesPreservesDataTypes() {
        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        Map<String, String> headers = Map.of("X-Custom-Header", "Test Value");
        var body = "{\"stringValue\": \"{{stringVar}}\", \"boolValue\": {{boolVar}}, \"numValue\": {{numVar}}, \"untouchedBool\": {{untouchedBool}}, \"untouchedNum\": {{untouchedNum}}}";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("https://example.com/test")
                .setMethod(HttpMethod.POST)
                .setHeaders(headers)
                .setBody(body)
                .setBatch(false)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(""));

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("stringVar", "hello world");
        configMap.put("boolVar", true);
        configMap.put("numVar", 42);
        configMap.put("untouchedBool", false);  // No tokens, should preserve boolean type
        configMap.put("untouchedNum", 123);     // No tokens, should preserve number type
        configMap.put("configId", "123");

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_data_types");
        var graphContext = new GraphContext();
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig, graphContext);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockRestClient).postRaw(any(), bodyCaptor.capture(), any());

        String actualBody = bodyCaptor.getValue();
        
        // Verify the body contains resolved tokens and preserved types
        assertTrue(actualBody.contains("\"stringValue\": \"hello world\""));
        assertTrue(actualBody.contains("\"boolValue\": true"));
        assertTrue(actualBody.contains("\"numValue\": 42"));
        assertTrue(actualBody.contains("\"untouchedBool\": false"));
        assertTrue(actualBody.contains("\"untouchedNum\": 123"));
    }

    @Test
    public void testJTwigConditionalWithNumericValues() {
        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        Map<String, String> headers = Map.of("X-Custom-Header", "Test Value");
        var body = "{\"base\": \"test\"{% if count > 0 %},\"items\": {{count}}{% endif %}{% if score == 0 %},\"noScore\": true{% endif %}}";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint("https://example.com/test")
                .setMethod(HttpMethod.POST)
                .setHeaders(headers)
                .setBody(body)
                .setBatch(false)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(""));

        // Test with count > 0 and score != 0
        Map<String, Object> configMap1 = new HashMap<>();
        configMap1.put("count", 5);
        configMap1.put("score", 100);
        configMap1.put("configId", "123");

        GenericActionConfig genericActionConfig1 = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap1).setType(Type.CUSTOM).setName("test_numeric_conditional");
        var graphContext = new GraphContext();
        graphContext.cache("customActionDefinition123", new CustomActionDefinition());

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());

        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig1, graphContext);

        ArgumentCaptor<String> bodyCaptor1 = ArgumentCaptor.forClass(String.class);
        verify(mockRestClient).postRaw(any(), bodyCaptor1.capture(), any());

        String actualBody1 = bodyCaptor1.getValue();
        String expectedBody1 = "{\"base\": \"test\",\"items\": 5}";
        assertEquals(expectedBody1, actualBody1);

        // Test with count = 0 and score = 0
        mockRestClient = mock(SyncariEntityDataRestClient.class);
        Map<String, Object> configMap2 = new HashMap<>();
        configMap2.put("count", 0);
        configMap2.put("score", 0);
        configMap2.put("configId", "123");

        GenericActionConfig genericActionConfig2 = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap2).setType(Type.CUSTOM).setName("test_numeric_conditional_zero");

        doReturn(new ResponseEntity<String>(HttpStatus.OK)).when(mockRestClient).postRaw(any(), any(), any());
        httpAction.setRestClient(mockRestClient);
        httpAction.execute(genericActionConfig2, graphContext);

        ArgumentCaptor<String> bodyCaptor2 = ArgumentCaptor.forClass(String.class);
        verify(mockRestClient).postRaw(any(), bodyCaptor2.capture(), any());

        String actualBody2 = bodyCaptor2.getValue();
        String expectedBody2 = "{\"base\": \"test\",\"noScore\": true}";
        assertEquals(expectedBody2, actualBody2);
    }

    @Test
    public void testCustomActionsBatching(){
        var mockRestClient = mock(SyncariEntityDataRestClient.class);

        HTTPAction spy = spy(httpAction);

        Map<String, String> headers = Map.of("X-Custom-Header1", "Custom Value1", "X-Custom-Header2", "Custom Value2");

        String endpoint = "https://api.hubapi.com/contacts/v1/lists/{{listId}}/add/";

        String body = "{\n" +
                "\n" +
                "vids: [\n" +
                "\t\n" +
                "    \"240638\",\n" +
                "    \n" +
                "    \"239982\",\n" +
                "\t\n" +
                "\t\"243917\"\n" +
                "    \n" +
                "\n" +
                "]\n" +
                "\n" +
                "}";

        var httpActionProperties = new HttpActionProperties()
                .setEndPoint(endpoint)
                .setMethod(HttpMethod.POST)
                .setHeaders(headers)
                .setBatch(true)
                .setBatchSize(2)
                .setBody(body)
                .setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Credential).setCredentialId(oauthCredential.getId()));

        Map<String, Object> configMap = new HashMap<String, Object>();
        configMap.put("vid", "243817");
        configMap.put("configId", "1234");

        List<FunctionConfiguration> configuration =  List.of(

                new FunctionConfiguration().setName("vid").setDatatype(new StringType()).setLabel("vid")
                        .setDefaultValue("").setMultiValuedVariable(true).setHelpSummary(i18n("addToHubspotList_list_action_help")).setRequired(true).setAdditionalProperties(Map.of()),

                new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
                        .setMultiValuedVariable(false)
                        .setDefaultValue("").setHelpSummary(i18n("addToHubspotList_value_action_help")).setRequired(true).setAdditionalProperties(Map.of())
        );

        ActionDefinition actionDefinition =  new ActionDefinition()
                .setName(ActionConstants.ADD_TO_HUBSPOT_LIST).setDisplayName("Add To HubSpot List").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("addToHubspotList_action_help")).setIconPath(format(ActionsSeed.iconPath, "add-to-list"))
                .setHelpPath("actions." + ActionConstants.ADD_TO_HUBSPOT_LIST).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
        Optional<ActionDefinition> optionalActionDefinition = Optional.of(actionDefinition);

        GenericActionConfig genericActionConfig = (GenericActionConfig) new GenericActionConfig().setActionProperties(httpActionProperties)
                .setConfigMap(configMap).setType(Type.CUSTOM).setName("test_http_action");
        var graphContext = new GraphContext();
        MappingNode node = new MappingNode();
        node.setId("123");
        graphContext.setCurrentNode(node);
        String responseArray = "{\"updated\":[339201,339251]}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>("",HttpStatus.ACCEPTED);
        ResponseEntity<String> response = new ResponseEntity<>(responseArray,HttpStatus.OK);
        RequestEntity<String> request = new RequestEntity<>(HttpMethod.POST, URI.create("https://api.hubapi.com/contacts/v1/lists/6/add/"));
        doReturn(response).when(mockRestClient).getResponse(any(), any());
        doReturn(responseEntity).when(mockRestClient).postRaw(Mockito.any(), Mockito.anyString(),Mockito.any());
        doReturn(optionalActionDefinition)
                .when(customActionDefinitionRepoImpl).findByObjectId(Mockito.anyString());
        ActionResult actionResult = new ActionResult(true, "123");
        Pair<RequestEntity<String>, ResponseEntity<String>> result = Pair.of(request,response);

        doReturn(result)
                .when(spy).execute(Mockito.any(),Mockito.anyMap(), anyBoolean(), any());

        mockHttpAction.setRestClient(mockRestClient);
        spy.execute(genericActionConfig, graphContext);

        configMap.put("vid","240638");
        genericActionConfig.setConfigMap(configMap);

        spy.execute(genericActionConfig, graphContext);

        configMap.put("vid","239982");
        genericActionConfig.setConfigMap(configMap);

        spy.execute(genericActionConfig, graphContext);

        graphContext.getBatchActionContext().enableRunActions();

        spy.execute(genericActionConfig, graphContext);

        verify(spy , atLeast(2)).execute(Mockito.any(), Mockito.anyMap(), anyBoolean(), any());

    }

}
