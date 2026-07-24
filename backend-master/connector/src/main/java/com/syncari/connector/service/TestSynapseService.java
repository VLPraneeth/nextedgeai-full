package com.syncari.connector.service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.ListBasedIterator;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component(Constants.TEST_SYNAPSE)
public class TestSynapseService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService {

    // In-memory storage for dynamically created entities and fields
    // Key: connectorId, Value: Map of entityApiName -> EntitySchema
    private static final Map<String, Map<String, EntitySchema>> dynamicEntities = new HashMap<>();

    /**
     * Reset all dynamic entities (call this between tests)
     */
    public static void resetDynamicEntities() {
        dynamicEntities.clear();
    }

    /**
     * Get or create the entity map for a connector
     */
    private Map<String, EntitySchema> getEntityMap(String connectorId) {
        return dynamicEntities.computeIfAbsent(connectorId, k -> new HashMap<>());
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
        return new SyncResponse();
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return new SyncResponse();
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return new SyncResponse();
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        // First check dynamic entities
        String connectorId = request.getConnector() != null ? request.getConnector().getId() : "default";
        Map<String, EntitySchema> entityMap = getEntityMap(connectorId);
        if (entityMap.containsKey(request.getEntity())) {
            return Optional.of(entityMap.get(request.getEntity()));
        }
        // Fall back to seeded entities
        EntitySchema seeded = TestSynapseSeed.getSeededEntity(request.getEntity());
        return Optional.ofNullable(seeded);
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        // Combine seeded and dynamic entities
        String connectorId = request.getConnector() != null ? request.getConnector().getId() : "default";
        Map<String, EntitySchema> entityMap = getEntityMap(connectorId);

        List<EntitySchema> result = new java.util.ArrayList<>(TestSynapseSeed.getAllSeededEntities());
        result.addAll(entityMap.values());
        return result;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        String connectorId = request.getConnector() != null ? request.getConnector().getId() : "default";
        EntitySchema entitySchema = request.getSchema();

        if (entitySchema == null) {
            log.error("Cannot create object: entity schema is null");
            return null;
        }

        // Create a copy of the entity schema
        EntitySchema createdEntity = new EntitySchema(entitySchema.getApiName(), entitySchema.getDisplayName());
        createdEntity.setCustom(entitySchema.isCustom());

        // Add default system fields
        createdEntity.addField(new AttributeSchema()
                .setApiName("id")
                .setDisplayName("Id")
                .setDataType("id")
                .setIdField(true)
                .setUpdateable(false)
                .setSystem(true)
                .setUnique(true)
                .setNillable(false));
        createdEntity.addField(new AttributeSchema()
                .setApiName("createdAt")
                .setDisplayName("Created At")
                .setDataType("datetime")
                .setCreatedAtField(true)
                .setSystem(true)
                .setUpdateable(false));
        createdEntity.addField(new AttributeSchema()
                .setApiName("updatedAt")
                .setDisplayName("Updated At")
                .setDataType("datetime")
                .setUpdatedAtField(true)
                .setWatermarkField(true)
                .setUpdateable(false));

        // Copy any existing attributes from the request
        if (entitySchema.getAttributes() != null) {
            for (AttributeSchema attr : entitySchema.getAttributes()) {
                if (!attr.getApiName().equals("id") && !attr.getApiName().equals("createdAt") && !attr.getApiName().equals("updatedAt")) {
                    createdEntity.addField(attr);
                }
            }
        }

        // Store in dynamic entities
        Map<String, EntitySchema> entityMap = getEntityMap(connectorId);
        entityMap.put(createdEntity.getApiName(), createdEntity);

        log.info("Created dynamic entity: {} for connector: {}", createdEntity.getApiName(), connectorId);
        return createdEntity;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        String connectorId = request.getConnector() != null ? request.getConnector().getId() : "default";
        String entityApiName = request.getEntityName();
        AttributeSchema fieldSchema = request.getSchema();

        if (fieldSchema == null) {
            log.error("Cannot create field: field schema is null");
            return null;
        }

        Map<String, EntitySchema> entityMap = getEntityMap(connectorId);
        EntitySchema entity = entityMap.get(entityApiName);

        if (entity == null) {
            // Check if it's a seeded entity - if so, create a mutable copy
            EntitySchema seededEntity = TestSynapseSeed.getSeededEntity(entityApiName);
            if (seededEntity != null) {
                entity = new EntitySchema(seededEntity.getApiName(), seededEntity.getDisplayName());
                entity.setCustom(seededEntity.isCustom());
                if (seededEntity.getAttributes() != null) {
                    for (AttributeSchema attr : seededEntity.getAttributes()) {
                        entity.addField(attr);
                    }
                }
                entityMap.put(entityApiName, entity);
            } else {
                log.error("Cannot create field: entity {} not found for connector {}", entityApiName, connectorId);
                return null;
            }
        }

        // Create the field
        AttributeSchema createdField = new AttributeSchema()
                .setApiName(fieldSchema.getApiName())
                .setDisplayName(fieldSchema.getDisplayName())
                .setDataType(fieldSchema.getDataType())
                .setNillable(fieldSchema.isNillable())
                .setUpdateable(fieldSchema.isUpdateable())
                .setIdField(fieldSchema.isIdField())
                .setWatermarkField(fieldSchema.isWatermarkField())
                .setSystem(false)
                .setCustom(true);

        entity.addField(createdField);
        log.info("Created field: {} in entity: {} for connector: {}", createdField.getApiName(), entityApiName, connectorId);

        return createdField;
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        String connectorId = request.getConnector() != null ? request.getConnector().getId() : "default";
        String entityApiName = request.getEntityName();
        String fieldApiName = request.getFieldName();

        Map<String, EntitySchema> entityMap = getEntityMap(connectorId);
        EntitySchema entity = entityMap.get(entityApiName);

        if (entity != null && entity.getAttributes() != null) {
            entity.setAttributes(entity.getAttributes().stream()
                    .filter(attr -> !attr.getApiName().equals(fieldApiName))
                    .collect(Collectors.toList()));
            log.info("Deleted field: {} from entity: {} for connector: {}", fieldApiName, entityApiName, connectorId);
        }
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of();
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of();
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of(Constants.ACCOUNT.toLowerCase(), "account", Constants.CONTACT.toLowerCase(), "contact");
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return TestSynapseSeed.getAttributeMappings(entityApiName);
    }

