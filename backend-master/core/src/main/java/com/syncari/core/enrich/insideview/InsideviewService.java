package com.syncari.core.enrich.insideview;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import com.syncari.connector.DefaultAuthTokenHandler;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.OAuthRequest;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.UIMetadata;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.enrich.AbstractEnrichmentService;
import com.syncari.core.model.EnrichmentCache;
import com.syncari.core.model.LookupData;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.service.LookupService;
import com.syncari.core.utils.EnrichUtil;
import com.syncari.utils.I18n;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.INSIDEVIEW)
public class InsideviewService extends AbstractEnrichmentService implements LookupService, OauthAuthenticationService, SynapseInfoService {

    public static final String OAUTH_URL = "https://login.insideview.com/Auth/login/v1/token.json";
    private static final String ENRICH_URL = "https://api.insideview.com/api/v1/enrich";
    
    @Autowired
    DefaultAuthTokenHandler tokenHandler;

    @Autowired
    ObjectMapper mapper;

    Map<String, InsideviewRestClient> clientByConnector = new HashMap<>();

    public InsideviewRestClient getRestClient(ConnectorInfo connector) {
        if (!clientByConnector.containsKey(connector.getId())) {
            JsonParserConfig parserConfig = new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
            clientByConnector.put(connector.getId(),
                new InsideviewRestClient(connector.getAuthConfig().getAccessToken(), parserConfig, mapper));
        }
        return clientByConnector.get(connector.getId());
    }
    
    @Override
    public TestConnectionResponse testConnection(ConnectorInfo connector, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            SearchCriteria searchCriteria = new SearchCriteria();
            searchCriteria.setMetaFilters(Map.of("lookupEntity", "company", "lookupField", "websites"));
            searchCriteria.and("companyName", "syncari");
            LookupData data = lookup(connector, searchCriteria);
            return response;
        } catch (Exception e) {
            log.error(I18n.i18n("insideview_auth_failure") + " due to {} ", e.getMessage(), e);
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getSimpleOAuthType());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public String getName() {
        return Constants.INSIDEVIEW;
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/insideview.svg")
                .setDisplayName("Insideview")
                .setBackgroundColor("#F5F9FF")
                .setHelpUrl(helpArticlesBaseUrl + "/");
    }

    @Override
    public String getCategory() {
        return "EnrichService";
    }

    @Override
    public ConnectorType getType() {
        return ConnectorType.Enrich;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        return getAccessToken(oAuthRequest.getConfig());
    }

