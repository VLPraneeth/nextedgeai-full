package com.syncari.connector.oraclepim;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.JsonNodeDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.AbstractEntityDataBatchIterator;
import com.syncari.connector.data.iterator.DefaultDataIterator;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import java.io.StringWriter;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Component(Constants.ORACLE_PIM)
public class OraclePIMService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService {

    private static final String ITEMS_ENTITY_NAME = "Items";

    public static final String ITEMS_RESOURCE_NAME = "itemsV2";
    public static final List<String> SUPPORTED_ENTITIES = List.of(ITEMS_ENTITY_NAME);
    public static final Map<String, String> SUPPORTED_ID_FIELDS = Map.of(ITEMS_ENTITY_NAME, "ItemId");
    public static final int API_MAX_PAGESIZE = 200;
    public static String NAME = "name";

    private static final String API_VERSION = "11.13.18.05";
    private static final String DESCRIBE_ENTITY_RESOURCE_PATH = "/fscmRestApi/resources/%s/%s/describe";
    private static Map<String, String> ENTITY_CONFIG = Map.of("itemsV2", "title");
    private static final String RESOURCE_PATH = "/fscmRestApi/resources/" + API_VERSION + "/%s";
    private static final String ITEMS_URL_SOAP = "%s/fscmService/ItemServiceV2";
    private static final String WM_FIELD = "LastUpdateDateTime";
    private static final String ITEM_ATTACHMENT = "ItemAttachment";
    private static final String ITEM_CATEGORY = "ItemCategory";
    private static final Map<String, String> ENTITY_NAME_TO_RESOURCE_MAP = Map.of(ITEMS_ENTITY_NAME,ITEMS_RESOURCE_NAME);
    private static final Set<String> JSON_FIELDS_IN_ITEMS = Set.of("ItemCategory", "ItemTranslation", "ItemRevision", "ItemEffCategory", "ItemDFF", "ItemGlobalDFF", ITEM_ATTACHMENT);
    private static final Map<String, String> RESURCE_NAME_TO_ENTITY_MAP = Map.of(ITEMS_RESOURCE_NAME, ITEMS_ENTITY_NAME);
    private static final Set<String> JSON_ARRAY_FIELDS = Set.of(ITEM_ATTACHMENT, ITEM_CATEGORY);

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    LocalStorageService localStorageService;

    private  OraclePIMServiceSoapClient getSOAPClient(){
        return new OraclePIMServiceSoapClient();
    }

    private String getItemsEndPoint(ConnectorInfo config){
        return String.format(ITEMS_URL_SOAP, getAPIPathSoap(config));
    }

    private String getHOST(ConnectorInfo config) {
        return config.getEndpoint();
    }

    private String getAPIPath(ConnectorInfo config) {
        return getHOST(config) + String.format(RESOURCE_PATH, ITEMS_RESOURCE_NAME);
    }

    private String getAPIPathSoap(ConnectorInfo connectorInfo) {
        return getHOST(connectorInfo);
    }

    private JsonParserConfig getSingleJsonConfig() {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
    }

    protected OraclePIMServiceRestClient getClient() {
        return new OraclePIMServiceRestClient(getSingleJsonConfig(), mapper);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {

        TestConnectionResponse result = new TestConnectionResponse();
        try {
            ResponseEntity<String> data = getClient().getResponse(getAPIPath(config), config.getAuthConfig());
            log.debug("Data received from minimal describe " + data);
        } catch (Exception e) {
            handleAuthenticationErrorMessage(result, e);
            return result;
        }

        OraclePIMServiceSoapClient c = getSOAPClient();
        try {
            if (!(c.getItem(getItemsEndPoint(config), config).getSOAPBody().getFault() == null))
                result.setErrors(List.of("Authentication failed. Please verify the Endpoint, username, password and try again"));
        } catch (Exception e) {
            result.setErrors(List.of("Authentication failed. Please verify the Endpoint, username, password and try again"));
        }
        return result;
    }

    private Node getChildNode(String nodeLocalName, Node parentNode) {
        NodeList childNodes = parentNode.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            childNodes.item(i);
            Node node = childNodes.item(i);
            if (nodeLocalName.equals(node.getLocalName())) {
                return node;
            }
        }
        return null;
    }

