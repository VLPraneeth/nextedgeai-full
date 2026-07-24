package com.syncari.core.enrich.salesintel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
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
import com.syncari.core.repositories.customer.EnrichmentCacheRepo;
import com.syncari.core.service.LookupService;
import com.syncari.core.service.ProvisioningService;
import com.syncari.core.utils.EnrichUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component(Constants.SALESINTEL)
public class SalesIntelService extends AbstractEnrichmentService implements LookupService, OauthAuthenticationService, SynapseInfoService {
    protected SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient();
    protected ObjectMapper mapper = new ObjectMapper();

    private static final String AUTH_HEADER = "X-CB-ApiKey";
    private static final String BASE_URL = "https://api.circleback.com";
    private static final String WHO_AM_I = "/account/whoami";
    private static final String COMPANY_PATH = BASE_URL + "/service/company?page=1&page_size=10";
    private static final String PERSON_URL = BASE_URL + "/service/people?verified=true&page=1&page_size=10";
    private static final int WAIT_TIMEOUT_MILLIS = 5000;

    private static final List<String> SOCIAL_PROFILES = List.of("linkedin","facebook","twitter");
    private static final List<String> Codes = List.of("naicsCodes","sicCodes");


    @Override
    public TestConnectionResponse testConnection(ConnectorInfo connectorInfo, List<String> entityNames) {
        String apiKey = connectorInfo.getAuthConfig().getToken();
        String url = BASE_URL+WHO_AM_I;
        try {
            AuthConfig authConfig = new AuthConfig();
            authConfig.addHeader(AUTH_HEADER, apiKey);
            ResponseEntity<String> response = restClient.getResponse(url, authConfig);
            if (response.getStatusCodeValue() == HttpStatus.OK.value()){
                return new TestConnectionResponse();
            }else {
                return new TestConnectionResponse(response.getBody(), "ERROR", List.of());
            }
        } catch (Exception e) {
            log.error("SalesIntel Authentication failed. Error: {}", e.getMessage());
            return new TestConnectionResponse(e.getMessage(), ConnectorErrorCodes.CONNECTION_ERROR, List.of());
        }
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
        return Constants.SALESINTEL;
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
        return new UIMetadata().setIconPath("/assets/icons/logos/salesintel.svg")
                .setDisplayName("SalesIntel")
                .setBackgroundColor("#F8F8F8")
                .setHelpUrl(helpArticlesBaseUrl + SYNAPSE_COMING_SOON_ARTICLE);
    }

    @Override
    public LookupData lookup(ConnectorInfo connector, SearchCriteria criteria) {
        validateInput(criteria);
        String entityName = criteria.getMetaFilters().get(LOOKUP_ENTITY).toString();
        assert (null != entityName);
        assert ((entityName.equalsIgnoreCase("Company") || (entityName.equalsIgnoreCase("Contact"))));
        String lookupField = criteria.getMetaFilters().get(LOOKUP_FIELD).toString();
        if ((null != entityName) && (entityName.equalsIgnoreCase("Company"))){
            return lookupCompany(connector, criteria,lookupField);
        } else{
            return lookupContact(connector, criteria,lookupField);
        }
    }

    private LookupData lookupContact(ConnectorInfo connector, SearchCriteria criteria, String lookupField) {
        String url = buildContactURL(criteria);
        String entityName = criteria.getMetaFilters().get(LOOKUP_ENTITY).toString();
        return ConnectorHelper.withHttpErrorHandling(() -> {
            Optional<EnrichmentCache> cached = getCachedValue(connector.getId(), "contact", criteria.getSearchFieldNameValues().toString());
            String jsonResponse;
            if(cached.isPresent()){
                jsonResponse = cached.get().getEnrichValue();
            } else {
                log.info("Enriching {} information for Search Criteria {}", "contact", criteria.toString());

                HttpHeaders headers = getHeaders(connector.getAuthConfig());
                ResponseEntity<String> response = restClient.getResponse(headers, url, connector.getAuthConfig());
                log.info("POST: HTTP Status {}",response.getStatusCode());
                log.debug("Response body: {}", response.getBody());
                jsonResponse = response.getBody();
                validateResponse(response, "contact", criteria);
                saveToCache(connector.getId(), "contact", criteria.getSearchFieldNameValues().toString(), jsonResponse);
            }

            ReadContext ctx = JsonPath.parse(jsonResponse);
            List<Map<String, Object>> data = ctx.read("search_results");
            LookupData selectedResult = extractLeadValue("contact", lookupField, data);
            if(selectedResult.getValueAsString("error") != null){
                log.error("Unable to Enrich {} for Search Criteria {}. ErrorMsg: {}",
                        "contact", criteria.toString(), selectedResult.getValueAsString("error"));
                return null;
            }
            selectedResult.setLookupEntityName(entityName);
            return selectedResult;
        });
    }

    private LookupData extractLeadValue(String entityName, String lookupField, List<Map<String, Object>> data) {
        LookupData selectedResult = new LookupData();
        if("contact".equalsIgnoreCase(entityName) && SOCIAL_PROFILES.contains(lookupField) && (!data.isEmpty())) {
            if (data.get(0).isEmpty()) {
                return selectedResult;
            }
            List<Map<String, Object>> requiriedList =  data.stream().filter(x -> x.containsKey("social_profiles")).collect(Collectors.toList());
            if (requiriedList.get(0).containsKey("social_profiles")){
                List<Map<String,String>> social_profiles = (List<Map<String,String>>)(requiriedList.get(0).get("social_profiles"));
                for(Map<String, String> profileMap : social_profiles){
                    if (profileMap.get("type").equalsIgnoreCase(lookupField)){
                        selectedResult.addValue(lookupField,profileMap.get("url"));
                        break;
                    }
                }
            }
        }else{
            String lookMappedField = SalesIntelSeed.getAttributeToResponseMapping(entityName).get(lookupField);
            if ((null != lookMappedField) &&(!data.isEmpty())) {
                selectedResult.addValue(lookupField, EnrichUtil.findInResponseBody(lookMappedField, data.get(0)));
            }
        }
        return selectedResult;
    }

