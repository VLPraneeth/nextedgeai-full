package com.syncari.connector.odatav4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.UrlEscapers;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
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
import org.apache.olingo.client.api.domain.ClientProperty;
import org.apache.olingo.client.api.uri.FilterFactory;
import org.apache.olingo.client.api.uri.URIBuilder;
import org.apache.olingo.client.api.uri.URIFilter;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.client.core.http.DefaultHttpClientFactory;
import org.apache.olingo.commons.api.edm.*;
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
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.syncari.utils.ExceptionUtils.rethrow;
import static java.lang.String.format;

@Slf4j
@Component("msdynamicsbizcentral")
public class MSDynamicsBizCentralService implements CommonDataService, MetadataService,
        SynapseInfoService, OauthAuthenticationService {
    protected static final String SYSTEM_MODIFIED_AT = "SystemModifiedAt";
    protected static final String SYSTEM_CREATED_AT = "SystemCreatedAt";
    public static final String EXPAND_SUB_RESOURCES = "expandSubResources";
    public static final int PAGE_SIZE = 100;
    protected static final String BIZ_CENTRAL_SCOPE_URL = "https://api.businesscentral.dynamics.com/.default";
    protected static final int MAX_CHILD_RECORDS = 2000;
    public static final String CHILD_ORIGINAL_ID_FIELD_NAME = "__childOriginalIdFieldName";
    public static final String CONTAINER_PARENT = "__containerParent";
    public static final String CONTAINER_PARENT_ID_FIELD = "__containerParentIdField";
    public static final String CONTAINER_PARENT_ID_FIELD_TYPE = "__containerParentIdFieldType";
    public static final String CONTAINER_PARENT_ID_FIELD_COMPOSITE_KEY = "__containerParentIdFieldCompositeKey";
    public static final String CONTAINER_PARENT_WM_FIELD = "__containerParentWMField";
    public static final String CONTAINER_PARENT_WM_FIELD_TYPE = "__containerParentWMFieldType";
    public static final String CHILD_FIELD_NAME_IN_PARENT = "__childFieldNameInParent";
    public static final String CHILD_FIELD_NAME_IN_PARENT_MULTIVALUED = "__childFieldNameInParentMultivalued";
    public static final String CONTAINER_IS_DIRECT_COMPANY_CHILD = "__containerIsDirectCompanyChild";
    public static final String COMPANY = "company";

    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    MSDynamicsBizCentralServiceV1 msDynamicsBizCentralServiceV1;
    static Set<String> SUPPORTED_DATATYPES = Set.of(
            "Edm.String", "Edm.Guid", "Edm.Float", "Edm.Double", "Edm.Int16", "Edm.Int32", "Edm.Int64",
            "Edm.DateTime", "Edm.DateTimeOffset", "Edm.Boolean", "Edm.Decimal", "Edm.Date");

    @Override
    public String getCapabilitiesArticleId() {
        return "19203489925012";
    }

    @SneakyThrows
    @Override
    public FetchResponse getByWatermark(SyncRequest r) {
        if(StringUtils.isBlank(getCompanyName(r.getConnector()))){
            return msDynamicsBizCentralServiceV1.getByWatermark(r);
        }
        ODataClient client = getODataClient(r.getConnector());
        final EntitySchema entitySchema = r.getEntitySchema();
        final Optional<EntitySchema> parentSchema = getParentSchemaIfPresent(r);
        final WatermarkInfo watermark = r.getWatermark();

        final long start = watermark.getStart();
        final long offset = watermark.getOffset();
        final int pageSize = r.getPageSize() == 0 ? PAGE_SIZE : Math.min(PAGE_SIZE, r.getPageSize());

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

    private static Optional<EntitySchema> getParentSchemaIfPresent(SyncRequest r) {
        EntitySchema entitySchema = r.getEntitySchema();
        final Map<String, Object> schemaProperties = entitySchema.getAdditionalProperties();
        if (schemaProperties.containsKey(CONTAINER_PARENT)) {
            String parentName = schemaProperties.get(CONTAINER_PARENT).toString();
            String parentWmField = schemaProperties.get(CONTAINER_PARENT_WM_FIELD).toString();
            String parentWmFieldType = schemaProperties.get(CONTAINER_PARENT_WM_FIELD_TYPE).toString();
            String parentIdField = schemaProperties.get(CONTAINER_PARENT_ID_FIELD).toString();
            String parentIdFieldType = schemaProperties.get(CONTAINER_PARENT_ID_FIELD_TYPE).toString();
            String parentIdFieldCompositeKey = StringUtils.isNotBlank(schemaProperties.get(CONTAINER_PARENT_ID_FIELD_COMPOSITE_KEY).toString()) ? schemaProperties.get(CONTAINER_PARENT_ID_FIELD_COMPOSITE_KEY).toString() : null;
            String childFieldNameInParent = schemaProperties.get(CHILD_FIELD_NAME_IN_PARENT).toString();
            boolean childFieldInParentMultivalued = Boolean.getBoolean(schemaProperties.get(CHILD_FIELD_NAME_IN_PARENT_MULTIVALUED).toString());
            EntitySchema parentSchema = new EntitySchema(parentName);
            parentSchema.addField(new AttributeSchema(parentIdField, parentIdFieldType).setIdField(true).setCompositeKey(parentIdFieldCompositeKey));
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

    // Only datetime is accepted watermark
    // https://learn.microsoft.com/en-us/dynamics365/business-central/dev-itpro/developer/devenv-table-system-fields#audit
    // Top level Entity
    // Case 1: Company set in config & watermark is set (Ideal if Business Central > 2020 release wave 2 and later)
    //      Add filter on watermark
    // Case 2: Company set in config & watermark is not set
    //      Loop through all records
    // Case 3: Company not set in config (Retrieving companies)
    //      Add filter on watermark
    // Child Entity
    // Case 1: Company set in config & child got a valid watermark (Ideal if Business Central > 2020 release wave 2 and later)
    //      Loop through all parent records
    //      For each parent return all its children
    // Case 2: Company set in config & child didnt get a valid watermark
    //      Loop through all parent records
    //      For each parent return all its children
    // Case 3: Company not set in config
    //      Loop through all parent records
    //      For each parent return all its children
    // TODO: Case 1 Fetch all parents with expand on child filter and count. For each parent return all its children with watermark check
    private Pair<WatermarkInfo, List<EntityData>> getNextPage(SyncRequest r, ODataClient client, EntitySchema entitySchema, Optional<EntitySchema> parentSchema, long startTimeInMillis, long offset, int pageSize, WatermarkInfo watermark) {
        WatermarkInfo wm = watermark.copy();
        final AttributeSchema watermarkField = entitySchema.getWatermarkField();
        final int actualOffset = getOffsetFromChangeStream(watermark);
        final String startDate = DateUtil.format(Instant.ofEpochMilli(startTimeInMillis).atZone(ZoneOffset.UTC), DateUtil.dateFormatMillis);
        String entityName = parentSchema.map(parent -> parent.getApiName()).orElse(r.getEntityName());
        String orderBy = (parentSchema.isEmpty() && !Constants.SYNCARI_FABRICATED_WATERMARKFIELD.equalsIgnoreCase(watermarkField.getApiName())) ? watermarkField.getApiName() : "";
        final String filterString = (parentSchema.isEmpty() && !Constants.SYNCARI_FABRICATED_WATERMARKFIELD.equalsIgnoreCase(watermarkField.getApiName())) ? String.format("(%s gt %s)", watermarkField.getApiName(), startDate) : "";
        final URIBuilder uriBuilder = client.newURIBuilder(r.getConnector().getEndpoint())
                .appendEntitySetSegment(entityName)
                .top(pageSize)
                .skip(actualOffset);
        if(parentSchema.isEmpty() && !Constants.SYNCARI_FABRICATED_WATERMARKFIELD.equalsIgnoreCase(watermarkField.getApiName())){
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
            if(!Constants.SYNCARI_FABRICATED_WATERMARKFIELD.equalsIgnoreCase(watermarkField.getApiName())){
                wm.setStart(lastRecord.getLastModified()).setEnd(lastRecord.getLastModified());
            }
        }
        parentSchema.ifPresentOrElse(p -> wm.setChangeStream(String.valueOf(actualOffset + parentRecordsConsumed)),
                () -> wm.setChangeStream(String.valueOf(actualOffset + recordsToReturn.size()))
        );
        return Pair.of(wm, recordsToReturn);
    }

    protected static Integer getOffsetFromChangeStream(WatermarkInfo wm) {
        final String changeStream = wm.getChangeStream();
        return StringUtils.isBlank(changeStream) ? 0 : Integer.valueOf(changeStream);
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

            final List<EntityData> childRecords = getOriginalChildIdField(childSchema)
                    .map(originalChildIdField -> toRecords(childSchema, originalChildIdField, navigationRecordSet.getEntities()))
                    .orElse(List.of());
            log.info("Executed fetch children for parent {}({}) child {}, numRecords: {} ", parentSchema.getApiName(), record.getId(), childSchema.getApiName(), childRecords.size());
            //set updated at,created at & parent id
            setFabricatedFieldValuesOnChildren(childSchema, parentSchema, record, childRecords);
            records.addAll(childRecords);
            parentRecordsConsumed++;
            if (records.size() >= MAX_CHILD_RECORDS) {
                break;
            }
        }
        return Pair.of(parentRecordsConsumed, records);
    }

    protected static void setFabricatedFieldValuesOnChildren(EntitySchema childSchema, EntitySchema parentSchema, EntityData parentRecord, List<EntityData> childRecords) {
        final AttributeSchema childWatermarkField = childSchema.getWatermarkField();
        final Optional<AttributeSchema> createdAtField = childSchema.getCreatedAtField();
        for (EntityData childRecord : childRecords) {
            childRecord.setLastModified(parentRecord.getLastModified());
            childRecord.setCreatedAt(parentRecord.getCreatedAt());
            createdAtField.ifPresent(c -> {
                childRecord.setCreatedAt(parentRecord.getCreatedAt());
            });
            //add recordId
            childRecord.setId(childRecord.getId() + ":" + parentRecord.getId());
            //this won't be used/saved by the f/w. Only valid inside synapse
            childRecord.setParentId(parentRecord.getId());
            childRecord.addValue("recordId", childRecord.getId());
            childRecord.addValue(childWatermarkField.getApiName(), parentRecord.getLastModified());
            childRecord.addValue(parentSchema.getApiName(), parentRecord.getId());
        }
    }

    protected static List<String> getIdFieldsFromCompositeKey(AttributeSchema idField){
        return StringUtils.isEmpty(idField.getCompositeKey()) ? List.of(idField.getApiName()) : Arrays.asList(idField.getCompositeKey().split(Pattern.quote(EntitySchema.COMPOSITE_KEY_DELIMETER)));
    }


    //idField is explicitly passed here, so that fabricated child id field is not used while populating data
    private static List<EntityData> toRecords(EntitySchema entitySchema, AttributeSchema idField, List<ClientEntity> entities) {
        List<EntityData> records = new ArrayList<>();
        Optional<AttributeSchema> updatedAtField = entitySchema.getUpdatedAtField();
        Optional<AttributeSchema> createdAtField = entitySchema.getCreatedAtField();
        AttributeSchema waterMarkField = entitySchema.getWatermarkField();

        List<String> idFields = getIdFieldsFromCompositeKey(idField);

        entities.forEach(e -> {
            final EntityData record = new EntityData(entitySchema.getApiName());
            Map<String, Object> idMap = new HashMap<>();
            e.getProperties().forEach(p -> {
                final Object value = extractValue(p);
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
                final String translatedApiName = getApiName(p.getName());
                // Check if the value is of type java.sql.Timestamp
                if (value instanceof Timestamp && p != null) {
                    Timestamp timestamp = (Timestamp) value;
                    record.addValue(translatedApiName, timestamp.toInstant().toEpochMilli());
                } else {
                    record.addValue(translatedApiName, value);
                }
            });

            long currentEpochTimeInMillis = Instant.now().toEpochMilli();

            if (Constants.SYNCARI_FABRICATED_WATERMARKFIELD.equalsIgnoreCase(waterMarkField.getApiName())){
                record.addValue(waterMarkField.getApiName(), currentEpochTimeInMillis);
            }
            if(createdAtField.isEmpty()){
                record.setCreatedAt(currentEpochTimeInMillis);
            }
            if(updatedAtField.isEmpty()){
                record.setLastModified(currentEpochTimeInMillis);
            }

            String recordId = idFields.stream().map(id->idMap.get(id).toString()).collect(Collectors.joining(EntitySchema.COMPOSITE_KEY_DELIMETER));

            record.setId(recordId);

            records.add(record);
        });
        return records;
    }

    protected static String getApiName(String p) {
        final String translatedApiName = p.endsWith("@odata.type") ? p.split("@")[0] : p;
        return translatedApiName;
    }

    @SneakyThrows
    protected static Object extractValue(ClientProperty p) {
        Object value = null;
        if (p.getValue().isPrimitive()) {
            if (p.getValue().getTypeName().equalsIgnoreCase("Edm.date") && StringUtils.isNotBlank(p.getValue().toString())) {
                value = DateUtil.convertDate(p.getValue().toString());
            } else if (p.getValue().getTypeName().equalsIgnoreCase("Edm.DateTime") && StringUtils.isNotBlank(p.getValue().toString())) {
                value = DateUtil.convertDateTime(p.getValue().toString());
            } else {
                value = p.getValue().asPrimitive().toValue();
            }
        } else if (p.getValue().isCollection()) {
            value = new ArrayList<>(p.getValue().asCollection().asJavaCollection());
        } else if (p.getValue().isComplex()) {
            value = p.getComplexValue().asJavaMap();
        } else if (p.getValue().isEnum()) {
            value = p.getEnumValue().getValue();
        } else {
            log.warn("Unsupported ");
        }
        return value;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest r) {
        if (StringUtils.isBlank(getCompanyName(r.getConnector()))){
            return msDynamicsBizCentralServiceV1.getByIds(r);
        }
        final EntitySchema entitySchema = r.getEntitySchema();
        final Optional<AttributeSchema> originalChildIdField = getOriginalChildIdField(entitySchema);

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
                    .appendKeySegment(getIdFilterSegment(entitySchema, idField, compositeRecordId, "", ""));
            final ODataRetrieveResponse<ClientEntity> response = client.getRetrieveRequestFactory().getEntityRequest(childResourceURI.build()).execute();
            return toRecords(entitySchema, idField, List.of(response.getBody())).stream();
        }).collect(Collectors.toList());
    }

    // parentId and childId is passed only for logging and error message purpose only
    protected static Map<String, Object> getIdFilterSegment (EntitySchema schema, AttributeSchema idField, String recordId, String parentId, String childId){
        Map<String, Object> idFilterSegment = new HashMap<>();
        if(StringUtils.isNotEmpty(idField.getCompositeKey())){
            String[] idFields = idField.getCompositeKey().split(Pattern.quote(EntitySchema.COMPOSITE_KEY_DELIMETER));
            String[] idValues = recordId.split(Pattern.quote(EntitySchema.COMPOSITE_KEY_DELIMETER));
            if (idFields.length != idValues.length){
                String msg = "Invalid Id provided "
                        + (StringUtils.isEmpty(childId) ? "" : childId+ ":")
                        + recordId
                        + (StringUtils.isEmpty(parentId) ? "" : ":"+parentId);
                log.error(msg);
                throw new NonRetriableException(ErrorCodes.DATA_NOT_FOUND, msg, ErrorCodes.DATA_NOT_FOUND.toString());
            }
            for (int i=0; i<idFields.length; i++){
                AttributeSchema idAttr = schema.getField(idFields[i]).get();
                idFilterSegment.put(idFields[i], escaped(idAttr, idValues[i]));
            }
        } else {
            idFilterSegment.put(idField.getApiName(), escaped(idField, recordId));
        }
        return idFilterSegment;
    }

    private List<EntityData> getChildRecordById(SyncRequest r, EntitySchema childSchema, AttributeSchema childIdField, EntitySchema parentSchema, AttributeSchema parentIdField, String childRecordId, EntityData pr) {
        ODataClient client = getODataClient(r.getConnector());

        final URIBuilder childResourceURI = client.newURIBuilder(r.getConnector().getEndpoint())
                .appendEntitySetSegment(parentSchema.getApiName())
                .appendKeySegment(getIdFilterSegment(parentSchema, parentIdField, pr.getId(), "", childRecordId))
                .appendNavigationSegment(childSchema.getApiName())
                .appendKeySegment(getIdFilterSegment(childSchema, childIdField, childRecordId, pr.getId(), ""));

        final ODataRetrieveResponse<ClientEntity> response = client.getRetrieveRequestFactory().getEntityRequest(childResourceURI.build()).execute();
        final List<EntityData> childRecords = toRecords(childSchema, childIdField, List.of(response.getBody()));
        setFabricatedFieldValuesOnChildren(childSchema, parentSchema, pr, childRecords);
        return childRecords;
    }

    private Optional<EntityData> getParentRecordById(ConnectorInfo connector, EntitySchema parent, String parentRecordId) {
        final List<EntityData> parentRecords = getParentRecordsById(
                new SyncRequest().setConnector(connector).setEntitySchema(parent).setData(
                        Map.of(connector.getId(), List.of(new EntityData(parent.getApiName()).setId(parentRecordId)))
                ));
        return parentRecords.stream().findFirst();
    }

    protected static Optional<AttributeSchema> getOriginalChildIdField(EntitySchema entitySchema) {
        return Optional.ofNullable(entitySchema.getAdditionalProperties().get(CHILD_ORIGINAL_ID_FIELD_NAME))
                .flatMap(originalIdFieldName -> entitySchema.getField(originalIdFieldName.toString()));
    }

    private Optional<URIFilter> createIdFilter(SyncRequest r, AttributeSchema idField, FilterFactory filterFactory) {
        //OData V4.0 "in" clause doesn't work with Business Central, so creating a series of "or" clauses instead
        final Optional<URIFilter> idFilter = r.getIds()
                .stream()
                .map(id -> filterFactory.eq(idField.getApiName(), escaped(idField, id)))
                .reduce((op1, op2) -> filterFactory.or(op1, op2));
        return idFilter;
    }

    private static Object escaped(AttributeSchema idField, String id) {
        switch (idField.getDataType()) {
            case "integer":
                return id;
            //Olingo filter builder doesn't escape single quote!!
            default:
                return UrlEscapers.urlFragmentEscaper().escape(id.replace("'", "''"));
        }
    }
    private Object escapedSingleQuoted(AttributeSchema idField, String id) {
        switch (idField.getDataType()) {
            case "integer":
                return id;
            //Olingo filter builder doesn't escape single quote!!
            default:
                return "'" + id.replace("'", "''") + "'";
        }
    }
    @Override
    public SyncResponse create(SyncRequest request) {
        return null;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return null;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return null;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        if(StringUtils.isBlank(getCompanyName(request.getConnector()))){
            return msDynamicsBizCentralServiceV1.describeFullSchema(request.getConnector()).stream().filter(s -> s.getApiName().equals(request.getEntity())).findFirst();
        }
        return describeFullSchema(request.getConnector()).stream().filter(s -> s.getApiName().equals(request.getEntity())).findFirst();
    }

    private List<EntitySchema> describeFullSchema(ConnectorInfo connector) {
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
                    String companyName = getCompanyName(connector);
                    httpRequest.addHeader("company", companyName);
                });
                client.getParams().setParameter("http.useragent", USER_AGENT);
                return client;
            }
        };
        client.getConfiguration().setHttpClientFactory(factory);
        return client;
    }

    private static void markCompanyChildrenAsTopLevelParents(Optional<EntitySchema> company, Map<String, EntitySchema> entityNameMap){
        if(company.isEmpty())
            return;
        for (AttributeSchema a : company.get().getAttributes()) {
            if ("child".equals(a.getDataType())) {
                final EntitySchema childSchema = entityNameMap.get(a.getReferenceTo());
                childSchema.setAdditionalProperties(Map.of(
                        CONTAINER_IS_DIRECT_COMPANY_CHILD, String.valueOf(true)
                ));
            }
        }
    }

    private static void updateSchemaRelationships(List<EntitySchema> entities, Map<String, EntitySchema> entityNameMap) {
        //We need to do some gymnastics to map ODataV4 to Syncari model. ODataV4 supports deeply nested records,
        //but MS BiZCentrla implementation doesn't seem tohonor $expand at all . So we do the following
        // 1. Level 1 child schemas are NOT marked as child schemas and are marked as top level
        // 2. We don't support level 2 onwards in this version, until BizCentral fixes the $expand
        // 3. All level 2 onwards schemas are removed from the schema

        markCompanyChildrenAsTopLevelParents(entities.stream().filter(e -> COMPANY.equalsIgnoreCase(e.getApiName())).findFirst(), entityNameMap);

        List<EntitySchema> schemasToRemove = new ArrayList<>();
        for (EntitySchema e : entities) {
            List<AttributeSchema> childAttributesToRemove = new ArrayList<>();
            for (AttributeSchema a : e.getAttributes()) {
                if ("child".equals(a.getDataType())) {
                    //Do this only if the schema is top level object
                    final EntitySchema childSchema = entityNameMap.get(a.getReferenceTo());
                    if (!hasParent(e)) {
                        Optional<AttributeSchema> parentWMFieldOpt = e.getWatermarkAttr();
                        final Optional<AttributeSchema> createdAtField = e.getCreatedAtField();
                        AttributeSchema parentIdField = e.getIdField();
                        // populate additional properties in the child schemas unless parent is Company
                        // If parent is company, we consider it as top level company
                        // If childSchema is direct child of company, consider it as top level company
                        if (childSchema != null
                                && !COMPANY.equalsIgnoreCase(e.getApiName())
                                && !childSchema.getAdditionalProperties().containsKey(CONTAINER_IS_DIRECT_COMPANY_CHILD)) {
                            final AttributeSchema idField = childSchema.getIdField();
                            childSchema.setAdditionalProperties(Map.of(
                                    CONTAINER_PARENT, e.getApiName(),
                                    CONTAINER_PARENT_ID_FIELD, parentIdField.getApiName(),
                                    CONTAINER_PARENT_ID_FIELD_TYPE, parentIdField.getDataType(),
                                    CONTAINER_PARENT_ID_FIELD_COMPOSITE_KEY, StringUtils.isNotBlank(parentIdField.getCompositeKey()) ? parentIdField.getCompositeKey() : "",
                                    CONTAINER_PARENT_WM_FIELD, parentWMFieldOpt.isPresent() ? parentWMFieldOpt.get().getApiName() : "",
                                    CONTAINER_PARENT_WM_FIELD_TYPE, parentWMFieldOpt.isPresent() ? parentWMFieldOpt.get().getDataType() : "",
                                    CHILD_FIELD_NAME_IN_PARENT, a.getApiName(),
                                    CHILD_ORIGINAL_ID_FIELD_NAME, idField.getApiName(),
                                    CHILD_FIELD_NAME_IN_PARENT_MULTIVALUED, String.valueOf(a.isMultiValueField())
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
                        if (childSchema != null && !childSchema.getAdditionalProperties().containsKey(CONTAINER_IS_DIRECT_COMPANY_CHILD)) {
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
        return (!COMPANY.equalsIgnoreCase(e.getApiName()) &&
                (e.getAdditionalProperties().containsKey(CONTAINER_PARENT)
                        || !e.getAdditionalProperties().containsKey(CONTAINER_IS_DIRECT_COMPANY_CHILD)));
    }

    private EntitySchema toEntitySchema(EdmEntityType entityType) {
        final EntitySchema entitySchema = new EntitySchema(entityType.getName(), entityType.getName());
        final Set<String> keyFieldNames = addKeyFields(entityType, entitySchema);
        entityType.getPropertyNames().forEach(p -> {
            final EdmProperty structuralProperty = entityType.getStructuralProperty(p);
            final String fullQualifiedTypeName = structuralProperty.getType().getFullQualifiedName().getFullQualifiedNameAsString();
            if (!keyFieldNames.contains(structuralProperty.getName())) {
                if (SUPPORTED_DATATYPES.contains(fullQualifiedTypeName)) {
                    final AttributeSchema field = toAttribute(structuralProperty);
                    if (SYSTEM_MODIFIED_AT.equals(structuralProperty.getName())) {
                        field.setWatermarkField(true);
                        field.setSystem(true);
                        field.setUpdatedAtField(true);
                    } else if (SYSTEM_CREATED_AT.equals(structuralProperty.getName())) {
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

        // If there is no Watermark field. add a fabricated watermark field
        if(entitySchema.getWatermarkAttr().isEmpty()){
            final AttributeSchema watermarkField = new AttributeSchema(Constants.SYNCARI_FABRICATED_WATERMARKFIELD, "datetime")
                    .setDisplayName("Syncari Fabricated Watermark Field")
                    .setUpdatedAtField(true)
                    .setWatermarkField(true)
                    .setSystem(true);
            entitySchema.addField(watermarkField);
        }

        //All BizCentral entities are RO in this version

        entitySchema.setReadOnly(true);
        return entitySchema;
    }

    protected static Set<String> addKeyFields(EdmEntityType e, EntitySchema entitySchema) {
        final List<EdmKeyPropertyRef> keyPropertyRefs = e.getKeyPropertyRefs();
        final List<AttributeSchema> keyFields = keyPropertyRefs.stream().map(k -> toAttribute(k.getProperty())).collect(Collectors.toList());
        final Set<String> keyFieldNames = keyPropertyRefs.stream().map(k -> k.getName()).collect(Collectors.toSet());
        if (!keyFields.isEmpty()) {
            keyFields.get(0).setIdField(true);
        }
        if (keyFields.size() > 1) {
            final String compositeKey = keyFields.stream().map(k -> k.getApiName()).reduce((k1, k2) -> k1 + "|" + k2).orElse("");
            keyFields.get(0).setCompositeKey(compositeKey);
        }
        keyFields.forEach(k -> {
            k.setUpdateable(false);
            k.setSystem(true);
            entitySchema.addField(k);
        });
        return keyFieldNames;
    }

    protected static  AttributeSchema toAttribute(EdmProperty structuralProperty) {
        return new AttributeSchema(structuralProperty.getName(), toSyncariDatatype(structuralProperty.getType()))
                .setDisplayName(structuralProperty.getName())
                .setNillable(structuralProperty.isNullable())
                .setPrecision(structuralProperty.getPrecision() == null ? 0 : structuralProperty.getPrecision())
                .setMultiValueField(structuralProperty.isCollection())
                .setLength(structuralProperty.getMaxLength() == null ? 0 : structuralProperty.getMaxLength());
    }

    protected static String toSyncariDatatype(EdmType type) {
        switch (type.getName().toLowerCase()) {
            case "string":
            case "guid":
                return "string";
            case "float":
            case "double":
            case "decimal":
                return "Double";
            case "int16":
            case "int32":
            case "int64":
                return "integer";
            case "datetime":
            case "datetimeoffset":
                return "datetime";
            case "boolean":
                return "boolean";
            case "date":
                return "date";
            default:
                return "string";
        }

    }

    private AuthConfig getAuthConfig(AuthConfig authConfig) {
        if (authConfig.getAccessToken() == null || authConfig.expiresSoon()) {
            authConfig = getAccessToken(authConfig);
        }
        return authConfig;
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        final List<EntitySchema> entitySchemas = StringUtils.isBlank(getCompanyName(request.getConnector())) ? msDynamicsBizCentralServiceV1.describeFullSchema(request.getConnector()) : describeFullSchema(request.getConnector());
        final Set<String> requestedEntities = new HashSet<>(request.getEntities());
        return request.getEntities().isEmpty() ? entitySchemas :
                entitySchemas
                        .stream()
                        .filter(e -> requestedEntities.contains(e.getApiName()))
                        .collect(Collectors.toList());
    }

    @Override
    public List<Capability> getCapabilities() {
        return List.of(Capability.getById, Capability.getByWatermark, Capability.schemaEditInSyncari, Capability.userEditableWm, Capability.compositeId);
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
        final AuthMetadata simpleOAuthType = new AuthMetadata(AuthType.SimpleOAuth, new ArrayList<>(List.of(ConnectorHelper.getClientIdField(), ConnectorHelper.getClientSecretField())), "Simple OAuth", "");
        simpleOAuthType.addField(
                new AuthField().setName("accessTokenEndpoint").setLabel("Access Token URL").setDataType("string").setRequired(true)
                        .setDescription("This is used to get an access code. It's generally of the format https://login.microsoftonline.com/<tenant-id>/oauth2/v2.0/token"));

        return List.of(
                simpleOAuthType
        );
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(new AuthField().setName("endpoint").setLabel("Service Endpoint")
                        .setDataType("string")
                        .setDescription("This is the Business Central OData V4.0 service endpoint. Its generally of the format https://api.businesscentral.dynamics.com/v2.0/<tenant-id>/Production/ODataV4")
                        .setRequired(true),
                // Add an config field to track the top level company Name
                // Optional for backward compatibility
                new AuthField().setName("companyName").setLabel("Company Name")
                        .setDataType("string")
                        .setDescription("This is the optional Business Central top level Company")
                        .setRequired(false),
                ConnectorHelper.getSupportedAuthPicker());
    }

    private String getCompanyName(ConnectorInfo connectorInfo){
        return connectorInfo.getMetaConfig().getOrDefault("companyName", "").toString();
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
        return "msdynamicsbizcentral";
    }

    @Override
    public String getCategory() {
        return "CRM";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/msdynamicsbizcentral.svg")
                .setDisplayName("Dynamics 365 Business Central")
                .setBackgroundColor("#EFF8FF")
                .setHelpUrl(helpArticlesBaseUrl + "/22699077243540");
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        try {
            final AuthConfig accessToken = getAccessToken(config.getAuthConfig());
            final TestConnectionResponse success = new TestConnectionResponse(null, "", List.of());
            success.setAuthConfig(accessToken);
            return success;
        } catch (Exception e) {
            log.error("Authentication failed", e);
            return new TestConnectionResponse(TestConnectionResponse.AUTH_FAILED_MESSAGE, ConnectorErrorCodes.CONNECTION_ERROR, List.of(e.getMessage()));
        }
    }

    @SneakyThrows
    private AuthConfig getAccessToken(AuthConfig authConfig) {
        String accessTokenURL = authConfig.getHeader("accessTokenEndpoint");
        final HttpRequest accessTokenRequest = HttpRequest.newBuilder()
                .uri(URI.create(accessTokenURL))
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                String.format("client_id=%s&client_secret=%s&scope=%s&grant_type=%s",
                                        authConfig.getClientId(), authConfig.getClientSecret(), BIZ_CENTRAL_SCOPE_URL, "client_credentials")
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

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        throw new RuntimeException("OAuth2 Authorization Code flow not supported by Business Central");
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        return getAccessToken(connector.getAuthConfig());
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "";
    }
}

