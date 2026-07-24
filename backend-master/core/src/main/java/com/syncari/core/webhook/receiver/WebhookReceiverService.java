package com.syncari.core.webhook.receiver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.*;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.Connector;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.token.XPathTokenResolver;
import com.syncari.core.utils.JsonSchemaHelper;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component(Constants.WEBHOOK_RECEIVER)
public class WebhookReceiverService implements MetadataService, SynapseInfoService, WebhookService, AuthenticationService, CommonDataService {
    private static String ENDPOINT_FORMAT = "%s/api/v1/webhooks/whs/%s";
    
    @Autowired
    Transformer transformer;
    @Autowired
    TextUtil textUtil;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    AppConfig appConfig;
    @Autowired
    TokenHelper tokenHelper;

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
    	AuthMetadata none = new AuthMetadata(AuthType.None, List.of(), "None", "");
    	
    	AuthField signatureHeader = new AuthField();
    	signatureHeader.setDataType("text");
    	signatureHeader.setName("signatureHeader");
    	signatureHeader.setLabel("Signature Header");
    	signatureHeader.setDefaultValue("X-Signature");
    	AuthField hashAlgo = new AuthField();
    	hashAlgo.setDataType("radio");
    	hashAlgo.setName("hashAlgorithm");
    	hashAlgo.setLabel("Hash Algorithm");
    	hashAlgo.setDefaultValue("HmacSHA256");
        hashAlgo.setOptions(List.of(Map.of("label", "SHA-256", "value", "HmacSHA256"), Map.of("label", "SHA1", "value", "HmacSHA1")));
    	AuthField signature = new AuthField();
    	signature.setDataType("password");
    	signature.setName("clientSecret");
    	signature.setLabel("Client Secret");
    	AuthMetadata hmac = new AuthMetadata(AuthType.Hmac, List.of(signatureHeader, hashAlgo, signature), "HMAC", "");
    	
        AuthMetadata apiKey = ConnectorHelper.getApiKey();
        List<AuthField> apiKeyFields = new ArrayList<AuthField>(apiKey.getFields());
        AuthField apiKeyHeader = new AuthField();
        apiKeyHeader.setDataType("text");
        apiKeyHeader.setName("apiKeyHeader");
        apiKeyHeader.setLabel("API Key Header");
        apiKeyHeader.setDefaultValue("X-API-KEY");
        apiKeyFields.add(0, apiKeyHeader);
        apiKey.setFields(apiKeyFields);
      
        AuthField bearerTokenField = new AuthField();
        bearerTokenField.setDataType("password");
        bearerTokenField.setName("accessToken");
        bearerTokenField.setLabel("Bearer Token");
        AuthMetadata bearerToken = new AuthMetadata(AuthType.ApiSecretKey, List.of(bearerTokenField), "Bearer Token", "");
        
        AuthMetadata basic = ConnectorHelper.getUserPwd();
        basic.setLabel("Basic");
        
