package com.syncari.karibu.rest.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.syncari.connector.Constants;
import com.syncari.connector.data.AuthType;
import com.syncari.core.model.Connector;
import com.syncari.core.service.ConnectorService;
import com.syncari.karibu.rest.request.SynapseRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SynapseTestUtil {

    @Autowired
    ConnectorService connectorService;

    public String getSalesforceSynapseRequestString (String synapseName) throws Exception {
        SynapseRequest synapseRequest = new SynapseRequest();
        synapseRequest.setName(synapseName);
        Map<String, Object> configuration = new HashMap<String, Object>();
        //Salesforce
        synapseRequest.setSynapseTypeId(connectorService.describe("salesforce").getId());
        configuration.put("endpoint", "https://syncariinc--unittests.sandbox.my.salesforce.com");
        configuration.put("userName", "mary+pbo@syncari.com.unittests");
        configuration.put("password", System.getenv().getOrDefault("TEST_SF_PASSWORD", "REPLACE_ME"));
        configuration.put("token", System.getenv().getOrDefault("TEST_SF_TOKEN", "REPLACE_ME"));
        configuration.put("authType", "UserPasswordToken");
        synapseRequest.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(synapseRequest);
    }

    public String getSalesforceOauthSynapseRequestString (String synapseName) throws Exception {
        SynapseRequest synapseRequest = new SynapseRequest();
        synapseRequest.setName(synapseName);
        Map<String, Object> configuration = new HashMap<String, Object>();
        //Salesforce
        synapseRequest.setSynapseTypeId(connectorService.describe("salesforce").getId());
        configuration.put("endpoint", "https://syncariinc--unittests.sandbox.my.salesforce.com");
        configuration.put("clientId", System.getenv().getOrDefault("TEST_SF_OAUTH_CLIENT_ID", "REPLACE_ME"));
        configuration.put("clientSecret", System.getenv().getOrDefault("TEST_SF_OAUTH_CLIENT_SECRET", "REPLACE_ME"));
        configuration.put("accessToken", System.getenv().getOrDefault("TEST_SF_OAUTH_ACCESS_TOKEN", "REPLACE_ME"));
        configuration.put("refreshToken", System.getenv().getOrDefault("TEST_SF_OAUTH_REFRESH_TOKEN", "REPLACE_ME"));
        configuration.put("authType", "Oauth");
        synapseRequest.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(synapseRequest);
    }

    public String getHubspotSynapseRequestString (String synapseName) throws Exception {
        SynapseRequest synapseRequest = new SynapseRequest();
        synapseRequest.setName(synapseName);
        Map<String, Object> configuration = new HashMap<String, Object>();
        //Salesforce
        synapseRequest.setSynapseTypeId(connectorService.describe("hubspot").getId());
        configuration.put("clientId", "hubspotClientId");
        configuration.put("clientSecret", "hubspotSecretId");
        configuration.put("accessToken", "hubspotAccessToken");
        configuration.put("refreshToken", "hubspotRefreshToken");
        configuration.put("oAuthRedirectUrl", "http://localhost:3000/oauth/authorize?client_id=a5dd557c-6967-4f23-8589-ae624c6d32c0");
        configuration.put("portalId", "123456");
        configuration.put("authType", "Oauth");
        synapseRequest.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(synapseRequest);
    }

    public String getHubspotSynapseUpdateRequestString () throws Exception {
        SynapseRequest synapseRequest = new SynapseRequest();
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("clientId", "hubspotClientIdUpdate");
        configuration.put("clientSecret", "hubspotSecretIdUpdate");
        configuration.put("accessToken", "hubspotAccessTokenUpdate");
        configuration.put("refreshToken", "hubspotRefreshTokenUpdate");
        synapseRequest.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(synapseRequest);
    }

    public String getImpartnerSynapseRequestString (String synapseName) throws Exception {
        SynapseRequest synapseRequest = new SynapseRequest();
        synapseRequest.setName(synapseName);
        Map<String, Object> configuration = new HashMap<String, Object>();
        //impartner
        synapseRequest.setSynapseTypeId(connectorService.describe("impartner").getId());
        configuration.put("endpoint", "https://prod.impartner.live");
        configuration.put("userName", "eng@syncari.com.dev");
        configuration.put("password", System.getenv().getOrDefault("TEST_IMPARTNER_PASSWORD", "REPLACE_ME"));
        configuration.put("authType", "UserPassword");
        configuration.put("timeZoneId", "America/Los_Angeles");
        synapseRequest.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(synapseRequest );
    }

    public String getMarketoSynapseRequestString (String synapseName) throws Exception {
        SynapseRequest synapseRequest = new SynapseRequest();
        synapseRequest.setName(synapseName);
        Map<String, Object> configuration = new HashMap<String, Object>();
        // Marketo
        synapseRequest.setSynapseTypeId(connectorService.describe("marketo").getId());
        configuration.put("clientId", System.getenv().getOrDefault("TEST_MARKETO_CLIENT_ID", "REPLACE_ME"));
        configuration.put("clientSecret", System.getenv().getOrDefault("TEST_MARKETO_CLIENT_SECRET", "REPLACE_ME"));
        configuration.put("munchkin", "183-LYQ-451");
        configuration.put("staticListId", "*");
        configuration.put("authType", "SimpleOAuth");
        synapseRequest.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(synapseRequest );
    }

    public String getGoogleSheetSynapseRequestString (String synapseName) throws Exception {
        SynapseRequest synapseRequest = new SynapseRequest();
        synapseRequest.setName(synapseName);
        Map<String, Object> configuration = new HashMap<String, Object>();
        // Marketo
        synapseRequest.setSynapseTypeId(connectorService.describe(Constants.GOOGLESHEETS).getId());
        configuration.put("clientId", System.getenv().getOrDefault("TEST_GSHEETS_CLIENT_ID", "REPLACE_ME"));
        configuration.put("clientSecret", System.getenv().getOrDefault("TEST_GSHEETS_CLIENT_SECRET", "REPLACE_ME"));
        configuration.put("refreshToken", System.getenv().getOrDefault("TEST_GSHEETS_REFRESH_TOKEN", "REPLACE_ME"));
        //configuration.put("redirectUri", "http://localhost:3000/oauth/authorize?guid=5e969c480698500ea92bdcd7");
        configuration.put("folderId", System.getenv().getOrDefault("TEST_GSHEETS_FOLDER_ID", "REPLACE_ME"));
        configuration.put("authType", "Oauth");
        synapseRequest.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(synapseRequest);
    }

    public String getGoogleSheetSynapseUpdateRequestString_INVALID () throws Exception {
        SynapseRequest synapseRequest = new SynapseRequest();
        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put("clientSecret", "gsheetSecretIdUpdate");
        configuration.put("accessToken", "gsheetAccessTokenUpdate");
        configuration.put("refreshToken", "gsheetRefreshTokenUpdate");
        synapseRequest.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(synapseRequest);
    }

    public String getBadSalesforceSynapseRequestString (String synapseName, String badData) throws Exception {
        SynapseRequest synapseRequest = new SynapseRequest();
        synapseRequest.setName(synapseName);
        Map<String, Object> configuration = new HashMap<String, Object>();
        //Salesforce
        if (badData.equals("synapseTypeId")) {
            synapseRequest.setSynapseTypeId("badId");
        } else {
            synapseRequest.setSynapseTypeId(connectorService.describe("salesforce").getId());
        }
        if (badData.equals("endpoint")) {
        } else {
            configuration.put("endpoint", "https://syncariinc--unittests.sandbox.my.salesforce.com");
        }
        if (badData.equals("userName")) {
        } else {
            configuration.put("userName", "eng@syncari.com.dev");
        }
        if (badData.equals("password")) {
        } else {
            configuration.put("password", System.getenv().getOrDefault("TEST_BAD_SF_PASSWORD", "REPLACE_ME"));
        }
        if (badData.equals("token")) {
        } else {
            configuration.put("token", System.getenv().getOrDefault("TEST_BAD_SF_TOKEN", "REPLACE_ME"));
        }
        if (badData.equals("authType")) {
        } else {
            configuration.put("authType", "UserPasswordToken");
        }
        if (badData.equals("timeZoneId")) {
            configuration.put("timeZoneId", "America/Los_Angeles");
        }

        synapseRequest.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(synapseRequest);
    }


    public String getUpdateSalesforceSynapseRequestString (String synapseName) throws Exception {
        SynapseRequest synapseRequest = new SynapseRequest();
        synapseRequest.setName(synapseName);
        Map<String, Object> configuration = new HashMap<String, Object>();
        //Salesforce
        configuration.put("endpoint", "https://syncariinc--unittests-updated.sandbox.my.salesforce.com");
        configuration.put("userName", "mary+pbo@syncari.com.unittests");
        synapseRequest.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(synapseRequest);
    }

    public String getUpdateMarketoSynapseRequestString (String synapseName) throws Exception {
        SynapseRequest synapseRequest = new SynapseRequest();
        synapseRequest.setName(synapseName);
        Map<String, Object> configuration = new HashMap<String, Object>();
        // Marketo
        synapseRequest.setSynapseTypeId(connectorService.describe("marketo").getId());
        configuration.put("clientId", "newClientId");
        configuration.put("clientSecret", "newClietSecret");
        configuration.put("munchkin", "183-LYQ-451");
        synapseRequest.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(synapseRequest);
    }

    public String getUpdateImpartnerSynapseRequestString (String synapseName) throws Exception {
        SynapseRequest synapseRequest = new SynapseRequest();
        synapseRequest.setName(synapseName);
        Map<String, Object> configuration = new HashMap<String, Object>();
        //impartner
        synapseRequest.setSynapseTypeId(connectorService.describe("impartner").getId());
        configuration.put("accessToken", "123456789");
        configuration.put("authType", "ApiKey");
        configuration.put("endpoint", "https://prod.impartner.live");
        configuration.put("timeZoneId", "America/Los_Angeles");
        synapseRequest.setConfiguration(configuration);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(synapseRequest );
    }

    public Connector getActiveMarketoConnector() {
        Connector connector = new Connector("mkto1", connectorService.describe("marketo"), null, null, null);
        connector.getMetaConfig().put("munchkin", "183-LYQ-451");
        connector.setAuthType(AuthType.SimpleOAuth);
        connector.getAuthConfig().setClientId(System.getenv().getOrDefault("TEST_MARKETO_CLIENT_ID", "REPLACE_ME"));
        connector.getAuthConfig().setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
        connector.getAuthConfig().setConsumerKey("MKTOWS_183-LYQ-451_1");
        connector.getAuthConfig().setConsumerSecret(System.getenv().getOrDefault("TEST_MARKETO_CONSUMER_SECRET", "REPLACE_ME"));
        connector = connectorService.save(connector);
        connectorService.testConnection(connector.getId());
        connectorService.activate(connector.getId());

        return connector;
    }
}
