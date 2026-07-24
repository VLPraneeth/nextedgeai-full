package com.syncari.connector.stripe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.syncari.connector.stripe.StripeService.CHILD_SCHEMA_MAP;
import static com.syncari.connector.stripe.StripeService.LINE_ITEM_BIMAP;
import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;

@Slf4j
public class StripeRestClient extends SyncariEntityDataRestClient {

    private static final Set<String> DELETE_SUPPORTED = Set.of(StripeSeed.CUSTOMERS, StripeSeed.PRODUCTS, StripeSeed.SUBSCRIPTION_ITEMS, StripeSeed.INVOICE_ITEMS, StripeSeed.COUPONS);

    // Cannot update these with the same values
    private static final Map<String, List<String>> SAME_VALUE_UPDATE_RESTRICTION = Map.ofEntries(
            Map.entry(StripeSeed.CHARGES, List.of("customer"))
    );

    private static final Map<String, Map<String,String>> COUPON_ENTITY_APINAME_MAP = Map.of(
            StripeSeed.CUSTOMERS, Map.of("coupon", "discount-coupon"),
            StripeSeed.SUBSCRIPTIONS, Map.of("coupon", "discount-coupon")
    );

    private static final String INVOICE_COUPON_PATH = "discounts[%s].coupon.id";

    ObjectMapper mapper = new ObjectMapper();

    public DataWithCursor getDataWithCursor(String url, SyncRequest request, String prevCursor, String accountId, EntitySchema schema) {
        ResponseEntity<String> response = getResponse(url, addAccountToAuthConfig(accountId, request.getConnector().getAuthConfig()));
        ReadContext ctx = JsonPath.parse(response.getBody());
        List<EntityData> results = getResults(ctx, request, schema);
        String nextCursor = "";
        boolean end_of_stream = true;
        try {
            end_of_stream = !(boolean) ctx.read("has_more");
        } catch (JsonPathException e) {
            // Nothing, links not found in the response.
        }
        if (!end_of_stream && !results.isEmpty()) {
            nextCursor = results.get(results.size()-1).getId();
        }
        results = results.stream().sorted(Comparator.comparingLong(EntityData::getLastModified)).collect(Collectors.toList());
        return new DataWithCursor(prevCursor, nextCursor, results);
    }

    public List<EntityData> getByIds(SyncRequest request, List<String> ids, String url, String accountId) {
        EntitySchema schema = request.getEntitySchema();
        boolean isChild = StripeService.LINE_ITEM_MAP.containsKey(schema.getApiName());
        List<EntityData> results = new ArrayList<>();
        ids.forEach(id -> {
        	try {
                String parentId = "";
                if(isChild) {
                    if(!id.contains("#")) return;
                    String[] parts = id.split("#");
                    id = parts[1];
                    parentId = parts[0];
                }
        		EntityData byId = getById(request, url, accountId, id);
                if(isChild) {
                    byId.setId(parentId + "#" + id);
                }
        		results.add(byId);
			} catch (Exception e) {
				log.error("Error fetching record with id {}", id);
				log.error(ExceptionUtils.getStackTrace(e));
			}
        });
        return results;
    }

    private EntityData getById(SyncRequest request, String url, String accountId, String id) {
        ResponseEntity<String> response = getResponse(format(url, request.getEntityName(), id), addAccountToAuthConfig(accountId, request.getConnector().getAuthConfig()));
        ReadContext ctx = JsonPath.parse(response.getBody());
        String pathPrefix = "";
        if(request.getEntityName().equalsIgnoreCase(StripeSeed.DISCOUNTS)){
            pathPrefix = "discount";
        }
        EntityData entityData = parseJSON(ctx, request.getEntitySchema(), pathPrefix, Optional.of(request));
        if(LINE_ITEM_BIMAP.inverse().containsKey(request.getEntityName())) {
            pathPrefix = pathPrefix + "items.";
            EntitySchema childSchema = CHILD_SCHEMA_MAP.get(request.getEntityName());
            SyncRequest syncRequest = new SyncRequest();
            syncRequest.setEntitySchema(childSchema);
            syncRequest.setConnector(request.getConnector());
            List<EntityData> entityDataList = getChildItems(ctx, syncRequest, pathPrefix, entityData, childSchema);
            entityData.addValue("items", entityDataList);
        }
        return entityData;
    }

