package com.syncari.connector.OracleErpSales;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.AbstractEntityDataBatchIterator;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;

import com.syncari.connector.data.iterator.LocalStorageService;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
@Component(Constants.ORACLE_ERP_SALES)
public class OracleERPService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService {

    public static final String API_VERSION = "11.13.18.05";

    private static final String PRIMARY_OBJECT_DESCRIBE_ENTITY_RESOURCE_PATH = "/crmRestApi/resources/%s/%s/describe";
    private static final String PRIMARY_OBJECT_WATERMARK_QUERY_PARAMS = "?q=%s>%s and <=%s&limit=%s&offset=%s&orderBy=%s";
    public static final String SECONDARY_OBJECTS_ENTITY_RESOURCE_PATH = "/crmRestApi/resources/%s/%s/%s/child/%s";
    public static final String SECONDARY_OBJECTS_WATERMARK_QUERY_PARAMS = "/crmRestApi/resources/%s/%s/%s/child/%s?q=%s>%s and <=%s&limit=%s&orderBy=%s";
    private static final String PRIMARY_OBJECT_GET_ONE_RECORD = "/crmRestApi/resources/%s/%s?limit=1";
    private static final String PRIMARY_OBJECT_GET_ID_URL = "/crmRestApi/resources/%s/%s?fields=%s&offset=%s&limit=%s";
    private static final String RESOURCE_PATH = "/crmRestApi/resources/"+API_VERSION+"/%s";
    public static final String PRIMARY_OBJECTS_ENTITY_RESOURCE_PATH = "/crmRestApi/resources/%s/%s";
    public static final String ID_QUERY_PARAMS = "?q=%s='%s'";

    private static final String SECONDARY_OBJECT_DESCRIBE_ENTITY_RESOURCE_PATH = "/crmRestApi/resources/%s/%s/%s/child/%s/describe";

    //primary objects
    public static final String ACCOUNTS_ENTITY_NAME = "accounts";
    public static final String CONTACTS_ENTITY_NAME = "contacts";

    //child objects for accounts
    public static final String ACCOUNTS_ATTACHMENTS = "Attachment";
    public static final String ACCOUNTS_SALES_TEAM_MEMBERS = "SalesTeamMember";
    public static final String ACCOUNTS_TEAM_MEMBERS = "AccountTeam";
    public static final String ACCOUNTS_NOTES = "Note";
    public static final String ACCOUNTS_ORGANIZATION_CONTACTS = "AccountContact";
    public static final String ACCOUNTS_PRIMARY_ADDRESS = "PrimaryAddress";

    //child objects for contacts
    public static final String CONTACTS_ATTACHMENT = "Attachment";
    public static final String CONTACTS_PRIMARY_ADDRESS = "PrimaryAddress";
    public static final String CONTACTS_NOTE = "Note";

    //Child Entity Names
    public static final String ACCOUNTS_ATTACHMENTS_ENTITY_NAME = "account_attachments";
    public static final String ACCOUNTS_SALES_TEAM_MEMBERS_ENTITY_NAME = "account_sales_team_member";
    public static final String ACCOUNTS_TEAM_MEMBERS_ENTITY_NAME = "account_team";
    public static final String ACCOUNTS_NOTES_ENTITY_NAME = "account_notes";
    public static final String ACCOUNTS_ORGANIZATION_CONTACTS_ENTITY_NAME = "account_organization_contact";
    public static final String ACCOUNTS_PRIMARY_ADDRESS_ENTITY_NAME = "account_primary_Address";

    public static final String CONTACTS_ATTACHMENT_ENTITY_NAME = "contacts_attachments";
    public static final String CONTACTS_PRIMARY_ADDRESS_ENTITY_NAME = "contacts_primary_address";
    public static final String CONTACTS_NOTE_ENTITY_NAME = "contacts_notes";

