package com.syncari.connector.chargebee;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;

@Slf4j
public class ChargebeeRestClient extends SyncariEntityDataRestClient {

    public static final Set<String> SUPPORTED_INVOICE_ENTITY_TYPES = Set.of("plan_item_price", "addon_item_price", "charge_item_price");

    public DataWithCursor getDataWithCursor(String url, SyncRequest request, String prevCursor) {
        ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
        ReadContext ctx = JsonPath.parse(response.getBody());
        List<EntityData> results = getResults(ctx, request);
        String nextCursor = "";
        try {
            nextCursor = ctx.read("next_offset");
        } catch (JsonPathException e) {
            // Nothing, links not found in the response.
        }
        final String wmField = getWatermarkField(request);

        // Item family does not sort results
        results = request.getEntityName().equalsIgnoreCase(ChargebeeSeed.ITEM_FAMILIES) ? results.stream()
            .sorted(Comparator.comparingLong(EntityData::getLastModified)).collect(Collectors.toList()) : results;

        results = results.stream().filter(x -> {
            if (!x.has(wmField) || (Long)x.getValue(wmField) >= request.getWatermark().getStart()) {
                return true;
            }
            // no WM field or outside wm window, discard it.
            return false;
        }).collect(Collectors.toList());

        return new DataWithCursor(prevCursor, nextCursor, results);
    }

    private String getWatermarkField(SyncRequest request) {
        AttributeSchema waterMarkField = request.getEntitySchema().getWatermarkField();
        String waterMarkFieldName = "updated_at";
        if (waterMarkField != null) {
            waterMarkFieldName = waterMarkField.getApiName();
        }
        return waterMarkFieldName;
    }


    public List<EntityData> getById(String url, SyncRequest request) {
        List<EntityData> data = new ArrayList<>();
        try {
            ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
            ReadContext ctx = JsonPath.parse(response.getBody());
            data.addAll(getResult(ctx, request));
        } catch (NonRetriableException e) {
            if(!e.getStatusCode().equalsIgnoreCase("404 NOT_FOUND")) {
                throw e;
            }
        }
        return data;
    }

    private List<EntityData> getResult(ReadContext ctx, SyncRequest request) {
        return extractResults(ctx, request,"");
    }

    private List<EntityData> getResults(ReadContext ctx, SyncRequest request) {
        List<EntityData> results = new ArrayList<>();
        JSONArray data = ctx.read("list");
        for(int i = 0; i < data.size(); i++) {
            String prefix = String.format("list[%d].", i);
            List<EntityData> result = extractResults(ctx, request, prefix);
            results.addAll(result);
        }
        return results;
    }

    private List<EntityData> extractResults(ReadContext ctx, SyncRequest request, String prefix) {
        List<EntityData> results = new ArrayList<>();
        EntitySchema schema = request.getEntitySchema();
        if(ChargebeeSeed.LINE_ITEMS.containsKey(schema.getApiName())) {
            String pathPrefix = String.format(prefix + "%s.%s",getSingularName(ChargebeeSeed.LINE_ITEMS.get(schema.getApiName())), ChargebeeSeed.LINE_ITEM_API_NAMES.get(request.getEntityName()));
            JSONArray lineItemData = ctx.read(pathPrefix);
            for(int j = 0; j < lineItemData.size(); j++) {
                String linePathPrefix = pathPrefix + String.format("[%d].", j);
                results.add(parseJSON(ctx, schema, linePathPrefix, String.format(prefix + "%s.", getSingularName(ChargebeeSeed.LINE_ITEMS.get(schema.getApiName())))));
            }
        } else {
            String pathPrefix = String.format(prefix + "%s.", getSingularName(schema.getApiName()));
            EntityData entityData = parseJSON(ctx, schema, pathPrefix, "");
            if(ChargebeeSeed.LINE_ITEM_BIMAP.inverse().containsKey(request.getEntityName())) {
                String childEntity = ChargebeeSeed.LINE_ITEM_BIMAP.inverse().get(request.getEntityName());
                SyncRequest childRequest = request.withEntitySchema(ChargebeeSeed.getSchema(childEntity));
                List<EntityData> childData = extractResults(ctx, childRequest, prefix);
                entityData.addValue(childEntity, childData);
            }
            results.add(entityData);
        }
        return results;
    }