    public SyncResponse createOrUpdate(SyncRequest request, boolean update, String url, String accountId) {
        SyncResponse response = new SyncResponse(true);
        List<EntityData> entityDataList = request.getData().get(request.getConnector().getId());
        if (CollectionUtils.isEmpty(entityDataList)) {
            log.error("Nothing to be created for stripe");
            return new SyncResponse(false);
        }
        EntitySchema schema = request.getEntitySchema();
        Set<String> nestedFields = getNestedFields(schema.getAttributes());
        if (schema.getApiName().equalsIgnoreCase(StripeSeed.INVOICES) || schema.getApiName().equalsIgnoreCase(StripeSeed.INVOICE_ITEMS)){
            nestedFields.add("coupon");
        }
        url = url + format("/%s", request.getEntityName());
        for(EntityData data: entityDataList) {
            AtomicReference<String> subscriptionId = new AtomicReference<>(data.getValueAsString("subscription"));
            log.debug("Data - " + data.getValues().toString());
            MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
            String currentUrl = url;
            if(update) {
                String id = data.getId();
                boolean isChild = StripeService.LINE_ITEM_MAP.containsKey(schema.getApiName());
                if(isChild) {
                    if (!data.getId().contains("#")) {
                        Result result = new Result(false, id, data.getSyncariEntityId());
                        result.addError("Invalid id");
                        response.getResults().add(result);
                    }
                    ;
                    String[] parts = id.split("#");
                    id = parts[1];
                }
                currentUrl = url + format("/%s", id);
                // Refresh the data from the end system and replace with fields to update

                EntityData endSystemData = getEndSystemData(request, accountId, data, currentUrl);
                Map<String, Object> values = new HashMap<>();
                values.putAll(endSystemData.getValues());
                for (String k : values.keySet()) {
                    Optional<AttributeSchema> attributeSchema = schema.getField(k);
                    if (attributeSchema.isPresent() && attributeSchema.get().isReference()) {
                        Object syncariValue = data.getValue(k);
                        Object endSystemValue = endSystemData.getValue(k);
                        if (syncariValue != null && endSystemValue != null) {
                            if (syncariValue.equals(endSystemValue)) {
                                endSystemData.remove(k);
                            }
                        } else if(syncariValue == null) {
                            endSystemData.remove(k);
                        }
                    }
                    if(attributeSchema.isPresent() && (attributeSchema.get().getDataType().equalsIgnoreCase("datetime") || attributeSchema.get().getDataType().equalsIgnoreCase("timestamp"))
                            && values.get(k) != null) {
                        System.out.println(attributeSchema.get().getDataType() + ":" + k + ":" + values.get(k).getClass() + ":" + values.get(k));
                        endSystemData.addValue(k, (long) values.get(k) / 1000);
                    }
                    if(attributeSchema.isPresent() && attributeSchema.get().getDataType().equalsIgnoreCase("child")) {
                        endSystemData.remove(k);
                    }
                }
                data.getValues().forEach((k,v) -> {
                    if(endSystemData.has(k)) {
                        endSystemData.addValue(k, v);
                    }
                });
                Iterator<Map.Entry<String, Object>> it = endSystemData.getValues().entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Object> entry = it.next();
                    String k = entry.getKey();
                    Optional<AttributeSchema> attributeSchema = schema.getField(k);
                    if(attributeSchema.isPresent() && !attributeSchema.get().isUpdateable()) {
                        it.remove();
                    }
                }
                data = endSystemData;
            }
            data.getValues().forEach((k, v) -> {
                Optional<AttributeSchema> attributeSchema = schema.getField(k);
                if(attributeSchema.isPresent()) {
                    if (!update && attributeSchema.get().isInitializable() && !attributeSchema.get().isWatermarkField() && !attributeSchema.get().getDataType().equalsIgnoreCase("child")) {
                        addData(nestedFields, map, k, v, attributeSchema.get());
                    }
                    if (update && attributeSchema.get().isUpdateable() && !attributeSchema.get().isWatermarkField() && !(request.getEntityName().equalsIgnoreCase(StripeSeed.SUBSCRIPTION_ITEMS) && k.equalsIgnoreCase("subscription"))) {
                        addData(nestedFields, map, k, v, attributeSchema.get());
                    }
                    if (!update && attributeSchema.get().getDataType().equalsIgnoreCase("child")) {
                        if (LINE_ITEM_BIMAP.inverse().containsKey(request.getEntityName())) {
                            List<EntityData> childItems = (List<EntityData>) v;
                            for (int i = 0; i < childItems.size(); i++) {
                                EntityData childItem = childItems.get(i);
                                Map<String, Object> values = childItem.getValues();
                                for (String key : values.keySet()) {
                                    Object value = values.get(key);
                                    map.add("items[" + i + "][" + key + "]", value);
                                }
                            }
                        }
                    }
                    if(request.getEntityName().equalsIgnoreCase(StripeSeed.SUBSCRIPTION_ITEMS) && k.equalsIgnoreCase("subscription")) {
                        subscriptionId.set((String) v);
                    }
                }
            });
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(currentUrl);
            try {
                ResponseEntity<String> responseEntity = postFormDataURI(uriBuilder.build().toUri(), map, addAccountToAuthConfig(accountId, request.getConnector().getAuthConfig()));
                Map<String, Object> responseMap = mapper.readValue(responseEntity.getBody(), Map.class);
                String id = (String) responseMap.get("id");
                if (request.getEntityName().equalsIgnoreCase(StripeSeed.SUBSCRIPTION_ITEMS)) {
                    id = subscriptionId + "#" + id;
                }
                response.getResults().add(new Result(true, id, data.getSyncariEntityId()));
            } catch (Exception e) {
                Result result = new Result(false, data.getId(), data.getSyncariEntityId());
                result.addError(e.getMessage());
                response.getResults().add(result);
            }
        }
        return response;
    }

    private EntityData getEndSystemData(SyncRequest request, String accountId, EntityData data, String currentUrl) {
        //there are rules while updating line items, that fail if we send the entire existing record
        if (StripeSeed.INVOICE_ITEMS.equalsIgnoreCase(request.getEntityName()) || StripeSeed.SUBSCRIPTIONS.equalsIgnoreCase(request.getEntityName())) {
            return data;
        } else {
            return getById(request, currentUrl, accountId, data.getId());
        }
    }

    private void addData(Set<String> nestedFields, MultiValueMap<String, Object> map, String k, Object v, AttributeSchema attributeSchema) {
        String datatype = attributeSchema.getDataType();
        if (nestedFields.contains(k)) {
            String apiName = extractApiName(k);
            /*
            While creating/updating invoices/invoiceLineItem, we must support passing discounts object
            whose type is array of hashes.
            Ex: discounts[0][coupons]=abc and discounts[0][discount]=def
            Discounts object accepts coupons as well as discount in the array.
         */
            if (v instanceof List) {
                if (apiName.contains("coupon")) {
                    addDiscountItem(map, (List<Object>) v);
                } else {
                    List<String> list = (List<String>) v;
                    if (apiName.contains("applies_to")) {
                        for (int i = 0; i < list.size(); i++) {
                            map.add(apiName + "[" + i + "]", list.get(i));
                        }
                    } else {
                        int sub = apiName.indexOf("[");
                        String temp = apiName.substring(0, sub) + "[%s]" + apiName.substring(sub);
                        for (int i = 0; i < list.size(); i++) {
                            temp = String.format(temp, i);
                            map.add(temp, list.get(i));
                        }
                    }
                }

            } else {
                if (apiName.contains("coupon") && v != null) {
                    addDiscountItem(map, List.of(v));
                } else {
                    map.add(apiName, v);
                }
            }

        } else if (v instanceof JSONArray || v instanceof List) {
            List array = (List) v;
            for (int i = 0; i < array.size(); i++) {
                map.add(k + "[" + i + "]", array.get(i));
            }
        } else if("timestamp".equalsIgnoreCase(datatype) || "datetime".equalsIgnoreCase(datatype)) {
            if(v instanceof Instant) {
                map.add(k, ((Instant) v).toEpochMilli() / 1000);
            } else if (v instanceof Long) {
                map.add(k, v);
            } else if(v instanceof ZonedDateTime) {
                map.add(k, ((ZonedDateTime)v).toEpochSecond());
            }
        } else {
            log.debug("Adding value {} to map", v);
            map.add(k, v);
        }
    }

    private static void addDiscountItem(MultiValueMap<String, Object> map, List<Object> list) {
        for(int i = 0; i < list.size() ; i++){
            map.add(String.format("discounts[%d][coupon]", i), list.get(i));
        }
    }

    private String extractApiName(String k) {
        String[] parts = k.split("-");
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < parts.length; i++) {
            if(i == 0) sb.append(parts[i]);
            else sb.append("[" + parts[i] + "]");
        }
        return sb.toString();
    }

    private Set<String> getNestedFields(List<AttributeSchema> attributes) {
        return attributes.stream()
                .map(AttributeSchema::getApiName)
                .filter(apiName -> apiName.contains("-"))
                .collect(Collectors.toSet());
    }

    public void deleteByIds(SyncRequest request, List<String> ids, String url, String accountId) {
        if(!DELETE_SUPPORTED.contains(request.getEntityName())) {
            log.info("Delete not supported for entity {}", request.getEntityName());
        } else {
            ids.forEach(id -> {
                boolean isChild = StripeService.LINE_ITEM_MAP.containsKey(request.getEntitySchema().getApiName());
                if(isChild) {
                    if (!id.contains("#")) {
                        log.error("Invalid id {} for child entity {}", id, request.getEntityName());
                        return;
                    }
                    String[] parts = id.split("#");
                    id = parts[1];
                }
                delete(format(url, request.getEntityName(), id), addAccountToAuthConfig(accountId, request.getConnector().getAuthConfig()));
            });
        }
    }

    public String createWebhook(ConnectorInfo config, String webhookEndpoint, String url, List<String> events) {
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        for(int i = 0; i < events.size(); i++) {
            map.add("enabled_events[" + i + "]", events.get(i));
        }
        map.add("url", webhookEndpoint);
        if(config.getMetaConfig().containsKey("connectedAccountId") && StringUtils.isNotBlank(config.getMetaConfig().get("connectedAccountId").toString())) {
            map.add("connect", true);
        }
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url);
        try {
            ResponseEntity<String> responseEntity = postFormDataURI(uriBuilder.build().toUri(), map, config.getAuthConfig());
            Map<String, Object> responseMap = mapper.readValue(responseEntity.getBody(), Map.class);
            String id = (String) responseMap.get("id");
            String secret = (String) responseMap.get("secret");
            // Delete previous webhook if exists
            deleteWebhook(config, url);
            return id + ":" + secret;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid response for create webhook request");
        } catch (NonRetriableException e) {
            if(e.getStatusCode().equalsIgnoreCase("401 UNAUTHORIZED")) {
                throw new NonRetriableException(ErrorCodes.API_ERROR, "Invalid API Key provided", e.getStatusCode());
            } else {
                throw e;
            }
        }
    }

    public void deleteWebhook(ConnectorInfo config, String url) {
        Map<String, Object> metaConfig = config.getMetaConfig();
        if(metaConfig.containsKey("webhook_id")) {
            try {
                deleteWebhook(config, url, (String) metaConfig.get("webhook_id"));
            } catch (NonRetriableException e) {
                log.error("Deletion failed for Webhook with id {}", "webhook_id");
                if(e.getStatusCode().equalsIgnoreCase("401 UNAUTHORIZED")) {
                    log.error("Invalid API Key provided");
                }
            }
        }
    }

    private void deleteWebhook(ConnectorInfo config, String url, String id) {
        url = url + "/" + id;
        try {
            delete(url, config.getAuthConfig());
        } catch (NonRetriableException e) {
            log.error("Deletion failed for Webhook with id {}", "webhook_id");
            if(e.getStatusCode().equalsIgnoreCase("401 UNAUTHORIZED")) {
                log.error("Invalid API Key provided");
            }
        }
    }

    private List<EntityData> getResults(ReadContext ctx, SyncRequest request, EntitySchema schema) {
        List<EntityData> results = new ArrayList<>();
        JSONArray data = ctx.read("data");
        String pathPrefix = null;
        for(int i = 0; i < data.size(); i++) {
            pathPrefix = String.format("data[%d].", i);
            if(schema.getApiName().equalsIgnoreCase(StripeSeed.DISCOUNTS)){
                pathPrefix = String.format("data[%d].discount.", i);
            }
            EntityData entityData = parseJSON(ctx, schema, pathPrefix, Optional.of(request));
            results.add(entityData);
        }
        return results;
    }

    private List<EntityData> getChildItems(ReadContext ctx, SyncRequest request, String parentPathPrefix, EntityData parent, EntitySchema schema) {
        List<EntityData> results = new ArrayList<>();
        boolean hasMore = ctx.read(parentPathPrefix + "has_more");
        if (!hasMore) {
            JSONArray data = ctx.read(parentPathPrefix + "data");
            String pathPrefix;
            for (int i = 0; i < data.size(); i++) {
                pathPrefix = parentPathPrefix + String.format("data[%d].", i);
                EntityData entityData = parseJSON(ctx, schema, pathPrefix, Optional.of(request));
                entityData.setId(parent.getId() + "#" + entityData.getId());
                entityData.setChild(true);
                entityData.setParentId(parent.getId());
                entityData.setLastModified(parent.getLastModified());
                results.add(entityData);
            }
        } else {
            String nextUrl = ctx.read(parentPathPrefix + "url");
            String substring = nextUrl.substring(nextUrl.indexOf("/v1") + 3);
            String initialUrl = StripeService.BASE_URL + substring + "&limit=100";
            String nextPageUrl = StripeService.BASE_URL + substring + "&limit=100&starting_after=%s";
            String nextCursor = "";
            do {
                String url = initialUrl;
                if(StringUtils.isNotBlank(nextCursor)) {
                    url = String.format(nextPageUrl, nextCursor);
                }
                DataWithCursor dataWithCursor = getDataWithCursor(url, request, nextCursor, StripeService.getConnectedAccountId(request.getConnector()), schema);
                nextCursor = dataWithCursor.getNextPageURL();
                List<EntityData> childData = dataWithCursor.getData();
                childData.forEach(cd -> {
                    cd.setId(parent.getId() + "#" + cd.getId());
                    cd.setChild(true);
                    cd.setParentId(parent.getId());
                    cd.setLastModified(parent.getLastModified());
                });
                results.addAll(childData);
            } while (StringUtils.isNotBlank(nextCursor));
        }
        return results;
    }

    public EntityData parseJSON(ReadContext ctx, EntitySchema schema, String pathPrefix) {
        return parseJSON(ctx, schema, pathPrefix, Optional.empty());
    }

    public EntityData parseJSON(ReadContext ctx, EntitySchema schema, String pathPrefix, Optional<SyncRequest> request) {
        EntityData ed = new EntityData(schema.getApiName());
        schema.getAttributes().forEach(attr -> {
            boolean isCoupon = false;
            String apiName = attr.getApiName();
            if(COUPON_ENTITY_APINAME_MAP.containsKey(schema.getApiName()) && COUPON_ENTITY_APINAME_MAP.get(schema.getApiName()).containsKey(attr.getApiName())) {
                apiName = COUPON_ENTITY_APINAME_MAP.get(schema.getApiName()).get(attr.getApiName());
                isCoupon = true;
            }
            String path = pathPrefix + sanitizeApiName(apiName);
            try {
                if((schema.getApiName().equalsIgnoreCase(StripeSeed.INVOICES) || schema.getApiName().equalsIgnoreCase(StripeSeed.INVOICE_ITEMS)) && attr.getApiName().equalsIgnoreCase("coupon")) {
                    path = pathPrefix + "discounts";
                    JSONArray discounts = ctx.read(path);
                    List<String> coupons = new ArrayList<>();
                    for(int i = 0; i < discounts.size(); i++) {
                        path = pathPrefix + String.format(INVOICE_COUPON_PATH, i);
                        String value = ctx.read(path);
                        coupons.add(value);
                    }
                    if(!coupons.isEmpty()) {
                        ed.addValue("coupon", coupons);
                    }
                    return;
                }
                Object value = ctx.read(path);
                if (attr.getDataType().equalsIgnoreCase("child")) return;
                if ((attr.getDataType().equalsIgnoreCase("datetime") || attr.getDataType().equalsIgnoreCase("timestamp")) && value != null) {
                    if (value instanceof Integer) {
                        ed.addValue(attr.getApiName(), ZonedDateTime.ofInstant(Instant.ofEpochSecond((Integer) value), UTC).toEpochSecond() * 1000);
                    } else {
                        ed.addValue(attr.getApiName(), ZonedDateTime.ofInstant(Instant.ofEpochSecond((Long) value), UTC).toEpochSecond());
                    }
                    if (attr.isWatermarkField()) {
                        // we rely on created_at as the watermark field. Updates are processed in StripeEventProcessor where we update LastModified
                        ed.setCreatedAt(ZonedDateTime.ofInstant(Instant.ofEpochSecond((Integer) value), UTC).toEpochSecond() * 1000);
                        ed.setLastModified(ZonedDateTime.ofInstant(Instant.ofEpochSecond((Integer) value), UTC).toEpochSecond() * 1000);
                    }
                } else if (value instanceof Map && attr.getApiName().equalsIgnoreCase("metadata")) {
                    Map map = (Map)value;
                    ed.addValue(attr.getApiName(),map.toString());
                } else if (!attr.isReference()) {
                    ed.addValue(attr.getApiName(), value);
                    if (attr.isIdField()) {
                        ed.setId((String) value);
                    }
                } else if (value instanceof Map && attr.isReference()) {
                    Map<String, Object> valueMap = (Map<String, Object>) value;
                    if(ed.getName().equalsIgnoreCase(StripeSeed.COUPONS)){
                        ed.addValue(attr.getApiName(), valueMap.get("products"));
                    } else if (isCoupon) {
                        String couponId = (String) valueMap.get("id");
                        ed.addValue("coupon",couponId);
                    } else{
                        ed.addValue(attr.getApiName(), valueMap.get("id"));
                    }

                } else {
                    ed.addValue(attr.getApiName(),value);
                }
            } catch (PathNotFoundException e) {
                // attribute not included in response
                log.debug("Attribute {} not found in response json", attr);
            }
        });
        if(StripeService.LINE_ITEM_BIMAP.inverse().containsKey(schema.getApiName())) {
            String path = pathPrefix + "items.";
            EntitySchema childSchema = CHILD_SCHEMA_MAP.get(schema.getApiName());
            SyncRequest syncRequest = new SyncRequest();
            syncRequest.setEntitySchema(childSchema);
            syncRequest.setConnector(request.get().getConnector());
            List<EntityData> entityDataList = getChildItems(ctx, syncRequest, path, ed, childSchema);
            ed.addValue("items", entityDataList);
        }
        handleNonWMEntities(ed, request);
        if(schema.getApiName().equals(StripeSeed.DISCOUNTS)){
            //convert discount to customerObject
            EntityData customer = new EntityData(StripeSeed.getCustomerSchema().getApiName());
            ed.getValues().entrySet().forEach(entry -> {
                if(entry.getKey().equalsIgnoreCase("customer")){
                    customer.setId((String)entry.getValue());
                }
                customer.addValue("discount-"+entry.getKey(),entry.getValue());
            });
            return customer;
        }
        return ed;
    }

    private void handleNonWMEntities(EntityData ed, Optional<SyncRequest> request) {
        if(request.isPresent() && request.get().getWatermark() != null) {
            if (ed.getLastModified() == 0) {
                ed.setLastModified(request.get().getWatermark().getEnd());
            }
            if (ed.getCreatedAt() == 0) {
                ed.setCreatedAt(request.get().getWatermark().getEnd());
                ed.addValue("created", request.get().getWatermark().getEnd());
            }
        }
    }

    private String sanitizeApiName(String apiName) {
        return apiName.replace("-", ".");
    }

    private AuthConfig addAccountToAuthConfig(String accountId, AuthConfig config) {
        if(StringUtils.isNotBlank(accountId))
            return config.addHeader("Stripe-Account", accountId);
        return config;
    }
}