    public static final Map<String, String> ENTITY_TO_RESOURCE_MAP = Map.ofEntries(
            Map.entry(ACCOUNTS_ENTITY_NAME, ACCOUNTS_ENTITY_NAME),
            Map.entry(CONTACTS_ENTITY_NAME, CONTACTS_ENTITY_NAME),
            Map.entry(ACCOUNTS_ATTACHMENTS_ENTITY_NAME, ACCOUNTS_ATTACHMENTS),
            Map.entry(ACCOUNTS_SALES_TEAM_MEMBERS_ENTITY_NAME, ACCOUNTS_SALES_TEAM_MEMBERS),
            Map.entry(ACCOUNTS_TEAM_MEMBERS_ENTITY_NAME, ACCOUNTS_TEAM_MEMBERS),
            Map.entry(ACCOUNTS_NOTES_ENTITY_NAME, ACCOUNTS_NOTES),
            Map.entry(ACCOUNTS_ORGANIZATION_CONTACTS_ENTITY_NAME, ACCOUNTS_ORGANIZATION_CONTACTS),
            Map.entry(ACCOUNTS_PRIMARY_ADDRESS_ENTITY_NAME, ACCOUNTS_PRIMARY_ADDRESS),
            Map.entry(CONTACTS_ATTACHMENT_ENTITY_NAME, CONTACTS_ATTACHMENT),
            Map.entry(CONTACTS_PRIMARY_ADDRESS_ENTITY_NAME, CONTACTS_PRIMARY_ADDRESS),
            Map.entry(CONTACTS_NOTE_ENTITY_NAME, CONTACTS_NOTE)
    );

    public static final Set<String> ACCOUNTS_CHILD_OBJECTS = Set.of(ACCOUNTS_ATTACHMENTS_ENTITY_NAME, ACCOUNTS_SALES_TEAM_MEMBERS_ENTITY_NAME, ACCOUNTS_TEAM_MEMBERS_ENTITY_NAME, ACCOUNTS_NOTES_ENTITY_NAME,
            ACCOUNTS_ORGANIZATION_CONTACTS_ENTITY_NAME, ACCOUNTS_PRIMARY_ADDRESS_ENTITY_NAME);

    public static final Set<String> CONTACTS_CHILD_OBJECTS = Set.of(CONTACTS_ATTACHMENT_ENTITY_NAME,
            CONTACTS_PRIMARY_ADDRESS_ENTITY_NAME, CONTACTS_NOTE_ENTITY_NAME);

    private static final Map<String, String> SUPPORTED_ID_FIELDS = Map.ofEntries(
            Map.entry(ACCOUNTS_ENTITY_NAME, "PartyNumber"),
            Map.entry(CONTACTS_ENTITY_NAME, "PartyNumber"),
            Map.entry(ACCOUNTS_ATTACHMENTS_ENTITY_NAME, "AttachedDocumentId"),
            Map.entry(ACCOUNTS_SALES_TEAM_MEMBERS_ENTITY_NAME, "ResourceId"),
            Map.entry(ACCOUNTS_TEAM_MEMBERS_ENTITY_NAME, "ResourceId"),
            Map.entry(ACCOUNTS_NOTES_ENTITY_NAME, "NoteId"),
            Map.entry(ACCOUNTS_ORGANIZATION_CONTACTS_ENTITY_NAME, "AccountContactId"),
            Map.entry(ACCOUNTS_PRIMARY_ADDRESS_ENTITY_NAME, "AddressId"),
            Map.entry(CONTACTS_ATTACHMENT_ENTITY_NAME,"AttachedDocumentId"),
            Map.entry(CONTACTS_PRIMARY_ADDRESS_ENTITY_NAME, "AddressId"),
            Map.entry(CONTACTS_NOTE_ENTITY_NAME, "NoteId")
    );

    private static final String DISPLAY_NAME_KEY = "title";
    private static final String PARENT_ID_FIELD = "parent_id";

    @Autowired
    LocalStorageService localStorageService;

    public static final List<String> PRIMARY_OBJECTS = List.of(ACCOUNTS_ENTITY_NAME, CONTACTS_ENTITY_NAME);
    public static final List<String> SECONDARY_OBJECTS =  Stream.concat(ACCOUNTS_CHILD_OBJECTS.stream(), CONTACTS_CHILD_OBJECTS.stream())
            .collect(Collectors.toList());

    public static final int API_MAX_PAGESIZE = 200;
    // for max records per batch, no way to exactly figure this as we cannot know how many records can be there with the
    // parent object. In case this creates issues, we need to reduce the API_MAX_PAGESIZE or further increase the MAX_RECORDS_PER_BATCH value
    public static final int MAX_RECORDS_PER_BATCH = 100000;

