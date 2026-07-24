package com.syncari.connector.chargebee;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.service.def.*;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component(Constants.CHARGEBEE)
public class ChargebeeService implements AuthenticationService, CommonDataService, MetadataService, SynapseInfoService, WebhookService {

    public static final String GET_BY_WATERMARK_INITIAL_URL = "https://%s.chargebee.com/api/v2/%s?limit=%s&updated_at[after]=%s&updated_at[before]=%s&sort_by[asc]=updated_at";

    public static final String GET_BY_WATERMARK_URL = "https://%s.chargebee.com/api/v2/%s?limit=%s&offset=%s&updated_at[after]=%s&updated_at[before]=%s&sort_by[asc]=updated_at";

//    public static final String DELETE_BY_STATUS_URL = "&status[in]=[active,archived,deleted]";

    public static final String DELETE_BY_STATUS_URL = "";

    public static final String INCLUDE_DELETES_URL = "&include_deleted=true";

    public static final String BY_ID_URL = "https://%s.chargebee.com/api/v2/%s/%s";

    public static final String CREATE_URL = "https://%s.chargebee.com/api/v2/%s";

    public static final String SUBSCRIPTION_CREATE_URL = "https://%s.chargebee.com/api/v2/customers/%s/subscription_for_items";

    public static final String SUBSCRIPTION_UPDATE_URL = "https://%s.chargebee.com/api/v2/%s/%s/update_for_items";

    public static final String INVOICE_CREATE_URL = "https://%s.chargebee.com/api/v2/invoices/create_for_charge_items_and_charges";

    public static final String INVOICE_UPDATE_URL = "https://%s.chargebee.com/api/v2/%s/%s/update_details";

    public static final String DELETE_BY_ID_URL = "https://%s.chargebee.com/api/v2/%s/%s/delete";

    public static final String QUOTE_GET_BY_WATERMARK_INITIAL_URL = "https://%s.chargebee.com/api/v2/%s?limit=%s&updated_at[after]=%s&updated_at[before]=%s&sort_by[asc]=date";

    public static final String QUOTE_GET_BY_WATERMARK_URL = "https://%s.chargebee.com/api/v2/%s?limit=%s&offset=%s&updated_at[after]=%s&updated_at[before]=%s&sort_by[asc]=date";

    public static final Integer API_MAX_PAGESIZE = 100;

    private static final Map<String, String> SUPPORTED_EVENTS = Map.of("customer_deleted", ChargebeeSeed.CUSTOMERS,
            "payment_source_deleted", ChargebeeSeed.PAYMENT_SOURCES, "invoice_deleted", ChargebeeSeed.INVOICES,
            "subscription_deleted", ChargebeeSeed.SUBSCRIPTIONS, "quote_deleted", ChargebeeSeed.QUOTES,
            "item_deleted", ChargebeeSeed.ITEMS, "item_family_deleted", ChargebeeSeed.ITEM_FAMILIES,
            "item_price_deleted", ChargebeeSeed.ITEM_PRICES);