    public static String getSingularName(String entityName) {
        return entityName.equalsIgnoreCase(ChargebeeSeed.ITEM_FAMILIES) ? "item_family" : entityName.substring(0, entityName.length()-1);
    }

    private EntityData parseJSON(ReadContext ctx, EntitySchema schema, String pathPrefix, String parentPathPrefix) {
        EntityData ed = new EntityData(schema.getApiName());
        schema.getAttributes().forEach(attr -> {
            String path = pathPrefix + sanitizeApiName(attr.getApiName());
            try {
                Object value = ctx.read(path);
                if(attr.getDataType().equalsIgnoreCase("datetime") && value != null) {
                    ed.addValue(attr.getApiName(), ZonedDateTime.ofInstant(Instant.ofEpochSecond((Integer) value), UTC).toEpochSecond() * 1000);
                    if (attr.isWatermarkField()) {
                        ed.setLastModified(ZonedDateTime.ofInstant(Instant.ofEpochSecond((Integer) value), UTC).toEpochSecond() * 1000);
                    }
                    if (attr.isCreatedAtField()) {
                        ed.setCreatedAt(ZonedDateTime.ofInstant(Instant.ofEpochSecond((Integer) value), UTC).toEpochSecond() * 1000);
                    }
                } else if (value instanceof List && attr.isReference() && ChargebeeSeed.MULTI_VALUED_REFERENCES.containsKey(schema.getApiName())) {
                    String referenceField = ChargebeeSeed.MULTI_VALUED_REFERENCES.get(schema.getApiName()).getOrDefault(attr.getApiName(), "");
                    if(StringUtils.isNotEmpty(referenceField)) {
                        List values = (List) value;
                        List<String> referenceIds = new ArrayList<>();
                        values.forEach(v -> {
                            if(v instanceof Map) {
                                Map<String, Object> map = (Map<String, Object>) v;
                                if(map.containsKey(referenceField)) {
                                    referenceIds.add((String)map.get(referenceField));
                                }
                            }
                        });
                        ed.addValue(attr.getApiName(), referenceIds);
                    }
                } else {
                    boolean isSubscriptionItemPrice = ChargebeeSeed.SUBSCRIPTION_LINE_ITEMS.equalsIgnoreCase(schema.getApiName()) && attr.getApiName().equalsIgnoreCase("item_price_id");
                    if (attr.isIdField() || isSubscriptionItemPrice) {
                        if(!ChargebeeSeed.LINE_ITEMS.containsKey(schema.getApiName())) {
                            ed.setId((String) value);
                        } else {
                            if(attr.isIdField() && ChargebeeSeed.SUBSCRIPTION_LINE_ITEMS.equalsIgnoreCase(schema.getApiName())) {
                                // Id does not exist for subscription item. We use item_price_id in its place
                                return;
                            }
                            // get parent id
                            String parentIdPath = parentPathPrefix + "id";
                            String parentId = ctx.read(parentIdPath);
                            ed.setParentId(parentId);
                            String parentIdField = getSingularName(ChargebeeSeed.LINE_ITEMS.get(schema.getApiName())) + "_id";
                            ed.addValue(parentIdField, parentId);
                            ed.setId(parentId + "#" + value);
                            String idField = isSubscriptionItemPrice ? "id" : attr.getApiName();
                            ed.addValue(idField, ed.getId());
                            return;
                        }
                    }
                    ed.addValue(attr.getApiName(),value);
                }
            } catch (PathNotFoundException e) {
                // attribute not included in response
                log.debug("Attribute {} not found in response json", attr);
            }
        });
        if(ChargebeeSeed.LINE_ITEMS.containsKey(schema.getApiName()) && ChargebeeSeed.NO_WM_ENTITIES.contains(schema.getApiName())) {
            String parentLastModifiedPath = parentPathPrefix + "updated_at";
            Object parentLastModified = ctx.read(parentLastModifiedPath);
            long updatedAt = ZonedDateTime.ofInstant(Instant.ofEpochSecond((Integer) parentLastModified), UTC).toEpochSecond() * 1000;
            ed.addValue("updated_at", updatedAt);
            ed.setLastModified(updatedAt);
        }
        if((ed.has("deleted") && (boolean)ed.getValue("deleted")) || (ed.has("status") && ((String)ed.getValue("status")).equalsIgnoreCase("deleted"))) {
            ed.setDeleted(true);
        }
        return ed;
    }