    private LookupData extractCompanyValue(String entityName, String lookupField, List<Map<String, Object>> data) {
        LookupData selectedResult = new LookupData();
        if("company".equalsIgnoreCase(entityName) && Codes.contains(lookupField) && (!data.isEmpty())) {
            if (data.get(0).isEmpty()) {
                return selectedResult;
            }
            String lookMappedField = SalesIntelSeed.getAttributeToResponseMapping(entityName).get(lookupField);
            if (null != lookMappedField) {
                selectedResult.addValue(lookupField, EnrichUtil.findInResponseBody(lookMappedField, data.get(0)));
            }
        }else{
            String lookMappedField = SalesIntelSeed.getAttributeToResponseMapping(entityName).get(lookupField);
            if (null != lookMappedField) {
                selectedResult.addValue(lookupField, EnrichUtil.findInResponseBody(lookMappedField, data.get(0)));
            }
        }
        return selectedResult;
    }

    private String buildRequest(ConnectorInfo connector, SearchCriteria criteria){
        SalesIntelEnrichPersonRequest request = new SalesIntelEnrichPersonRequest();
        request.addInput(criteria.getSearchFieldNameValues());
        return request.getRequestString();
    }

    private String buildContactURL(SearchCriteria criteria) {
        Map<String, Object> map = criteria.getSearchFieldNameValues();
        StringBuilder result = new StringBuilder(PERSON_URL);
        if (!StringUtils.isBlank((String)map.get("email"))){
            result.append("&email=" + (String)map.get("email"));
        }
        return result.toString();
    }

    private String buildCompanyURL(SearchCriteria criteria) {
        Map<String, Object> map = criteria.getSearchFieldNameValues();
        StringBuilder result = new StringBuilder(COMPANY_PATH);
        if (!StringUtils.isBlank((String)map.get("companyDomain"))){
            result.append("&company_domain=" + (String)map.get("companyDomain"));
        }
        if (!StringUtils.isBlank((String)map.get("companyName"))){
            result.append("&company_name=" + (String)map.get("companyName"));
        }
        if (!StringUtils.isBlank((String)map.get("companyIndustries"))){
            result.append("&company_industries=" + (String)map.get("companyIndustries"));
        }
        if (!StringUtils.isBlank((String)map.get("companyLocationStates"))){
            result.append("&company_location_states=" + (String)map.get("companyLocationStates"));
        }
        return result.toString();
    }

    private LookupData lookupCompany(ConnectorInfo connector, SearchCriteria criteria,String lookupField) {
        validateInput(criteria);
        String url = buildCompanyURL(criteria);
        String entityName = criteria.getMetaFilters().get(LOOKUP_ENTITY).toString();
        return ConnectorHelper.withHttpErrorHandling(() -> {
            Optional<EnrichmentCache> cached = getCachedValue(connector.getId(), entityName, criteria.getSearchFieldNameValues().toString());
            String jsonResponse;
            if(cached.isPresent()){
                jsonResponse = cached.get().getEnrichValue();
            } else {
                log.info("Enriching {} information for Search Criteria {}", entityName, criteria.toString());
                AuthConfig authConfig = connector.getAuthConfig();
                HttpHeaders headers = getHeaders(connector.getAuthConfig());

                ResponseEntity<String> resp = restClient.getResponse(headers,url,authConfig);

                log.info("GET: HTTP Status {}",resp.getStatusCode());
                log.debug("Response body: {}", resp.getBody());
                validateResponse(resp, entityName, criteria);
                jsonResponse = resp.getBody();
                saveToCache(connector.getId(), entityName, criteria.getSearchFieldNameValues().toString(), jsonResponse);
            }
            ReadContext ctx = JsonPath.parse(jsonResponse);
            List<Map<String, Object>> data = ctx.read("search_results");
            LookupData selectedResult = extractCompanyValue(entityName, lookupField, data);
            if(selectedResult.getValueAsString("error") != null){
                log.error("Unable to Enrich {} for Search Criteria {}. ErrorMsg: {}",
                        entityName, criteria.toString(), selectedResult.getValueAsString("error"));
                return null;
            }
            selectedResult.setLookupEntityName(entityName);
            return selectedResult;
        });
    }

    @Override
    public EntitySchema describe(DescribeRequest request) {
        return Optional.of(SalesIntelSeed.getEntity(request.getEntity())).get();
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

@Data
@Slf4j
class SalesIntelEnrichPersonRequest {

    List<Map<String, Object>> match_requests = new ArrayList<>();
    void addInput(Map<String, Object> inputValue){
        match_requests.add(inputValue);
    }

    String getRequestString(){
        String inputFieldName = "match_requests";
        var request = Map.of(inputFieldName, match_requests);
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.error("Error creating SalesIntelEnrichPersonRequest {}", e.getMessage());
            throw new RuntimeException("Error creating SalesIntelEnrichPersonRequest", e);
        }
    }
}
