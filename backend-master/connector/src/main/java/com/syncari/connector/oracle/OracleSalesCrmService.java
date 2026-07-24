package com.syncari.connector.oracle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component(Constants.ORACLESALESCRM)
public class OracleSalesCrmService implements AuthenticationService, SynapseInfoService,MetadataService, CommonDataService {

    private static final String API_VERSION = "11.13.18.05";
    private static final String ENTITY_RESOURCE_PATH = "/crmRestApi/resources/%s/%s";
    private static final String C_ENTITY_RESOURCE_PATH = "/crmRestApi/resources/%s/";
    private static final String DESCRIBE_ALL_RESOURCE_PATH = "/crmRestApi/resources/%s/describe?metadataMode=minimal";
    private static final String DESCRIBE_ENTITY_RESOURCE_PATH = "/crmRestApi/resources/%s/%s/describe";
    private static final String WATERMARK_QUERY_PARAMS = "?q=(%s>'%s' and %s<='%s')&onlyData=true&limit=%s&offset=%s&orderBy=%s";
    private static final String ID_QUERY_PARAMS = "?q=%s='%s'&onlyData=true";
    public static final List<String> SUPPORTED_ENTITIES = List.of("accounts", "leads", "opportunities", "contacts","resourceUsers", "activities",
        "deals", "partners","partnerContacts");

    private static final Map<String,String> SUPPORTED_ID_FIELDS = Map.of("accounts","PartyNumber","opportunities","OptyNumber","activities","ActivityNumber","leads",
            "LeadId","resourceUsers","ResourceProfileId","contacts","PartyNumber","deals","DealId","partners","CompanyNumber",
            "partnerContacts","PartyNumber");

    @Autowired
    private ObjectMapper mapper;

    protected static final int API_MAX_PAGESIZE = 200;
    protected static final int CUD_API_MAX_RECORDS = 50;

