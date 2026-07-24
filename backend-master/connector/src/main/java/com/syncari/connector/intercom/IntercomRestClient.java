package com.syncari.connector.intercom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.intercom.Pagination;
import com.syncari.connector.data.iterator.intercom.Query;
import com.syncari.connector.data.iterator.intercom.Search;
import com.syncari.connector.data.iterator.intercom.Sort;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;

@Slf4j
public class IntercomRestClient extends SyncariEntityDataRestClient  {

    public IntercomRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
        super(parserConfig, objectMapper);
    }

    protected String getAccessToken(ConnectorInfo connector) {
        String authType = connector.getMetaConfig().getOrDefault("authType", "").toString();

        if (StringUtils.isEmpty(authType)) {
            String msg = "Failed to acquire apikey. No authentication type provided.";
            log.error(msg);
            throw new RuntimeException(msg);
        }

        // If authType is apikey based, return the connector apikey.
        if (authType.equalsIgnoreCase(AuthType.ApiKey.name())) {
            return connector.getAuthConfig().getAccessToken();
        }
        log.error("Empty apikey");
        return "";
    }

    public DataWithCursor getDataWithCursor(String url, SyncRequest request, int pageSize, String prevCursor) {

        AuthConfig authConfig = request.getConnector().getAuthConfig();
        ResponseEntity<String> response;

        try {

            if(IntercomService.CONTACT.equals(request.getEntityName()) ||
                    IntercomService.TICKET.equals(request.getEntityName())) {
                response = callSearchAPI(url, request, pageSize, prevCursor, authConfig);
            } else if(IntercomService.COMPANY.equals(request.getEntityName()) ||
                      IntercomService.CONVERSATION.equals(request.getEntityName())) {
                response = callGETWithPagination(url, pageSize, prevCursor, authConfig);
            } else {
                response = callGETWithoutPagination(url, authConfig);
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }



        String responseStr = response.getBody();
        ReadContext ctx = JsonPath.parse(responseStr);
        List<EntityData> results = getResults(ctx, request);

        String nextCursor = "";
        if(IntercomService.CONTACT.equals(request.getEntityName()) ||
                IntercomService.TICKET.equals(request.getEntityName())) {
            nextCursor = getNextCursorForContact(ctx, nextCursor);
        } else if (IntercomService.CONVERSATION.equals(request.getEntityName())) {
            nextCursor = constructCursor(url, ctx, pageSize);
        }else if(IntercomService.COMPANY.equals(request.getEntityName()) ) {
            try {
                nextCursor = ctx.read("pages.next");
            } catch (PathNotFoundException e) {
                log.debug("No pagination cursor found in response for entity: {}", request.getEntityName());
            }
        }

        final String wmField = getWatermarkField(request);

        results = results.stream().filter(x -> {
            // no WM field or outside wm window, discard it.
            // entitydata should store time-in-milliseconds
            return !x.has(wmField) || Long.parseLong(x.getValue(wmField).toString()) >= (request.getWatermark().getStart());
        }).collect(Collectors.toList());


        results.sort(Comparator.comparingLong(EntityData::getLastModified));

        return new DataWithCursor(prevCursor, nextCursor, results);
    }

    private ResponseEntity<String> callGETWithoutPagination(final String url, AuthConfig authConfig) {
        return withBackoffAndErrorHandling(()->
                getTemplate().exchange(url, HttpMethod.GET,
                        new HttpEntity( getHeaders(authConfig)), String.class));
    }

    private String getNextCursorForContact(ReadContext ctx, String nextCursor) {
        try {
            Map nextCursorMap = ctx.read("pages.next");
            if(nextCursorMap!=null){
                nextCursor = (String) nextCursorMap.get("starting_after");
            }
        } catch (JsonPathException e) {
            // Nothing, links not found in the response.
        }
        return nextCursor;
    }

    private String constructCursor(String url, ReadContext ctx, int pageSize) {
        String nextCursor = ctx.read("pages.next.starting_after");
        if(!StringUtils.isBlank(nextCursor)) {
            return url + "?per_page="+ pageSize + "&starting_after="+nextCursor;
        }
        return nextCursor;
    }

    private ResponseEntity<String> callGETWithPagination(String url, int pageSize, String prevCursor, AuthConfig authConfig) {
        ResponseEntity<String> response;
        if (!StringUtils.isEmpty(prevCursor)) {
            url = prevCursor;
        } else{
            url += "?per_page="+ pageSize;
        }

        String finalUrl = url;
        response = withBackoffAndErrorHandling(()->
                getTemplate().exchange(finalUrl, HttpMethod.GET,
                        new HttpEntity( doGetHeaders(authConfig)), String.class));
        HttpHeaders headers = response.getHeaders();
        log.debug("Intercom headers: {}", headers);
        return response;
    }

    private ResponseEntity<String> callSearchAPI(String url, SyncRequest request, int pageSize, String prevCursor, AuthConfig authConfig) throws JsonProcessingException {
        Search search = new Search();

        Pagination pagination = new Pagination();
        pagination.setPer_page(pageSize);
        if (!StringUtils.isEmpty(prevCursor)) {
            pagination.setStarting_after(prevCursor);
        }
        search.setPagination(pagination);


        Query query1 = new Query();
        query1.setOperator(">");
        query1.setField(IntercomService.UPDATED_AT);
        // Intercom use 'date' datatype for 'updated_at' field instead of 'timestamp' datatype.
        // So, in search, we set start-date criteria as 1 day before epoc-seconds so that we dont miss records.
        long watermarkStartInSec = request.getWatermark().getStart()/1000 - 86400;
        query1.setValue(watermarkStartInSec);
        search.setQuery(query1);

        Sort sort = new Sort();
        sort.setField(IntercomService.UPDATED_AT);
        sort.setOrder("ascending");
        search.setSort(sort);

        String searchBody = objectMapper.writeValueAsString(search);

        //System.out.println("#MARKER searchBody"+searchBody);

        return withBackoffAndErrorHandling(() ->
                getTemplate().exchange(url, HttpMethod.POST,
                        new HttpEntity(searchBody, doGetHeaders(authConfig)), String.class));
    }

    HttpHeaders doGetHeaders(AuthConfig authConf) {
        HttpHeaders headers = getHeaders(authConf);
        headers.add("Intercom-Version", "2.10");
        return headers;
    }

    private String getWatermarkField(SyncRequest request) {
        String watermark = IntercomService.UPDATED_AT;
        EntitySchema entitySchema = request.getEntitySchema();
        if(entitySchema.hasWatermarkField()){
            AttributeSchema waterMarkField = entitySchema.getWatermarkField();
            if (waterMarkField != null) {
                return waterMarkField.getApiName();
            }
        }
        return watermark;
    }

    private List<EntityData> getResults(ReadContext ctx, SyncRequest request) {
        List<EntityData> results = new ArrayList<>();

        String dataField = getDataFieldName(request.getEntityName());
        try {
            JSONArray data = ctx.read(dataField);
            for(int i = 0; i < data.size(); i++) {
                String prefix = String.format(dataField+"[%d]", i);
                List<EntityData> result = extractResults(ctx, request, prefix);
                results.addAll(result);
            }
        } catch (PathNotFoundException e) {
            log.debug("No data found for field '{}' in response", dataField);
        }
        return results;
    }

    private String getDataFieldName(String entityName) {
        if (IntercomService.CONVERSATION.equals(entityName)) {
            return "conversations";
        } else if (IntercomService.TICKET.equals(entityName)) {
            return "tickets";
        }
        return "data";
    }

    private List<EntityData> extractResults(ReadContext ctx, SyncRequest request, String prefix) {
        List<EntityData> results = new ArrayList<>();
        EntitySchema schema = request.getEntitySchema();
        results.add(parseJSON(ctx, schema, prefix));
        return results;
    }

    private EntityData parseJSON(ReadContext ctx, EntitySchema schema, String pathPrefix) {
        EntityData ed = new EntityData(schema.getApiName());
        schema.getAttributes().forEach(attr -> {

            String path = attr.getApiName();

            if (path.startsWith("custom_attributes.")){
                path = path.substring(0, path.lastIndexOf("_")).replaceFirst("\\.","['")+"']";
            }

            if(StringUtils.isNotEmpty(pathPrefix)){
                path = Joiner.on(".").join(pathPrefix ,path);
            }
            try {
                Object value = ctx.read(path);
                if(attr.getDataType().equalsIgnoreCase("datetime") && value != null) {
                    // save value in entitydata in milliseconds
                    long timeInmilliseconds = Long.parseLong(value.toString()) * 1000;
                    ed.addValue(attr.getApiName(),timeInmilliseconds);
                    if (attr.isWatermarkField()) {
                        ed.setLastModified(timeInmilliseconds);
                    }
                    if (attr.isCreatedAtField()) {
                        ed.setCreatedAt(timeInmilliseconds);
                    }
                } else if (IntercomService.MULTI_VALUED_ATTRS.containsKey(schema.getApiName()) &&
                        IntercomService.MULTI_VALUED_ATTRS.get(schema.getApiName()).contains(attr.getApiName())) {
                    // handle tags, companies attributes of contact.

                    List<String> referenceIds = new ArrayList<>();

                    value = ctx.read(path+".data");

                    if(value instanceof List) {
                        List values = (List) value;
                        values.forEach(v -> {
                            if (v instanceof Map) {
                                Map  map = (Map) v;
                                if (map.containsKey("id")) {
                                    referenceIds.add(map.get("id").toString());
                                }
                            }
                        });
                    }
                    ed.addValue(attr.getApiName(), referenceIds);

                } else {
                    if (attr.isIdField()) {
                        ed.setId((String) value);
                    }
                    ed.addValue(attr.getApiName(), value);
                }

            } catch (PathNotFoundException e) {
                // attribute not included in response
                log.debug("Attribute {} not found in response json", attr);
            }
        });

        List<AttributeSchema> compositeKeyFields = schema.getCompositeKeyFields();
        if (CollectionUtils.isNotEmpty(compositeKeyFields) && org.apache.commons.collections.MapUtils.isNotEmpty(ed.getCompositeKeyData()) && StringUtils.isEmpty(ed.getId())){
            String idValue = StringUtils.join(IntStream.range(0,compositeKeyFields.size()).mapToObj(i -> ed.getCompositeKeyData().get(compositeKeyFields.get(i))).collect(Collectors.toList()),EntitySchema.COMPOSITE_KEY_DELIMETER);
            ed.setId(idValue);
        }

        return ed;
    }

    public Map parseSingleObjectResponse(ResponseEntity<String> response) {
        Map singleObjResponse;
        try {
            singleObjResponse = objectMapper.readValue(response.getBody(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to process single object response due to " + e.getMessage(), e);
        }
        return singleObjResponse;
    }

    public SyncResponse deleteRecords(String crudURL, SyncRequest request) {
        String entityName = request.getEntityName();
        SyncResponse response = new SyncResponse();
        request.getData().get(request.getConnector().getId()).forEach(ed -> {
            String url = String.format(crudURL, IntercomService.getHost(request.getConnector()), IntercomService.plural(request.getEntityName()), ed.getId());
            ResponseEntity<String> dResp = delete(doGetHeaders(request.getConnector().getAuthConfig()), url, request.getConnector().getAuthConfig());

            List<Result> results = response.getResults();

            Map data = Maps.newHashMap();
            if(HttpStatus.OK.equals(dResp.getStatusCode())){
                data = parseSingleObjectResponse(dResp);

                results.add(new Result(true, data.get(IntercomService.ID).toString(), ed.getSyncariEntityId()));
            } else {
                results.add(new Result(false, ed.getId(), ed.getSyncariEntityId()));
            }
        });
        return response;
    }

    public List<EntityData> getById(String url, SyncRequest request) {
        List<EntityData> eds = new ArrayList<>();
        try {
            ResponseEntity<String> response = getResponse(doGetHeaders(request.getConnector().getAuthConfig()), url, request.getConnector().getAuthConfig());
            ReadContext ctx = JsonPath.parse(response.getBody());
            EntitySchema schema = request.getEntitySchema();
            EntityData ed = parseJSON(ctx, schema, "");
            eds.add(ed);
        } catch (NonRetriableException e) {
            if(!e.getMessage().equalsIgnoreCase("404 NOT_FOUND") && !e.getStatusCode().equalsIgnoreCase("404 NOT_FOUND")) {
                throw e;
            }
        }
        return eds;
    }

}