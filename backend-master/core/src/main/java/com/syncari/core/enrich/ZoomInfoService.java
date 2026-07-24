package com.syncari.core.enrich;

import java.util.*;
import java.util.stream.Collectors;

import com.syncari.connector.data.*;
import com.syncari.core.model.EnrichmentCache;
import com.syncari.core.repositories.customer.EnrichmentCacheRepo;
import com.syncari.utils.DateUtil;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.zoominfo.ZoomInfoSeed;
import com.syncari.core.model.LookupData;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.service.LookupService;
import com.syncari.core.utils.EnrichUtil;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.ZOOMINFO)
public class ZoomInfoService implements LookupService, OauthAuthenticationService, SynapseInfoService {

    private static final String EXTERNAL_URLS = "externalUrls";
    private static final String SOCIAL_MEDIA_URLS = "socialMediaUrls";
    private static final String API_HOST = "https://api.zoominfo.com";
    private static final String ENRICH_ENDPOINT = "/enrich/%s";
    private static final int WAIT_TIMEOUT_MILLIS = 5000;
    private static final List<String> SUPPORTED_ENTITIES = List.of("contact", "company");
    private static final List<String> URL_FIELDS = List.of("linkedinUrl", "twitterUrl", "salesforceUrl", "facebookUrl");
    private static final Map<String, String> CONTACT_URL_KEY_MAP = Map.of("linkedinUrl", "linkedin.com", "twitterUrl",
            "twitter.com", "salesforceUrl", "salesforce.com", "facebookUrl", "facebook.com");
    private static final Map<String, String> COMPANY_URL_KEY_MAP = Map.of("linkedinUrl", "LINKED_IN", "twitterUrl",
            "TWITTER", "facebookUrl", "FACEBOOK");
    private static final String LOOKUP_ENTITY = "lookupEntity";
    private static final String LOOKUP_FIELD = "lookupField";
    private static final String ADDITIONAL_LOOKUP_FIELD = "additionalLookupField";

    private static final int CACHE_EXPIRY_DAYS = 30;

    @Autowired
    ObjectMapper mapper;
    
    @Autowired
    com.syncari.connector.zoominfo.ZoomInfoService service;

