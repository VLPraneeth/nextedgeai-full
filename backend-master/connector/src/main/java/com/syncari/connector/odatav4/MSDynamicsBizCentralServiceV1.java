package com.syncari.connector.odatav4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.UrlEscapers;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.Stats;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.Transformer;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.request.retrieve.EdmMetadataRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetRequest;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.uri.URIBuilder;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.client.core.http.DefaultHttpClientFactory;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.syncari.utils.ExceptionUtils.rethrow;
import static java.lang.String.format;

@Slf4j
@Component("msdynamicsbizcentralv1")
class MSDynamicsBizCentralServiceV1 {

    @Autowired
    ObjectMapper mapper;

    @SneakyThrows
    public FetchResponse getByWatermark(SyncRequest r) {
        ODataClient client = getODataClient(r.getConnector());
        final EntitySchema entitySchema = r.getEntitySchema();
        final Optional<EntitySchema> parentSchema = getParentSchemaIfPresent(r);
        final WatermarkInfo watermark = r.getWatermark();

        final long start = watermark.getStart();
        final long offset = watermark.getOffset();
        final int pageSize = r.getPageSize() == 0 ? MSDynamicsBizCentralService.PAGE_SIZE : Math.min(MSDynamicsBizCentralService.PAGE_SIZE, r.getPageSize());

        return new FetchResponse(watermark, new EntityDataBatchIterator() {
            Pair<WatermarkInfo, List<EntityData>> entityData = getNextPage(r, client, entitySchema, parentSchema, start, offset, pageSize, watermark);
            long currentOffset = offset;
            long lastWatermark = start;
            String changeStream;


            @Override
            public String getChangeStream() {
                return changeStream;
            }

            @Override
            public long getLastWatermark() {
                return lastWatermark;
            }

            @Override
            public Offset getOffsetInfo() {
                return new Offset(Offset.OffsetType.RECORD_COUNT, pageSize);
            }

            @Override
            public long getLastOffset() {
                return currentOffset;
            }

            @Override
            public Stats getStats() {
                return new Stats();
            }

            @Override
            public boolean hasNext() {
                return !getCurrentRecords().isEmpty();
            }

            @Override
            public List<EntityData> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                List<EntityData> records = getCurrentRecords();
                final WatermarkInfo currentWatermark = getCurrentWatermark();
                changeStream = currentWatermark.getChangeStream();
                lastWatermark = currentWatermark.getStart();
                log.info("next() ->  Entity {}, Watermark {} ", entitySchema.getApiName(), currentWatermark);
                entityData = getNextPage(r, client, entitySchema, parentSchema, start, 0, pageSize, currentWatermark);
                if (getCurrentRecords().isEmpty()) {
                    changeStream = null;
                }
                return records;
            }

            private List<EntityData> getCurrentRecords() {
                return entityData.y;
            }

            private WatermarkInfo getCurrentWatermark() {
                return entityData.x;
            }

        });
    }

    private Pair<WatermarkInfo, List<EntityData>> getNextPage(SyncRequest r, ODataClient client, EntitySchema entitySchema, Optional<EntitySchema> parentSchema, long startTimeInMillis, long offset, int pageSize, WatermarkInfo watermark) {
        WatermarkInfo wm = watermark.copy();
        final AttributeSchema watermarkField = entitySchema.getWatermarkField();
        final int actualOffset = MSDynamicsBizCentralService.getOffsetFromChangeStream(watermark);
        final String startDate = DateUtil.format(Instant.ofEpochMilli(startTimeInMillis).atZone(ZoneOffset.UTC), DateUtil.dateFormatMillis);
        String entityName = parentSchema.map(parent -> parent.getApiName()).orElse(r.getEntityName());
        String orderBy = parentSchema.isEmpty() ? watermarkField.getApiName() : "";
        final String filterString = parentSchema.isEmpty() ? String.format("(%s gt %s)", watermarkField.getApiName(), startDate) : "" ;
        final URIBuilder uriBuilder = client.newURIBuilder(r.getConnector().getEndpoint())
                .appendEntitySetSegment(entityName)
                .top(pageSize)
                .skip(actualOffset);
        if(parentSchema.isEmpty()){
            uriBuilder.filter(filterString).orderBy(orderBy);
        }
        //We are querying a top level entity, so use limit/offset
        //Select only id and WM of parent if we are enumerating just child records
        parentSchema.ifPresent(parent -> {
            uriBuilder.select(parent.getIdField().getApiName(), parent.getWatermarkField().getApiName());
        });
        log.info("Executing query on entity {}, filter {} orderBy {}, pageSize {}", entityName, filterString, orderBy, pageSize);
        ODataEntitySetRequest<ClientEntitySet> request = client.getRetrieveRequestFactory()
                .getEntitySetRequest(uriBuilder
                        .build());
        final ODataRetrieveResponse<ClientEntitySet> response = request.execute();
        final EntitySchema schemaToUse = parentSchema.orElse(entitySchema);
        final List<EntityData> entityData = toRecords(schemaToUse, schemaToUse.getIdField(), response.getBody().getEntities());
        log.info("Executed query on entity {}, filter {} orderBy {}, records {}", entityName, filterString, orderBy, entityData.size());

        final Pair<Integer, List<EntityData>> numParentsAndRecordsToReturn = parentSchema.map(parent -> findNavigationRecords(r, client, entitySchema, entityData, parent)).orElse(Pair.of(entityData.size(), entityData));
        final Integer parentRecordsConsumed = numParentsAndRecordsToReturn.getX();
        List<EntityData> recordsToReturn = numParentsAndRecordsToReturn.getY();
        if (!recordsToReturn.isEmpty()) {
            final EntityData lastRecord = recordsToReturn.get(recordsToReturn.size() - 1);
            wm.setStart(lastRecord.getLastModified()).setEnd(lastRecord.getLastModified());
        }
        parentSchema.ifPresentOrElse(p -> wm.setChangeStream(String.valueOf(actualOffset + parentRecordsConsumed)),
                () -> wm.setChangeStream(String.valueOf(actualOffset + recordsToReturn.size()))
        );
        return Pair.of(wm, recordsToReturn);
    }

    private Pair<Integer, List<EntityData>> findNavigationRecords(SyncRequest r, ODataClient client, EntitySchema childSchema, List<EntityData> entityData, EntitySchema parentSchema) {
        List<EntityData> records = new ArrayList<>();
        int parentRecordsConsumed = 0;
        for (EntityData record : entityData) {
            //Exhaust all children of all parents as a workaround for pagination
            ODataEntitySetRequest<ClientEntitySet> navigationRecordRequest = client.getRetrieveRequestFactory()
                    .getEntitySetRequest(client.newURIBuilder(r.getConnector().getEndpoint())
                            .appendEntitySetSegment(parentSchema.getApiName())
                            .appendKeySegment(UrlEscapers.urlFragmentEscaper().escape(record.getId()))
                            .appendNavigationSegment(childSchema.getApiName())
                            .build());
            log.info("Executing fetch children for parent {} child {}, parent record id : {}", parentSchema.getApiName(), childSchema.getApiName(), record.getId());
            final ClientEntitySet navigationRecordSet = navigationRecordRequest.execute().getBody();

            final List<EntityData> childRecords = MSDynamicsBizCentralService.getOriginalChildIdField(childSchema)
                    .map(originalChildIdField -> toRecords(childSchema, originalChildIdField, navigationRecordSet.getEntities()))
                    .orElse(List.of());
            log.info("Executed fetch children for parent {}({}) child {}, numRecords: {} ", parentSchema.getApiName(), record.getId(), childSchema.getApiName(), childRecords.size());
            //set updated at,created at & parent id
            MSDynamicsBizCentralService.setFabricatedFieldValuesOnChildren(childSchema, parentSchema, record, childRecords);
            records.addAll(childRecords);
            parentRecordsConsumed++;
            if (records.size() >= MSDynamicsBizCentralService.MAX_CHILD_RECORDS) {
                break;
            }
        }
        return Pair.of(parentRecordsConsumed, records);
    }

    private static Optional<EntitySchema> getParentSchemaIfPresent(SyncRequest r) {
        EntitySchema entitySchema = r.getEntitySchema();
        final Map<String, Object> schemaProperties = entitySchema.getAdditionalProperties();
        if (schemaProperties.containsKey(MSDynamicsBizCentralService.CONTAINER_PARENT)) {
            String parentName = schemaProperties.get(MSDynamicsBizCentralService.CONTAINER_PARENT).toString();
            String parentWmField = schemaProperties.get(MSDynamicsBizCentralService.CONTAINER_PARENT_WM_FIELD).toString();
            String parentWmFieldType = schemaProperties.get(MSDynamicsBizCentralService.CONTAINER_PARENT_WM_FIELD_TYPE).toString();
            String parentIdField = schemaProperties.get(MSDynamicsBizCentralService.CONTAINER_PARENT_ID_FIELD).toString();
            String parentIdFieldType = schemaProperties.get(MSDynamicsBizCentralService.CONTAINER_PARENT_ID_FIELD_TYPE).toString();
            String childFieldNameInParent = schemaProperties.get(MSDynamicsBizCentralService.CHILD_FIELD_NAME_IN_PARENT).toString();
            boolean childFieldInParentMultivalued = Boolean.getBoolean(schemaProperties.get(MSDynamicsBizCentralService.CHILD_FIELD_NAME_IN_PARENT_MULTIVALUED).toString());
            EntitySchema parentSchema = new EntitySchema(parentName);
            parentSchema.addField(new AttributeSchema(parentIdField, parentIdFieldType).setIdField(true));
            parentSchema.addField(new AttributeSchema(parentWmField, parentWmFieldType).setWatermarkField(true).setUpdatedAtField(true));
            parentSchema.addField(new AttributeSchema(childFieldNameInParent, "child")
                    .setMultiValueField(childFieldInParentMultivalued)
                    .setChildSchema(entitySchema)
            );
            return Optional.of(parentSchema);
        } else {
            return Optional.empty();
        }
    }

    public List<EntityData> getByIds(SyncRequest r) {
        final EntitySchema entitySchema = r.getEntitySchema();
        final Optional<AttributeSchema> originalChildIdField = MSDynamicsBizCentralService.getOriginalChildIdField(entitySchema);

        if (originalChildIdField.isPresent()) {
            return getChildRecordsByIds(r, originalChildIdField);
        } else {
            return getParentRecordsById(r);
        }
    }

    private List<EntityData> getChildRecordsByIds(SyncRequest r, Optional<AttributeSchema> originalChildIdField) {
        EntitySchema entitySchema = r.getEntitySchema();

        return originalChildIdField.stream().flatMap(originalIdField -> {
            final Optional<EntitySchema> parentSchema = getParentSchemaIfPresent(r);

            return parentSchema.stream().flatMap(parent -> {
                final AttributeSchema parentIdField = parent.getIdField();

                return r.getIds().stream().flatMap(compositeRecordId -> {
                    final String[] idParts = compositeRecordId.split(":");
                    final String childRecordId = idParts[0];
                    final String parentRecordId = idParts[1];
                    final Optional<EntityData> parentRecord = getParentRecordById(r.getConnector(), parent, parentRecordId);

                    return parentRecord.stream().flatMap(pr ->
                            getChildRecordById(r, entitySchema, originalIdField, parent, parentIdField, childRecordId, pr).stream()
                    );
                });
            });
        }).collect(Collectors.toList());
    }

    private List<EntityData> getParentRecordsById(SyncRequest r) {
        EntitySchema entitySchema = r.getEntitySchema();
        AttributeSchema idField = entitySchema.getIdField();

        ODataClient client = getODataClient(r.getConnector());

        return r.getIds().stream().flatMap(compositeRecordId -> {
            final URIBuilder childResourceURI = client.newURIBuilder(r.getConnector().getEndpoint())
                    .appendEntitySetSegment(entitySchema.getApiName())
                    .appendKeySegment(MSDynamicsBizCentralService.getIdFilterSegment(entitySchema, idField, compositeRecordId, "", ""));
            final ODataRetrieveResponse<ClientEntity> response = client.getRetrieveRequestFactory().getEntityRequest(childResourceURI.build()).execute();
            return toRecords(entitySchema, idField, List.of(response.getBody())).stream();
        }).collect(Collectors.toList());
    }

    private List<EntityData> getChildRecordById(SyncRequest r, EntitySchema childSchema, AttributeSchema childIdField, EntitySchema parentSchema, AttributeSchema parentIdField, String childRecordId, EntityData pr) {
        ODataClient client = getODataClient(r.getConnector());

        final URIBuilder childResourceURI = client.newURIBuilder(r.getConnector().getEndpoint())
                .appendEntitySetSegment(parentSchema.getApiName())
                .appendKeySegment(MSDynamicsBizCentralService.getIdFilterSegment(parentSchema, parentIdField, pr.getId(), "", childRecordId))
                .appendNavigationSegment(childSchema.getApiName())
                .appendKeySegment(MSDynamicsBizCentralService.getIdFilterSegment(childSchema, childIdField, childRecordId, pr.getId(), ""));

        final ODataRetrieveResponse<ClientEntity> response = client.getRetrieveRequestFactory().getEntityRequest(childResourceURI.build()).execute();
        final List<EntityData> childRecords = toRecords(childSchema, childIdField, List.of(response.getBody()));
        MSDynamicsBizCentralService.setFabricatedFieldValuesOnChildren(childSchema, parentSchema, pr, childRecords);
        return childRecords;
    }

    private Optional<EntityData> getParentRecordById(ConnectorInfo connector, EntitySchema parent, String parentRecordId) {
        final List<EntityData> parentRecords = getParentRecordsById(
                new SyncRequest().setConnector(connector).setEntitySchema(parent).setData(
                        Map.of(connector.getId(), List.of(new EntityData(parent.getApiName()).setId(parentRecordId)))
                ));
        return parentRecords.stream().findFirst();
    }


    private static List<EntityData> toRecords(EntitySchema entitySchema, AttributeSchema idField, List<ClientEntity> entities) {
        List<EntityData> records = new ArrayList<>();
        Optional<AttributeSchema> updatedAtField = entitySchema.getUpdatedAtField();
        Optional<AttributeSchema> createdAtField = entitySchema.getCreatedAtField();
        AttributeSchema waterMarkField = entitySchema.getWatermarkField();

        List<String> idFields = MSDynamicsBizCentralService.getIdFieldsFromCompositeKey(idField);

        entities.forEach(e -> {
            final EntityData record = new EntityData(entitySchema.getApiName());
            Map<String, Object> idMap = new HashMap<>();
            e.getProperties().forEach(p -> {
                final Object value = MSDynamicsBizCentralService.extractValue(p);
                if (idFields.contains(p.getName())) {
                    idMap.put(p.getName(), p.getValue().toString());
                }
                updatedAtField.ifPresent(u -> {
                    if (p.getName().equals(u.getApiName())) {
                        Timestamp updatedAt = Timestamp.class.cast(value);
                        record.setLastModified(updatedAt.toInstant().toEpochMilli());
                    }
                });
                createdAtField.ifPresent(c -> {
                    if (p.getName().equals(c.getApiName())) {
                        Timestamp createdAt = Timestamp.class.cast(value);
                        record.setLastModified(createdAt.toInstant().toEpochMilli());
                    }
                });
                final String translatedApiName = MSDynamicsBizCentralService.getApiName(p.getName());
                record.addValue(translatedApiName, value);
            });

            String recordId = idFields.stream().map(id->idMap.get(id).toString()).collect(Collectors.joining(EntitySchema.COMPOSITE_KEY_DELIMETER));

            record.setId(recordId);

            records.add(record);
        });
        return records;
    }

    protected List<EntitySchema> describeFullSchema(ConnectorInfo connector) {
        try {
            ODataClient client = getODataClient(connector);
            final EdmMetadataRequest metadataRequest = client.getRetrieveRequestFactory()
                    .getMetadataRequest(connector.getEndpoint());
            Edm edm = metadataRequest.execute().getBody();
            List<EntitySchema> entities = new ArrayList<>();
            Map<String, EntitySchema> entityNameMap = new HashMap<>();
            edm.getEntityContainer().getEntitySets().forEach(e -> {
                final EntitySchema entitySchema = toEntitySchema(e.getEntityTypeWithAnnotations());
                entities.add(entitySchema);
                entityNameMap.put(entitySchema.getApiName(), entitySchema);
            });
            updateSchemaRelationships(entities, entityNameMap);
            return entities;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private ODataClient getODataClient(ConnectorInfo connector) {
        final AuthConfig renewedAuthConfig = getAuthConfig(connector.getAuthConfig());
        ODataClient client = ODataClientFactory.getClient();
        final DefaultHttpClientFactory factory = new DefaultHttpClientFactory() {
            public DefaultHttpClient create(HttpMethod method, URI uri) {
                DefaultHttpClient client = new DefaultHttpClient();
                client.addRequestInterceptor((httpRequest, httpContext) -> {
                    httpRequest.setHeader("Authorization", "Bearer " + renewedAuthConfig.getAccessToken());
                });
                client.getParams().setParameter("http.useragent", USER_AGENT);
                return client;
            }
        };
        client.getConfiguration().setHttpClientFactory(factory);
        return client;
    }
    private static void updateSchemaRelationships(List<EntitySchema> entities, Map<String, EntitySchema> entityNameMap) {
        //We need to do some gymnastics to map ODataV4 to Syncari model. ODataV4 supports deeply nested records,
        //but MS BiZCentrla implementation doesn't seem tohonor $expand at all . So we do the following
        // 1. Level 1 child schemas are NOT marked as child schemas and are marked as top level
        // 2. We don't support level 2 onwards in this version, until BizCentral fixes the $expand
        // 3. All level 2 onwards schemas are removed from the schema
        List<EntitySchema> schemasToRemove = new ArrayList<>();
        for (EntitySchema e : entities) {
            List<AttributeSchema> childAttributesToRemove = new ArrayList<>();
            for (AttributeSchema a : e.getAttributes()) {
                if ("child".equals(a.getDataType())) {
                    //Do this only if the schema is top level object
                    final EntitySchema childSchema = entityNameMap.get(a.getReferenceTo());
                    if (hasParent(e)) {
                        Optional<AttributeSchema> parentWMFieldOpt = e.getWatermarkAttr();
                        final Optional<AttributeSchema> createdAtField = e.getCreatedAtField();
                        AttributeSchema parentIdField = e.getIdField();
                        // populate additional properties in the child schemas
                        if (childSchema != null) {
                            final AttributeSchema idField = childSchema.getIdField();
                            childSchema.setAdditionalProperties(Map.of(
                                    MSDynamicsBizCentralService.CONTAINER_PARENT, e.getApiName(),
                                    MSDynamicsBizCentralService.CONTAINER_PARENT_ID_FIELD, parentIdField.getApiName(),
                                    MSDynamicsBizCentralService.CONTAINER_PARENT_ID_FIELD_TYPE, parentIdField.getDataType(),
                                    MSDynamicsBizCentralService.CONTAINER_PARENT_WM_FIELD, parentWMFieldOpt.isPresent() ? parentWMFieldOpt.get().getApiName() : "",
                                    MSDynamicsBizCentralService.CONTAINER_PARENT_WM_FIELD_TYPE, parentWMFieldOpt.isPresent() ? parentWMFieldOpt.get().getDataType() : "",
                                    MSDynamicsBizCentralService.CHILD_FIELD_NAME_IN_PARENT, a.getApiName(),
                                    MSDynamicsBizCentralService.CHILD_ORIGINAL_ID_FIELD_NAME, idField.getApiName(),
                                    MSDynamicsBizCentralService.CHILD_FIELD_NAME_IN_PARENT_MULTIVALUED, String.valueOf(a.isMultiValueField())
                            ));
                            //Copy the parents WM field into child if no watermark is set on child Schema
                            if(childSchema.getWatermarkAttr().isEmpty()) {
                                parentWMFieldOpt.ifPresent(parentWMField -> {
                                    childSchema.addField(new AttributeSchema(parentWMField.getApiName(), parentWMField.getDataType())
                                            .setUpdatedAtField(true).setWatermarkField(true).setSystem(true)
                                            .setDisplayName(parentWMField.getDisplayName()));
                                });
                            }
                            if(childSchema.getCreatedAtField().isEmpty()) {
                                createdAtField.ifPresent(createdAt -> {
                                    childSchema.addField(new AttributeSchema(createdAt.getApiName(), createdAt.getDataType())
                                            .setCreatedAtField(true).setSystem(true)
                                            .setDisplayName(createdAt.getDisplayName()));

                                });
                            }
                            //Add a fabricated record id field and make that the id field.
                            // The id values will be of the format <id>:<parentid>. This helps run live tests etc
                            childSchema.addField(new AttributeSchema("recordId", "string")
                                    .setIdField(true).setSystem(true)
                                    .setDisplayName("Record Id"));
                            idField.setIdField(false);

                            //Add a ref to parent
                            childSchema.addField(new AttributeSchema(e.getApiName(), "reference")
                                    .setReferenceTo(e.getApiName())
                                    .setDisplayName(e.getDisplayName())
                                    .setReferenceTargetField(parentIdField.getApiName()));


                        } else {
                            log.warn("Cannot find child schema for attribute {} on entity {} referencing {}", a.getApiName(), e.getApiName(), a.getReferenceTo());
                        }
                        childAttributesToRemove.add(a);
                    } else {
                        if (childSchema != null) {
                            schemasToRemove.add(childSchema);
                        }
                    }
                }
            }
            e.getAttributes().removeAll(childAttributesToRemove);
        }
        entities.removeAll(schemasToRemove);
    }

    private static boolean hasParent(EntitySchema e) {
        return e.getAdditionalProperties().containsKey(MSDynamicsBizCentralService.CONTAINER_PARENT);
    }

    private EntitySchema toEntitySchema(EdmEntityType entityType) {
        final EntitySchema entitySchema = new EntitySchema(entityType.getName(), entityType.getName());
        final Set<String> keyFieldNames = MSDynamicsBizCentralService.addKeyFields(entityType, entitySchema);
        entityType.getPropertyNames().forEach(p -> {
            final EdmProperty structuralProperty = entityType.getStructuralProperty(p);
            final String fullQualifiedTypeName = structuralProperty.getType().getFullQualifiedName().getFullQualifiedNameAsString();
            if (!keyFieldNames.contains(structuralProperty.getName())) {
                if (MSDynamicsBizCentralService.SUPPORTED_DATATYPES.contains(fullQualifiedTypeName)) {
                    final AttributeSchema field = MSDynamicsBizCentralService.toAttribute(structuralProperty);
                    if (MSDynamicsBizCentralService.SYSTEM_MODIFIED_AT.equals(structuralProperty.getName())) {
                        field.setWatermarkField(true);
                        field.setSystem(true);
                        field.setUpdatedAtField(true);
                    } else if (MSDynamicsBizCentralService.SYSTEM_CREATED_AT.equals(structuralProperty.getName())) {
                        field.setSystem(true);
                        field.setCreatedAtField(true);
                    }

                    entitySchema.addField(field);
                } else {
                    log.warn("Unsupported OData datatype {} for field {} in entity {}",
                            structuralProperty.getType(), structuralProperty.getName(), entityType.getName());
                }
            }
        });
        entityType.getNavigationPropertyNames().forEach(p -> {
            final EdmNavigationProperty navigationProperty = entityType.getNavigationProperty(p);
            final EdmEntityType referenceType = navigationProperty.getType();
            final String keyName = entityType.getKeyPropertyRefs().get(0).getName();
            final String dataType = navigationProperty.containsTarget() ? "child" : "reference";
            final AttributeSchema childOrFKAttribute = new AttributeSchema(navigationProperty.getName(), dataType)
                    .setReferenceTo(referenceType.getName())
                    .setReferenceTargetField(keyName)
                    .setDisplayName(navigationProperty.getName())
                    .setMultiValueField(navigationProperty.isCollection())
                    .setNillable(navigationProperty.isNullable());
            entitySchema.addField(childOrFKAttribute);
        });

        //All BizCentral entities are RO in this version
        entitySchema.setReadOnly(true);
        return entitySchema;
    }

    private AuthConfig getAuthConfig(AuthConfig authConfig) {
        if (authConfig.getAccessToken() == null || authConfig.expiresSoon()) {
            authConfig = getAccessToken(authConfig);
        }
        return authConfig;
    }

    @SneakyThrows
    private AuthConfig getAccessToken(AuthConfig authConfig) {
        String accessTokenURL = authConfig.getHeader("accessTokenEndpoint");
        final HttpRequest accessTokenRequest = HttpRequest.newBuilder()
                .uri(URI.create(accessTokenURL))
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                String.format("client_id=%s&client_secret=%s&scope=%s&grant_type=%s",
                                        authConfig.getClientId(), authConfig.getClientSecret(), MSDynamicsBizCentralService.BIZ_CENTRAL_SCOPE_URL, "client_credentials")
                        )
                ).headers("Content-Type", "application/x-www-form-urlencoded").build();
        final HttpResponse<String> response = ConnectorHelper.withHttpErrorHandling(() -> HttpClient.newHttpClient().send(
                accessTokenRequest, HttpResponse.BodyHandlers.ofString()));
        if( response.statusCode() != HttpStatus.OK.value()
                || StringUtils.isBlank(response.body())
                || !StringUtils.contains(response.body(), "access_token")
        ){
            String msg = format("Error while authorizing Dynamics 365 Business Central: code: %s, details: %s", response.statusCode(), "Invalid Client Id or Access Token URL. Check Token URL is of the format https://login.microsoftonline.com/<tenant-id>/oauth2/v2.0/token");
            ErrorCodes ec= ErrorCodes.CONNECTION_ERROR;
            if(response.statusCode() == HttpStatus.UNAUTHORIZED.value()){
                ec = ErrorCodes.LOGIN_ERROR;
                msg = "Invalid Client Secret";
            }
            log.error(msg);
            throw new NonRetriableException(ec, msg, ec.name() );
        }
        final HashMap accessTokenInfo = rethrow(() -> mapper.readValue(response.body(), HashMap.class));
        authConfig.setAccessToken(accessTokenInfo.get("access_token").toString());
        authConfig.setExpiresIn(accessTokenInfo.get("expires_in").toString());
        authConfig.setLastRefreshed(Instant.now());
        //set refresh token to be the same as access token, for framework call to expiresSoon to work
        authConfig.setRefreshToken(accessTokenInfo.get("access_token").toString());
        return authConfig;
    }
}
