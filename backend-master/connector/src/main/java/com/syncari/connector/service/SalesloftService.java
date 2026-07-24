package com.syncari.connector.service;

import static com.syncari.utils.ExceptionUtils.rethrow;
import static java.lang.String.format;
import static com.syncari.connector.ConnectorHelper.withRateLimitHandling;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.syncari.connector.data.*;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.DefaultAuthTokenHandler;
import com.syncari.connector.EntityData;
import com.syncari.connector.ListBasedIterator;
import com.syncari.connector.SalesloftEntityPage;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.iterator.SalesloftIterator;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.SalesloftRestClient;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.seed.SalesloftSeed;
import com.syncari.utils.DateUtil;
import com.syncari.utils.I18n;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.SALESLOFT)
public class SalesloftService implements OauthAuthenticationService, SynapseInfoService, 
	MetadataService, CommonDataService {
	
	@Autowired
	Transformer transformer;
	
	@Autowired
	ObjectMapper mapper;
	
	@Autowired
	DateUtil dateUtil;
	
	@Autowired
	DefaultAuthTokenHandler tokenHandler;

	public static final String OAUTH_URL = "https://accounts.salesloft.com/oauth/token";
	public static final String ENTITY_END_POINT = "https://api.salesloft.com/v2/";

	private static final String OAUTH_HOST = "https://accounts.salesloft.com/oauth";
	private static final String VERIFY_ACCESS_URL = "https://api.salesloft.com/v2/me.json";
    private static Map<String, String> objPluralMap = new HashMap<String, String>();
	private static final String GET_BY_WATERMARK = "%s.json?per_page=%s&page=%s&sort_by=%s&sort_direction=asc&updated_at[gte]=%s&updated_at[lte]=%s";
	private static final String GET_BY_WATERMARK_ALL = "%s.json?per_page=%s&page=%s&sort_by=%s&sort_direction=asc";
	protected static int API_MAX_PAGESIZE = 100;
	private static final String CUSTOM_FIELDS_DISCOVERY_ENDPOINT = "custom_fields.json?per_page=%s&page=%s";
	private static final String CREATE_ENTITY_ENDPOINT = "%s.json";
	private static final String GET_BY_IDS_ENPOINT = "%s.json?ids[]=%s";
	private static final String PUT_DELETE_ENTITY_ENDPOINT = "%s/%s.json";
	private static final List<String> unsupportedGetById = List.of("success");
    private static final List<String> supportedCustomFieldEntities = List.of("account", "person");
    private static final List<String> supportedCustomFieldDataTypes = List.of("text", "date", "picklist");
    protected Cache<ConnectorInfo, List<EntityData>> userCache = CacheBuilder.newBuilder().maximumSize(1000)
            .expireAfterWrite(6l, TimeUnit.HOURS).build();
    // Referenced fields which are named differently for GET and POST calls
    // By default all referenced fields are suffixed with _id for POST call below are certain exceptions
    private static final Map<String, Map<String, String>> SPECIAL_FIELD_MAPPING_FOR_CREATE = Map.of(
            "call", Map.of("called_person", "person_id",
                    "user", "user_guid",
                    "note", "notes")
    );
    
    public SalesloftService() {
        objPluralMap.put("account", "accounts"); 
        objPluralMap.put("person", "people");
        objPluralMap.put("user", "users");
        objPluralMap.put("crm_activity", "crm_activities");
        objPluralMap.put("cadence", "cadences");
        objPluralMap.put("cadence_membership", "cadence_memberships");
        objPluralMap.put("action", "actions");
        objPluralMap.put("person_stage", "person_stages");
        objPluralMap.put("call", "activities/calls");
        objPluralMap.put("note", "notes");
        objPluralMap.put("step", "steps");
        objPluralMap.put("success", "successes");
        objPluralMap.put("email", "activities/emails");
        objPluralMap.put("account_tier", "account_tiers");
        objPluralMap.put("conversation","conversations");
    }
    
	@Override
	public List<AuthMetadata> getSupportedAuthTypes() {
		return List.of(new AuthMetadata(AuthType.Oauth, Lists.newArrayList(), "OAuth", ""));
	}
	
	@Override
	public List<AuthField> getConfigureFields() {
		return List.of(ConnectorHelper.getSupportedAuthPicker());
	}

	@Override
	public String getName() {
		return Constants.SALESLOFT;
	}
	
	public UIMetadata getUIMetadata() {
		return new UIMetadata().setIconPath("/assets/icons/logos/salesloft.svg")
				.setDisplayName("Salesloft")
				.setBackgroundColor("#EDF4F7")
				.setHelpUrl(helpArticlesBaseUrl + "/360054632292-Salesloft-Setup");
	}

    @Override
    public String getCapabilitiesArticleId() {
        return "19206152495636";
    }


    @Override
	public String getOAuthUri(ConnectorInfo connector) {
		return "/authorize?client_id={{client_id}}&redirect_uri={{redirect_uri}}&response_type=code";
	}

	@Override
	public String getAuthHost(AuthConfig config) {
		return OAUTH_HOST;
	}

    public SalesloftRestClient getClient() {
        return new SalesloftRestClient(getSingleJsonConfig(""), mapper, dateUtil);
    }

	@Override
	public AuthConfig refreshToken(ConnectorInfo connector) {
		
		AuthConfig config = connector.getAuthConfig();
		Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.REFRESH_TOKEN,
            DefaultAuthTokenHandler.REFRESH_TOKEN, config.getRefreshToken(),
            DefaultAuthTokenHandler.CLIENT_ID, config.getClientId(), 
            DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret());
			
		return tokenHandler.refreshToken(config, OAUTH_URL, map);

	}

	@Override
	public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
		
		 Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, "authorization_code", 
            DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(), 
            DefaultAuthTokenHandler.CLIENT_ID, oAuthRequest.getConfig().getClientId(),
            DefaultAuthTokenHandler.CLIENT_SECRET, oAuthRequest.getConfig().getClientSecret(), 
            DefaultAuthTokenHandler.REDIRECT_URI, oAuthRequest.getRedirectUri());
			
		return tokenHandler.getAccessToken(OAUTH_URL, map);
	}


	@Override
	public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
		
		TestConnectionResponse response = new TestConnectionResponse();	
	    HttpClient client = HttpClient.newHttpClient();
	    
	    try {
	    	HttpRequest request = HttpRequest.newBuilder()
	    									.uri(URI.create(VERIFY_ACCESS_URL))
	    									.setHeader("Authorization", "Bearer " + config.getAuthConfig().getAccessToken())
	    									.timeout(Duration.ofMinutes(1)).GET().build();
	    
	    	HttpResponse<String> apiResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
	    	
	    	if (apiResponse.statusCode() != 200) {
	    		response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
	    		response.setMessage(I18n.i18n("invalid_token_bearer"));
            }
	    	
	    	return response;	    	
	    } catch(Exception e) {
	    	handleAuthenticationErrorMessage(response, e);
        }
		return response;
	}
	
	@Override
	public String getCategory() {
		return "Sales";
	}

	@Override
	public Map<String, String> getEntityMappings() {
		return Map.of(Constants.ACCOUNT.toLowerCase(),Constants.ACCOUNT, "person", "Person", "user", "User");
	}

	@Override
	public Map<String, String> getAttributeMappings(String entityApiName) {
	    return Map.of();
	}

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        return withRateLimitHandling(request.getConnector().getId(), () -> {
            EntitySchema schema = SalesloftSeed.getSeedEntitySchema(request.getEntity());
            SalesloftRestClient restClient = getClient();
            if ("account".equalsIgnoreCase(request.getEntity()) || "person".equalsIgnoreCase(request.getEntity())) {
                int pageNumber = 1;
                boolean hasMore = true;
                do {
                    String url = format(ENTITY_END_POINT + CUSTOM_FIELDS_DISCOVERY_ENDPOINT, API_MAX_PAGESIZE, pageNumber);
                    ResponseEntity<String> response = restClient.getResponse(url, request.getConnector().getAuthConfig());
                    ReadContext responseBody = JsonPath.parse(response.getBody());
                    List<Map> rows = responseBody.read("data");
                    rows.forEach(x -> {
                        if (supportedCustomFieldEntities.contains(request.getEntity())) {
                            if(List.of("company", "person").contains(x.get("field_type").toString())){
                                String dataType = Objects.toString(x.get("value_type"), "");
                                if(supportedCustomFieldDataTypes.contains(dataType)){
                                    AttributeSchema attributeSchema = new AttributeSchema(x.get("name").toString(), x.get("name").toString())
                                            .setDisplayName(x.get("name").toString()).setCustom(true);
                                    if(dataType.equals("picklist"))
                                        attributeSchema.setDataType("text");
                                    else
                                        attributeSchema.setDataType(dataType);
                                    schema.addField(attributeSchema);
                                } else {
                                    log.warn("Ignored custom field {} with type {}", x.get("name").toString(), dataType);
                                }
                            }
                        }
                    });
                    ++pageNumber;
                    if (rows.size() == 0) hasMore = false;
                } while (hasMore);
            }
            return Optional.ofNullable(schema);
        });
    }

	@Override
	public List<EntitySchema> describeAll(DescribeAllRequest request) {
		List<EntitySchema> allSchemas = new ArrayList<>();
        ConnectorInfo connector = request.getConnector();
        objPluralMap.keySet().forEach( k -> {
            allSchemas.add(describe(new DescribeRequest(connector, k)).get());
        });
        
        return allSchemas;
	}

	@Override
	public EntitySchema createObject(CreateObjectRequest request) {
		throw new RuntimeException("Not Implemented");
	}

	@Override
	public AttributeSchema createField(CreateFieldRequest request) {
		throw new RuntimeException("Not Implemented");
	}

	@Override
	public void deleteField(DeleteFieldRequest request) {
		throw new RuntimeException("Not Implemented");
	}

	@Override
	public FetchResponse getByWatermark(SyncRequest request) {
        return withRateLimitHandling(request.getConnector().getId(), () -> {
            SalesloftRestClient restClient = getClient();
            if("user".equalsIgnoreCase(request.getEntityName())) {
                List<EntityData> existing = userCache.getIfPresent(request);
                if(existing == null) {
                    existing = fetchUsers(request, restClient);
                    userCache.put(request.getConnector(), existing);
                }
                return new FetchResponse(request.getWatermark(),
                        new ListBasedIterator(existing, request.getWatermark()));
            }
            String plural = objPluralMap.get(request.getEntityName());

            int pageSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();
            
            Function2<WatermarkInfo, Integer, SalesloftEntityPage> generator = (wm, pageIndex) -> {
                String start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wm.getStart()), ZoneOffset.UTC).toString();
                String end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wm.getEnd()), ZoneOffset.UTC).toString();
                String url = format(ENTITY_END_POINT + GET_BY_WATERMARK, plural, pageSize, pageIndex, "updated_at", start, end);
                return restClient.get(url, request);			
            };
            
            SalesloftIterator iterator = new SalesloftIterator(request.getWatermark(), generator, new ArrayList<>(), pageSize);
            long pageNumber = (request.getWatermark().getOffset() > 0) ? request.getWatermark().getOffset() : 1;
            iterator.setNextPageNumber(Integer.valueOf((int) pageNumber));

            return new FetchResponse(request.getWatermark(), iterator);
        });
	}

	@Override
	public long getFirstCreatedTime(SyncRequest request) {
		return 0;
	}

	@Override
	public List<EntityData> getByIds(SyncRequest request) {
        return withRateLimitHandling(request.getConnector().getId(), () -> {
            List<EntityData> result = new ArrayList<>();
            if(unsupportedGetById.contains(request.getEntityName())) return result;
            String plural = objPluralMap.get(request.getEntityName());
            List<EntityData> data = request.getData().get(request.getConnector().getId());
            var partitioned = Lists.partition(data, 100);
            
            partitioned.forEach(partition -> {
                List<String> ids = partition.stream().map(e -> e.getId()).filter(id->!StringUtils.isBlank(id)).collect(Collectors.toList());
                String url = format(ENTITY_END_POINT + GET_BY_IDS_ENPOINT, plural, ids.stream().collect(Collectors.joining("&ids[]=")));
                List<EntityData> entitiesByIds = getEntitiesByIds(url, request); 
                result.addAll(entitiesByIds);
            });

            return result;
        });
	}

	@Override
	public SyncResponse create(SyncRequest request) {
        return withRateLimitHandling(request.getConnector().getId(), () -> {
            String plural = objPluralMap.get(request.getEntityName());
            SyncResponse response = new SyncResponse();
            List<EntityData> toBeCreated = request.getData().get(request.getConnector().getId());
                    
            if (toBeCreated == null || toBeCreated.isEmpty()) {
                log.info("Nothing to be created for Salesloft");
                return response;
            }
            
            log.info("Calling create for salesloft with size {} for {}", toBeCreated.size(), request.getEntityName());
            List<String> customFieldsForEntity = getCustomFieldsForEntity(request.getEntityName(), request.getEntitySchema());
            log.info("Custom fields for {}: {}", request.getEntityName(), customFieldsForEntity);

            String url = String.format(ENTITY_END_POINT +  CREATE_ENTITY_ENDPOINT, plural);
            for (EntityData data : toBeCreated) {	
                try {
                    processValuesForCreateUpdate(customFieldsForEntity, data.getValues(), request.getEntitySchema());
                    String payloadString = mapper.writeValueAsString(data.getValues());
                    EntityData createdData = postEntityData(url, payloadString, request);
                    response.getResults().add(new Result(true, createdData.getId(), data.getSyncariEntityId()));
                    log.info("SyncResponse response create", response.getResults().toString());
                }catch (JsonProcessingException e) {
                    log.error(e.getMessage(), e);
                    Result error = new Result(false, null, data.getSyncariEntityId());
                    error.getErrors().add(e.getMessage());
                    response.getResults().add(error);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    if (e instanceof NonRetriableException && 
                        ErrorCodes.TOO_MANY_REQUESTS.toString().equals(((NonRetriableException) e).getErrorCode())) {
                        throw e;
                    }
                    Result error = new Result(false, null, data.getSyncariEntityId());
                    error.getErrors().add(e.getMessage());
                    response.getResults().add(error);
                } 
            }
            
            return response;
        });
	}

	@Override
	public SyncResponse update(SyncRequest request) {
        return withRateLimitHandling(request.getConnector().getId(), () -> {
            String plural = objPluralMap.get(request.getEntityName());
            SyncResponse response = new SyncResponse();        
            List<EntityData> toBeUpdated = request.getData().get(request.getConnector().getId());
            
            if (toBeUpdated == null || toBeUpdated.isEmpty()) {
                log.info("Nothing to be updated for Salesloft");
                return response;
            }
            
            log.info("Calling update for salesloft with size {} for {}", toBeUpdated.size(), request.getEntityName());
    
            List<String> customFieldsForEntity = getCustomFieldsForEntity(request.getEntityName(), request.getEntitySchema());
            log.info("Custom fields for {}: {}", request.getEntityName(), customFieldsForEntity);
            
            for (EntityData data : toBeUpdated) {
                try {
                    processValuesForCreateUpdate(customFieldsForEntity, data.getValues(), request.getEntitySchema());
                    String payloadString = mapper.writeValueAsString(data.getValues());
                    String url = String.format(ENTITY_END_POINT +  PUT_DELETE_ENTITY_ENDPOINT, plural, data.getId());
                    EntityData updatedData = updateEntityData(url, payloadString, request);
                    response.getResults().add(new Result(true, updatedData.getId(), data.getSyncariEntityId()));
                    log.info("SyncResponse response update", response.getResults().toString());
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    if (e instanceof NonRetriableException && 
                        ErrorCodes.TOO_MANY_REQUESTS.toString().equals(((NonRetriableException) e).getErrorCode())) {
                        throw e;
                    }
                    Result error = new Result(false, data.getId(), data.getSyncariEntityId());
                    error.getErrors().add(e.getMessage());
                    response.getResults().add(error);
                }
            }
            
            return response;
        });
	}

    private List<String> getCustomFieldsForEntity(String entityName, EntitySchema entitySchema) {
        List<String> customFieldsForEntity = new ArrayList<>();
        if (supportedCustomFieldEntities.contains(entityName)) {
            customFieldsForEntity = entitySchema.getAttributes().stream()
                .filter(x -> x.isCustom()).map(y -> y.getApiName()).collect(Collectors.toList());
        }
        return customFieldsForEntity;
    }

    private void processValuesForCreateUpdate(List<String> customFieldsForEntity, Map<String, Object> values, EntitySchema schema) {
        // handle reference fields
        // reference fields during creates/updates are suffixed with _id.
        // e.g. person field in account entity need to be added as person_id in payload during create and update
        Map<String, Object> referencedFields = new HashMap<>();
        Set<String> keysToRemove = new HashSet<>();
        values.forEach((k, v) -> {
            schema.getField(k).ifPresent(f -> {
                if(f.isReference()){
                    var fieldName = SPECIAL_FIELD_MAPPING_FOR_CREATE.containsKey(schema.getApiName())
                            && SPECIAL_FIELD_MAPPING_FOR_CREATE.get(schema.getApiName()).containsKey(k)
                            ? SPECIAL_FIELD_MAPPING_FOR_CREATE.get(schema.getApiName()).get(k)
                            : k;
                    referencedFields.put(fieldName + "_id", v);
                    keysToRemove.add(k);
                }
            });
        });
        values.putAll(referencedFields);
        keysToRemove.forEach(k -> values.remove(k));

        // handle custom fields
        if (!customFieldsForEntity.isEmpty()) {
            Map<String, Object> customValues = new LinkedHashMap<>();
            for (String customField: customFieldsForEntity) {
                if (values.containsKey(customField)) {
                    customValues.put(customField, values.get(customField));
                }
            }
            values.put("custom_fields", customValues);
        }
    }

	@Override
	public SyncResponse delete(SyncRequest request) {
        return withRateLimitHandling(request.getConnector().getId(), () -> {
            //TODO - Need to handle how to process records that have been deleted from Salesloft as they are removed from the api response
            SyncResponse response = new SyncResponse();
            String plural = objPluralMap.get(request.getEntityName());
            SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(request.getEntityName()),mapper);
            List<EntityData> toBeDeleted = request.getData().get(request.getConnector().getId());
        
            if (toBeDeleted == null || toBeDeleted.isEmpty()) {
                log.info("Nothing to be updated for Salesloft");
                return response;
            }
            
            for (EntityData data : toBeDeleted) {
                try {
                    String url = String.format(ENTITY_END_POINT +  PUT_DELETE_ENTITY_ENDPOINT, plural, data.getId());	
                    restClient.delete(url, request.getConnector().getAuthConfig());
                    response.getResults().add(new Result(true, data.getId(), data.getSyncariEntityId()));
                    log.info("Successfully deleted {} {}", request.getEntityName(), data.getId());
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    if (e instanceof NonRetriableException && 
                        ErrorCodes.TOO_MANY_REQUESTS.toString().equals(((NonRetriableException) e).getErrorCode())) {
                        throw e;
                    }
                    Result error = new Result(false, data.getId(), data.getSyncariEntityId());
                    error.getErrors().add(e.getMessage());
                    response.getResults().add(error);
                }
            }
            
            return response;
        });
	}
	
	private JsonParserConfig getSingleJsonConfig(String plural) {
		return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
	}
	
	private List<EntityData> getEntitiesByIds(String url, SyncRequest request) {
		ResponseEntity<String> dataResponse = getClient().getResponse(url, request.getConnector().getAuthConfig());
		ReadContext dataCtx = JsonPath.parse(dataResponse.getBody());
		List<EntityData> results = 	getClient().parseEntityDataList(dataCtx, request);	
		
		return results;
	}
	
	private EntityData postEntityData(String url, String payload, SyncRequest request){
		ResponseEntity<String> dataResponse = getClient().postSingleEntity(url, payload, request.getConnector().getAuthConfig());
		
		try {
		    Map responseValues = rethrow(()->mapper.readValue(dataResponse.getBody(), Map.class));
			return generateEntityDataObject(responseValues, request);
		} catch (ResourceAccessException e){
            throw new RetriableException("IOError", e.getMessage(), "IOError");
        }
	}
	
	private EntityData updateEntityData(String url, String payload, SyncRequest request){
		SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
		ResponseEntity<String> dataResponse = null;
		try{
            dataResponse = restClient.put(url, payload, request.getConnector().getAuthConfig());
        }catch (NonRetriableException e) {
            if (e.getErrorCode() == ErrorCodes.ACCESS_DENIED.name()) {
                // Refresh the token for long running sync cycles.
                // Note, this is not a loop and we only attempt to refresh token once.
                request.getConnector().getAuthConfig().setAccessToken(refreshToken(request.getConnector()).getAccessToken());
                dataResponse = restClient.put(url, payload, request.getConnector().getAuthConfig());
            }else{
                throw e;
            }
        }
		try {
		    String responseBody = dataResponse.getBody();
		    Map responseValues = rethrow(()->mapper.readValue(responseBody, Map.class));
			return generateEntityDataObject(responseValues, request);
		} catch (ResourceAccessException e){
            throw new RetriableException("IOError", e.getMessage(), "IOError");
        }
	}
	
	private EntityData generateEntityDataObject(Map responseValues, SyncRequest request) {
		EntityData data = new EntityData(request.getEntityName());
		data.setConnectorId(request.getConnector().getId());
		Map dataAttrs = (Map) responseValues.get("data");
		data.setId(dataAttrs.get("id").toString());
		
		return data;
	}
	
    private List<EntityData> fetchUsers(SyncRequest request, SalesloftRestClient restClient) {
        String plural = objPluralMap.get(request.getEntityName());
        int pageSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();
        Function2<WatermarkInfo, Integer, SalesloftEntityPage> generator = (wm, pageIndex) -> {
            String url = format(ENTITY_END_POINT + GET_BY_WATERMARK_ALL, plural, pageSize, pageIndex, "id");
            return restClient.get(url, request);            
        };
        
        SalesloftIterator iterator = new SalesloftIterator(request.getWatermark(), generator, new ArrayList<>(), pageSize);
        iterator.setNextPageNumber(1);
         
        FetchResponse resp = new FetchResponse(request.getWatermark(), iterator);
        List<EntityData> result = new ArrayList<>();
        while(resp.getIterator().hasNext()) {
            result.addAll(resp.getIterator().next());
        }
        List<EntityData> filtered = result.stream().filter(r -> r.getLastModified() >= request.getWatermark().getStart()
                && r.getLastModified() <= request.getWatermark().getEnd()).collect(Collectors.toList());
        filtered.sort(Comparator.comparingLong(EntityData::getLastModified));
        return filtered;
    }

    @Override
    public MergeResponse merge(MergeRequest request) {
        // TODO reparent if there are any references to the losers
        List<SyncResponse> loserResults = new ArrayList<>();
        // Call the delete on losers
        request.getLosers().forEach(l -> {
            SyncRequest deleteRequest = new SyncRequest().Builder(request.getConnector(), request.getEntitySchema());
            deleteRequest.addData(request.getConnector().getId(), l);
            loserResults.add(delete(deleteRequest));
        });

        MergeResponse response = upsertWinner(request);
        if (!loserResults.isEmpty()){
            response.setLoserResult(loserResults.get(loserResults.size()-1));
        }
        return response;
    }
}