    @Autowired
    EnrichmentCacheRepo cacheRepo;

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        return service.getAccessToken(oAuthRequest);
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        return service.refreshToken(connector);
    }

    @Override
    public String getAuthHost(AuthConfig config) {
        return service.getAuthHost(config);
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        throw new RuntimeException("OAuth Implicit Flow not supported by ZoomInfo");
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo connector, List<String> entityNames) {
        return service.testConnection(connector, entityNames);
    }

    @Override
    public LookupData lookup(ConnectorInfo connector, SearchCriteria criteria) {
        validateInput(criteria);
        String entityName = criteria.getMetaFilters().get(LOOKUP_ENTITY).toString();
        String lookupField = criteria.getMetaFilters().get(LOOKUP_FIELD).toString();
        String additionalLookupField = Objects.isNull(criteria.getMetaFilters().get(ADDITIONAL_LOOKUP_FIELD)) ? "" : criteria.getMetaFilters().get(ADDITIONAL_LOOKUP_FIELD).toString();
        EntitySchema entitySchema = describe(new DescribeRequest(connector, entityName));
        Optional<AttributeSchema> attributeSchema = entitySchema.getField(lookupField);
        if (attributeSchema.isEmpty()) {
            return null;
        }
        Optional<AttributeSchema> additionalAttributeSchema;
        if("contact".equalsIgnoreCase(entityName) && StringUtils.isNotBlank(additionalLookupField)){
            additionalAttributeSchema = entitySchema.getField(additionalLookupField);
            if (additionalAttributeSchema.isEmpty()){
                return null;
            }
        }
        String url = String.format(API_HOST + ENRICH_ENDPOINT, entityName.toLowerCase());
        RestTemplate restTemplate = getTemplate();
        return ConnectorHelper.withHttpErrorHandling(() -> {
            Optional<EnrichmentCache> cached = getCachedValue(connector.getId(), entityName, criteria.getSearchFieldNameValues().toString());
            String jsonResponse;
            if(cached.isPresent()){
                jsonResponse = cached.get().getEnrichValue();
            } else {
                String requestString = buildRequest(connector, criteria);
                log.info("Enriching {} information for Search Criteria {}", entityName, criteria.toString());

                HttpHeaders headers = getHeaders(connector.getAuthConfig());
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST,
                        new HttpEntity(requestString, headers), String.class);
                log.info("POST: HTTP Status {}",response.getStatusCode());
                log.debug("Response body: {}", response.getBody());
                jsonResponse = response.getBody();
                validateResponse(jsonResponse, entityName, criteria);
                saveToCache(connector.getId(), entityName, criteria.getSearchFieldNameValues().toString(), jsonResponse);
            }

            ReadContext ctx = JsonPath.parse(jsonResponse);
            Map<String, Object> data = ctx.read("data");
            LookupData selectedResult = extractValue(entityName, attributeSchema.get(), data);
            if(selectedResult.getValueAsString("error") != null){
                log.error("Unable to Enrich {} for Search Criteria {}. ErrorMsg: {}",
                        entityName, criteria.toString(), selectedResult.getValueAsString("error"));
                return null;
            }
            selectedResult.setLookupEntityName(entityName);
            return selectedResult;
        });
    }

    private Optional<EnrichmentCache> getCachedValue(String connectorId, String entityName, String key) {
        Optional<EnrichmentCache> cached = cacheRepo.findByServiceIdAndEntityNameAndEnrichKey(connectorId, entityName, key);
        if(cached.isPresent() && cached.get().getCreatedAt() != null
                && cached.get().getCreatedAt().before(DateUtil.subtractDaysFromToday(CACHE_EXPIRY_DAYS))) {
            cacheRepo.delete(cached.get());
            return Optional.empty();
        }
        return cached;
    }

    private void saveToCache(String connectorId, String entityName, String key, String jsonBody) {
        EnrichmentCache toBeCached = new EnrichmentCache().setServiceId(connectorId).setEntityName(entityName)
                .setEnrichKey(key).setEnrichValue(jsonBody);
        try {
            cacheRepo.save(toBeCached);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private LookupData extractUrls(String lookupField, String urlGroupKey, Map<String, String> keyMap,  Map<String, Object> data){
        LookupData selectedResult = new LookupData();

        List dataList = (List) data.get("result");
        if (dataList.isEmpty() || !((Map)dataList.get(0)).containsKey("data")) {
            return selectedResult;
        }
        List items = (List) ((Map)dataList.get(0)).get("data");
        if (!items.isEmpty() && ((Map)items.get(0)).containsKey(urlGroupKey)) {
            List urls = (List) ((Map)items.get(0)).get(urlGroupKey);
            for (Object urlMap : urls) {
                Map map = (Map) urlMap;
                if (map.get("type").toString().equals(keyMap.get(lookupField))) {
                    selectedResult.addValue(lookupField, map.get("url"));
                    return selectedResult;
                }
            }
        }
        return selectedResult;
    }

    private LookupData extractValue(String entityName, AttributeSchema lookupField, Map<String, Object> data) {
        LookupData selectedResult = new LookupData();
        if(URL_FIELDS.contains(lookupField.getApiName()) && data.containsKey("result")) {
            List dataList = (List) data.get("result");
            if (dataList.isEmpty() || !((Map)dataList.get(0)).containsKey("data")) {
                return selectedResult;
            }
            if("contact".equalsIgnoreCase(entityName)){
                return extractUrls(lookupField.getApiName(), EXTERNAL_URLS, CONTACT_URL_KEY_MAP, data);
            } else if ("company".equalsIgnoreCase(entityName)){
                return extractUrls(lookupField.getApiName(), SOCIAL_MEDIA_URLS, COMPANY_URL_KEY_MAP, data);
            }
        } else {
            String absoluteFieldPath = "result.data" + "." + getResponsePathForField(entityName, lookupField.getApiName());
            if (lookupField.isMultiValueField()){
                selectedResult.addValue(lookupField.getApiName(), EnrichUtil.findMultiValueInResponseBody(absoluteFieldPath, data));
            } else {
                selectedResult.addValue(lookupField.getApiName(), EnrichUtil.findInResponseBody(absoluteFieldPath, data));
            }
            return selectedResult;
        }
        return selectedResult;
    }

    private void validateResponse(String jsonResponse, String entityName, SearchCriteria criteria) {
        ReadContext ctx = JsonPath.parse(jsonResponse);
        Boolean isSuccess = ctx.read("success");
        if(BooleanUtils.isTrue(isSuccess)){
            Map<String, Object> data = ctx.read("data");
            var error = EnrichUtil.findInResponseBody("result.data.error", data);
            if(error != null && !StringUtils.isEmpty(error.toString())){
                throw new RuntimeException(String.format("Error in Enriching data. ErrorMsg:%s", error.toString()));
            }

            var errorMsg = EnrichUtil.findInResponseBody("result.data.errorMessage", data);
            if(errorMsg != null && !StringUtils.isEmpty(errorMsg.toString())){
                throw new RuntimeException(String.format("Error in Enriching data. ErrorMsg:%s", errorMsg.toString()));
            }
        } else {
            throw new RuntimeException(String.format("Unable to Enrich %s for Search Criteria %s", entityName, criteria.toString()));
        }

    }

    private void validateInput(SearchCriteria criteria) {
        log.debug("Search Criteria: {}", criteria.toString());
        if(criteria == null) throw new RuntimeException("Search Criteria for lookup not defined");
        if(MapUtils.isEmpty(criteria.getMetaFilters())) throw new RuntimeException("No Filters provided in Search Criteria");
        if(!criteria.getMetaFilters().containsKey(LOOKUP_ENTITY)) throw new RuntimeException("Missing lookupEntity in Search filter");
        String lookupEntity = criteria.getMetaFilters().get(LOOKUP_ENTITY).toString();
        if(!SUPPORTED_ENTITIES.contains(lookupEntity)) throw new RuntimeException(String.format("Lookup Entity %s not Supported by ZoomInfo", lookupEntity));
        if(!criteria.getMetaFilters().containsKey(LOOKUP_FIELD)) throw new RuntimeException("Missing lookupField in Search filter");
        if(MapUtils.isEmpty(criteria.getSearchFieldNameValues())) throw new RuntimeException("Missing fields to lookup in SearchCriteria");
    }

    @Override
    public EntitySchema describe(DescribeRequest request) {
        return service.describe(request).get();
    }

    @Override
    public Map<String, String> getInputFields(ConnectorInfo connector, String entity) {
        // seeding the input fields manually for zoominfo
        switch (entity) {
            case "contact":
                return Map.of("emailAddress", "Email");
            case "company":
                return Map.of("companyName", "Company Name", "companyWebsite", "Company Website");
            default:
                throw new RuntimeException("Entity %s not supported by ZoomInfo");

        }
    }

    @Override
    public Map<String, String> getOutputFields(ConnectorInfo connector, String entity) {
        EntitySchema schema = describe(new DescribeRequest(connector, entity));
        return schema.getAttributes().stream().filter(a -> !a.isSystem())
                .collect(Collectors.toMap(a -> a.getApiName(), a -> a.getDisplayName()));
    }

    public RestTemplate getTemplate() {
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(WAIT_TIMEOUT_MILLIS);
        clientHttpRequestFactory.setReadTimeout(WAIT_TIMEOUT_MILLIS);
        return new RestTemplate(clientHttpRequestFactory);
    }

    private String buildRequest(ConnectorInfo connector, SearchCriteria criteria){
        String entityName = criteria.getMetaFilters().get(LOOKUP_ENTITY).toString();
        String lookupField = criteria.getMetaFilters().get(LOOKUP_FIELD).toString();
        EnrichRequest request = new EnrichRequest();
        request.setEntityName(entityName);
        request.addInput(criteria.getSearchFieldNameValues());
        // send request to fetch all output fields and cache result
        Set<String> allOutputFields = getOutputFields(connector, entityName).keySet();
        allOutputFields.removeAll(ZoomInfoSeed.DERIVED_FIELDS);
        List<String> outputFields = new ArrayList<>(allOutputFields);
        if ("contact".equalsIgnoreCase(entityName)){
            outputFields.add(EXTERNAL_URLS);
        } else if ("company".equalsIgnoreCase(entityName)) {
            outputFields.add(SOCIAL_MEDIA_URLS);
            outputFields.add("departmentBudgets");
        }
        request.setOutputFields(outputFields);

        return request.getRequestString();
    }

    private String getResponsePathForField(String entity, String fieldName){
        return ZoomInfoSeed.getAttributeToResponseMapping(entity).get(fieldName);
    }

    private HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.set("Authorization", "Bearer " + authConf.getAccessToken());
        headers.addAll(authHeaders);
        return headers;
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public Map<String, String> getEntityMappings() {
        // TODO
        return Map.of();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        // TODO
        return Map.of();
    }

    @Override
    public String getName() {
        return Constants.ZOOMINFO;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/zoominfo.svg")
                .setDisplayName("ZoomInfo")
                .setBackgroundColor("#f2fbff")
                .setHelpUrl(helpArticlesBaseUrl + SYNAPSE_COMING_SOON_ARTICLE);
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
        return "19208829480468";
    }
}

@Data
@Slf4j
class EnrichRequest {

    String entityName;
    List<Map<String, Object>> input = new ArrayList<>();
    List<String> outputFields = new ArrayList<>();

    void addInput(Map<String, Object> inputValue){
        input.add(inputValue);
    }

    void addOutputField(String fieldName){
        outputFields.add(fieldName);
    }

    String getRequestString(){
        String inputFieldName = "contact".equalsIgnoreCase(entityName) ? "matchPersonInput" : "matchCompanyInput";
        var request = Map.of(inputFieldName, input, "outputFields" , outputFields);

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.error("Error creating EnrichRequest {}", e.getMessage());
            throw new RuntimeException("Error creating Enrich Request", e);
        }
    }
}