    // Insideview has specific way to get accessToken details. 
    // Hence we have specific implementation here.
    private AuthConfig getAccessToken(AuthConfig authConfig) {
        try {
            JsonParserConfig parserConfig = new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
            InsideviewRestClient client = new InsideviewRestClient("", parserConfig, mapper);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(OAUTH_URL);
            uriBuilder.queryParam("clientId", authConfig.getClientId());
            uriBuilder.queryParam("clientSecret", authConfig.getClientSecret());
            uriBuilder.queryParam("grantType", "cred");
            log.info(uriBuilder.build().toUri().toString());
            ResponseEntity<String> resp = client.postWithoutBody(uriBuilder.build().toUri());

            if (resp.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Invalid response code " + resp.getStatusCode() + " from access token endpoint ");
            }
            Map<String, Object> respJson = mapper.readValue(resp.getBody(), Map.class);
            AuthConfig token = new AuthConfig();
            Map<String, Object> tokenVals = (Map) respJson.get("accessTokenDetails");
            if (tokenVals.containsKey("accessToken")) {
                token.setAccessToken(tokenVals.get("accessToken").toString());
            }
            if (tokenVals.containsKey("expirationTime")) {
                // Convert the expiresin to epoch time based on the string format datetime value.
                SimpleDateFormat df = new SimpleDateFormat("EEE, MMM dd, yyyy HH:mm:ss aaa zzz");
                Date date = df.parse(tokenVals.get("expirationTime").toString());
                token.setExpiresIn(String.valueOf(date.getTime()));
            }
            return token;
        } catch (Exception e) {
            String errorMsg = String.format(I18n.i18n("insideview_accesstoken_failure") + " due to %s ", e.getMessage());
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        return getAccessToken(connector.getAuthConfig());
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "?clientId={{client_id}}&clientSecret={{client_secret}}&grantType=cred";
    }

    @Override
    public LookupData lookup(ConnectorInfo connector, SearchCriteria criteria) {
        validateInput(criteria);
        String entityName = criteria.getMetaFilters().get(LOOKUP_ENTITY).toString();
        String lookupField = criteria.getMetaFilters().get(LOOKUP_FIELD).toString();
        return ConnectorHelper.withHttpErrorHandling(() -> {
            Optional<EnrichmentCache> cached = getCachedValue(connector.getId(), entityName, criteria.getSearchFieldNameValues().toString());
            String jsonResponse;
            if(cached.isPresent()){
                jsonResponse = cached.get().getEnrichValue();
            } else {
                log.info("Enriching {} information for Search Criteria {}", entityName, criteria.toString());

                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(ENRICH_URL);
                AuthConfig authConfig = connector.getAuthConfig();
                authConfig.addHeader("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE);
                
                MultiValueMap<String, String> bodyParams = new LinkedMultiValueMap<String, String>();
                for (Map.Entry<String, Object> entry : criteria.getSearchFieldNameValues().entrySet()) {
                    bodyParams.add(entry.getKey(), entry.getValue().toString());
                }
                URI value = uriBuilder.build().toUri();
                InsideviewRestClient client = getRestClient(connector);
                ResponseEntity<String> resp = client.postFormDataURI(value, bodyParams, authConfig);
                
                log.info("POST: HTTP Status {}",resp.getStatusCode());
                log.debug("Response body: {}", resp.getBody());
                validateResponse(resp, entityName, criteria);
                
                jsonResponse = resp.getBody();
                saveToCache(connector.getId(), entityName, criteria.getSearchFieldNameValues().toString(), jsonResponse);
            }

            Map<String, Object> respObject = mapper.readValue(jsonResponse, Map.class);
            LookupData selectedResult = extractValue(entityName, lookupField, respObject);
            if(selectedResult.getValueAsString("error") != null){
                log.error("Unable to Enrich {} for Search Criteria {}. ErrorMsg: {}",
                        entityName, criteria.toString(), selectedResult.getValueAsString("error"));
                return null;
            }
            selectedResult.setLookupEntityName(entityName);
            return selectedResult;
        });
    }

    protected void validateInput(SearchCriteria criteria) {
        super.validateInput(criteria);
    }

    private LookupData extractValue(String entityName, String lookupField, Map<String, Object> data) {
        LookupData selectedResult = new LookupData();
        String responsePathField = getResponsePathForField(entityName, lookupField);
        if (StringUtils.isEmpty(responsePathField)) {
            throw new RuntimeException(String.format("Error in Enriching data. Invalid lookupField %s for entity %s", lookupField, entityName));
        }
        // This special value is always on the top level.
        if ("confidenceScore".equalsIgnoreCase(lookupField)) {
            selectedResult.addValue(lookupField, data.get("confidenceScore"));
            return selectedResult;
        }
        String absoluteFieldPath = (!responsePathField.contains(".")) ? entityName + "." + responsePathField : responsePathField;
        selectedResult.addValue(lookupField, EnrichUtil.findInResponseBody(absoluteFieldPath, data));
        return selectedResult;
    }

    private String getResponsePathForField(String entity, String fieldName){
        return InsideviewSeed.getAttributeToResponseMapping(entity).get(fieldName);
    }

    @Override
    public EntitySchema describe(DescribeRequest request) {
        return Optional.of(InsideviewSeed.getEntity(request.getEntity())).get();
    }

    @Override
    public Map<String, String> getInputFields(ConnectorInfo connectorInfo, String entityName) {
        // seeding the input fields manually for insideview
        switch (entityName) {
            case "contact":
                return Map.of("email", "Email");
            case "company":
                return Map.of("companyName", "Company Name", "url", "Company Website");
            default:
                throw new RuntimeException("Entity %s not supported by Insideview");
        }
    }

    @Override
    public Map<String, String> getOutputFields(ConnectorInfo connector, String entityName) {
        EntitySchema schema = describe(new DescribeRequest(connector, entityName));
        return schema.getAttributes().stream().filter(a -> !a.isSystem())
                .collect(Collectors.toMap(a -> a.getApiName(), a -> a.getDisplayName()));
    }

}
