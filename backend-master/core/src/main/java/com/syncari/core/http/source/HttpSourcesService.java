package com.syncari.core.http.source;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;
import com.syncari.connector.data.iterator.DefaultDataPageNumberIterator;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.JsonSchemaHelper;
import com.syncari.utils.KeyValue;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@Component(Constants.HTTP_SOURCES)
public class HttpSourcesService implements CommonDataService, MetadataService, SynapseInfoService, OauthAuthenticationService {
    @Autowired
    Transformer transformer;
    @Autowired
    TextUtil textUtil;
    @Autowired
    HttpSourcesHelper helper;
	@Autowired
	TokenHelper tokenHelper;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
    	
    	AuthField testField = new AuthField();
    	testField.setDataType("httptest");
    	testField.setName("test");
        testField.setLabel("Test");
        testField.setRequired(false);
        testField.setHidden(true);
        
    	AuthMetadata none = new AuthMetadata(AuthType.None, List.of(), "None", "");
    	
        AuthMetadata apiKey = ConnectorHelper.getApiKey();
        List<AuthField> apiKeyFields = new ArrayList<AuthField>(apiKey.getFields());
        apiKeyFields.add(testField);
        apiKey.setFields(apiKeyFields);
        
        AuthField accessTokenEndpoint = new AuthField();
        accessTokenEndpoint.setDataType("text");
        accessTokenEndpoint.setName("accessTokenEndpoint");
        accessTokenEndpoint.setLabel("Access Token URL");
        accessTokenEndpoint.setRequired(true);
        
		AuthMetadata oauth = new AuthMetadata(
				AuthType.SimpleOAuth, List.of(ConnectorHelper.getClientIdField(),
						ConnectorHelper.getClientSecretField(), accessTokenEndpoint),
				"OAuth with Client Credentials", "");
        
        AuthField bearerTokenField = new AuthField();
        bearerTokenField.setDataType("password");
        bearerTokenField.setName("accessToken");
        bearerTokenField.setLabel("Bearer Token");
        AuthMetadata bearerToken = new AuthMetadata(AuthType.ApiSecretKey, List.of(bearerTokenField, testField), "Bearer Token", "");
        
