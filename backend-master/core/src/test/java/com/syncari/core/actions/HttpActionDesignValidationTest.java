package com.syncari.core.actions;

import com.syncari.connector.ConnectorType;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.actions.http.AuthenticationInfo;
import com.syncari.core.actions.http.HTTPAction;
import com.syncari.core.actions.http.HttpActionProperties;
import static org.junit.Assert.*;

import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.FunctionConfiguration;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpActionDesignValidationTest extends AbstractSyncariTest {

    @Autowired
    private HTTPAction httpAction;

    @Test
    public void testInvalidEndpoint() {
        var actionDefinition = new CustomActionDefinition();
        String body = "";
        String endpoint = "";
        Map<String, String> headers = Map.of();
        HttpActionProperties actionProperties = new HttpActionProperties().setEndPoint(endpoint).setBody(body);
        actionProperties.setMethod(HttpMethod.POST).setBody("").setEndPoint(endpoint).setHeaders(headers).setAuthenticationInfo(new AuthenticationInfo());
        actionDefinition.setProperties(actionProperties);
        try {
            httpAction.validate(actionDefinition);
            fail();
        } catch (Exception e) {
            assertEquals("Invalid 'Endpoint' in Action Configuration", e.getMessage());
        }

/*
        endpoint = "http:/www.example.com";
        actionProperties.setEndPoint(endpoint);
        try {
            httpAction.validate(actionDefinition);
            fail();
        } catch (Exception e) {
            assertEquals("Invalid 'Endpoint' in Action Configuration", e.getMessage());
        }

        endpoint = "http:/www.example.om";
        actionProperties.setEndPoint(endpoint);
        try {
            httpAction.validate(actionDefinition);
            fail();
        } catch (Exception e) {
            assertEquals("Invalid 'Endpoint' in Action Configuration", e.getMessage());
        }

        endpoint = "htp://www.example.com";
        actionProperties.setEndPoint(endpoint);
        try {
            httpAction.validate(actionDefinition);
            fail();
        } catch (Exception e) {
            assertEquals("Invalid 'Endpoint' in Action Configuration", e.getMessage());
        }
*/
        endpoint = "http://www.example.com";
        actionProperties.setEndPoint(endpoint);
        httpAction.validate(actionDefinition);
    }

    @Test
    public void testInvalidVariables() {
        var actionDefinition = new CustomActionDefinition();
        String endpoint = "http://www.example.com/";
        String body = "";
        Map<String, String> headers = Map.of();
        HttpActionProperties actionProperties = new HttpActionProperties().setEndPoint(endpoint).setBody(body);
        actionProperties.setMethod(HttpMethod.POST).setBody("").setEndPoint(endpoint).setHeaders(headers).setAuthenticationInfo(new AuthenticationInfo());
        actionDefinition.setProperties(actionProperties);

        var variables = List.of(new FunctionConfiguration());
        actionDefinition.setConfiguration(variables);

        try {
            httpAction.validate(actionDefinition);
            fail();
        } catch (Exception e) {
            assertEquals("Variable(s) configured without valid Name.", e.getMessage());
        }

        variables = List.of(
                new FunctionConfiguration().setName("name1").setLabel("Name 1").setRequired(true).setDatatype(StringType.VALUE),
                new FunctionConfiguration().setName("name2").setLabel("Name 2").setRequired(true).setDatatype(StringType.VALUE),
                new FunctionConfiguration().setName("name1").setLabel("Name 3").setRequired(true).setDatatype(StringType.VALUE)
        );
        actionDefinition.setConfiguration(variables);

        try {
            httpAction.validate(actionDefinition);
            fail();
        } catch (Exception e) {
            assertEquals("Duplicate variable names found name1", e.getMessage());
        }
    }
    @Test
    public void testInvalidHostName(){

        List<String> endpoints = List.of(
        		"https://192.168.23.1/lookup",
        		"https://syncari.net/lookup",
        		"https://metadata.google.internal/lookup",
        		"https://google.internal/lookup"
        		);


        ActionDefinition actionDefinition = new ActionDefinition();
        HttpActionProperties properties = new HttpActionProperties();
        properties.setMethod(HttpMethod.POST).setBody("").setHeaders(Map.of()).setAuthenticationInfo(new AuthenticationInfo());
        for(String url : endpoints){
            properties.setEndPoint(url);
            actionDefinition.setProperties(properties);
            try{
                httpAction.validate(actionDefinition);
                fail();
            }catch (SyncariValidationException e){

            }
        }
    }
    
    @Test
    public void testInvalidScheme(){

        List<String> endpoints = List.of(
        		"file://example.com/path",
        		"gopher://example.com/0a_gopher_selector"
        		);


        ActionDefinition actionDefinition = new ActionDefinition();
        HttpActionProperties properties = new HttpActionProperties();
        properties.setMethod(HttpMethod.POST).setBody("").setHeaders(Map.of()).setAuthenticationInfo(new AuthenticationInfo());
        for(String url : endpoints){
            properties.setEndPoint(url);
            actionDefinition.setProperties(properties);
            try{
                httpAction.validate(actionDefinition);
                fail();
            }catch (SyncariValidationException e){

            }
        }
    }

    @Test(expected = Test.None.class)
    public void testUrl(){
        List<String> endpoint = new ArrayList<>();
        endpoint.add("google.com");
        endpoint.add("https://api.hubapi.com/contacts/v1/lists/{cid}/add");
        endpoint.add("https://catfact.ninja/fact/{% set size = \"1,2,3,4,5\" %}{% for i in size.split(\",\") %}{{i}}{% endfor %}");

        ActionDefinition actionDefinition = new ActionDefinition();
        HttpActionProperties properties = new HttpActionProperties();
        for(String url : endpoint){
            properties.setEndPoint(url);
            actionDefinition.setProperties(properties);
            try{
                httpAction.validate(actionDefinition);
            }catch (Exception e){

            }
        }
    }
    
	@Test
	public void testInvalidSynapseCredentials() {

		String endpoint = "https://example.com/path";

		ActionDefinition actionDefinition = new ActionDefinition();
		HttpActionProperties properties = new HttpActionProperties();
		properties.setMethod(HttpMethod.POST).setBody("").setHeaders(Map.of())
				.setAuthenticationInfo(new AuthenticationInfo().setCredentialType(ConnectorType.Synapse).setCredentialId("invalid"));
		properties.setEndPoint(endpoint);
		actionDefinition.setProperties(properties);
		try {
			httpAction.validate(actionDefinition);
			fail();
		} catch (SyncariValidationException e) {
			assertEquals("Invalid 'Credential' in Action Configuration", e.getMessage());
		}
	}
	
	@Test
    public void testTokensInBody() {

        String endpoint = "https://example.com/path";
        String bodyLeading = "{{ test1}}";
        String bodyTrailing = "{{test2 }}";
        String bodyBoth = "{{ test3 }}";
        String bodyValid = "{{test}}";

        ActionDefinition actionDefinition = new ActionDefinition();
        HttpActionProperties properties = new HttpActionProperties();
        properties.setMethod(HttpMethod.POST).setHeaders(Map.of()).setAuthenticationInfo(new AuthenticationInfo());
        properties.setEndPoint(endpoint);
        actionDefinition.setProperties(properties);
        try {
            properties.setBody(bodyLeading);
            httpAction.validate(actionDefinition);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals("Invalid token(s) [ test1]. The token(s) contain leading or trailing whitespace", e.getMessage());
        }
        try {
          properties.setBody(bodyTrailing);
          httpAction.validate(actionDefinition);
          fail();
        } catch (SyncariValidationException e) {
            assertEquals("Invalid token(s) [test2 ]. The token(s) contain leading or trailing whitespace", e.getMessage());
        }
        try {
          properties.setBody(bodyBoth);
          httpAction.validate(actionDefinition);
          fail();
        } catch (SyncariValidationException e) {
            assertEquals("Invalid token(s) [ test3 ]. The token(s) contain leading or trailing whitespace", e.getMessage());
        }
        properties.setBody(bodyValid);
        httpAction.validate(actionDefinition);
    }

}