        return List.of(none, hmac, basic, bearerToken, apiKey);
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
        return Constants.WEBHOOK_RECEIVER;
    }

    public UIMetadata getUIMetadata() {
    	return new UIMetadata().setIconPath("/assets/icons/custom-synapse-default.svg")
                .setDisplayName("Webhook Receiver")
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
        return "31951324289172";
    }

	@Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
    	String entityName = request.getEntity();
    	EntitySchema entitySchema = null;
    	if(request.getConnector().getWebhookConfig() != null) {
    		var httpConfig = request.getConnector().getWebhookConfig();
    		entitySchema = new EntitySchema(entityName, StringUtils.capitalize(entityName));
    		if(!StringUtils.isBlank(httpConfig.getSchema())) {
    		  var attribs = JsonSchemaHelper.getAttributesFromSchema(httpConfig.getSchema(), httpConfig.getRecordSelector(), httpConfig.getIdSelector(), null);
    		  for(var attr : attribs) {
    		    entitySchema.addField(attr);
    		  }
    		}
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
    		AttributeSchema receivedAt = new AttributeSchema("received_at", "datetime");
    		receivedAt.setDisplayName("Received At");
    		receivedAt.setWatermarkField(true);
    		receivedAt.setUpdateable(false);
    		entitySchema.addField(receivedAt);
    		if(createId) {
    		  AttributeSchema id = new AttributeSchema("id", "string");
    		  id.setDisplayName("ID");
    		  id.setIdField(true);
    		  id.setUpdateable(false);
    		  entitySchema.addField(id);
    		}
    	}
    	return Optional.of(entitySchema);
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
    	return ConnectorHelper.withHttpErrorHandling(() -> {
            List<EntitySchema> results = new ArrayList<>();
            List<String> entityNames = new ArrayList<>();
            if(CollectionUtils.isNotEmpty(request.getActiveEntities())) {
              entityNames.addAll(request.getActiveEntities());
            } else {
              entityNames.add(TextUtil.createApiName(request.getConnector().getConnectorMetadataName()));
            }
            entityNames.forEach(entityApiName -> {
              Optional<EntitySchema> entity = describe(new DescribeRequest(request.getConnector(), entityApiName));
              entity.ifPresent(e -> results.add(e));
            });
            log.debug("Successfully completed webhook receiver describeall");
            return results;
        });
    }


    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("Webhook Receiver does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("Webhook Receiver does not support delete field");
    }
    
    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in Webhook Receiver yet");
    }
    
    @Override
    public void deleteObject(DeleteObjectRequest request) {
        throw new RuntimeException("Webhook Receiver does not support delete");
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }
    
    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }
    
    public List<EventData> test(WebhookRequest req) {
      return parseEventData(req);
   }

    @Override
    public String extractIdentifier(WebhookRequest request) {
      return request.getParams().getOrDefault("uniqueId", "").toString();
    }

    @Override
    public String getIdentifier(ConnectorInfo config) {
      if (StringUtils.isNotBlank(config.getEndpoint())) {
        int lastIndex = config.getEndpoint().lastIndexOf('/');
        if (lastIndex == -1 || lastIndex == config.getEndpoint().length() - 1) {
          return "";
        }
        return config.getEndpoint().substring(lastIndex + 1);
      }
      return "";
    }

    @Override
    public String getEndpoint() {
      return String.format(ENDPOINT_FORMAT, appConfig.getWebhookServerHost(), UUID.randomUUID().toString().replace("-", ""));
    }

    @Override
    public List<EventData> parseEventData(WebhookRequest request) {
      List<EventData> response = new ArrayList<>();
      try {
        var records = getRecords(request);
        records.forEach(rec -> {
          EventData eventData = new EventData();
          eventData.setOperation(Operation.create);
          eventData.setData(rec);
          response.add(eventData);
        });
      } catch (JsonProcessingException e) {
          log.error(ExceptionUtils.getStackTrace(e));
          throw new RuntimeException("Invalid request. The eventdata json is invalid");
      }
      log.info("Parsed {} records for webhook receiver", response.size());
      return response;
    }

    public String getWebhookResponse(Connector connector, String body, Map<String, Object> headers) {
        return Optional
                .ofNullable(connector.getMetadata().getResponseTemplate()).map(template -> {
                    return tokenHelper.resolveTokens(
                            Map.of("requestPayload", Optional.ofNullable(body).map(b -> b).orElse(""),
                                    "requestHeaders", headers),
                            template).x;
                }).orElse("");
    }
    
    public List<EntityData> getRecords(WebhookRequest request) throws JsonMappingException, JsonProcessingException {
      var config = request.getConfig().getWebhookConfig();
      List<String> entityNames = new ArrayList<>();
      if(CollectionUtils.isNotEmpty(request.getActiveEntities())) {
        entityNames.addAll(request.getActiveEntities());
      } else {
        entityNames.add(TextUtil.createApiName(request.getConfig().getConnectorMetadataName()));
      }
      List<EntityData> recs = new ArrayList<EntityData>();
      for(var entityApiName: entityNames) {
        EntitySchema schema = describe(new DescribeRequest(request.getConfig(), entityApiName)).get();
        if(StringUtils.isBlank(request.getBody())) {
          recs.add(createRecord(request, Map.of(), config, request.getBody(), schema, ZonedDateTime.now()));
        } else {
          List<Object> records = new ArrayList<Object>();
          Object bodyMap = JsonSchemaHelper.jsonNodeToMap(mapper.readTree(request.getBody()));
          if(!StringUtils.isBlank(config.getRecordSelector())) {
            if(bodyMap instanceof Map) {
              XPathTokenResolver xpathResolver = new XPathTokenResolver(config.getRecordSelector().trim());
              var resolved = xpathResolver.resolveToken((Map<String, Object>) bodyMap);
              if (resolved.hasTokenSyntaxErrors() || !resolved.isKeyFoundInContext()) {
                log.error("XPath evaluation failed {}. Taking the entire response body as a single record", config.getRecordSelector());
                records.add(bodyMap);
              } else if(resolved.getResolvedValue() instanceof List) {
                records.addAll((List<Object>)resolved.getResolvedValue());
              } else if(resolved.getResolvedValue() != null) {
                records.add(resolved.getResolvedValue());
              }
            } else if(bodyMap instanceof List) {
              log.error("Cannot use xpath in a array");
              records.addAll((List<Object>)bodyMap);
            }
            
          } else {
            log.debug("No record selector, taking entire response as a single record");
            if(bodyMap instanceof List) {
              records.addAll((List<Object>)bodyMap);
            } else {
              records.add(bodyMap);
            }
          }
          recs.addAll(records
              .stream().filter(rec -> rec instanceof Map).map(rec -> createRecord(request,
                  (Map<String, Object>) rec, config, bodyMap, schema, ZonedDateTime.now()))
              .collect(Collectors.toList()));
        }
      }
      return recs;
  }
  
    public EntityData createRecord(WebhookRequest request, Map<String, Object> jsonRec, WebhookConfigInfo config, Object fullBody, EntitySchema schema, ZonedDateTime receivedAt) {
      Map<String, Object> values = new HashMap<>();
      //Standard data
      values.put("headers", request.getHeaders());
      values.put("status_code", 200);
      if(StringUtils.isNotBlank(config.getSchema())) {
          if(MapUtils.isEmpty(jsonRec)) {
            values.put("schema_error", "Empty request payload");
          } else {
            try {
              values.put("schema_error", JsonSchemaHelper.validateJson(config.getSchema(), new ObjectMapper().writeValueAsString(jsonRec)));
            } catch (JsonProcessingException e) {
              values.put("schema_error", e.getMessage());
            }
          }
      }
      values.put("received_at", receivedAt);
      values.put("response_body", jsonRec);
      String id = JsonSchemaHelper.getFieldValueBySelector(jsonRec, config.getIdSelector()).orElse(DigestUtils.md5Hex(jsonRec.toString())).toString();
      values.put("id", id);
      jsonRec.forEach((k, v) -> {
          if(StringUtils.isBlank(k)) return;
          var attr = schema.getField(k).orElse(null);
          var key = attr == null ? k : attr.getApiName();
          values.put(key, v);
      });
      EntityData record = new EntityData().setValues(values);
      record.setId(id);
      record.setLastModified(receivedAt.toInstant().toEpochMilli());
      record.setName(schema.getApiName());
      record.setConnectorId(request.getConfig().getId());
      return record;
  }
    public boolean isValidRequest(WebhookRequest request) {
      switch (request.getConfig().getAuthType()) {
        case None:
          return true;
        case Hmac:
          return isValidHMac(request);
        case ApiKey:
          return isValidApiKey(request);
        case ApiSecretKey:
          return isValidBearerToken(request);
        case UserPassword:
          return isValidBasicAuth(request);
        default:
          return false;
      }
    }

    private boolean isValidBasicAuth(WebhookRequest request) {
      var authConfig = request.getConfig().getAuthConfig();
      String userName = authConfig.getUserName();
      String password = authConfig.getPassword();
      if(StringUtils.isEmpty(userName)) {
        log.warn("User name not configured {}",request.getConfig().getName());
        return false;
      }
      if(StringUtils.isEmpty(password)) {
        log.warn("Password not configured {}", request.getConfig().getName());
        return false;
      }
      String authHeader = request.getHeaders().get("authorization") == null ? null: request.getHeaders().get("authorization").toString();
      if(StringUtils.isEmpty(authHeader) || !authHeader.trim().startsWith("Basic ")) {
        log.warn("Invalid Authorization for {}",request.getConfig().getName());
        return false;
      }
      String usrPwd = userName + ":" + password;
      String constrcutedAuthHeader = String.format("Basic %s", Base64.getEncoder().encodeToString(usrPwd.getBytes()));
      if(constrcutedAuthHeader.equals(authHeader.trim())) {
        return true;
      }
      log.warn("Basic auth failed for {}", request.getConfig().getName());
      return false;
    }

    private boolean isValidBearerToken(WebhookRequest request) {
      var authConfig = request.getConfig().getAuthConfig();
      String accessToken = authConfig.getAccessToken();
      if(StringUtils.isEmpty(accessToken)) {
        log.warn("accessToken not configured {}",request.getConfig().getName());
        return false;
      }
      String authHeader = request.getHeaders().get("authorization") == null ? null: request.getHeaders().get("authorization").toString();
      if(StringUtils.isEmpty(authHeader) || !authHeader.trim().startsWith("Bearer ")) {
        log.warn("Invalid Authorization for {}",request.getConfig().getName());
        return false;
      }
      String constrcutedAuthHeader = String.format("Bearer %s", accessToken);
      if(constrcutedAuthHeader.equals(authHeader.trim())) {
        return true;
      }
      log.warn("Bearer  token auth failed for {}", request.getConfig().getName());
      return false;
    }

    private boolean isValidApiKey(WebhookRequest request) {
      var authConfig = request.getConfig().getAuthConfig();
      if(authConfig == null) {
        return false;
      }
      String accessToken = authConfig.getAccessToken();
      if(StringUtils.isEmpty(accessToken)) {
        log.warn("accessToken not configured {}",request.getConfig().getName());
        return false;
      }
      String apiKeyHeader = Optional.ofNullable(authConfig.getApiKeyHeader()).orElse("X-API-KEY").toLowerCase();
      String authHeader = request.getHeaders().get(apiKeyHeader) == null ? null: request.getHeaders().get(apiKeyHeader).toString();
      if(StringUtils.isEmpty(authHeader)) {
        log.warn("Invalid {} for {}", apiKeyHeader, request.getConfig().getName());
        return false;
      }
      if(accessToken.equals(authHeader.trim())) {
        return true;
      }
      log.warn("Api Key auth failed for {}", request.getConfig().getName());
      return false;
    }

    private boolean isValidHMac(WebhookRequest request) {
      var authConfig = request.getConfig().getAuthConfig();
      String clientSecret = authConfig.getClientSecret();
      String hashAlgorithm = authConfig.getHashAlgorithm();
      if (StringUtils.isEmpty(clientSecret)) {
        log.warn("clientSecret not configured {}", request.getConfig().getName());
        return false;
      }
      if (StringUtils.isEmpty(hashAlgorithm)) {
        log.warn("hashAlgorithm not configured {}", request.getConfig().getName());
        return false;
      }
      String signHeader = Optional.ofNullable(authConfig.getSignatureHeader()).orElse("X-Signature").toLowerCase();
      if (!request.getHeaders().containsKey(signHeader)) {
        log.warn("Invalid request. The signature header {} not present for {}", signHeader,
            request.getConfig().getName());
        return false;
      }

      String signature = request.getHeaders().get(signHeader).toString();
      String expectedSignature = hashAlgorithm.equalsIgnoreCase("HmacSHA256")
          ? "sha256=" + TextUtil.hmacSha256InHex(request.getBody(), clientSecret)
          : "sha1=" + TextUtil.hmacSha1InHex(request.getBody(), clientSecret);

      if (!expectedSignature.equalsIgnoreCase(signature)) {
        log.warn("Invalid request. The signatures do not match for {}",
            request.getConfig().getName());
        return false;
      }
      return true;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
      return new TestConnectionResponse();
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
      return new FetchResponse(request.getWatermark(), new ListBasedIterator(List.of(), request.getWatermark()));
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
      return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
      return List.of();
    }

    @Override
    public SyncResponse create(SyncRequest request) {
      throw new RuntimeException("Webhook Receiver does not support create");
    }

    @Override
    public SyncResponse update(SyncRequest request) {
      throw new RuntimeException("Webhook Receiver does not support update");
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
      throw new RuntimeException("Webhook Receiver does not support delete");
    }
    
    public Map<String, Object> buildWebhookAuthHeaders(AuthType authType, AuthConfig authConfig, String payload) {
      switch (authType) {
        case None:
          return Map.of();
        case Hmac:
          String clientSecret = authConfig.getClientSecret();
          String hashAlgorithm = authConfig.getHashAlgorithm();
          if(StringUtils.isNotEmpty(clientSecret) && StringUtils.isNotEmpty(hashAlgorithm)) {
            String signHeader = Optional.ofNullable(authConfig.getSignatureHeader()).orElse("X-Signature").toLowerCase();
            String expectedSignature = hashAlgorithm.equalsIgnoreCase("HmacSHA256")
                ? "sha256=" + TextUtil.hmacSha256InHex(payload, clientSecret)
                : "sha1=" + TextUtil.hmacSha1InHex(payload, clientSecret);
            return Map.of(signHeader, expectedSignature);
          } else {
            return Map.of();
          }
        case ApiKey:
          String apiKey = authConfig.getAccessToken();
          String apiKeyHeader = Optional.ofNullable(authConfig.getApiKeyHeader()).orElse("X-API-KEY").toLowerCase();
          if(StringUtils.isNotEmpty(apiKey)) {
            return Map.of(apiKeyHeader, apiKey);
          } else {
            return Map.of();
          }
        case ApiSecretKey:
          String accessToken = authConfig.getAccessToken();
          if(StringUtils.isNotEmpty(accessToken)) {
            return Map.of("authorization", String.format("Bearer %s", accessToken));
          } else {
            return Map.of();
          }
        case UserPassword:
          String userName = authConfig.getUserName();
          String password = authConfig.getPassword();
          if(userName != null && password != null) {
            String usrPwd = userName + ":" + password;
            return Map.of("authorization", String.format("Basic %s", Base64.getEncoder().encodeToString(usrPwd.getBytes())));
          } else {
            return Map.of();
          }
        default:
          return Map.of();
      }
    }
    
}