		return List.of(none, apiKey, oauth, bearerToken);
    }
    
    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getSupportedAuthPicker());
    }
    
    @Override
    public boolean isSink() {
        return false;
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }
    
    @Override
    public String getName() {
        return Constants.HTTP_SOURCES;
    }

    public UIMetadata getUIMetadata() {
    	return new UIMetadata().setIconPath("/assets/icons/custom-synapse-default.svg")
                .setDisplayName("Http Sources")
                .setBackgroundColor("#EFF2F6")
                .setHelpUrl(helpSectionsBaseUrl + "/4578749288980-Custom-Synapse");
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.userEditableId);
        capabilities.add(Capability.userEditableWm);
        return capabilities;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }


    @Override
	public FetchResponse getByWatermark(SyncRequest request) {
    	log.info("HttpSourcesService received getByWatermark request for {}", request.getEntityName());
    	HttpSourceConfigInfo httpSource = null;
    	if(request.getConnector().getHttpSourceConfig() != null) {
    		httpSource = request.getConnector().getHttpSourceConfig().stream().filter(hs -> hs.getApiName().equals(request.getEntityName())).findFirst().orElse(null);
    	}
    	if(httpSource != null) {
    	    httpSource = updateSelectorXPaths(httpSource);
    		var context = new HashMap<String, Object>();
    		context.putAll(getSystemTokens(request));
    		context.putAll(getAdditionConfig(request.getConnector()));
    		//Variable values from entity config
			Map<String, Boolean> variableTypeMap = httpSource.getVariables() == null ? Map.of()
					: httpSource.getVariables().stream().collect(Collectors.toMap(a -> a.get("name"),
							a -> BooleanUtils.toBoolean(a.getOrDefault("multivalued", "false").toString())));
            var evaluatedContext = evaluateVariables(request.getAdditionalParams(), variableTypeMap, context);
        	context.putAll(evaluatedContext);
    		switch (httpSource.getType()) {
			case NO_PAGINATION:
				return getNoPaginationIterator(request, httpSource, context);
			case LIMIT_OFFSET:
				return getLimitOffsetIterator(request, httpSource, context);
			case PAGE_NUMBER:
				return getPageNumberIterator(request, httpSource, context);
			case CURSOR:
				if("parameter".equals(httpSource.getCursorType())) {
					return getCustorParameterIterator(request, httpSource, context);
				} else if("link_in_body".equals(httpSource.getCursorType())) {
					return getCustorLinkInBodyIterator(request, httpSource, context);
				} else {
					return getNoPaginationIterator(request, httpSource, context);
				}
			default:
				return getNoPaginationIterator(request, httpSource, context);
			}
    	} else {
    		throw new RuntimeException(String.format("entity %s not found", request.getEntityName()));
    	}
	}
    
	private Map<String, Object> getSystemTokens(SyncRequest request) {
		ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
		return Map.of("syncari",
				Map.of("system",
						Map.of("currentTimeInMillis", System.currentTimeMillis(),
								"currentDate", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
								"currentDateTime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")))),
				request.getConnector().getName(), Map.of("watermark", request.getWatermark()));
	}

	private Map<String, Object> getAdditionConfig(ConnectorInfo connector) {
		if (connector.getMetaConfig() != null) {
			return connector.getMetaConfig().entrySet().stream()
					.filter(entry -> !Set.of("authType").contains(entry.getKey()))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		} else {
			return Map.of();
		}
	}

    private FetchResponse getNoPaginationIterator(SyncRequest request, HttpSourceConfigInfo httpSource, Map<String, Object> context) {
    	var result = ConnectorHelper.withHttpErrorHandling(() -> helper.execute(request.getConnector(), httpSource, context, false));
    	List<EntityData> data = new ArrayList<EntityData>();
    	if(result.getBody() != null) {
    		data = new HttpSourceNoPaginationGenerator().getRecords(request, result, httpSource);
    	}
    	ListBasedIterator iterator = new ListBasedIterator(data, request.getWatermark());
        return new FetchResponse(request.getWatermark(), iterator);
	}
    
    private FetchResponse getLimitOffsetIterator(SyncRequest request, HttpSourceConfigInfo httpSource, Map<String, Object> context) {
        HttpSourceOffsetLimitGenerator generator = new HttpSourceOffsetLimitGenerator(request, helper, httpSource, context);
		int pgSize = httpSource.getLimitValue() == null || httpSource.getLimitValue() <= 0 ? 100
				: httpSource.getLimitValue();
		long defaultOffset = httpSource.getOffsetValue() == null || httpSource.getOffsetValue() <= 0 ? 0
				: httpSource.getOffsetValue();
		long offset = request.getWatermark().getOffset();
		if (request.getWatermark().isInitial() && request.getWatermark().isResync()){
			offset = defaultOffset;
        }
        DefaultDataOffsetIterator iterator = new DefaultDataOffsetIterator(request.getWatermark(),
        		offset, generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pgSize, request.getWatermark().getLimit());
		return new FetchResponse(request.getWatermark(), iterator);
	}
    
    private FetchResponse getCustorLinkInBodyIterator(SyncRequest request, HttpSourceConfigInfo httpSource, Map<String, Object> context) {
        HttpSourceCursorLinkGenerator generator = new HttpSourceCursorLinkGenerator(request, helper, httpSource, context);
		Integer pgSize = httpSource.getPageSize() == null || httpSource.getPageSize() <= 0 ? 100
				: httpSource.getPageSize();
		DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(request.getWatermark(),
            StringUtils.isNotBlank(request.getWatermark().getChangeStream())
                ? request.getWatermark().getChangeStream()
                : httpSource.getEndpoint(),
            request.getWatermark().getOffset(), generator, new ArrayList<>(), pgSize,
            request.getWatermark().getLimit(), true);
        return new FetchResponse(request.getWatermark(), iterator);
	}
    
    private FetchResponse getCustorParameterIterator(SyncRequest request, HttpSourceConfigInfo httpSource, Map<String, Object> context) {
        HttpSourceCursorParamGenerator generator = new HttpSourceCursorParamGenerator(request, helper, httpSource, context);
		Integer pgSize = httpSource.getPageSize() == null || httpSource.getPageSize() <= 0 ? 100
				: httpSource.getPageSize();
		DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(request.getWatermark(),
            StringUtils.isNotBlank(request.getWatermark().getChangeStream())
                ? request.getWatermark().getChangeStream()
                : StringUtils.isNotBlank(httpSource.getStartValue()) ? httpSource.getStartValue()
                    : HttpSourceCursorParamGenerator.EMPTY_START_VALUE_TOKEN,
				request.getWatermark().getOffset(), generator, new ArrayList<>(), pgSize,
				request.getWatermark().getLimit(), true);
        return new FetchResponse(request.getWatermark(), iterator);
	}
    
    private FetchResponse getPageNumberIterator(SyncRequest request, HttpSourceConfigInfo httpSource, Map<String, Object> context) {
        HttpSourcePageNumberGenerator generator = new HttpSourcePageNumberGenerator(request, helper, httpSource, context);
		int pgSize = httpSource.getPageSize() == null || httpSource.getPageSize() <= 0 ? 100
				: httpSource.getPageSize();
		int defaultPageNumber = httpSource.getPageNumberValue() == null || httpSource.getPageNumberValue() <= 0 ? 1
				: httpSource.getPageNumberValue();
        DefaultDataPageNumberIterator iterator = new DefaultDataPageNumberIterator(request.getWatermark(),
        		defaultPageNumber, generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pgSize, request.getWatermark().getLimit());
		return new FetchResponse(request.getWatermark(), iterator);
	}

	@Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
    	String entityName = request.getEntity();
    	EntitySchema entitySchema = null;
    	if(request.getConnector().getHttpSourceConfig() != null) {
    		var httpConfig = request.getConnector().getHttpSourceConfig().stream().filter(config -> config.getApiName().equals(entityName)).findFirst().orElse(null);
    		if(httpConfig != null) {
    		    httpConfig = updateSelectorXPaths(httpConfig);
    			entitySchema = new EntitySchema(entityName, StringUtils.capitalize(entityName));
    			if(!StringUtils.isBlank(httpConfig.getSchema())) {
    				var attribs = JsonSchemaHelper.getAttributesFromSchema(httpConfig.getSchema(), httpConfig.getRecordSelector(), httpConfig.getIdSelector(), httpConfig.getWmSelector());
    				for(var attr : attribs) {
    					entitySchema.addField(attr);
    				}
    			}
    			boolean designateCalledAtAsWM = entitySchema.getAttributes().stream().filter(attr -> attr.isWatermarkField()).findFirst().isEmpty();
    			boolean createId = entitySchema.getAttributes().stream().filter(attr -> attr.isIdField()).findFirst().isEmpty();
    			AttributeSchema headers = new AttributeSchema("headers", "object");
    			headers.setDisplayName("Headers");
    			entitySchema.addField(headers);
    			AttributeSchema body = new AttributeSchema("response_body", "object");
    			body.setDisplayName("Response Body");
    			entitySchema.addField(body);
    			AttributeSchema status = new AttributeSchema("status_code", "integer");
    			status.setDisplayName("Status Code");
    			entitySchema.addField(status);
    			AttributeSchema schemaError = new AttributeSchema("schema_error", "string");
    			schemaError.setDisplayName("Schema Error");
    			entitySchema.addField(schemaError);
    			AttributeSchema calledAt = new AttributeSchema("called_at", "datetime");
    			calledAt.setDisplayName("Called At");
    			calledAt.setWatermarkField(designateCalledAtAsWM);
    			calledAt.setUpdateable(false);
    			entitySchema.addField(calledAt);
    			if(createId) {
	    			AttributeSchema id = new AttributeSchema("id", "string");
	    			id.setDisplayName("ID");
	    			id.setIdField(true);
	    			id.setUpdateable(false);
	    			entitySchema.addField(id);
    			}
    		}
    	}
    	
    	return Optional.of(entitySchema);
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
    	return ConnectorHelper.withHttpErrorHandling(() -> {
            List<EntitySchema> results = new ArrayList<>();
            if(request.getConnector().getHttpSourceConfig() != null) {
            	List<HttpSourceConfigInfo> entitiesConfig = request.getConnector().getHttpSourceConfig();
            	for (int i = 0; i < entitiesConfig.size(); ++i) {
            		String entityApiName = entitiesConfig.get(i).getApiName();
            		Optional<EntitySchema> entity = describe(new DescribeRequest(request.getConnector(), entityApiName));
            		entity.ifPresent(e -> results.add(e));
            	}
            }
            log.debug("Successfully completed http sources describeall");
            return results;
        });
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
    	TestConnectionResponse testResponse = new TestConnectionResponse();
    	if(config.getAuthType() == AuthType.None) {
    	} else if(config.getAuthType() == AuthType.SimpleOAuth) {
          try{
              AuthConfig updatedConfig = refreshToken(config);
              testResponse.setAuthConfig(updatedConfig);
              log.info(format("Successfully authenticated OAuth credentials for %s", config.getName()));
              return testResponse;
          } catch (Exception e) {
            testResponse.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            testResponse.setMessage(StringUtils.isBlank(e.getMessage()) ? ConnectorErrorCodes.CONNECTION_ERROR : e.getMessage());
          }
          return testResponse;
    	}
    	else if(config.getAuthType() == AuthType.ApiKey || config.getAuthType() == AuthType.ApiSecretKey) {
    		Map<String, Object> context = new HashMap<String, Object>();
    		context.putAll(getAdditionConfig(config));
			if (CollectionUtils.isNotEmpty(config.getHttpSourceConfig())
					&& StringUtils.isNotBlank(config.getHttpSourceConfig().get(0).getEndpoint())) {
				var result = helper.execute(config, updateSelectorXPaths(config.getHttpSourceConfig().get(0)), context, true); // Use the first entry for testing
				if (result.getStatusCode() == 401 || result.getStatusCode() == 403) {
					testResponse = new TestConnectionResponse("Error when testing the authenticated connection",
							ConnectorErrorCodes.CONNECTION_ERROR, Arrays.asList(result.getBodyString()));
				}
			}
    	}
    	return testResponse;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("Http Sources does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("Http Sources does not support delete field");
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
    	throw new NotSupportedException("Http Sources does not support getbyids");    	
    }
    

    @Override
    public SyncResponse create(SyncRequest request) {
        throw new RuntimeException("Http Sources does not support create");
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        throw new RuntimeException("Http Sources does not support update");
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        throw new RuntimeException("Http Sources does not support delete");
    }
    
    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in Http Sources yet");
    }
    
    @Override
    public void deleteObject(DeleteObjectRequest request) {
        throw new RuntimeException("Http Sources does not support delete");
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }
    
    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }
    
    public HTTPSourceResult test(ConnectorInfo connector, HttpSourceConfigInfo sourceConfig, List<KeyValue> variableValues) {
        if(connector.getAuthType() == AuthType.SimpleOAuth) {
          AuthConfig auth = refreshToken(connector);
          connector.setAuthConfig(auth);
        }
        
        sourceConfig = updateSelectorXPaths(sourceConfig);
    	Map<String, Object> context = new HashMap<>();
    	if(CollectionUtils.isNotEmpty(variableValues)) {
    		variableValues.forEach(kv -> {
    			context.put(kv.get("name"), kv.get("value"));
    		});
    	}
    	return helper.execute(connector, sourceConfig, context, true);
    }
    