    private String sanitizeApiName(String apiName) {
        return apiName.replace("-", ".");
    }

    public void createRecords(SyncRequest request, String url, SyncResponse response, List<EntityData> entityDataList, String entityName) {
        EntitySchema schema = request.getEntitySchema();
        for(EntityData data: entityDataList) {
            url = formatCreateUrl(request, url, entityName, data);
            MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
            data.getValues().forEach((k, v) -> {
                Optional<AttributeSchema> attributeSchema = schema.getField(k);
                if (attributeSchema.isPresent() && attributeSchema.get().isInitializable() && !attributeSchema.get().isWatermarkField()) {
                    if (v instanceof List) {
                        if(ChargebeeSeed.LINE_ITEMS.containsKey(k)) {
                            processLineItems(request, map, k, v);
                        } else {
                            List array = (List) v;
                            for (int i = 0; i < array.size(); i++) {
                                map.add(convertToApiName(k) + "[" + i + "]", array.get(i));
                            }
                        }
                    } else if(v instanceof Instant) {
                        map.add(convertToApiName(k), (((Instant) v).toEpochMilli()/1000));
                    } else if(v instanceof Date) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        String formattedDate = sdf.format((Date) v);
                        map.add(convertToApiName(k), formattedDate);
                    } else {
                        map.add(convertToApiName(k), v);
                    }
                }
            });
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url);
            ResponseEntity<String> responseEntity = postFormDataURI(uriBuilder.build().toUri(), map, request.getConnector().getAuthConfig());
            ObjectMapper mapper = new ObjectMapper();
            try {
                Map<String, Object> responseMap = mapper.readValue(responseEntity.getBody(), Map.class);
                Map<String, Object> customerMap = (Map<String, Object>) responseMap.get(getSingularName(request.getEntityName()));
                String id = (String) customerMap.get("id");
                response.getResults().add(new Result(true, id, data.getSyncariEntityId()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Invalid response for create request");
            }
        }
    }

    private String convertToApiName(String apiName) {
        return apiName.contains("-") ? apiName.replace("-", "[") + "]" : apiName;
    }

