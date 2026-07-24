package com.syncari.karibu.rest.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.EntityData;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.UserService;
import com.syncari.core.utils.ExternalIdVisitor;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.response.EntityDataResponse;
import com.syncari.restutils.data.DataQueryMetadata;
import com.syncari.restutils.data.EntityRecord;
import com.syncari.restutils.transformers.RestObjectTransformer;
import com.syncari.restutils.utils.ApiUtils;
import com.syncari.restutils.utils.DataQueryUtils;
import com.syncari.utils.KeyValue;
import com.syncari.utils.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.syncari.utils.I18n.i18n;

@Component
public class DataUtils {

    @Autowired
    EntityRepoService repoService;

    @Autowired
    DataQueryUtils dataQueryUtils;

    @Autowired
    SchemaService schemaService;

    @Autowired
    UserService userService;

    @Autowired
    ApiUtils apiUtils;

    @Autowired
    RestObjectTransformer restObjectTransformer;

    ObjectMapper mapper = new ObjectMapper();

    public String convertDataRequestPredicates (String entityId, String predicatesRequest) {
        List<String> fieldNames = new ArrayList<>();
        List<Map<String, String>> fieldIdMappings = new ArrayList<>();

        try {
            JsonNode filters = mapper.readTree(predicatesRequest);

            // get field names from the request
            for (JsonNode predicates : filters.get("predicates")) {
                if(null != predicates.get("left"))
                    fieldNames.add(predicates.get("left").get("apiName").asText());

                // allow for one nested level of filters
                if(null != predicates.get("predicates")){
                    for (JsonNode nestedPredicates : predicates.get("predicates")) {
                        fieldNames.add(nestedPredicates.get("left").get("apiName").asText());
                    }
                }
            }

            predicatesRequest = removeWhitespaces(predicatesRequest);

            // map the filter api name to the field id
            for (String fieldName : fieldNames) {
                if ((!fieldName.equalsIgnoreCase("syncariid")) && (!fieldName.equalsIgnoreCase("lastModified")) && (!fieldName.equalsIgnoreCase("isSyncariDeleted"))){
                    AttributeDefinition attributeDefinition = schemaService.getAttributeByName(entityId, fieldName);
                    predicatesRequest = predicatesRequest.replaceFirst("\"apiName\":\""+fieldName+"\"","\"value\":\""+attributeDefinition.getId()+"\"");
                }else{
                    if (fieldName.equalsIgnoreCase("lastModified")){
                        predicatesRequest = predicatesRequest.replaceFirst("\"apiName\":\""+fieldName+"\"","\"value\":\"" + ExternalIdVisitor.DATASTUDIO_LAST_MODIFIED+"\"");
                    }else  if (fieldName.equalsIgnoreCase("isSyncariDeleted")){
                        predicatesRequest = predicatesRequest.replaceFirst("\"apiName\":\""+fieldName+"\"","\"value\":\"" + ExternalIdVisitor.DATASTUDIO_IS_DELETED+"\"");
                    }else{
                        predicatesRequest = predicatesRequest.replaceFirst("\"apiName\":\""+fieldName+"\"","\"value\":\"" + ExternalIdVisitor.DATASTUDIO_SYNCARI_ID+"\"");

                    }
                }

            }

        } catch (Exception e) {
            throw new BadRequestException(i18n("data_request_error"));
        }

        return predicatesRequest;
    }

    private static String removeWhitespaces(String json) {

        boolean quoted = false;
        boolean escaped = false;
        String out = "";

        for(Character c : json.toCharArray()) {

            if(escaped) {
                out += c;
                escaped = false;
                continue;
            }

            if(c == '"') {
                quoted = !quoted;
            } else if(c == '\\') {
                escaped = true;
            }

            if(c == ' ' &! quoted) {
                continue;
            }

            out += c;

        }

        return out;

    }

    public Long getCount(String entityId, String predicates){
        String encodePredicates = null;
        if(predicates != null)
            encodePredicates = apiUtils.encodeCursor(predicates);
        Optional<Expression> input = dataQueryUtils.getExpression(encodePredicates);
        return repoService.countData(entityId,input);
    }

    public Pair<List<EntityRecord>, Boolean> getEntityData(String entityId, String predicates, String cursorId, Integer limit) {
        String encodePredicates = null;
        if(predicates != null)
            encodePredicates = apiUtils.encodeCursor(predicates);

        Optional<Expression> input = dataQueryUtils.getExpression(encodePredicates);
        EntityDefinition entity = schemaService.getEntity(entityId);
        Map<String, KeyValue> fields = dataQueryUtils.getFilterFields(entity);
        List<Connector> sourceConnectors= new ArrayList<>();
        List<EntityDefinition> entities = new ArrayList<>();
        dataQueryUtils.populateConnectorsAndEntities(entityId, sourceConnectors, entities);

        Set<String> selectedColumns = userService.getDataStudioColumns(SyncariContext.getUser().getId(), entityId);

        DataQueryMetadata metadata = new DataQueryMetadata(
                selectedColumns == null || selectedColumns.isEmpty() ? fields.keySet() : selectedColumns, fields
        );

        Page<EntityData> page = repoService.query(entityId, input, new PageCursor(cursorId, PageDirection.next, limit),false);
        boolean hasMore = page.getPageInfo().isHasMore();
        List<EntityRecord> entityRecords = restObjectTransformer.toEntityRecords(page.getRecords());
        dataQueryUtils.populateIdMappings(entity, entityRecords, sourceConnectors, entities);
        return Pair.of(entityRecords, hasMore);
    }

    public List<EntityDataResponse> getEntityDataResponse(List<EntityRecord> entityRecords, String entityId) {
        List<EntityDataResponse> responses = new ArrayList<>();

        for(EntityRecord entityRecord : entityRecords) {
            EntityDataResponse response = new EntityDataResponse();
            response.setId(entityRecord.getSyncariId());
            response.setSyncariDeleted(entityRecord.isDeleted());
            response.setDataFitnessIndex(entityRecord.getValues().get("dfi").toString());
            entityRecord.getValues().remove("dfi");
            entityRecord.getValues().remove("syncariId");
            response.setIdMapping(entityRecord.getIdMapping());
            response.setValues(entityRecord.getValues());
            responses.add(response);
        }

        EntityDefinition entity = schemaService.getEntity(entityId);
        List<AttributeDefinition> attributeDefinitions = entity.getAttributes();

        for(EntityDataResponse r : responses) {
            for (AttributeDefinition a : attributeDefinitions) {
                if(!r.getValues().containsKey(a.getApiName()))
                    r.getValues().put(a.getApiName(), null);
            }
        }

        return responses;
    }

}