    @Autowired
    private ObjectMapper mapper;

    private String getHOST(AuthConfig config){
        return config.getEndpoint();
    }

    private String getResourceNameFromEntityName(String entityName) {
        return ENTITY_TO_RESOURCE_MAP.getOrDefault(entityName, entityName);
    }

    private String getAPIPath(AuthConfig config, String entityName){
        return getHOST(config)+String.format(RESOURCE_PATH, getResourceNameFromEntityName(entityName));
    }

    private JsonParserConfig getSingleJsonConfig() {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
    }

    protected OracleERPServiceRestClient getClient() {
        return new OracleERPServiceRestClient(getSingleJsonConfig(), mapper);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();
        entityNames = entityNames.isEmpty() ? new ArrayList<>(PRIMARY_OBJECTS) : entityNames;
        for(String entityName: entityNames) {
            if (!result.getErrors().isEmpty()) {
                break;
            }
            String url = getAPIPath(config.getAuthConfig(), entityName);
            try {
                ResponseEntity<String> data = getClient().getResponse(url, config.getAuthConfig());
                log.debug("Data received from minimal describe " + data);
            } catch (Exception e) {
                handleAuthenticationErrorMessage(result, e);
            }
        }
        return result;
    }

    private String buildWaterMarkEntityEndPoint(ConnectorInfo connectorInfo, String entityName,int limit, Long offset, String orderByField, String start,String end){
        String endpoint = getHOST(connectorInfo.getAuthConfig());
        String formattedString = String.format(PRIMARY_OBJECT_WATERMARK_QUERY_PARAMS,orderByField, start, end, limit, offset, orderByField);
        String resourcePath = String.format(PRIMARY_OBJECTS_ENTITY_RESOURCE_PATH, API_VERSION, entityName) + formattedString;
        return endpoint + resourcePath;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if (SECONDARY_OBJECTS.contains(request.getEntityName()))
            return getChildObjectsByWatermark(request);
        Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator = (wm, pageSize, offset) -> {
            ConnectorInfo connectorInfo = request.getConnector();
            String start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wm.getStart()), ZoneOffset.UTC).toString();
            String end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wm.getEnd()), ZoneOffset.UTC).toString();
            String watermarkField = request.getEntitySchema().getWatermarkField().getApiName();
            String url = buildWaterMarkEntityEndPoint(connectorInfo, request.getEntityName(),pageSize,offset,watermarkField, start, end);
            log.info("Url to fetch data by watermark is {}",url);
            return getClient().getDataWithOffset(url,offset,request);
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();

        DefaultDataOffsetIterator iterator = new DefaultDataOffsetIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pgSize, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private List<String> getParentIds(String parentEntityName, long offset, int size, SyncRequest request) {
        String idKeyParent = SUPPORTED_ID_FIELDS.get(parentEntityName);
        String parentEntityURL = getHOST(request.getConnector().getAuthConfig())+String.format(PRIMARY_OBJECT_GET_ID_URL, API_VERSION, parentEntityName, idKeyParent, offset, size);
        return getClient().getUniqueIds(parentEntityURL, idKeyParent, request.getConnector().getAuthConfig());
    }

    public FetchResponse getChildObjectsByWatermark(SyncRequest request) {
        localStorageService.provisionIfNotExists(request, request.getEntityName());
        localStorageService.cleanupDB(request);
        localStorageService.provisionIfNotExists(request, request.getEntityName());

        String entityName = request.getEntityName();
        String parentEntityName = getParentEntityName(entityName);

        if (!SUPPORTED_ID_FIELDS.containsKey(parentEntityName) || !SECONDARY_OBJECTS.contains(entityName)) {
            log.error(String.format("Entity %s is not supported. parentEntityName : %s", entityName, parentEntityName));
            throw new RuntimeException(String.format("Entity %s is not supported", entityName));
        }

        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            log.info("Using offset {} pagesize {}", offset, pageSize);
            int retries = 5;
            List<EntityData> data = new ArrayList<>();
            while (retries > 0) {
                try {
                    List<String> parentIds = getParentIds(parentEntityName, offset, API_MAX_PAGESIZE, request);
                    log.info("fetched {} parent ids for entity {}, parent entity", parentIds.size(), entityName, parentEntityName);
                    for (String parentId : parentIds) {
                        String orderByField = request.getEntitySchema().getWatermarkField().getApiName();
                        String startTs = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wm.getStart()), ZoneOffset.UTC).toString();
                        String endTs = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wm.getEnd()), ZoneOffset.UTC).toString();
                        String childObjectUrl = getHOST(request.getConnector().getAuthConfig()) + String.format(SECONDARY_OBJECTS_WATERMARK_QUERY_PARAMS, API_VERSION, parentEntityName, parentId, ENTITY_TO_RESOURCE_MAP.get(entityName), orderByField, startTs, endTs, API_MAX_PAGESIZE, orderByField);
                        List<EntityData> currBatchData = getClient().getChildObjectsWithOffset(childObjectUrl, PARENT_ID_FIELD, parentId, request);
                        data.addAll(currBatchData);
                    }
                    break;
                } catch (Exception e) {
                    if (e.getMessage().contains("Read timed out")) {
                        log.error("Retrying because of read timeout for offset - {}. Retries left: " + (retries - 1), offset);
                        retries--;
                    } else {
                        throw e;
                    }
                }
            }
            return Pair.of((long) data.size(), data.stream());
        };

        long start = request.getWatermark().getStart();
        long end = request.getWatermark().getEnd();
        WatermarkInfo initialWatermark = new WatermarkInfo(start, end, true, 0);
        DefaultDataIterator iterator = new DefaultDataIterator(initialWatermark, 0, generator, new ArrayList<>(), request.getEntitySchema().getWatermarkField(), MAX_RECORDS_PER_BATCH);
        localStorageService.fetch(request, iterator);
        int currOffset = API_MAX_PAGESIZE;
        boolean completed = false;
        while (!completed){
            iterator = new DefaultDataIterator(initialWatermark, currOffset, generator, new ArrayList<>(), request.getEntitySchema().getWatermarkField(), MAX_RECORDS_PER_BATCH);
            localStorageService.fetch(request, iterator);
            List<String> parentIds = getParentIds(parentEntityName, currOffset, 1, request);
            if (parentIds.isEmpty())
                completed = true; //no more parentIds to fetch
            currOffset += API_MAX_PAGESIZE;
        }

        FetchResponse fetchResponse = localStorageService.getByWatermark(request.setWatermark(initialWatermark));
        AbstractEntityDataBatchIterator localStorageIterator = (AbstractEntityDataBatchIterator)fetchResponse.getIterator();
        localStorageIterator.setMaxRecordsPerEntitySyncCycle((int) localStorageService.count(request.getConnector(), entityName, request.getWatermark().getStart())+1);
        return fetchResponse;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    private String buildByIdEntityEndPoint(ConnectorInfo connectorInfo, String entityName,String idfield, String idFieldValue){
        String endpoint = getHOST(connectorInfo.getAuthConfig());
        String[] ids = idFieldValue.split("\\|");
        if (ids.length == 0 || (PRIMARY_OBJECTS.contains(entityName) && ids.length != 1) || (SECONDARY_OBJECTS.contains(entityName) && ids.length != 2)) {
            String msg = String.format("Given id %s is not in not in the valid format for entity %s", idFieldValue, entityName);
            log.error(msg);
            throw new RuntimeException(msg);
        }
        String uniqueIdValue = PRIMARY_OBJECTS.contains(entityName) ? ids[0] : ids[1];
        String resourcePath = PRIMARY_OBJECTS.contains(entityName) ? String.format(PRIMARY_OBJECTS_ENTITY_RESOURCE_PATH, API_VERSION, ENTITY_TO_RESOURCE_MAP.get(entityName)) :
                String.format(SECONDARY_OBJECTS_ENTITY_RESOURCE_PATH, API_VERSION, getParentEntityName(entityName), ids[0], ENTITY_TO_RESOURCE_MAP.get(entityName));
        String idQueryParams = String.format(ID_QUERY_PARAMS, idfield, uniqueIdValue);
        return endpoint + resourcePath + idQueryParams;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> data = new ArrayList<>();
        ConnectorInfo connectorInfo = request.getConnector();
        String idField = request.getEntitySchema().getIdField().getApiName();
        request.getIds().forEach(id -> {
            String url = buildByIdEntityEndPoint(connectorInfo, request.getEntityName(), idField, id);
            data.addAll(PRIMARY_OBJECTS.contains(request.getEntityName()) ? getClient().getData(url, request) :
                     getClient().getChildObjectData(url, PARENT_ID_FIELD, id.split("\\|")[0], request));
        });
        return data;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        throw new RuntimeException("Create not supported for Oracle ERP Sales synapse");
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        throw new RuntimeException("Update not supported for Oracle ERP Sales synapse");
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        throw new RuntimeException("Delete not supported for Oracle ERP Sales synapse");
    }

    private AttributeSchema toAttribSchema(Map<String, Object> attributeMap, String entityName) {
        String supportedIdField = SUPPORTED_ID_FIELDS.get(entityName);
        log.debug("raw values of map is {} ", attributeMap);
        AttributeSchema field = new AttributeSchema(attributeMap.get("name").toString(), attributeMap.get("type").toString().toLowerCase());
        field.setDisplayName(attributeMap.containsKey("title")? attributeMap.get("title").toString() : attributeMap.get("name").toString());

        field.setNillable(!(Boolean) attributeMap.get("mandatory"));
        field.setUpdateable(attributeMap.containsKey("updatable") ? (Boolean) attributeMap.get("updatable") : true);

        if (supportedIdField.equals(field.getApiName())){
            field.setIdField(true);
            field.setSystem(true);
            field.setUpdateable(false);
            field.setNillable(false);
        }

        switch (field.getApiName()) {
            case "LastUpdateDate":
                field.setWatermarkField(true);
                field.setUpdatedAtField(true);
                field.setSystem(true);
                break;
            case "CreationDate":
                field.setCreatedAtField(true);
                field.setSystem(true);
                break;
            case "CreatedBy":
            case "LastUpdatedBy":
                field.setSystem(true);
                break;
            default:
                break;
        }

        if (field.getDataType().toLowerCase().contains("long text")) {
            field.setDataType("textarea");
        }

        if (field.getDataType().toLowerCase().contains("null")) {
            field.setDataType("string");
        }
        field.setLength(attributeMap.containsKey("maxLength") ? Integer.parseInt(attributeMap.get("maxLength").toString()) : 0);
        if (!"boolean".equalsIgnoreCase(field.getDataType()) &&
                field.getDataType().toLowerCase().contains("array")) {
            field.setMultiValueField(true);
        }
        return field;
    }

    private String getParentEntityName(String childEntityName){
        if (SECONDARY_OBJECTS.contains(childEntityName)) {
            if (CONTACTS_CHILD_OBJECTS.contains(childEntityName))
                return CONTACTS_ENTITY_NAME;
            else if (ACCOUNTS_CHILD_OBJECTS.contains(childEntityName))
                return ACCOUNTS_ENTITY_NAME;
        }
        return null;
    }

    private List<AttributeSchema> toAttributeSchemaList(List<Map<String, Object>> attributes, String entityName){
        List<AttributeSchema> allAttributeSchema = new ArrayList<>();
        attributes.forEach(attrib -> {
            allAttributeSchema.add(toAttribSchema(attrib,entityName));
        });
        return allAttributeSchema;
    }

    private String getParentId(String entityName, String key, ConnectorInfo connectorInfo) {
        String endPoint = getHOST(connectorInfo.getAuthConfig()) + String.format(PRIMARY_OBJECT_GET_ONE_RECORD,API_VERSION, entityName);
        List<String> ids = getClient().getUniqueIds(endPoint, key, connectorInfo.getAuthConfig());
        return ids.isEmpty() ? null : ids.get(0);
    }

    Optional<EntitySchema> toEntitySchema(String entityName, ConnectorInfo connectorInfo) {
        String describeEndpoint = "";
        if (PRIMARY_OBJECTS.contains(entityName))
            describeEndpoint = getHOST(connectorInfo.getAuthConfig()) + String.format(PRIMARY_OBJECT_DESCRIBE_ENTITY_RESOURCE_PATH, API_VERSION, ENTITY_TO_RESOURCE_MAP.get(entityName));
        else if (SECONDARY_OBJECTS.contains(entityName)){
            String parentEntityName = getParentEntityName(entityName);
            String sampleParentId = getParentId(parentEntityName, SUPPORTED_ID_FIELDS.get(parentEntityName) , connectorInfo);
            if (StringUtils.isBlank(sampleParentId)) {
                log.error("Cannot create entity for Secondary object {} as no parent object {} has no records", entityName, parentEntityName);
                return Optional.empty();
            }
            describeEndpoint = getHOST(connectorInfo.getAuthConfig()) + String.format(SECONDARY_OBJECT_DESCRIBE_ENTITY_RESOURCE_PATH, API_VERSION, parentEntityName, sampleParentId, ENTITY_TO_RESOURCE_MAP.get(entityName));
        } else {
            throw new RuntimeException(String.format("Entity name %s is not supported for Oracle ERP sales synapse", entityName));
        }
        ResponseEntity<String> describeRespectiveEntity = getClient()
                .getResponse(describeEndpoint, connectorInfo.getAuthConfig(),null);
        if (describeRespectiveEntity.getStatusCode() != HttpStatus.OK) {
            log.error("describe response for entity {} received {}", entityName,describeRespectiveEntity);
            throw new RuntimeException(String.format("Failed to get response for entity %s due to %s", entityName, describeRespectiveEntity));
        }
        Map describeMap;
        try {
            describeMap = mapper.readValue(describeRespectiveEntity.getBody(), Map.class);
        } catch (JsonProcessingException e1) {
            throw new RuntimeException("Failed to read detailed describe response.", e1);
        }
        Map<String, Object> resourcesMap = (Map<String, Object>) describeMap.get("Resources");
        Map<String, Object> entityResponse = (Map<String, Object>) resourcesMap.get(ENTITY_TO_RESOURCE_MAP.get(entityName));
        log.debug("Raw Response is :\n " +entityResponse);
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) entityResponse.get("attributes");
        String displayName = (String)entityResponse.getOrDefault(DISPLAY_NAME_KEY, entityName);
        String pluralName = (String)entityResponse.getOrDefault(DISPLAY_NAME_KEY, entityName);
        EntitySchema entitySchema = new EntitySchema(entityName);
        entitySchema.setDisplayName(displayName);
        entitySchema.setReadOnly(false);
        entitySchema.setPluralName(pluralName);
        if(CollectionUtils.isNotEmpty(attributes)) {
            List<Map<String, Object>> isAttributesContainLastUpdatedAt = attributes.stream().filter(x -> x.get("name").equals("LastUpdateDate")).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(isAttributesContainLastUpdatedAt)) {
                List<AttributeSchema> allAttribsSchema = toAttributeSchemaList(attributes, entityName);
                if(SECONDARY_OBJECTS.contains(entityName)) {
                    AttributeSchema parentIdField = new AttributeSchema(PARENT_ID_FIELD, "string");
                    parentIdField.setDisplayName(PARENT_ID_FIELD).setNillable(false);
                    allAttribsSchema.add(parentIdField);
                }
                entitySchema.setAttributes(allAttribsSchema);
            } else {
                log.info("Entity does not contain id field or watermark field, entity name is  {}", entityName);
                return Optional.empty();
            }
        }
        else {
            log.info("Entity does not have attributes, entity name is  {}",entityName);
            return Optional.empty();
        }
        return Optional.of(entitySchema);
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        ConnectorInfo connectorInfo = request.getConnector();
        Optional<EntitySchema> result = toEntitySchema(request.getEntity(), connectorInfo);
        return result;
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        ConnectorInfo connectorInfo = request.getConnector();
        List<EntitySchema> entitySchemaList = new ArrayList<>();
        ENTITY_TO_RESOURCE_MAP.keySet().forEach(entityName -> {
            Optional<EntitySchema> entitySchema = toEntitySchema(entityName, connectorInfo);
            entitySchema.ifPresent(entitySchemaGet -> entitySchemaList.add(entitySchemaGet));
        });
        return entitySchemaList;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support create Object field");
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support delete field");
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getEndpointField(), ConnectorHelper.getSupportedAuthPicker());
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
        return Constants.ORACLE_ERP_SALES;
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/oraclepim.svg")
                .setDisplayName("Oracle ERP Sales")
                .setBackgroundColor("#F8F8F8")
                .setHelpUrl("");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    @Override
    public boolean isSink() { return false; }
}

