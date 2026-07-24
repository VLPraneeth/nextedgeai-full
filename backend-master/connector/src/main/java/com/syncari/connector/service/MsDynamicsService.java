package com.syncari.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.ODataEntityDataIterator;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.odata.SyncariODataClient;
import com.syncari.connector.service.def.*;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.olingo.client.api.uri.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;

@Slf4j
@Component(Constants.MSDYNAMICS)
public class MsDynamicsService implements OauthAuthenticationService, CommonDataService, 
    MetadataService, SynapseInfoService, WebhookService {
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;
    @Autowired
    DateUtil dateUtil;

    public static final String OAUTH_URL = "%s/oauth2/token";
    public static final String SERVICE_URL = "%s/api/data/v9.1";
    public static final String SERVICE_URL_AUTH_FIELD = "crmServiceURL"; 

    // Default number of entities to run describe per batch/call.
    // Note 25 is the maximum supported for the entitydefinition filter. We get below error when this limit is exceeded,
    // org.apache.olingo.client.api.communication.ODataClientErrorException: 
    // (0x80044183) Query on Entity has exceeded the maximum supported depth of filters (25). [HTTP/1.1 400 Bad Request]
	public static final int DESCRIBE_BATCH_SIZE = 25;
    public static final int DEFAULT_BATCH_SIZE = 25;

    public static final String NAMESPACE = "Microsoft.Dynamics.CRM";
    public static final String DEF_WATERMARK_FIELD = "modifiedon";
    public static final String FILTER_BY_WATERMARK = "%s gt %s and %s le %s ";
    public static final String FILTER_BY_WATERMARK_NO_END = "%s gt %s";
    private static final String dateFormat = "yyyy-MM-dd'T'HH:mm:ss";

    private final static Set<String> SYSTEM_FIELDS = Set.of("createdby", "createdon", "modifiedby", DEF_WATERMARK_FIELD);

    private final static List<String> WEBHOOL_DELETE_SUPPORTED_ENTITIES = List.of("contact", "account", "lead", "opportunity",
            "competitor", "quote", "salesorder", "invoice", "product");

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(new AuthMetadata(AuthType.Oauth,
            List.of(ConnectorHelper.getClientIdField(), ConnectorHelper.getClientSecretField()), "OAuth", ""));
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField crmServiceURL = new AuthField();
        crmServiceURL.setDataType("text");
        crmServiceURL.setName(SERVICE_URL_AUTH_FIELD);
        crmServiceURL.setLabel("Dynamics CRM Organization URL");
        crmServiceURL.setHelpSummary("Example: https://<organization>.crm.dynamics.com");
        AuthField endpointURL = ConnectorHelper.getEndpointField();
        endpointURL.setHelpSummary("Must contain Dynamics CRM tenantId. Example, https://login.microsoftonline.com/<tenantId>");
        return List.of(endpointURL, crmServiceURL, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "CRM";
    }
    
    @Override
    public String getName() {
        return Constants.MSDYNAMICS;
    }
    
     public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/msdynamics.svg")
                .setDisplayName("MS Dynamics 365")
                .setBackgroundColor("#EFF8FF")
                .setHelpUrl(helpArticlesBaseUrl + "/360056301891-MS-Dynamics-365-Setup");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19207184258964";
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        String filter = "";
        String wmField = request.getEntitySchema().getWatermarkField().getApiName();
        if (!request.getWatermark().hasEnd()) {
            filter = String.format(FILTER_BY_WATERMARK_NO_END, wmField,
                dateUtil.format(request.getWatermark().getStart(), dateFormat) + "Z");
        } else {
            filter = String.format(FILTER_BY_WATERMARK, 
                wmField, dateUtil.format(request.getWatermark().getStart(), dateFormat) + "Z",
                wmField, dateUtil.format(request.getWatermark().getEnd(), dateFormat) + "Z");
        }
        return new FetchResponse(request.getWatermark(), getClient(request.getConnector()).query(filter, request));
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        AttributeSchema idField = request.getEntitySchema().getIdField();
        if (idField == null) {
            log.error("Cannot find id field in the syncrequest. " +
                "The unique id field is a required field for getByIds request.");
            return new ArrayList<>();
        }
        List<String> ids = new ArrayList<>();
        if (!request.getData().isEmpty()) {
            for (EntityData data: request.getData().get(request.getConnector().getId())) {
                ids.add(data.getId());
            }
        }
        return filterByAttributeValues(request, idField.getApiName(), ids);
    }
    
    // TODO, we should make this work for all datatypes.
    public List<EntityData> filterByAttributeValues(SyncRequest request, String attributeName, List<String> values) {
        List<EntityData> entities = new ArrayList<>();
        if (values.isEmpty()) {
            log.error("Could not apply empty filter for filterByAttributeValues request due to empty filter values.");
            return entities;
        }
        List<List<String>> partitions = Lists.partition(values, DEFAULT_BATCH_SIZE);
        for (List<String> partition: partitions) {
            String filter = "";
            for (String val: partition) {
                if (StringUtils.isEmpty(filter)) {
                    filter = String.format("%s eq ('%s') ", attributeName, val);
                } else {
                    filter += String.format("or %s eq ('%s') ", attributeName, val);
                }
            }
            ODataEntityDataIterator iterator = getClient(request.getConnector()).query(filter, request);
            while (iterator.hasNext()) {
                entities.addAll(iterator.next());
            }
        }
        return entities;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        String wmField = request.getEntitySchema().getWatermarkField().getApiName();
        return withBackoffAndErrorHandling(() -> {
            SyncariODataClient client = getClient(request.getConnector());
            
            // TODO, do this the right way. We should get the plural name from MSD and preserve it in the entityschema.
            URIBuilder absoluteUri = client.getODataClient().newURIBuilder(client.getServiceURL())
                .appendNavigationSegment(request.getEntitySchema().getPluralName());
            absoluteUri.select(wmField);
            absoluteUri.orderBy(wmField);
            absoluteUri.top(1);
            List<Map<String, Object>> values = client.executeODataRequest(absoluteUri.build());
            if (values != null && values.size() > 0) {
                Map<String, Object> record = values.get(0);
                if (record.containsKey(wmField)) {
                    return dateUtil.toEpochMilli(record.get(wmField).toString()) - 1;
                }
            }
            log.warn("Could not get the first record's {} time for {}", wmField, request.getEntityName());
            return Instant.EPOCH.toEpochMilli();
        });
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        return ConnectorHelper.withHttpErrorHandling(() -> {
            return getClient(request.getConnector()).post(request, Operation.create);
        });
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return ConnectorHelper.withHttpErrorHandling(() -> {
            return getClient(request.getConnector()).post(request, Operation.update);
        });
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return ConnectorHelper.withHttpErrorHandling(() -> {
            return getClient(request.getConnector()).post(request, Operation.delete);
        });
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        try {
            List<String> entities = new ArrayList<>();
            entities.add(request.getEntity());
            DescribeAllRequest describeAllRequest = new DescribeAllRequest(request.getConnector(), entities);
            List<EntitySchema> entitySchemas = describeAll(describeAllRequest);
            if (entitySchemas.size() > 0) {
                return Optional.of(entitySchemas.get(0));
            }
        } catch (Exception e) {
            log.error("Failed to get entity definition for " + request.getEntity(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> entitySchemas = new ArrayList<>();
        try {
            SyncariODataClient client = getClient(request.getConnector());

            List<String> entitiesToDescribe = request.getEntities();
            if (CollectionUtils.isEmpty(entitiesToDescribe)) {
                entitiesToDescribe = new ArrayList<>();
                URIBuilder absoluteUri = client.getODataClient().newURIBuilder(client.getServiceURL())
                        .appendEntitySetSegment("EntityDefinitions");
                absoluteUri.select("LogicalName");
                List<Map<String, Object>> vals = client.executeODataRequest(absoluteUri.build());
                for (Map<String, Object> val: vals) {
                    entitiesToDescribe.add(val.get("LogicalName").toString());
                }
            }

            List<List<String>> partitions = Lists.partition(entitiesToDescribe, DESCRIBE_BATCH_SIZE);
            for (List<String> partition: partitions) {
                URIBuilder absoluteUri = client.getODataClient().newURIBuilder(client.getServiceURL())
                    .appendEntitySetSegment("EntityDefinitions");
                String entityFilters = null;
                for (String entity: partition) {
                    if (entityFilters == null) {
                        entityFilters = String.format("LogicalName eq ('%s') ", entity);
                    } else {
                        entityFilters += String.format(" or LogicalName eq ('%s')", entity);
                    }
                }
                absoluteUri.filter(entityFilters);
                absoluteUri.select("LogicalName", "EntitySetName", "DisplayName", "Description", "IsCustomEntity");
                absoluteUri.expand("Attributes");
                absoluteUri.expandWithSelect("ManyToOneRelationships", 
                    "ReferencingAttribute", "ReferencedAttribute", "ReferencedEntity");
                List<Map<String, Object>> values = client.executeODataRequest(absoluteUri.build());
                if (!CollectionUtils.isEmpty(values)) {
                    for (Map<String, Object> entityDefinition: values) {
                        final String entityName = entityDefinition.get("LogicalName").toString();
                        log.info("Processing entity: " + entityName);
                        try {
                            entitySchemas.add(client.toEntitySchema(entityDefinition, DEF_WATERMARK_FIELD, SYSTEM_FIELDS));
                        } catch (NonRetriableException ex) {
                            log.error("Skipping entityschema for entity: {}, failed due to {} ", entityName, ex.getMessage(), ex);
                        }
                    }
                }
                log.info("Done processing batch of {} entities for describe", DESCRIBE_BATCH_SIZE);
            }
        } catch (IOException e) {
            log.error("Failed to get entity definitions ", e);
            return new ArrayList<>();
        }
        return entitySchemas;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("MSD does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("MSD does not support delete field");
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in MSD yet");
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }
    
    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        config.setEndpoint(config.getEndpoint());
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.REFRESH_TOKEN,
                DefaultAuthTokenHandler.REFRESH_TOKEN, config.getRefreshToken(),
                DefaultAuthTokenHandler.CLIENT_ID, config.getClientId(), 
                DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret());
        
        return tokenHandler.refreshToken(config, String.format(OAUTH_URL, config.getEndpoint()), map);
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        oAuthRequest.setEndpoint(oAuthRequest.getEndpoint());
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, "authorization_code", 
                DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(), 
                DefaultAuthTokenHandler.CLIENT_ID, oAuthRequest.getConfig().getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, oAuthRequest.getConfig().getClientSecret(), 
                DefaultAuthTokenHandler.RESOURCE, getCRMServiceURL(oAuthRequest.getMetaConfig()), 
                DefaultAuthTokenHandler.REDIRECT_URI, oAuthRequest.getRedirectUri());

        return tokenHandler.getAccessToken(String.format(OAUTH_URL, oAuthRequest.getEndpoint()), map);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            SyncariODataClient client = getClient(config);
            URIBuilder absoluteUri = client.getODataClient().newURIBuilder(client.getServiceURL())
                .appendEntitySetSegment("EntityDefinitions");
            absoluteUri.select("LogicalName");
            List<Map<String, Object>> vals = client.executeODataRequest(absoluteUri.build());
            log.info("EntitySets found: " + vals);
            log.info("EntitySets Count: " + vals.size());
        } catch (IOException e) {
            log.error("MS Dynamics testConnection failed due to " + e.getMessage());
            handleAuthenticationErrorMessage(response, e);
        }
        return response;
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        // TODO the scopes need a revisit, this is temporary to get the auth working for demo
        return "/oauth2/v2.0/authorize?redirect_uri={{redirect_uri}}&client_id={{client_id}}&response_type=code&scope=offline_access https://graph.microsoft.com/User.Read";
    }
    
    @Override
    public String getAuthHost(AuthConfig config) {
        return config.getEndpoint();
    }

    public SyncariODataClient getClient(ConnectorInfo connector) {
        return SyncariODataClient.builder().connector(connector)
            .dateUtil(dateUtil).mapper(mapper).namespace(NAMESPACE)
            .serviceURL(String.format(SERVICE_URL, getCRMServiceURL(connector.getMetaConfig())))
            .oAuthURL(connector.getAuthConfig().getEndpoint())
            .build();
    }

    protected String getCRMServiceURL(Map<String, Object> metaConfig) {
        if (!metaConfig.containsKey(SERVICE_URL_AUTH_FIELD)) {
            // log error with root cause to help debug.
            log.error("Dynamics CRM Organization URL not configured or empty");
            return "";
        }
        String crmServiceUrl = metaConfig.get(SERVICE_URL_AUTH_FIELD).toString();
        if(crmServiceUrl.endsWith("/")){
            return StringUtils.chop(crmServiceUrl);
        }
        return crmServiceUrl;
    }

    @Override
    public String extractIdentifier(WebhookRequest request) {
        Map<String, Object> headers = request.getHeaders();
        if(!headers.containsKey("x-ms-dynamics-organization")) {
            throw new RuntimeException("Invalid webhook request. Missing org id in header");
        }
        return (String) headers.get("x-ms-dynamics-organization");
    }

    @Override
    public String getIdentifier(ConnectorInfo config) {
        String crmServiceUrl = getCRMServiceURL(config.getMetaConfig());
        try {
            URI uri = URI.create(crmServiceUrl);
            return uri.getHost();
        } catch (Exception e) {
            throw new RuntimeException("Error extracting host from given service url");
        }
    }

    @Override
    public String getEndpoint() {
        return null;
    }

    @Override
    public List<EventData> parseEventData(WebhookRequest request) {
        List<EventData> response = new ArrayList<>();
        Map<String, Object> params = request.getParams();
        if(!params.containsKey("code")) {
            throw new RuntimeException("Invalid webhook request. Missing auth code param");
        }
        Map<String, Object> metaConfig = request.getConfig().getMetaConfig();
        List<String> codes = (List<String>) params.get("code");
        if(!codes.isEmpty() && !codes.get(0).equalsIgnoreCase((String) metaConfig.get("webhook_signing_secret"))) {
            throw new RuntimeException("Invalid webhook request. Wrong auth code param");
        }
        try {
            Map<String, Object> body = mapper.readValue(request.getBody(), Map.class);
            if(!body.containsKey("MessageName") || !((String) body.get("MessageName")).equalsIgnoreCase("Delete") ||
                    !body.containsKey("PrimaryEntityId") || !body.containsKey("PrimaryEntityName")) {
                throw new RuntimeException("Invalid webhook request. Missing required fields");
            }
            EntityData entityData = new EntityData((String)body.get("PrimaryEntityName"));
            entityData.setId((String)body.get("PrimaryEntityId"));
            entityData.setDeleted(true);
            entityData.setLastModified(ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).toInstant().toEpochMilli());
            entityData.setConnectorId(request.getConfig().getId());
            response.add(new EventData().setData(entityData).setOperation(Operation.delete));
        } catch (Exception e) {
            throw new RuntimeException("Error in parsing webhook data - " + e.getMessage());
        }
        return response;
    }

    @Override
    public boolean webhookCreatable() {
        return true;
    }

    @Override
    public String createWebhook(ConnectorInfo config, String spectrumHost) {
        try {
            if(StringUtils.isNotBlank((String) config.getMetaConfig().get("webhook_id")) && StringUtils.isNotBlank((String) config.getMetaConfig().get("webhook_signing_secret"))) {
                // Check if webhook is active
                if(getClient(config).isWebhookActive()) {
                    return config.getMetaConfig().get("webhook_id") + ":" + config.getMetaConfig().get("webhook_signing_secret");
                }
            }
            String webhookEndpoint = spectrumHost + "/arcade/api/v1/webhooks/msdynamics";
            if(StringUtils.isNotBlank(config.getAuthConfig().getAccessToken())) {
                return getClient(config).createWebhook(WEBHOOL_DELETE_SUPPORTED_ENTITIES, webhookEndpoint);
            } else {
                // return placeholder : since webhook create was invoked before fetching access token
                return ":";
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create webhook with exception - " + e.getMessage());
        }
    }

    @Override
    public void deleteWebhook(ConnectorInfo config) {
        try {
            getClient(config).deleteWebhook();
        } catch (Exception e) {
            log.error("Failed to delete webhook with exception - {}", e.getMessage());
        }
    }
}