    ObjectMapper mapper = new ObjectMapper();

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse testConnectionResponse = new TestConnectionResponse();
        try {
            String site = config.getMetaConfig().get("site").toString();
            String url = format(GET_BY_WATERMARK_INITIAL_URL, site, ChargebeeSeed.CUSTOMERS, 1, Instant.EPOCH.toEpochMilli()/1000, Instant.now().toEpochMilli()/1000);
            config.getAuthConfig().addHeader("AuthType", "ApiKeyAsUsername");
            ChargebeeRestClient restClient = new ChargebeeRestClient();
            ResponseEntity<String> response = restClient.getResponse(url, config.getAuthConfig());
            if(response.getStatusCode() != HttpStatus.OK) {
                testConnectionResponse.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
                testConnectionResponse.setMessage(I18n.i18n("invalid_token_bearer"));
            } else {
                testConnectionResponse.setAuthConfig(config.getAuthConfig());
            }
        } catch (Exception e) {
            handleAuthenticationErrorMessage(testConnectionResponse, e);
        }
        return testConnectionResponse;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19202228275348";
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize,
                                                                               cursor) -> {
            ChargebeeRestClient restClient = new ChargebeeRestClient();
            String url = format(getEntityName(request.getEntityName()).equalsIgnoreCase(ChargebeeSeed.QUOTES) ? QUOTE_GET_BY_WATERMARK_URL : GET_BY_WATERMARK_URL, request.getConnector().getMetaConfig().get("site").toString(), getEntityName(request.getEntityName()), pageSize, cursor,
                    request.getWatermark().getStart()/1000, request.getWatermark().getEnd()/1000);

            if(StringUtils.isEmpty(cursor)) {
                url = format(getEntityName(request.getEntityName()).equalsIgnoreCase(ChargebeeSeed.QUOTES) ? QUOTE_GET_BY_WATERMARK_INITIAL_URL : GET_BY_WATERMARK_INITIAL_URL, request.getConnector().getMetaConfig().get("site").toString(), getEntityName(request.getEntityName()), pageSize,
                        request.getWatermark().getStart()/1000, request.getWatermark().getEnd()/1000);
            }

            if(ChargebeeSeed.DELETED_BY_STATUS.contains(request.getEntityName())) {
                url = url + DELETE_BY_STATUS_URL;
            } else {
                url = url + INCLUDE_DELETES_URL;
            }

            return restClient.getDataWithCursor(url, request, cursor);
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();

        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(request.getWatermark(),
                request.getWatermark().getChangeStream(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                pgSize, request.getWatermark().getLimit(), true);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private String getEntityName(String entityName) {
        if(ChargebeeSeed.LINE_ITEMS.containsKey(entityName)) {
            return ChargebeeSeed.getSchema(ChargebeeSeed.LINE_ITEMS.get(entityName)).getApiName();
        }
        return entityName;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        ChargebeeRestClient restClient = new ChargebeeRestClient();
        Set<String> ids = new HashSet<>(request.getIds());
        Set<String> idsCopy = new HashSet<>(request.getIds());
        boolean isLineItem = false;
        if(ChargebeeSeed.LINE_ITEMS.containsKey(request.getEntityName())) {
            isLineItem = true;
            ids = request.getIds().stream().filter(Objects::nonNull).map(id -> id.split("#")[0]).collect(Collectors.toSet());
            request = request.withEntitySchema(ChargebeeSeed.getSchema(ChargebeeSeed.LINE_ITEMS.get(request.getEntityName())));
        }
        List<EntityData> results = new ArrayList<>();
        for(String id: ids) {
            String url = format(BY_ID_URL, request.getConnector().getMetaConfig().get("site").toString(), getEntityName(request.getEntityName()), id);
            List<EntityData> result = restClient.getById(url, request);
            results.addAll(result);
        }
        if(!isLineItem) {
            return results;
        } else {
            String entityName = ChargebeeSeed.LINE_ITEM_BIMAP.inverse().get(request.getEntityName());
            return results.stream().flatMap(item -> item.getChildrenRecords(entityName).stream())
                    .filter(ed -> idsCopy.contains(ed.getId())).collect(Collectors.toList());
        }
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        if(request.getEntityName().equalsIgnoreCase(ChargebeeSeed.ORDERS)) {
            log.error("Order create not supported by Chargebee");
            return new SyncResponse(false);
        }
        String url = getCreateUrl(request);
        return createOrUpdate(request, url, false);
    }

    private String getCreateUrl(SyncRequest request) {
        switch (request.getEntityName()) {
            case ChargebeeSeed.SUBSCRIPTIONS:
                return SUBSCRIPTION_CREATE_URL;
            case ChargebeeSeed.INVOICES:
                return INVOICE_CREATE_URL;
            default:
                return CREATE_URL;
        }
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        String url = getUpdateUrl(request);
        return createOrUpdate(request, url, true);
    }

    private SyncResponse createOrUpdate(SyncRequest request, String url, boolean isUpdate) {
        ChargebeeRestClient restClient = new ChargebeeRestClient();
        SyncResponse response = new SyncResponse(true);
        List<EntityData> entityDataList = request.getData().get(request.getConnector().getId());
        if (CollectionUtils.isEmpty(entityDataList)) {
            log.error("Nothing to be created/updated for chargebee");
            return new SyncResponse(false);
        }
        if(isUpdate) {
            restClient.updateRecords(request, url, getEntityName(request.getEntityName()), response, entityDataList);
        } else {
            restClient.createRecords(request, url, response, entityDataList, getEntityName(request.getEntityName()));
        }
        return response;
    }

    private String getUpdateUrl(SyncRequest request) {
        switch (request.getEntityName()) {
            case ChargebeeSeed.SUBSCRIPTIONS:
                return SUBSCRIPTION_UPDATE_URL;
            case ChargebeeSeed.INVOICES:
                return INVOICE_UPDATE_URL;
            default:
                return BY_ID_URL;
        }
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        if(request.getEntityName().equalsIgnoreCase(ChargebeeSeed.INVOICES) || request.getEntityName().equalsIgnoreCase(ChargebeeSeed.ORDERS)) {
            log.error("{} delete not supported by Chargebee", request.getEntityName());
            return new SyncResponse(false);
        }
        ChargebeeRestClient restClient = new ChargebeeRestClient();
        restClient.deleteRecords(request, getEntityName(request.getEntityName()), DELETE_BY_ID_URL);
        return new SyncResponse(true);
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        return Optional.of(ChargebeeSeed.getSchema(request.getEntity()));
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> result = new ArrayList<>();
        ChargebeeSeed.SUPPORTED_ENTITIES.forEach(entity -> result.add(ChargebeeSeed.getSchema(entity)));
        return result;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        return null;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        return null;
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {

    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getApiKey());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField teamId = new AuthField().setName("site").setLabel(i18n("domain_name"))
                .setRequired(true)
                .setDataType("text").setHelpSummary(i18n("domain_name_summary"));
        AuthField webhookId = new AuthField().setName("webhookId").setLabel(i18n("webhook_id"))
                .setRequired(true)
                .setDataType("text").setHelpSummary(i18n("webhook_id_summary"));
        AuthField webhookUser = new AuthField().setName("webhookUser").setLabel(i18n("webhook_user"))
                .setRequired(true)
                .setDataType("text").setHelpSummary(i18n("webhook_user_summary"));
        AuthField webhookPassword = new AuthField().setName("webhookPassword").setLabel(i18n("webhook_password"))
                .setRequired(true)
                .setDataType("password").setHelpSummary(i18n("webhook_password_summary"));
        return List.of(teamId, webhookId, webhookUser, webhookPassword, ConnectorHelper.getSupportedAuthPicker());
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
        return Constants.CHARGEBEE;
    }

    @Override
    public String getCategory() {
        return "Subscription Management";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/chargebee.svg")
                .setDisplayName("Chargebee")
                .setBackgroundColor("#EFF2F6")
                .setHelpUrl(helpArticlesBaseUrl + "/4964078779412");
    }

    @Override
    public String extractIdentifier(WebhookRequest request) {
        String id = "";
        try {
            Map<String, Object> map = mapper.readValue(request.getBody(), Map.class);
            if(map.containsKey("webhooks")) {
                List<Map<String, String>> webhooks = (List)map.get("webhooks");
                for(Map<String, String> webhookMap: webhooks) {
                    if (webhookMap.containsKey("id")) {
                        id = webhookMap.get("id");
                        break;
                    } else {
                        handleInvalidJson();
                    }
                }
            } else {
                handleInvalidJson();
            }
        } catch (JsonProcessingException e) {
            handleInvalidJson();
        }
        return id;
    }

    private void handleInvalidJson() {
        throw new RuntimeException("Invalid request. The eventdata json is invalid");
    }

    @Override
    public String getIdentifier(ConnectorInfo config) {
        return config.getMetaConfig().get("webhookId").toString();
    }

    @Override
    public String getEndpoint() {
        return null;
    }

    @Override
    public List<EventData> parseEventData(WebhookRequest request) {
        validateCaller(request);
        List<EventData> response = new ArrayList<>();
        try {
            Map<String, Object> map = mapper.readValue(request.getBody(), Map.class);
            if(map.containsKey("event_type") && SUPPORTED_EVENTS.containsKey((String)map.get("event_type"))) {
                Map<String, Object> content = (Map<String, Object>) map.get("content");
                for(String entityName: content.keySet()) {
                    if(ChargebeeRestClient.getSingularName(SUPPORTED_EVENTS.get((String)map.get("event_type"))).equalsIgnoreCase(entityName)) {
                        String entity = getPluralName(entityName);
                        EntityData entityData = new EntityData(entity);
                        Map<String, Object> entityMap = (Map<String, Object>) content.get(entityName);
                        String id = (String) entityMap.get("id");
                        if(entity.equalsIgnoreCase(ChargebeeSeed.QUOTES)) {
                            String[] parts = id.split(("_"));
                            if(parts.length >= 0) {
                                id = parts[0];
                            }
                        }
                        entityData.setId(id);
                        entityData.setConnectorId(request.getConfig().getId());
                        entityData.setDeleted(true);
                        Long updatedAt = (Integer)entityMap.get("updated_at") * 1000l;
                        if (null != updatedAt){
                            entityData.setLastModified(updatedAt);
                        }else{
                            entityData.setLastModified(ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).toInstant().toEpochMilli());
                        }
                        response.add(new EventData().setData(entityData).setOperation(Operation.delete));
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Invalid request. The eventdata json is invalid");
        }
        log.info("Parsed {} records for chargebee", response.size());
        return response;
    }

    private String getPluralName(String entityName) {
        return entityName.equalsIgnoreCase("item_family") ? ChargebeeSeed.ITEM_FAMILIES : entityName + "s";
    }

    private void validateCaller(WebhookRequest request) {
        Map<String, Object> headers = request.getHeaders();
        if(!headers.containsKey("authorization")) {
            throw new RuntimeException("Authorization not present in webhook request");
        }
        String authHeader = (String) headers.get("authorization");
        String webhookUserPwd = request.getConfig().getMetaConfig().get("webhookUser").toString() + ":" + request.getConfig().getMetaConfig().get("webhookPassword").toString();
        if(!authHeader.equalsIgnoreCase(getAuthHeader(webhookUserPwd))) {
            throw new RuntimeException("Webhooki authorization failure. Invalid Username/Password");
        }
    }

    private String getAuthHeader(String auth) {
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(Charset.forName("US-ASCII")));
        return "Basic " + new String(encodedAuth);
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.schemaCreateField);
        return capabilities;
    }
}