private Map<String, Object> evaluateVariables(Map<String, Object> variables,
    Map<String, Boolean> variableTypes, Map<String, Object> context) {
  Map<String, Object> resultMap = new HashMap<>();

  for (Map.Entry<String, Object> entry : variables.entrySet()) {
    String key = entry.getKey();
    Object value = entry.getValue();

    if (value == null) {
      continue;
    }
    Object resolvedValue = tokenHelper.resolveTokens((Map<String, Object>) context, value.toString()).y;

    if (variableTypes.containsKey(key)) {
      Datatype datatype = DatatypeFactory.getDatatype(key);
      if (datatype != null && resolvedValue instanceof String
          && ObjectType.VALUE.getName().equals(datatype.getName())) {
        Object parsedValue = ObjectType.VALUE.convertFromJsonString((String) resolvedValue);
        if (parsedValue != null) {
          resolvedValue = parsedValue;
        }
      }
    }

    resultMap.put(key, resolvedValue == null ? "" : resolvedValue);
  }

  return resultMap;
}

	@Override
	public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
	  try {
	    return getAccessTokenWithQueryParameters(oAuthRequest);
	  } catch (Exception e) {
	    return getAccessTokenWithAuthHeader(oAuthRequest);
      }
	}
	
	private AuthConfig getAccessTokenWithQueryParameters(OAuthRequest oAuthRequest) {
	  AuthConfig config = oAuthRequest.getConfig();
      String oauthUrl = config.getAccessTokenEndpoint();
      Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.CLIENT_CREDENTIALS,
              DefaultAuthTokenHandler.CLIENT_ID, config.getClientId(),
              DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret());

      var authConfig = tokenHandler.getAccessToken(oauthUrl, map);

      config.setAccessToken(authConfig.getAccessToken());
      if (StringUtils.isEmpty(authConfig.getRefreshToken())) {
          config.setRefreshToken(authConfig.getAccessToken());
      }else{
          config.setRefreshToken(authConfig.getRefreshToken());
      }
      config.setExpiresIn(authConfig.getExpiresIn());
        config.setLastRefreshed(authConfig.getLastRefreshed() == null ? Instant.now() : authConfig.getLastRefreshed());
      return config;
    }
	
	private AuthConfig getAccessTokenWithAuthHeader(OAuthRequest oAuthRequest) {
      AuthConfig config = oAuthRequest.getConfig();
      String oauthUrl = config.getAccessTokenEndpoint();
      Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.CLIENT_CREDENTIALS);
      String usrPwd = config.getClientId() + ":" + config.getClientSecret();
      String constrcutedAuthHeader = String.format("Basic %s", Base64.getEncoder().encodeToString(usrPwd.getBytes()));
      Map<String, String> headers = Map.of("Authorization", constrcutedAuthHeader);

      var authConfig = tokenHandler.getAccessToken(oauthUrl, map, headers);

      config.setAccessToken(authConfig.getAccessToken());
      if (StringUtils.isEmpty(authConfig.getRefreshToken())) {
          config.setRefreshToken(authConfig.getAccessToken());
      }else{
          config.setRefreshToken(authConfig.getRefreshToken());
      }
      config.setExpiresIn(authConfig.getExpiresIn());
        config.setLastRefreshed(authConfig.getLastRefreshed() == null ? Instant.now() : authConfig.getLastRefreshed());
      return config;
    }

	@Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
	  return getAccessToken(new OAuthRequest().setConfig(connector.getAuthConfig()));
    }

	@Override
	public String getOAuthUri(ConnectorInfo connector) {
		return null;
	}
	
	public HttpSourcesHelper getHelper() {
		return helper;
	}

	public void setHelper(HttpSourcesHelper helper) {
		this.helper = helper;
	}
	
     public void setTokenHandler(DefaultAuthTokenHandler tokenHandler) {
    this.tokenHandler = tokenHandler;
  }

    protected HttpSourceConfigInfo updateSelectorXPaths(HttpSourceConfigInfo config) {
      if(config != null) {
        config.setRecordSelector(toXPathForm(config.getRecordSelector()));
        config.setIdSelector(toXPathForm(config.getIdSelector()));
        config.setWmSelector(toXPathForm(config.getWmSelector()));
        config.setCreatedAtSelector(toXPathForm(config.getCreatedAtSelector()));
        config.setDeletedFlagSelector(toXPathForm(config.getDeletedFlagSelector()));
        config.setCreatedBySelector(toXPathForm(config.getCreatedBySelector()));
        config.setModifiedBySelector(toXPathForm(config.getModifiedBySelector()));
      }
      return config;
    }
	
    private String toXPathForm(String selector) {
      if (StringUtils.isNotBlank(selector)) {
        return selector.startsWith("/") ? selector : "/" + selector;
      }
      return selector;
    }

}