    private void processLineItems(SyncRequest request, MultiValueMap<String, Object> map, String k, Object v) {
        List<EntityData> array = (List<EntityData>) v;
        switch (request.getEntityName()) {
            case ChargebeeSeed.SUBSCRIPTIONS:
            case ChargebeeSeed.QUOTES:
                for (int i = 0; i < array.size(); i++) {
                    Map<String, Object> lineMap = array.get(i).getValues();
                    if(lineMap.containsKey("item_price_id")) {
                        map.add( "subscription_items[item_price_id][" + i + "]", lineMap.get("item_price_id"));
                    }
                    if(lineMap.containsKey("quantity")) {
                        map.add( "subscription_items[quantity][" + i + "]", lineMap.get("quantity"));
                    }
                    if(lineMap.containsKey("unit_price")) {
                        map.add( "subscription_items[unit_price][" + i + "]", lineMap.get("unit_price"));
                    }
                    if(lineMap.containsKey("trial_end")) {
                        map.add( "subscription_items[trial_end][" + i + "]", lineMap.get("trial_end"));
                    }
                }
            case ChargebeeSeed.INVOICES:
                for (int i = 0; i < array.size(); i++) {
                    Map<String, Object> lineMap = array.get(i).getValues();
                    if(lineMap.containsKey("entity_type") && SUPPORTED_INVOICE_ENTITY_TYPES.contains(lineMap.get("entity_type"))) {
                        if (lineMap.containsKey("entity_id")) {
                            map.add("item_prices[item_price_id][" + i + "]", lineMap.get("entity_id"));
                        }
                        if (lineMap.containsKey("quantity")) {
                                map.add("item_prices[quantity][" + i + "]", lineMap.get("quantity"));
                        }
                        if (lineMap.containsKey("unit_amount")) {
                            map.add("item_prices[unit_price][" + i + "]", lineMap.get("unit_amount"));
                        }
                        if (lineMap.containsKey("date_from")) {
                            map.add("item_prices[date_from][" + i + "]", lineMap.get("date_from"));
                        }
                        if (lineMap.containsKey("date_to")) {
                            map.add("item_prices[date_to][" + i + "]", lineMap.get("date_to"));
                        }
                    }
                }
        }
    }

    private String formatCreateUrl(SyncRequest request, String url, String entityName, EntityData data) {
        switch (request.getEntityName()) {
            case ChargebeeSeed.SUBSCRIPTIONS:
                return format(url, request.getConnector().getMetaConfig().get("site").toString(), data.getValue("customer_id"));
            default:
                return format(url, request.getConnector().getMetaConfig().get("site").toString(), entityName);
        }
    }

    public void updateRecords(SyncRequest request, String url, String entityName, SyncResponse response, List<EntityData> entityDataList) {
        EntitySchema schema = request.getEntitySchema();
        for(EntityData data: entityDataList) {
            MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
            data.getValues().forEach((k, v) -> {
                Optional<AttributeSchema> attributeSchema = schema.getField(k);
                if (attributeSchema.isPresent() && attributeSchema.get().isUpdateable() && !attributeSchema.get().isWatermarkField()) {
                    if (v instanceof List) {
                        if(ChargebeeSeed.LINE_ITEMS.containsKey(k)) {
                            processLineItems(request, map, k, v);
                        } else {
                            List array = (List) v;
                            for (int i = 0; i < array.size(); i++) {
                                map.add(convertToApiName(k) + "[" + i + "]", array.get(i));
                            }
                        }
                    } else if(v instanceof Instant) {
                        map.add(convertToApiName(k), (((Instant) v).toEpochMilli()/1000));
                    } else if(v instanceof Date) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        String formattedDate = sdf.format((Date)v);
                        map.add(convertToApiName(k), formattedDate);
                    } else {
                        map.add(convertToApiName(k), v);
                    }
                }
            });
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(format(url, request.getConnector().getMetaConfig().get("site").toString(), entityName, data.getId()));
            ResponseEntity<String> responseEntity = postFormDataURI(uriBuilder.build().toUri(), map, request.getConnector().getAuthConfig());
            ObjectMapper mapper = new ObjectMapper();
            try {
                Map<String, Object> responseMap = mapper.readValue(responseEntity.getBody(), Map.class);
                Map<String, Object> customerMap = (Map<String, Object>) responseMap.get(getSingularName(request.getEntityName()));
                String id = (String) customerMap.get("id");
                response.getResults().add(new Result(true, id, data.getSyncariEntityId()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Invalid response for create request");
            }
        }
    }

    public void deleteRecords(SyncRequest request, String entityName, String url) {
        request.getIds().forEach(id -> {
            String deleteUrl = format(url, request.getConnector().getMetaConfig().get("site").toString(), entityName, id);
            postRaw(deleteUrl, "", request.getConnector().getAuthConfig());
        });
    }
}