    @Override
    public String getName() {
        return Constants.TEST_SYNAPSE;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/syncari.svg")
                .setDisplayName(Constants.TEST_SYNAPSE)
                .setBackgroundColor("#f2fbff")
                .setHelpUrl(helpArticlesBaseUrl + SYNAPSE_COMING_SOON_ARTICLE);
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    @Override
    public String getCategory() {
        return "Test";
    }
}

@Slf4j
class TestSynapseSeed{

    public static final List<String> seededEntities = List.of("account", "contact");

    public static List<EntitySchema> getAllSeededEntities(){
        return seededEntities.stream().map(TestSynapseSeed::getSeededEntity).collect(Collectors.toList());
    }

    public static EntitySchema getSeededEntity(String entityName){
        switch (entityName){
            case "account": return getAccountEntity();
            case "contact": return getContactEntity();
            default:
                log.error("Entity {} is not seeded", entityName);
        }
        return null;
    }

    public static Map<String, String> getAttributeMappings(String entityApiName) {
        switch (entityApiName) {
            case "account": return getAccountAttrMapping();
            case "contact": return getContactAttrMapping();
            default:
                break;
        }
        return Map.of();
    }

    private static EntitySchema getAccountEntity() {
        EntitySchema program = new EntitySchema("account", StringUtils.capitalize("account"));
        // Add attributes
        program.addField(new AttributeSchema().setApiName("id").setDisplayName("Account Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        program.addField(new AttributeSchema().setApiName("name").setDisplayName("Account Name").setDataType("string").setUnique(true).setNillable(false));
        program.addField(new AttributeSchema().setApiName("description").setDisplayName("Account Description").setDataType("textarea"));
        program.addField(new AttributeSchema().setApiName("website").setDisplayName("Website").setDataType("url"));
        program.addField(new AttributeSchema().setApiName("phone").setDisplayName("Phone").setDataType("string"));
        program.addField(new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime").setCreatedAtField(true).setSystem(true).setUpdateable(false));
        program.addField(new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setDataType("datetime").setUpdatedAtField(true).setWatermarkField(true).setUpdateable(false));

        return program;
    }

    private static EntitySchema getContactEntity() {
        EntitySchema contact = new EntitySchema("contact", StringUtils.capitalize("contact"));
        // Add attributes
        contact.addField(new AttributeSchema().setApiName("id").setDisplayName("Contact Id").setDataType("id").setIdField(true).setUpdateable(false).setSystem(true).setUnique(true).setNillable(false));
        contact.addField(new AttributeSchema().setApiName("firstName").setDisplayName("First Name").setDataType("string").setNillable(false));
        contact.addField(new AttributeSchema().setApiName("lastName").setDisplayName("Last Name").setDataType("string").setNillable(false));
        contact.addField(new AttributeSchema().setApiName("email").setDisplayName("Email").setDataType("string").setNillable(false));
        contact.addField(new AttributeSchema().setApiName("company").setDisplayName("Company").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("accountId").setDisplayName("Account Id").setDataType("string").setReferenceTo("account"));
        contact.addField(new AttributeSchema().setApiName("phone").setDisplayName("Phone").setDataType("string"));
        contact.addField(new AttributeSchema().setApiName("createdAt").setDisplayName("Created At").setDataType("datetime").setCreatedAtField(true).setSystem(true).setUpdateable(false));
        contact.addField(new AttributeSchema().setApiName("updatedAt").setDisplayName("Updated At").setDataType("datetime").setUpdatedAtField(true).setWatermarkField(true).setUpdateable(false));

        return contact;
    }

    private static Map<String, String> getAccountAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("Name", "name");
        attrMap.put("Description", "description");
        attrMap.put("Website", "website");
        attrMap.put("Phone", "phone");
        return attrMap;
    }

    private static Map<String, String> getContactAttrMapping() {
        Map<String, String> attrMap = new HashMap<String, String>();
        attrMap.put("FirstName", "firstName");
        attrMap.put("LastName", "lastName");
        attrMap.put("Phone", "phone");
        attrMap.put("Email", "email");
        attrMap.put("company", "Company");
        return attrMap;
    }
}