    private Node getChildNode(String nodeLocalName, SOAPBody body) {
        NodeList childNodes = body.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            childNodes.item(i);
            Node node = childNodes.item(i);
            if (nodeLocalName.equals(node.getLocalName())) {
                return node;
            }
        }
        return null;
    }

    private String parseJsonField(Node node) {
        StringWriter output = new StringWriter();
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(node), new StreamResult(output));
            String xml = output.toString();
            XmlMapper xmlMapper = new XmlMapper();
            xmlMapper.registerModule(new SimpleModule().addDeserializer(
                JsonNode.class,
                new DuplicateToArrayJsonNodeDeserializer()
        ));
            return mapper.writeValueAsString(xmlMapper.readTree(xml.getBytes()));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> parseItem(Node itemData) {
        Map<String, Object> result = new HashMap<>();
        NodeList childNodes = itemData.getChildNodes();
        List<Object> itemAttachmentsList = new ArrayList<>();
        List<Object> itemCategoryList = new ArrayList<>();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            String key = node.getLocalName();
            Object value;
            if (JSON_FIELDS_IN_ITEMS.contains(key) && !JSON_ARRAY_FIELDS.contains(key)) {
                try {
                    value = mapper.readValue(parseJsonField(node), Map.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            } else
                value = node.getChildNodes().getLength() == 0 ? null : node.getChildNodes().item(0).getNodeValue();
            if (JSON_ARRAY_FIELDS.contains(key)) {
                (key.equals(ITEM_CATEGORY) ? itemCategoryList : itemAttachmentsList).add(new JSONObject(parseJsonField(node)));
            }
            else
                result.put(key, value);
        }
        result.put(ITEM_CATEGORY,  new JSONArray(itemCategoryList).toString());
        result.put(ITEM_ATTACHMENT, new JSONArray(itemAttachmentsList).toString());
        return result;
    }

    private Node getResultNode(SOAPMessage response) {
        try {
            SOAPBody soapBody = response.getSOAPBody();
            Node itemResponse = getChildNode("findItemResponse", soapBody);
            if (itemResponse == null){
                log.error("Cannot find key findItemResponse from items response");
                return null;
            }
            return getChildNode("result", itemResponse);
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Map<String, Object>> parseItems(SOAPMessage response){
        List<Map<String, Object>> items = new ArrayList<>();
        Node result = getResultNode(response);
        if (result != null) {
            for (int i = 0; i < result.getChildNodes().getLength(); i++) {
                Node node = result.getChildNodes().item(i);
                items.add(parseItem(node));
            }
        }
        return items;
    }

    public EntityData getOneRecord(String itemId, SyncRequest request) {
        ConnectorInfo config = request.getConnector();
        OraclePIMServiceSoapClient client = getSOAPClient();
        SOAPMessage response = client.getItemById(itemId, getItemsEndPoint(config),  request.getConnector());
        List<Map<String, Object>> data = parseItems(response);
        List<EntityData> result = processSOAPResponse(data, request);
        if (result.size() != 1){
            log.error("More than 1 record matched for ItemId : "+itemId);
            throw new RuntimeException("More than 1 record matched for ItemId : "+itemId);
        }
        return result.get(0);
    }

    public FetchResponse getAllRecords(SyncRequest request) {
        localStorageService.provisionIfNotExists(request, request.getEntityName());
        localStorageService.cleanupDB(request);
        localStorageService.provisionIfNotExists(request, request.getEntityName());
        OraclePIMServiceSoapClient client = getSOAPClient();
        long start = Instant.ofEpochMilli(0).toEpochMilli();
        Optional<EntitySchema> entitySchemaInfo = describe(new DescribeRequest(request.getConnector(), request.getEntityName()));
        if (entitySchemaInfo.isEmpty()) {
            log.error("Error fetching entity schema for entity : " + request.getEntityName());
            throw new RuntimeException("Error fetching entity schema for entity : " + request.getEntityName());
        }
        EntitySchema entitySchema = entitySchemaInfo.get();

        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            //wm is not used, only offset is used for iteration
            log.info("Using offset {} pagesize {}", offset, pageSize);
            int retries = 5;
            SOAPMessage response = null;

            while (retries > 0) {
                try {
                    response = client.getItems(offset.intValue(), pageSize, request.getConnector(), getItemsEndPoint(request.getConnector()));
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

            if(response == null) {
                throw new RuntimeException("Failed to fetch results for offset - " + offset);
            }
            List<EntityData> result;
            List<Map<String, Object>> currBatch = parseItems(response);
            result = processSOAPResponse(currBatch, request);
            log.info("Got {} records for offser {}, pagesize {}",result.size(), offset, pageSize);
            return Pair.of(Long.valueOf(result.size()), result.stream());
        };
        WatermarkInfo initialWatermark = new WatermarkInfo(start, Instant.now().toEpochMilli(), true, 0);
        OraclePIMIterator iterator = new OraclePIMIterator(initialWatermark, 0, generator, new ArrayList<>(), request.getEntitySchema().getWatermarkField(), API_MAX_PAGESIZE);
        localStorageService.fetch(request, iterator);
        int currOffset = API_MAX_PAGESIZE;
        while (!iterator.isEmptyResult()){
            // iterate till we get empty results
            iterator = new OraclePIMIterator(initialWatermark, currOffset, generator, new ArrayList<>(), request.getEntitySchema().getWatermarkField(), API_MAX_PAGESIZE);
            localStorageService.fetch(request, iterator);
            currOffset += API_MAX_PAGESIZE;
        }
        FetchResponse fetchResponse = localStorageService.getByWatermark(request.setWatermark(initialWatermark));
        AbstractEntityDataBatchIterator localStorageIterator = (AbstractEntityDataBatchIterator)fetchResponse.getIterator();
        localStorageIterator.setMaxRecordsPerEntitySyncCycle(currOffset);
        return fetchResponse;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        return getAllRecords(request);
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> data = new ArrayList<>();
        request.getIds().forEach(id -> data.add(getOneRecord(id, request)));
        return data;
    }

    protected List<EntityData> processSOAPResponse(List<Map<String, Object>> response, SyncRequest request) {
        String idField = request.getEntitySchema().getIdField().getApiName();
        String wmField = request.getEntitySchema().getWatermarkField().getApiName();
        List<EntityData> result = new ArrayList<>();
        int numberOfRecordsToProcess = response.size();
        IntStream.range(0, numberOfRecordsToProcess).forEach(x -> {
            Map<String, Object> attributes = response.get(x);
            result.add(processOneItem(idField, wmField, request.getEntityName(), attributes));
        });
        return result;
    }

    public EntityData processOneItem(String idField, String wmField, String entityName, Map<String, Object> attributes) {
        var ed = new EntityData();
        ed.setName(entityName);
        ed.setCreatedAt(ZonedDateTime.parse(attributes.get("CreationDateTime").toString()).toEpochSecond() * 1000);
        attributes.forEach((k, v) -> {
            if (k.equalsIgnoreCase(idField)) {
                ed.setId(v.toString());
            }
            if (wmField.equalsIgnoreCase(k)) {
                ed.setLastModified(ZonedDateTime.parse(attributes.get(wmField).toString()).toEpochSecond() * 1000);
            }
            ed.addValue(k, v);
        });
        return ed;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        throw new RuntimeException("Create not supported for Oracle PIM synapse");
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        throw new RuntimeException("Update not supported for Oracle PIM synapse");
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        throw new RuntimeException("Delete not supported for Oracle PIM synapse");
    }

    private AttributeSchema createAttribute(String attrName){
        String dataType = (JSON_FIELDS_IN_ITEMS.contains(attrName) && !JSON_ARRAY_FIELDS.contains(attrName)) ? "object" : "string";
        AttributeSchema field = new AttributeSchema(attrName, dataType);
        field.setDisplayName(attrName);
        field.setNillable(true);
        field.setUpdateable(true);
        return field;
    }

    private AttributeSchema toAttribSchema(Map<String, Object> attributeMap, String entityName) {
        String supportedIdField = SUPPORTED_ID_FIELDS.get(RESURCE_NAME_TO_ENTITY_MAP.getOrDefault(entityName, entityName));
        log.debug("raw values of map is {} ", attributeMap);
        AttributeSchema field = new AttributeSchema(attributeMap.get("name").toString(), attributeMap.get("type").toString().toLowerCase());
        field.setDisplayName(attributeMap.containsKey("title") ? attributeMap.get("title").toString() : attributeMap.get("name").toString());

        field.setNillable(!(Boolean) attributeMap.get("mandatory"));
        field.setUpdateable(attributeMap.containsKey("updatable") ? (Boolean) attributeMap.get("updatable") : true);

        if (supportedIdField.equals(field.getApiName())) {
            field.setIdField(true);
            field.setSystem(true);
            field.setUpdateable(false);
            field.setNillable(false);
        }

        switch (field.getApiName()) {
            case WM_FIELD:
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

    private List<AttributeSchema> toAttributeSchemaList(List<Map<String, Object>> attributes, Set<String> soapAttrs, String entityName) {
        List<AttributeSchema> allAttributeSchema = new ArrayList<>();
        attributes.forEach(attrib -> {
            allAttributeSchema.add(toAttribSchema(attrib, entityName));
            if (attrib.containsKey("name")){
                soapAttrs.remove((String) attrib.get("name"));
            }
        });
        soapAttrs.forEach(attr -> {
            allAttributeSchema.add(createAttribute(attr));
        });
        if(allAttributeSchema.stream().filter(a-> a.getApiName().equals(ITEM_ATTACHMENT)).findFirst().isEmpty())
            allAttributeSchema.add(createAttribute(ITEM_ATTACHMENT));
        if(allAttributeSchema.stream().filter(a-> a.getApiName().equals(ITEM_CATEGORY)).findFirst().isEmpty())
            allAttributeSchema.add(createAttribute(ITEM_CATEGORY));

        return allAttributeSchema;
    }

    public Set<String> getItemAttributes(ConnectorInfo config){
        OraclePIMServiceSoapClient soapClient = getSOAPClient();
        Set<String> attributes = new HashSet<>();

        SOAPMessage response = soapClient.getItems(0,1, config, getItemsEndPoint(config));
        Node result = getResultNode(response);

        if (result == null || result.getChildNodes().getLength() <= 0) {
            log.error("Error fetching records for schema. No records found for the given config : "+config.getMetaConfig());
            throw new RuntimeException("No records found for the given filters");
        }
        Node valueNode = result.getChildNodes().item(0);

        for (int i = 0; i < valueNode.getChildNodes().getLength(); i++) {
            Node node = valueNode.getChildNodes().item(i);
            attributes.add(node.getLocalName());
        }
        return attributes;
    }

    Optional<EntitySchema> toEntitySchema(String entityName, ConnectorInfo connectorInfo) {
        Set<String> attrsFromSoap = getItemAttributes(connectorInfo);
        String resourceName = ENTITY_NAME_TO_RESOURCE_MAP.getOrDefault(entityName, entityName);

        String describeEndpoint = connectorInfo.getEndpoint() + String.format(DESCRIBE_ENTITY_RESOURCE_PATH, API_VERSION, resourceName);
        ResponseEntity<String> describeRespectiveEntity = getClient()
                .getResponse(describeEndpoint, connectorInfo.getAuthConfig(), null);
        if (describeRespectiveEntity.getStatusCode() != HttpStatus.OK) {
            log.error("describe response for entity {} received {}", entityName, describeRespectiveEntity);
            throw new RuntimeException(String.format("Failed to get response for entity %s due to %s", entityName, describeRespectiveEntity));
        }
        Map describeMap;
        try {
            describeMap = mapper.readValue(describeRespectiveEntity.getBody(), Map.class);
        } catch (JsonProcessingException e1) {
            throw new RuntimeException("Failed to read detailed describe response.", e1);
        }
        Map<String, Object> resourcesMap = (Map<String, Object>) describeMap.get("Resources");
        Map<String, Object> entityResponse = (Map<String, Object>) resourcesMap.get(resourceName);
        log.debug("Raw Response is :\n " + entityResponse);
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) entityResponse.get("attributes");

        String pluralName = (String) entityResponse.get(ENTITY_CONFIG.get(entityName));
        EntitySchema entitySchema = new EntitySchema(entityName);
        entitySchema.setDisplayName(entityName);
        entitySchema.setReadOnly(false);
        entitySchema.setPluralName(pluralName);

        if (CollectionUtils.isNotEmpty(attributes)) {
            List<Map<String, Object>> isAttributesContainLastUpdatedAt = attributes.stream().filter(x -> x.get("name").equals(WM_FIELD)).collect(Collectors.toList());
            if (attrsFromSoap.contains(WM_FIELD) && CollectionUtils.isNotEmpty(isAttributesContainLastUpdatedAt)) {
                List<AttributeSchema> allAttribsSchema = toAttributeSchemaList(attributes, attrsFromSoap, entityName);
                entitySchema.setAttributes(allAttribsSchema);
            } else {
                log.info("Entity does not contain id field or watermark field, entity name is  {}", entityName);
                return Optional.empty();
            }
        } else {
            log.info("Entity does not have attributes, entity name is  {}", entityName);
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
        SUPPORTED_ENTITIES.forEach(entityName -> {
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

    public static AuthField getItemClassFilter() {
        AuthField filters = new AuthField();
        filters.setDataType("text");
        filters.setName("itemClass");
        filters.setLabel("Item Class");
        filters.setRequired(false);
        filters.setHelpSummary("Given filters will be applied to the items entity during sync. Format : itemClass1, itemClass2");
        return filters;
    }

    public static AuthField getItemStatusFilter() {
        AuthField filters = new AuthField();
        filters.setDataType("text");
        filters.setName("itemStatus");
        filters.setLabel("Item Status");
        filters.setRequired(false);
        filters.setHelpSummary("Accepts comma separated values. Leave it empty to ignore this filter. Format : Active, Eliminate, Deprecated");
        return filters;
    }

    public static AuthField getOrganizationCodeFilter() {
        AuthField filters = new AuthField();
        filters.setDataType("text");
        filters.setName("organizationCode");
        filters.setLabel("Organization Code");
        filters.setRequired(true);
        filters.setHelpSummary("Organization code. Only one organization can be managed in a synapse.");
        return filters;
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getEndpointField(), ConnectorHelper.getSupportedAuthPicker(),
                getItemClassFilter(), getItemStatusFilter(), getOrganizationCodeFilter());
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
        return Constants.ORACLE_PIM;
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/oraclepim.svg")
                .setDisplayName("Oracle PIM")
                .setBackgroundColor("#F8F8F8")
                .setHelpUrl(helpArticlesBaseUrl + "/");
    }

    @Override
    public boolean isSink() { return false; }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }
}

class OraclePIMIterator extends DefaultDataIterator {

    public OraclePIMIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator, List<EntityData> data, AttributeSchema watermarkField, int maxRecords) {
        super(baseWatermark, offset, generator, data, watermarkField, maxRecords, OraclePIMService.API_MAX_PAGESIZE);
    }

    public  boolean isEmptyResult() {
        return totalRecordsFetched == 0;
    }

}

class DuplicateToArrayJsonNodeDeserializer extends JsonNodeDeserializer {

    @Override
    protected void _handleDuplicateField(JsonParser p, DeserializationContext ctxt,
                                         JsonNodeFactory nodeFactory, String fieldName, ObjectNode objectNode,
                                         JsonNode oldValue, JsonNode newValue) {
        ArrayNode node;
        if(oldValue instanceof ArrayNode){
            node = (ArrayNode) oldValue;
            node.add(newValue);
        } else {
            node = nodeFactory.arrayNode();
            node.add(oldValue);
            node.add(newValue);
        }
        objectNode.set(fieldName, node);
    }
}

