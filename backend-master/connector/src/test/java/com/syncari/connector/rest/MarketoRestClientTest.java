package com.syncari.connector.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.QuotaExceededException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.MarketoService;
import com.syncari.utils.Storage;

public class MarketoRestClientTest {

    @Autowired
    MarketoService marketoService;

    @Test
    public void checkResponseAPILimitExceed() throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> body = Map.of("success", false,
                "errors", List.of(Map.of("code", "606", "message", "Max rate limit 100 exceeded with in 20 secs")));
        var response = ResponseEntity.ok().headers(null).body(objectMapper.writeValueAsString(body));
        MarketoRestClient restClient = new MarketoRestClient("mkto");
        try{
            restClient.checkResponse(response);
            fail();
        }catch (QuotaExceededException e){
            assertEquals(ErrorCodes.TOO_MANY_REQUESTS.name(), e.getErrorCode());
            assertEquals("606", e.getStatusCode());
            assertEquals("Max rate limit 100 exceeded with in 20 secs", e.getMessage());
            assertEquals(25, e.getTryInSeconds());
        }
    }

    @Test
    public void checkResponseRequestTimedOut() throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> body = Map.of("success", false,
                "errors", List.of(Map.of("code", "604", "message", "Request Timed Out")));
        var response = ResponseEntity.ok().headers(null).body(objectMapper.writeValueAsString(body));
        MarketoRestClient restClient = new MarketoRestClient();
        try{
            restClient.checkResponse(response);
        }catch (RetriableException e){
            assertEquals(ErrorCodes.TIME_OUT.name(), e.getErrorCode());
            assertEquals("604", e.getStatusCode());
            assertEquals("Request Timed Out", e.getMessage());
        }
    }

    @Test
    public void checkResponseTransientError() throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> body = Map.of("success", false,
                "errors", List.of(Map.of("code", "713", "message", "Transient error. Please retry")));
        var response = ResponseEntity.ok().headers(null).body(objectMapper.writeValueAsString(body));
        MarketoRestClient restClient = new MarketoRestClient();
        try{
            restClient.checkResponse(response);
        }catch (RetriableException e){
            assertEquals(ErrorCodes.API_ERROR.name(), e.getErrorCode());
            assertEquals("713", e.getStatusCode());
            assertEquals("Transient error. Please retry", e.getMessage());
        }
    }

    @Test
    public void checkResponseInvalidToken() throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> body = Map.of("success", false,
                "errors", List.of(Map.of("code", "601", "message", "Invalid Token")));
        var response = ResponseEntity.ok().headers(null).body(objectMapper.writeValueAsString(body));
        MarketoRestClient restClient = new MarketoRestClient();
        try{
            restClient.checkResponse(response);
        }catch (NonRetriableException e){
            assertEquals(ErrorCodes.TOKEN_EXPIRED.name(), e.getErrorCode());
            assertEquals("601", e.getStatusCode());
            assertEquals("Invalid Token", e.getMessage());
        }
    }

    @Test
    public void checkResponseExpiredToken() throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> body = Map.of("success", false,
                "errors", List.of(Map.of("code", "602", "message", "Token Expired")));
        var response = ResponseEntity.ok().headers(null).body(objectMapper.writeValueAsString(body));
        MarketoRestClient restClient = new MarketoRestClient();
        try{
            restClient.checkResponse(response);
        }catch (NonRetriableException e){
            assertEquals(ErrorCodes.TOKEN_EXPIRED.name(), e.getErrorCode());
            assertEquals("602", e.getStatusCode());
            assertEquals("Token Expired", e.getMessage());
        }
    }

    @Test
    public void checkResponseDailyAPILimitExceed() throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> body = Map.of("success", false,
                "errors", List.of(Map.of("code", "607", "message", "Daily quota reached")));
        var response = ResponseEntity.ok().headers(null).body(objectMapper.writeValueAsString(body));
        MarketoRestClient restClient = new MarketoRestClient("mkto");
        try{
            restClient.checkResponse(response);
            fail();
        }catch (QuotaExceededException e){
            assertEquals(ErrorCodes.TOO_MANY_REQUESTS.name(), e.getErrorCode());
            assertEquals("607", e.getStatusCode());
            assertEquals("Daily quota reached", e.getMessage());
            assertTrue(e.getTryInSeconds() > 0 && e.getTryInSeconds() < 24 * 60 * 60 * 1000);
            assertEquals("mkto", e.getConnectorId());
        }
    }

    @Test
    public void checkResponseConcurrentAPIAccessExceed() throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> body = Map.of("success", false,
                "errors", List.of(Map.of("code", "615", "message", "Concurrent access exceed")));
        var response = ResponseEntity.ok().headers(null).body(objectMapper.writeValueAsString(body));
        MarketoRestClient restClient = new MarketoRestClient("mkto");
        try{
            restClient.checkResponse(response);
            fail();
        }catch (QuotaExceededException e){
            assertEquals(ErrorCodes.TOO_MANY_REQUESTS.name(), e.getErrorCode());
            assertEquals("615", e.getStatusCode());
            assertEquals("Concurrent access exceed", e.getMessage());
            assertEquals(25, e.getTryInSeconds());
        }
    }

    @Test
    public void checkResponseUnknownError() throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> body = Map.of("success", false,
                "errors", List.of(Map.of("code", ErrorCodes.UNKNOWN_ERROR.name(), "message", "Unknown Error")));
        var response = ResponseEntity.ok().headers(null).body(objectMapper.writeValueAsString(body));
        MarketoRestClient restClient = new MarketoRestClient();
        try{
            restClient.checkResponse(response);
        }catch (NonRetriableException e){
            assertEquals(ErrorCodes.UNKNOWN_ERROR.name(), e.getErrorCode());
            assertEquals(ErrorCodes.UNKNOWN_ERROR.name(), e.getStatusCode());
            assertEquals("Unknown Error", e.getMessage());
        }
    }

    @Test
    public void getSuccessAfterTokenRefresh(){
        ConnectorInfo connector = getConnector();
        MarketoRestClient mockMarketoRestClient = Mockito.spy(MarketoRestClient.class);
        Supplier<AuthConfig> mockHandler = mock(Supplier.class);
        doThrow(getNonRetriableException())
                .doReturn(List.of(new EntityData("data1")))
                .when(mockMarketoRestClient).get(anyString(), any(AuthConfig.class));

        doReturn(new AuthConfig().setAccessToken("TOKEN2").setRefreshToken("TOKEN2")).when(mockHandler).get();

        assertEquals("TOKEN1", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN1", connector.getAuthConfig().getRefreshToken());

        List<EntityData> data = mockMarketoRestClient.get("someurl", connector, mockHandler);
        assertFalse(data.isEmpty());
        assertEquals("data1", data.get(0).getName());
        assertEquals("TOKEN2", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN2", connector.getAuthConfig().getRefreshToken());

        verify(mockMarketoRestClient, times(2)).get(anyString(), any(AuthConfig.class));
        verify(mockHandler).get();
    }

    private NonRetriableException getNonRetriableException() {
        return new NonRetriableException(ErrorCodes.TOKEN_EXPIRED, "Token Expired", "601");
    }
    
    @Test
    public void postSuccessAfterTokenRefresh() throws JsonProcessingException {
        ConnectorInfo connector = getConnector();
        Supplier<AuthConfig> mockHandler = mock(Supplier.class);
        RestTemplate template = Mockito.spy(RestTemplate.class);
        var response = ResponseEntity.ok().headers(null)
                .body(new ObjectMapper().writeValueAsString(Map.of("success", true)));
        doThrow(getNonRetriableException()).doReturn(response).when(template).exchange(anyString(), any(),
                any(), eq(String.class));
        MarketoRestClient mockMarketoRestClient = getClient(template);

        doReturn(new AuthConfig().setAccessToken("TOKEN2").setRefreshToken("TOKEN2")).when(mockHandler).get();
        assertEquals("TOKEN1", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN1", connector.getAuthConfig().getRefreshToken());

        List<EntityData> data = mockMarketoRestClient.postMultiple("someurl", "", connector, mockHandler);
        assertNotNull(data);
        assertEquals("TOKEN2", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN2", connector.getAuthConfig().getRefreshToken());
        verify(mockHandler).get();
    }
    
    @Test
    public void postMultipleFailureAfterTokenRefresh() throws JsonProcessingException{
        ConnectorInfo connector = getConnector();
        Supplier<AuthConfig> mockHandler = mock(Supplier.class);
        RestTemplate template = Mockito.spy(RestTemplate.class);
        doThrow(getNonRetriableException()).doThrow(getNonRetriableException()).when(template).exchange(anyString(), any(),
                any(), eq(String.class));
        MarketoRestClient mockMarketoRestClient = getClient(template);

        doReturn(new AuthConfig().setAccessToken("TOKEN2").setRefreshToken("TOKEN2")).when(mockHandler).get();
        assertEquals("TOKEN1", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN1", connector.getAuthConfig().getRefreshToken());
        try {
            mockMarketoRestClient.postMultiple("someurl", "", connector, mockHandler);
            fail();
        } catch (NonRetriableException e){
            assertEquals(ErrorCodes.TOKEN_EXPIRED.name(), e.getErrorCode());
            assertEquals("601", e.getStatusCode());
            assertEquals("Token Expired", e.getMessage());
        }

        assertEquals("TOKEN2", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN2", connector.getAuthConfig().getRefreshToken());
        verify(mockHandler).get();
    }
    
    @Test
    public void postProgramSuccessAfterTokenRefresh() throws JsonProcessingException {
        ConnectorInfo connector = getConnector();
        MultiValueMap map = new LinkedMultiValueMap();
        map.add("type", "Default");
        map.add("channel", "Operational");
        Supplier<AuthConfig> mockHandler = mock(Supplier.class);
        RestTemplate template = mock(RestTemplate.class);
        var response = ResponseEntity.ok().headers(null)
                .body(new ObjectMapper().writeValueAsString(Map.of("success", true)));
        doThrow(getNonRetriableException()).doReturn(response).when(template).exchange(anyString(), any(),
                any(), eq(String.class));
        MarketoRestClient mockMarketoRestClient = getClient(template);
        
        doReturn(new AuthConfig().setAccessToken("TOKEN2").setRefreshToken("TOKEN2")).when(mockHandler).get();
        assertEquals("TOKEN1", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN1", connector.getAuthConfig().getRefreshToken());
        
        List<EntityData> data = mockMarketoRestClient.postProgram("someurl", map, connector, mockHandler);
        assertNotNull(data);
        assertEquals("TOKEN2", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN2", connector.getAuthConfig().getRefreshToken());
        verify(mockHandler).get();
    }
    
    @Test
    public void postProgramFailureAfterTokenRefresh() throws JsonProcessingException{
        ConnectorInfo connector = getConnector();
        Supplier<AuthConfig> mockHandler = mock(Supplier.class);
        MultiValueMap map = new LinkedMultiValueMap();
        map.add("type", "Default");
        map.add("channel", "Operational");
        RestTemplate template = Mockito.spy(RestTemplate.class);
        doThrow(getNonRetriableException()).doThrow(getNonRetriableException()).when(template).exchange(anyString(), any(),
                any(), eq(String.class));
        MarketoRestClient mockMarketoRestClient = getClient(template);

        doReturn(new AuthConfig().setAccessToken("TOKEN2").setRefreshToken("TOKEN2")).when(mockHandler).get();
        assertEquals("TOKEN1", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN1", connector.getAuthConfig().getRefreshToken());
        try {
            mockMarketoRestClient.postProgram("someurl", map, connector, mockHandler);
            fail();
        } catch (NonRetriableException e){
            assertEquals(ErrorCodes.TOKEN_EXPIRED.name(), e.getErrorCode());
            assertEquals("601", e.getStatusCode());
            assertEquals("Token Expired", e.getMessage());
        }

        assertEquals("TOKEN2", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN2", connector.getAuthConfig().getRefreshToken());
        verify(mockHandler).get();
    }
    
    @Test
    public void getResponseSuccessAfterTokenRefresh() throws JsonProcessingException {
        ConnectorInfo connector = getConnector();
        MarketoRestClient mockMarketoRestClient = Mockito.spy(MarketoRestClient.class);
        Supplier<AuthConfig> mockHandler = mock(Supplier.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> body = Map.of("success", true);
        var response = ResponseEntity.ok().headers(null).body(objectMapper.writeValueAsString(body));
        doThrow(getNonRetriableException())
        .doReturn(response)
        .when(mockMarketoRestClient).getResponse(anyString(), any(AuthConfig.class), any());
        
        doReturn(new AuthConfig().setAccessToken("TOKEN2").setRefreshToken("TOKEN2")).when(mockHandler).get();
        assertEquals("TOKEN1", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN1", connector.getAuthConfig().getRefreshToken());
        
        ResponseEntity<String> data = mockMarketoRestClient.getResponse("someurl", connector, mockHandler);
        assertNotNull(data);
        assertEquals("TOKEN2", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN2", connector.getAuthConfig().getRefreshToken());
        
        verify(mockMarketoRestClient, times(2)).getResponse(anyString(), any(AuthConfig.class), any());
        verify(mockHandler).get();
    }

    @Test
    public void getFailureAfterTokenRefresh(){
        ConnectorInfo connector = getConnector();
        MarketoRestClient mockMarketoRestClient = Mockito.spy(MarketoRestClient.class);
        Supplier<AuthConfig> mockHandler = mock(Supplier.class);
        doThrow(getNonRetriableException())
                .doThrow(getNonRetriableException())
                .when(mockMarketoRestClient).get(anyString(), any(AuthConfig.class));

        doReturn(new AuthConfig().setAccessToken("TOKEN2").setRefreshToken("TOKEN2")).when(mockHandler).get();
        assertEquals("TOKEN1", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN1", connector.getAuthConfig().getRefreshToken());
        try {
            mockMarketoRestClient.get("someurl", connector, mockHandler);
            fail();
        } catch (NonRetriableException e){
            assertEquals(ErrorCodes.TOKEN_EXPIRED.name(), e.getErrorCode());
            assertEquals("601", e.getStatusCode());
            assertEquals("Token Expired", e.getMessage());
        }

        assertEquals("TOKEN2", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN2", connector.getAuthConfig().getRefreshToken());

        verify(mockMarketoRestClient, times(2)).get(anyString(), any(AuthConfig.class));
        verify(mockHandler).get();
    }

    @Test
    public void getResponseFailureAfterTokenRefresh() {
        ConnectorInfo connector = getConnector();
        MarketoRestClient mockMarketoRestClient = Mockito.spy(MarketoRestClient.class);
        Supplier<AuthConfig> mockHandler = mock(Supplier.class);

        doThrow(getNonRetriableException())
                .doThrow(getNonRetriableException())
                .when(mockMarketoRestClient).getResponse(anyString(), any(AuthConfig.class), any());

        doReturn(new AuthConfig().setAccessToken("TOKEN2").setRefreshToken("TOKEN2")).when(mockHandler).get();

        assertEquals("TOKEN1", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN1", connector.getAuthConfig().getRefreshToken());
        try{
            mockMarketoRestClient.getResponse("someurl", connector, mockHandler);
            fail();
        } catch (NonRetriableException e){
            assertEquals(ErrorCodes.TOKEN_EXPIRED.name(), e.getErrorCode());
            assertEquals("601", e.getStatusCode());
            assertEquals("Token Expired", e.getMessage());
        }
        assertEquals("TOKEN2", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN2", connector.getAuthConfig().getRefreshToken());

        verify(mockMarketoRestClient, times(2)).getResponse(anyString(), any(AuthConfig.class), any());
        verify(mockHandler).get();
    }

    private ConnectorInfo getConnector(){
        ConnectorInfo connector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setClientId("CLIENT_ID");
        authConfig.setClientSecret("CLIENT_SECRET");
        authConfig.setConsumerKey("USER_ID");
        authConfig.setConsumerSecret("ENCRYPTION_KEY");
        authConfig.setAccessToken("TOKEN1");
        authConfig.setRefreshToken("TOKEN1");
        connector.setAuthConfig(authConfig);
        connector.getMetaConfig().put("munchkin", "MUNCHKIN");

        connector.setId("mkto");
        return connector;
    }
    
    private MarketoRestClient getClient(RestTemplate template) {
        MarketoRestClient mockMarketoRestClient = new MarketoRestClient() {
            @Override
            public RestTemplate getTemplate() {
                return template;
            }  
            @Override
            public List<EntityData> getBatchResponse(ResponseEntity<String> response) {
                return List.of(new EntityData("data1"));
            }
        };
        return mockMarketoRestClient;
    }

    @Test
    public void testValidateFileContent() throws Exception {
        MarketoRestClient client = new MarketoRestClient();
        Storage mockStorage = mock(Storage.class);
        String filePath = "test.csv";

        // Test 1: Valid CSV file
        String validCsv = "name,email\nJohn,john@example.com\nJane,jane@example.com";
        doAnswer(invocation -> new java.io.ByteArrayInputStream(validCsv.getBytes()))
            .when(mockStorage).read(filePath);

        // Use reflection to access private method
        Method validateMethod = MarketoRestClient.class.getDeclaredMethod("validateFileContent", Storage.class, String.class);
        validateMethod.setAccessible(true);

        assertTrue("Valid CSV should pass validation", (Boolean) validateMethod.invoke(client, mockStorage, filePath));

        // Test 2: CSV with rate limit error content
        String rateLimitCsv = "error,message\nrate limit,exceeded\napi limit exceeded,try again";
        doAnswer(invocation -> new java.io.ByteArrayInputStream(rateLimitCsv.getBytes()))
            .when(mockStorage).read(filePath);

        assertFalse("CSV with rate limit error should fail validation", (Boolean) validateMethod.invoke(client, mockStorage, filePath));

        // Test 3: CSV with "too many requests" error
        String tooManyRequestsCsv = "status,error\nfailed,too many requests";
        doAnswer(invocation -> new java.io.ByteArrayInputStream(tooManyRequestsCsv.getBytes()))
            .when(mockStorage).read(filePath);

        assertFalse("CSV with 'too many requests' should fail validation", (Boolean) validateMethod.invoke(client, mockStorage, filePath));

        // Test 4: Empty/null file
        doReturn(null).when(mockStorage).read(filePath);

        assertFalse("Null file should fail validation", (Boolean) validateMethod.invoke(client, mockStorage, filePath));
    }
}
