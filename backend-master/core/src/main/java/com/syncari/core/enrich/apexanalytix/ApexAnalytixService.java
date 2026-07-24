package com.syncari.core.enrich.apexanalytix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.enrich.AbstractEnrichmentService;
import com.syncari.core.model.EnrichmentCache;
import com.syncari.core.model.LookupData;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.service.LookupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@Component(Constants.APEX_ANALYTIX)
public class ApexAnalytixService extends AbstractEnrichmentService implements LookupService, OauthAuthenticationService, SynapseInfoService {
    // Doc : https://api.smartvmapi.com/#/login
    protected SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient();
    protected ObjectMapper mapper = new ObjectMapper();

    private static final String AUTH_HEADER = "apiKey";
    private static final String BASE_URL = "https://api.smartvmapi.com/api";
    private static final String COMPANY_PATH = BASE_URL + "/companylistlookup/lookup";
    private static final String COMPANY = "company";
    private static final List<String> SUPPORTED_ENTITIES = List.of(COMPANY);
    private static final Map<String, String> URL_PATH = Map.of(COMPANY, COMPANY_PATH);
    @Autowired
    protected ObjectMapper objectMapper;

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo connectorInfo, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try{
            SearchCriteria criteria = new SearchCriteria();
            Map<String, Object> searchFieldNameValues = new HashMap<>();
            searchFieldNameValues.put("companyName", "Apex Analytix");
            searchFieldNameValues.put("country", "USA");
            criteria.setSearchFieldNameValues(searchFieldNameValues);
            lookupEntity(connectorInfo, criteria, COMPANY);
            log.info(format("Successfully authenticated ApexAnalytix connection for %s", connectorInfo.getName()));
            return response;
        } catch (Exception e) {
            log.error("ApexAnalytix Authentication failed {}", e.getMessage());
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        throw new NotSupportedException("Access token is not needed for this service");
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        throw new NotSupportedException("Refresh token is not needed for this service");
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        throw new NotSupportedException("Not supported for this service");
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getApiKey());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return null;
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
        return Constants.APEX_ANALYTIX;
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
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/apexanalytix.svg")
                .setDisplayName("ApexAnalytix")
                .setBackgroundColor("#F8F8F8")
                .setHelpUrl(helpArticlesBaseUrl + SYNAPSE_COMING_SOON_ARTICLE);
    }

    @Override
    public LookupData lookup(ConnectorInfo connector, SearchCriteria criteria) {
        validateInput(criteria);
        String entityName = criteria.getMetaFilters().get(LOOKUP_ENTITY).toString();
        assert (null != entityName);
        return lookupEntity(connector, criteria, entityName);
    }

    @Override
    protected List<String> getSupportedEntities() {
        return SUPPORTED_ENTITIES;
    }

    private LookupData lookupEntity(ConnectorInfo connector, SearchCriteria criteria, String entity) {
        criteria.getSearchFieldNameValues().put("requestedByClient", "Syncari");
        return ConnectorHelper.withHttpErrorHandling(() -> {
            Optional<EnrichmentCache> cached = getCachedValue(connector.getId(), entity, criteria.getSearchFieldNameValues().toString());
            String jsonResponse;
            if(cached.isPresent()){
                jsonResponse = cached.get().getEnrichValue();
            } else {
                log.info("Enriching {} information for Search Criteria {}", entity, criteria.toString());
                HttpHeaders headers = getHeaders(connector.getAuthConfig());
                ResponseEntity<String> response = restClient.post(headers, URL_PATH.get(entity), criteria.getSearchFieldNameValues());
                jsonResponse = response.getBody();
                validateResponse(response, entity, criteria);
                saveToCache(connector.getId(), entity, criteria.getSearchFieldNameValues().toString(), jsonResponse);
            }

            List<Map<String, Object>> data = mapper.readValue(jsonResponse, List.class);
            LookupData result = new LookupData();
            result.setLookupEntityName(entity);
            if(!data.isEmpty()) {
                result.setValues(data.get(0));
            }
            return result;
        });
    }

    @Override
    public EntitySchema describe(DescribeRequest request) {
        return Optional.of(ApexAnalytixSeed.getEntity(request.getEntity())).get();
    }

    @Override
    public Map<String, String> getInputFields(ConnectorInfo connectorInfo, String entityName) {
       throw new UnsupportedOperationException("Implementation of this is not required");
    }

    @Override
    public Map<String, String> getOutputFields(ConnectorInfo connectorInfo, String entityName) {
        EntitySchema schema = describe(new DescribeRequest(connectorInfo, entityName));
        return schema.getAttributes().stream().filter(a -> !a.isSystem())
                .collect(Collectors.toMap(a -> a.getApiName(), a -> a.getDisplayName()));
    }

    protected HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        headers.set(AUTH_HEADER, authConf.getToken());
        return headers;
    }
}