    private JsonParserConfig getSingleJsonConfig() {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("PartyId"), true, null);
    }

    protected OracleSalesCrmRestClient getClient(AuthConfig config) {
        return new OracleSalesCrmRestClient(getSingleJsonConfig(), mapper);
    }

    public String getAuthHost(AuthConfig config) {
        return config.getEndpoint();
    }

    private String buildWaterMarkEntityEndPoint(ConnectorInfo connectorInfo, String entityName,int limit, Long offset, String orderByField, String start,String end){
        String endpoint = connectorInfo.getEndpoint();
        String formattedString = String.format(WATERMARK_QUERY_PARAMS,orderByField,start,orderByField,end, limit, offset, orderByField);
        String resourcePath = String.format(ENTITY_RESOURCE_PATH, API_VERSION, entityName) + formattedString;
        return endpoint + resourcePath;
    }
    private String buildByIdEntityEndPoint(ConnectorInfo connectorInfo, String entityName,String idfield, String idFieldValue){
        String endpoint = connectorInfo.getEndpoint();
        String formattedString = String.format(ID_QUERY_PARAMS, idfield, idFieldValue);
        String resourcePath = String.format(ENTITY_RESOURCE_PATH, API_VERSION, entityName) + formattedString;
        return endpoint + resourcePath;
    }

    private String buildCUDEntityEndpoint(ConnectorInfo connectorInfo){
        String endpoint = connectorInfo.getEndpoint();
        String resourcePath = String.format(C_ENTITY_RESOURCE_PATH, API_VERSION);
        return endpoint + resourcePath;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo connectorInfo, List<String> entityNames) {
        String testEndpoint = connectorInfo.getEndpoint() + String.format(DESCRIBE_ALL_RESOURCE_PATH, API_VERSION);
        TestConnectionResponse result = new TestConnectionResponse();
        try {
            ResponseEntity<String> data = getClient(connectorInfo.getAuthConfig()).getResponse(testEndpoint,connectorInfo.getAuthConfig());
            log.debug("Data received from minimal describe " + data);
        } catch (Exception e) {
            handleAuthenticationErrorMessage(result, e);
        }
        return result;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        ConnectorInfo connectorInfo = request.getConnector();
        Optional<EntitySchema> result = toEntitySchema(request.getEntity(), connectorInfo);
        if(result.isPresent() && request.getExistingSchema() != null && request.getExistingSchema().isPresent() && checkForFieldDeletions(result.get(), request.getExistingSchema().get())) {
            return toEntitySchema(request.getEntity(), connectorInfo);
        }
        return result;
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        ConnectorInfo connectorInfo = request.getConnector();
        String describeAllEndpoint = connectorInfo.getEndpoint() + String.format(DESCRIBE_ALL_RESOURCE_PATH, API_VERSION);
        ResponseEntity<String> data = getClient(connectorInfo.getAuthConfig())
                .getResponse(describeAllEndpoint, connectorInfo.getAuthConfig(),null);
        return toEntitySchemas(request, data);
    }

    private List<EntitySchema> toEntitySchemas(DescribeAllRequest request, ResponseEntity<String> resp) {
        ConnectorInfo connectorInfo = request.getConnector();
        List<EntitySchema> entitySchemaList = new ArrayList<>();
        if (resp.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException(resp.getBody());
        }
        Map respMap;
        try {
            respMap = mapper.readValue(resp.getBody(), Map.class);
        } catch (JsonProcessingException e1) {
            throw new RuntimeException("Failed to read entities.", e1);
        }
        Map<String, Object> rawSchemas = (Map<String, Object>) respMap.get("Resources");
        Set<String> allEntities = rawSchemas.keySet();
        // filter only supported entities
        List<String> filteredEntities = allEntities.stream().filter(entityName -> SUPPORTED_ENTITIES.contains(entityName)).collect(Collectors.toList());
        filteredEntities.forEach(entityName -> {
            Optional<EntitySchema> entitySchema = toEntitySchema(entityName, connectorInfo);
            entitySchema.ifPresent(entitySchemaGet -> entitySchemaList.add(entitySchemaGet) );
        });
        return entitySchemaList;
    }

    Optional<EntitySchema> toEntitySchema(String entityName, ConnectorInfo connectorInfo) {
        String describeEndpoint = connectorInfo.getEndpoint() + String.format(DESCRIBE_ENTITY_RESOURCE_PATH,API_VERSION, entityName);
        ResponseEntity<String> describeRespectiveEntity = getClient(connectorInfo.getAuthConfig())
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
        Map<String, Object> entityResponse = (Map<String, Object>) resourcesMap.get(entityName);
        log.debug("Raw Response is :\n " +entityResponse);
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) entityResponse.get("attributes");

        String displayName = (String)entityResponse.get("title");
        String pluralName = (String)entityResponse.get("titlePlural");
        EntitySchema entitySchema = new EntitySchema(entityName);
        entitySchema.setDisplayName(displayName);
        entitySchema.setReadOnly(false);
        entitySchema.setPluralName(pluralName);
        if(CollectionUtils.isNotEmpty(attributes)){
            List<Map<String, Object>> isAttributesContainLastUpdatedAt =  attributes.stream().filter(x -> x.get("name").equals("LastUpdateDate") ).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(isAttributesContainLastUpdatedAt)){
                List<AttributeSchema> allAttribsSchema = toAttributeSchemaList(attributes,entityName);
                entitySchema.setAttributes(allAttribsSchema);
            }else{
                log.info("Entity does not contain id field or watermark field, entity name is  {}",entityName);
                return Optional.empty();
            }
        }else{
            log.info("Entity does not attributes, entity name is  {}",entityName);
            return Optional.empty();
        }
        return Optional.of(entitySchema);
    }


    boolean checkForFieldDeletions(EntitySchema entity, EntitySchema existing) {
        if (!entity.getAttributes().isEmpty()) {
            for (AttributeSchema attr : existing.getAttributes()) {
                if (!attr.isSyncariDefined() && !entity.hasField(attr.getApiName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<AttributeSchema> toAttributeSchemaList(List<Map<String, Object>> attributes, String entityName){
        List<AttributeSchema> allAttributeSchema = new ArrayList<>();
        attributes.forEach(attrib -> {
            allAttributeSchema.add(toAttribSchema(attrib,entityName));
        });
        return allAttributeSchema;
    }

    private AttributeSchema toAttribSchema(Map<String, Object> attributeMap, String entityName) {
        String supportedIdField = SUPPORTED_ID_FIELDS.get(entityName);
        log.debug("raw values of map is {} ", attributeMap);
        Map<String, OracleRefObject> refFields = OracleReferenceSeed.getReferenceMappings(entityName);
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
        if (refFields.containsKey(field.getApiName())){
            OracleRefObject oracleRefFields =  refFields.get(field.getApiName());
            field.setReferenceTo(oracleRefFields.getEntityName());
            field.setReferenceTargetField(oracleRefFields.getTargetFieldId());
            field.setDataType("reference");
            return field;
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

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator = (wm, pageSize, offset) -> {
            ConnectorInfo connectorInfo = request.getConnector();
            String start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wm.getStart()), ZoneOffset.UTC).toString();
            String end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wm.getEnd()), ZoneOffset.UTC).toString();
            String watermarkField = request.getEntitySchema().getWatermarkField().getApiName();
            String url = buildWaterMarkEntityEndPoint(connectorInfo, request.getEntityName(),pageSize,offset,watermarkField, start, end);
            log.info("Url to fetch data by watermark is {}",url);
            return getClient(connectorInfo.getAuthConfig()).getDataWithOffset(url,offset,request);
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();

        DefaultDataOffsetIterator iterator = new DefaultDataOffsetIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pgSize, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> data = new ArrayList<>();
        ConnectorInfo connectorInfo = request.getConnector();
        String idField = request.getEntitySchema().getIdField().getApiName();
        request.getIds().forEach(id -> {
            String url = buildByIdEntityEndPoint(connectorInfo, request.getEntityName(),idField,id);
            data.addAll(getClient(connectorInfo.getAuthConfig()).getData(url,request));
        });
        return data;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        String url = buildCUDEntityEndpoint(request.getConnector());
        return getClient(request.getConnector().getAuthConfig()).postRecords(url, HttpMethod.POST, request,"create", "/" + request.getEntityName());
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        String url = buildCUDEntityEndpoint(request.getConnector());
        String pathForEachOperation = "/" + request.getEntityName() + "/%s";
        return getClient(request.getConnector().getAuthConfig()).postRecords(url, HttpMethod.PATCH, request, "update", pathForEachOperation);
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        String url = buildCUDEntityEndpoint(request.getConnector());
        String pathForEachOperation = "/" + request.getEntityName() + "/%s";
        return getClient(request.getConnector().getAuthConfig()).postRecords(url, HttpMethod.DELETE, request, "delete",pathForEachOperation );
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
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return new HashMap<>();
    }

    @Override
    public String getName() {
        return Constants.ORACLESALESCRM;
    }

    @Override
    public String getCategory() {
        return "CRM";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/oraclesalescrm.svg")
                .setDisplayName("Oracle CRM")
                .setBackgroundColor("#F8F8F8")
                .setHelpUrl(helpArticlesBaseUrl + "/4409985554452");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19207362798996";
    }
}
