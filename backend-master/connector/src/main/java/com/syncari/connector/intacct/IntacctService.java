package com.syncari.connector.intacct;

import com.google.common.collect.Lists;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.def.*;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.I18n.i18n;

@Component(Constants.INTACCT)
@Slf4j
public class IntacctService implements AuthenticationService, CommonDataService, MetadataService, SynapseInfoService, OauthAuthenticationService {
    public static final String NAME = "intacct";
    public static final String DISPLAY_NAME = "Sage Intacct";


    public static final String API_SESSION_ENDPOINT = "https://api.intacct.com/ia/xml/xmlgw.phtml";
    public static final String COMPANY_ID_KEY = "companyId";
    public static final String LOCATION_ID_KEY = "locationId";
	// Sage Intacct partner credentials must be injected at runtime; never ship shared credentials in source.
	public static final String SENDER_ID = System.getenv().getOrDefault("NEXTEDGE_INTACCT_SENDER_ID", "");
	public static final String SENDER_PWD = System.getenv().getOrDefault("NEXTEDGE_INTACCT_SENDER_PASSWORD", "");

    static final Set<String> NO_WM_ENTITIES = Set.of("contactversion");

    IntacctClient restClient = new IntacctClient();
    public static final String ENDPOINT_KEY = "endpoint";

    @Override
    public String getCapabilitiesArticleId() {
        return "19201058394132";
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        AuthConfig authConfig = config.getAuthConfig();
        IntacctRequest apiSessionRequest = IntacctRequest.getAPISessionRequest(
                SENDER_ID,
                SENDER_PWD,
                authConfig.getUserName(),
                authConfig.getPassword(),
                config.getMetaConfig().get(COMPANY_ID_KEY).toString()
        );
        IntacctResponse response = performCall(API_SESSION_ENDPOINT, apiSessionRequest, config);
        if (response.hasErrors()) {
            return testErrorResponse(config, response);
        } else {
            return testSuccessResponse(config, response);
        }
    }

    private TestConnectionResponse testSuccessResponse(ConnectorInfo config, IntacctResponse response) {
        TestConnectionResponse testConnectionResponse = new TestConnectionResponse();
        API api = response.getOperation().getResults().get(0).getApi();
        AuthenticationResponse authenticationResponse = response.getOperation().getAuthentication();
        AuthConfig authConfigCopy = config.getAuthConfig().clone();
        testConnectionResponse.setAuthConfig(authConfigCopy
                .setAccessToken(api.getSessionid())
                //Required for token refresh logic in framework
                .setRefreshToken(api.getSessionid())
                .setExpiresIn(String.valueOf(authenticationResponse.expiresIn()))
                .setLastRefreshed(authenticationResponse.getSessiontimestamp().toInstant())
                .setEndpoint(api.getEndpoint()).addHeader(LOCATION_ID_KEY, api.getLocationid()));
        testConnectionResponse.setMetaConfig(config.getMetaConfig());
        return testConnectionResponse;
    }

    private TestConnectionResponse testErrorResponse(ConnectorInfo config, IntacctResponse response) {
        TestConnectionResponse testConnectionResponse = new TestConnectionResponse();
        List<String> errorMessages = response.getErrorMessages();
        String errorCode = response.getErrorCode();
        testConnectionResponse.setCode(errorCode);
        testConnectionResponse.setMessage(i18n("intacct_test_failed"));
        testConnectionResponse.setErrors(errorMessages);
        testConnectionResponse.setAuthConfig(config.getAuthConfig());
        testConnectionResponse.setMetaConfig(config.getMetaConfig());
        return testConnectionResponse;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if (request.getEntityName() != null && NO_WM_ENTITIES.contains(request.getEntityName().toLowerCase())) {
            return new FetchResponse(request.getWatermark(), new IntacctOffsetIterator(request));
        }
        return new FetchResponse(request.getWatermark(), new InacctIterator(request));
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        AuthConfig authConfig = request.getConnector().getAuthConfig();
        List<String> ids = request.getIds();
        List<List<String>> partitions = Lists.partition(ids, 100);
        List<EntityData> records = new ArrayList<>();
        for (List<String> partition : partitions) {
            Read read = new Read().withKeys(partition);
            IntacctResponse response;
            InacctEntityPage page = new InacctEntityPage();
            if(!read.getKeys().isBlank()){
                IntacctRequest byWatermark = IntacctRequest.readByIds(SENDER_ID,
                        SENDER_PWD,
                        authConfig.getAccessToken(),
                        request.getEntityName(), partition
                );
                response = performCall(authConfig.getEndpoint(), byWatermark, request.getConnector());
                checkForErrors(response);
                page = response.getOperation().getResults().get(0).getRecords();
            }
            records.addAll(page.getData());
        }
        return completeRecord(request, records);
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        AuthConfig authConfig = request.getConnector().getAuthConfig();

        if (!IntacctSeed.IS_WRITE_ENABLED(request.getEntityName())) {
            String msg = String.format("Intacct create operation not supported for entity %s", request.getEntityName());
            log.warn(msg);
            response.appendError(msg);
            return response;
        }

        List<EntityData> toBeCreated = request.getData().get(request.getConnector().getId());
        AttributeSchema idField = request.getEntitySchema().getIdField();
        resolveRecordIDReferences(request, toBeCreated);
        for (EntityData e : toBeCreated) {
            try {
                com.syncari.connector.data.Result result;
                Map<String, Object> payload = createPayload(e);
                IntacctRequest intactCreateReq = IntacctRequest.create(SENDER_ID,
                        SENDER_PWD,
                        authConfig.getAccessToken(),
                        request.getEntityName().toUpperCase(), payload
                );
                IntacctResponse serviceResponse = performCall(authConfig.getEndpoint(), intactCreateReq, request.getConnector());
                if (!serviceResponse.hasErrors()){
                    Result intacctResult = serviceResponse.getOperation().getResults().get(0);
                    if (!"success".equals(intacctResult.getStatus()) || intacctResult.getEntityData().getValue(idField.getApiName().toUpperCase()) == null){
                        result = new com.syncari.connector.data.Result(false, null, e.getSyncariEntityId());
                        result.addError("Create failed for entity "+e.getSyncariEntityId());
                        response.setSuccess(false);
                    } else{
                        Object key = intacctResult.getEntityData().getValue(idField.getApiName().toUpperCase());
                        result = new com.syncari.connector.data.Result(true, key.toString(), e.getSyncariEntityId());
                    }
                } else {
                    log.error("Create failed: "+ serviceResponse.getErrorMessage() );
                    result = new com.syncari.connector.data.Result(false, null, e.getSyncariEntityId());
                    result.addError(serviceResponse.getErrorMessage());
                    response.setSuccess(false);
                }
                response.getResults().add(result);
            } catch (Exception ex) {
                log.error(ExceptionUtils.getStackTrace(ex));
                com.syncari.connector.data.Result result = new com.syncari.connector.data.Result(false, null, e.getSyncariEntityId());
                result.addError(ex.getMessage());
                response.getResults().add(result);
                response.setSuccess(false);
            }
        }
        return response;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        AuthConfig authConfig = request.getConnector().getAuthConfig();

        if (!IntacctSeed.IS_WRITE_ENABLED(request.getEntityName())) {
            String msg = String.format("Intacct update operation not supported for entity %s", request.getEntityName());
            log.warn(msg);
            response.appendError(msg);
            return response;
        }

        List<com.syncari.connector.data.Result> results = new ArrayList<>();
        AttributeSchema idField = request.getEntitySchema().getIdField();
        List<EntityData> toBeUpdated = request.getData().get(request.getConnector().getId());
        resolveRecordIDReferences(request, toBeUpdated);
        for (EntityData e : toBeUpdated) {
            try{
                com.syncari.connector.data.Result result;
                Map<String, Object> payload = createPayload(e);
                payload.put(idField.getApiName().toUpperCase(), e.getId());
                IntacctRequest intactUpdateReq = IntacctRequest.update(SENDER_ID,
                        SENDER_PWD,
                        authConfig.getAccessToken(),
                        request.getEntityName().toUpperCase(), payload
                );
                IntacctResponse serviceResponse = performCall(authConfig.getEndpoint(), intactUpdateReq, request.getConnector());
                if (!serviceResponse.hasErrors()){
                    if (!"success".equals(serviceResponse.getOperation().getResults().get(0).getStatus())){
                        result = new com.syncari.connector.data.Result(false, null, e.getSyncariEntityId());
                        result.addError("Update failed for entity "+e.getSyncariEntityId());
                        response.setSuccess(false);
                    } else{
                        result = new com.syncari.connector.data.Result(true, e.getId(), e.getSyncariEntityId());
                    }
                } else {
                    log.error("Update failed: "+ serviceResponse.getErrorMessage() );
                    result = new com.syncari.connector.data.Result(false, null, e.getSyncariEntityId());
                    result.addError(serviceResponse.getErrorMessage());
                    response.setSuccess(false);
                }
                response.getResults().add(result);
            } catch (Exception ex) {
                log.error("Update failed", ExceptionUtils.getStackTrace(ex));
                com.syncari.connector.data.Result result = new com.syncari.connector.data.Result(false, e.getId(), e.getSyncariEntityId());
                result.addError(ex.getMessage());
                response.getResults().add(result);
                response.setSuccess(false);
            }
        }
        return response;
    }

    private Map<String, Object> createPayload (EntityData data) {
        Map<String, Object> payload = new HashMap<>();
        data.getValues().forEach((k, v) -> {
            extractKeyValues(payload, k, v);
        });
        return payload;
    }

    private void extractKeyValues(Map<String, Object> payload, String key, Object value){
        if(key.contains(".")){
            String[] split = key.split("\\.", 2);
            Map<String, Object> subMap = (Map<String, Object>)payload.getOrDefault(split[0], new HashMap<String, Object>());
            extractKeyValues(subMap, split[1], value);
            payload.put(split[0], subMap);
        } else {
            payload.put(key, value);
        }
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        AuthConfig authConfig = request.getConnector().getAuthConfig();

        if (!IntacctSeed.IS_WRITE_ENABLED(request.getEntityName())) {
            String msg = String.format("Intacct delete operation not supported for entity %s", request.getEntityName());
            log.warn(msg);
            response.appendError(msg);
            return response;
        }

        List<com.syncari.connector.data.Result> results = new ArrayList<>();

        List<EntityData>toBeDeleted = request.getData().get(request.getConnector().getId());
        for (EntityData e : toBeDeleted) {
            try{
                com.syncari.connector.data.Result result;
                Delete delete = new Delete().withKeys(List.of(e.getId()));
                if(delete.keys.isBlank()) continue;;
                IntacctRequest intactUpdateReq = IntacctRequest.delete(SENDER_ID,
                        SENDER_PWD,
                        authConfig.getAccessToken(),
                        request.getEntityName().toUpperCase(), List.of(e.getId())
                );
                IntacctResponse serviceResponse = performCall(authConfig.getEndpoint(), intactUpdateReq, request.getConnector());
                Result resultForId = serviceResponse.getOperation().getResults().get(0);
                if (!serviceResponse.hasErrors() || CollectionUtils.isNotEmpty(resultForId.getErrorMessage())){
                    if (!"success".equals(resultForId.getStatus())) {
                        String errorMessage = resultForId.getErrorMessage().stream().map(Error::getDescription2).collect(Collectors.joining(","));
                        result = new com.syncari.connector.data.Result(false, null, e.getSyncariEntityId());
                        result.addError("Delete failed for entity " + e.getSyncariEntityId() + " - " + errorMessage);
                        response.setSuccess(false);
                    } else{
                        result = new com.syncari.connector.data.Result(true, e.getId(), e.getSyncariEntityId());
                    }
                } else {
                    log.error("Delete failed: "+ serviceResponse.getErrorMessage() );
                    result = new com.syncari.connector.data.Result(false, null, e.getSyncariEntityId());
                    result.addError(serviceResponse.getErrorMessage());
                    response.setSuccess(false);
                }
                response.getResults().add(result);
            } catch (Exception ex) {
                log.error("Delete failed", ExceptionUtils.getStackTrace(ex));
                com.syncari.connector.data.Result result = new com.syncari.connector.data.Result(false, e.getId(), e.getSyncariEntityId());
                result.addError(ex.getMessage());
                response.getResults().add(result);
                response.setSuccess(false);
            }
        }
        return response;

    }

    private List<EntitySchema> getSchemas(ConnectorInfo connectorInfo, List<String> entities){
        AuthConfig authConfig = connectorInfo.getAuthConfig();
        List<EntitySchema> schemas = new ArrayList<>();
        if (entities.isEmpty()){
            return schemas;
        }

        List<List<String>> partitions = Lists.partition(entities, 100);

        for (List<String> partition: partitions){
            IntacctRequest lookupRequest = IntacctRequest.lookupObjects(SENDER_ID,
                    SENDER_PWD,
                    authConfig.getAccessToken(),
                    partition
            );
            IntacctResponse response = performCall(authConfig.getEndpoint(), lookupRequest, connectorInfo);
            if (response == null || response.getOperation() == null || response.getOperation().getResults() == null || response.getOperation().getResults().isEmpty()) {
                log.error("Not able to fetch the results");
                continue;
            } else if (!IntacctResponse.nullOrEmpty(response.errormessage) || !IntacctResponse.nullOrEmpty(response.getOperation().getErrormessage())){
                throw new NonRetriableException(response.getErrorCode(), response.getErrorMessage(), response.getErrorCode());
            }
            for (Result result:response.getOperation().getResults()){
                if (!IntacctResponse.nullOrEmpty(result.getErrorMessage())){
                    log.info("Skipping Intacct entity because of the error: {}",result.getErrorMessage().get(0).getDescription());
                    continue;
                }
                if (Optional.ofNullable(result.getEntity())
                        .map(EntitySchema::getApiName)
                        .orElse(null) == null) {
                    log.info("Skipping Intacct entity because is empty: {}", result.getControlid());
                    continue;
                }
                EntitySchema schema = result.getEntity();
                handleSchema(schema);
                if ((schema.hasWatermarkField() || (schema.getApiName() != null && NO_WM_ENTITIES.contains(schema.getApiName().toLowerCase()))) && schema.hasIdField()){
                    schemas.add(schema);
                } else {
                    log.debug("Skipping entity {}. has Id field {}. has watermark field {}", schema.getApiName(), schema.hasIdField(), schema.hasWatermarkField());
                }
            }
        }

        return schemas;
    }

    private Optional<EntitySchema> getSchema(ConnectorInfo connectorInfo, String entity) {
        AuthConfig authConfig = connectorInfo.getAuthConfig();
        IntacctRequest lookupRequest = IntacctRequest.lookupObjects(SENDER_ID,
                SENDER_PWD,
                authConfig.getAccessToken(),
                List.of(entity)
        );
        IntacctResponse response = performCall(authConfig.getEndpoint(), lookupRequest, connectorInfo);
        if (response == null || (response.hasErrors() && (isCustomObjectDescribeError(response) || isInvalidObject(response)))) {
            log.error("Trying to describe custom object or GL Entry, which is unsupported ");
            return Optional.empty();
        } else {
            checkForErrors(response);
        }
        EntitySchema schema = response.getOperation().getResults().get(0).getEntity();
        handleSchema(schema);
        //Only pull objects with a watermark field. We'll handle objects without a WM field later
        //GLBUDGET is an example. Exception: allow NO_WM_ENTITIES through
        boolean isNoWmEntity = schema.getApiName() != null && NO_WM_ENTITIES.contains(schema.getApiName().toLowerCase());
        return (schema.hasWatermarkField() || isNoWmEntity) ? Optional.of(schema) : Optional.empty();

    }

    private static void handleSchema(EntitySchema schema) {
        schema.setReadOnly(!IntacctSeed.IS_WRITE_ENABLED(schema.getApiName()));
        //When adding custom fields to entity we need to filter out during readByQuery adding the setSyncariDefined mark
        if("CUSTOMER".equalsIgnoreCase(schema.getApiName()) && !schema.hasField("RESTRICTEDLOCATIONS")){
            schema.addField((new AttributeSchema("RESTRICTEDLOCATIONS", "string")
                    .setSyncariDefined(true)
                    .setDisplayName("RESTRICTEDLOCATIONS")
                    .setUpdateable(true)
                    .setInitializable(true)
                    .setStatus(Status.ACTIVE)));
        }
        if("CUSTOMER".equalsIgnoreCase(schema.getApiName()) && !schema.hasField("RESTRICTEDDEPARTMENTS")){
            schema.addField((new AttributeSchema("RESTRICTEDDEPARTMENTS", "string")
                    .setSyncariDefined(true)
                    .setDisplayName("RESTRICTEDDEPARTMENTS")
                    .setUpdateable(true)
                    .setInitializable(true)
                    .setStatus(Status.ACTIVE)));
        }
        if ("CONTRACT".equalsIgnoreCase(schema.getApiName()) && schema.hasField("BASECURR")){
            schema.getField("BASECURR").get().setUpdateable(true);
        }
        if ("CONTRACT".equalsIgnoreCase(schema.getApiName()) && schema.hasField("STATE")){
            schema.getField("STATE").get().setUpdateable(true);
        }

        if (NO_WM_ENTITIES.contains(schema.getApiName().toLowerCase()) && !schema.hasWatermarkField()) {
            schema.addField(new AttributeSchema("WHENCREATED", "datetime")
                    .setWatermarkField(true)
                    .setSystem(true)
                    .setSyncariDefined(true)
                    .setStatus(Status.ACTIVE)
                    .setDisplayName("When Created")
                    .setUpdateable(false));
        }
        for (AttributeSchema attr: schema.getAttributes()) {
            if(attr.getApiName().contains(".")) {
                attr.setApiName(attr.getApiName().replace(".", "__"));
            }
        }
    }

    IntacctResponse performCall(String endpoint, IntacctRequest intacctRequest, ConnectorInfo connectorInfo) {
        IntacctResponse intacctResponse = restClient.post(endpoint, intacctRequest);
        //In the rare case that session is expired at this time we'll retry
        if (intacctResponse.hasErrors() && intacctResponse.getErrorMessage().contains("Session") && intacctResponse.getErrorMessage().contains("is not valid")) {
            AuthConfig authConfig = refreshToken(connectorInfo);
            intacctRequest.getOperation().getAuthentication().setSessionid(authConfig.getAccessToken());
            intacctResponse = restClient.post(endpoint, intacctRequest);
        }
        return intacctResponse;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        String entity = request.getEntity();
        return getSchema(request.getConnector(), entity);
    }

    private boolean isCustomObjectDescribeError(IntacctResponse response) {
        return response.getErrors().stream().anyMatch(e -> e.getErrorno().equalsIgnoreCase("XL01000033") && e.getDescription2().contains("Custom objects are not supported"));
    }

    private boolean isInvalidObject(IntacctResponse response) {
        return response.getErrors().stream().anyMatch(e -> e.getErrorno().equalsIgnoreCase("XL01000033") && e.getDescription2().contains("Invalid object name"));
    }

    private void checkForErrors(IntacctResponse response) {
        if (response.hasErrors()) {
            throw new NonRetriableException(response.getErrorCode(), response.getErrorMessage(), response.getErrorCode());
        }
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        AuthConfig authConfig = request.getConnector().getAuthConfig();
        IntacctRequest lookupRequest = IntacctRequest.inspectRequest(SENDER_ID,
                SENDER_PWD,
                authConfig.getAccessToken()
        );
        Set<String> requestedEntities = new HashSet<>(request.getEntities());
        IntacctResponse response = performCall(authConfig.getEndpoint(), lookupRequest, request.getConnector());
        checkForErrors(response);
        List<EntitySchema> entities = response.getOperation().getResults().get(0).getEntities();
        Set<String> entityNames = entities.stream().filter(x->!skipEntity(x)).map(e->e.getApiName()).collect(Collectors.toSet());

        List<String> entitiesToFetch = requestedEntities.isEmpty() ?
            new ArrayList<>(entityNames) :
            requestedEntities.stream().filter(e->entityNames.contains(e)).collect(Collectors.toList());

        List<EntitySchema> schemas = getSchemas(request.getConnector(), entitiesToFetch);

        Set<String> missingReferencedEntities = findMissingReferencedEntities(schemas, entityNames);
        if (!missingReferencedEntities.isEmpty()) {
            List<EntitySchema> missingSchemas = fetchMissingEntitySchemas(request.getConnector(), missingReferencedEntities);
            schemas.addAll(missingSchemas);
        }

        return schemas;
    }

    private boolean skipEntity(EntitySchema schema) {
        String schemaName = schema.getApiName().toUpperCase();
        return IntacctSeed.IS_ENTITY_UNSUPPORTED(schemaName) && !IntacctSeed.READ_WRITE_ENTITIES.contains(schemaName);
    }

    private Set<String> findMissingReferencedEntities(List<EntitySchema> schemas, Set<String> availableEntities) {
        Set<String> referencedEntities = new HashSet<>();

        // Collect all entities referenced by reference fields
        for (EntitySchema schema : schemas) {
            if (schema.getAttributes() != null) {
                schema.getAttributes().stream()
                    .filter(attr -> attr.getDataType() != null && attr.getDataType().equals("reference"))
                    .filter(attr -> attr.getReferenceTo() != null)
                    .forEach(attr -> referencedEntities.add(attr.getReferenceTo()));
            }
        }

        // Find which referenced entities are missing from the available entities
        Set<String> missingEntities = new HashSet<>(referencedEntities);
        missingEntities.removeAll(availableEntities);

        // Also remove entities that were already successfully fetched
        Set<String> alreadyFetchedEntities = schemas.stream()
            .map(EntitySchema::getApiName)
            .collect(Collectors.toSet());
        missingEntities.removeAll(alreadyFetchedEntities);

        return missingEntities;
    }

    private List<EntitySchema> fetchMissingEntitySchemas(ConnectorInfo connectorInfo, Set<String> missingEntities) {
        List<EntitySchema> missingSchemas = new ArrayList<>();

        for (String entityName : missingEntities) {
            try {
                Optional<EntitySchema> schema = getSchema(connectorInfo, entityName);
                if (schema.isPresent()) {
                    missingSchemas.add(schema.get());
                } else {
                    log.error("Could not fetch schema for missing entity: {}", entityName);
                }
            } catch (Exception e) {
                log.error("Failed to fetch schema for missing entity {}: {}", entityName, ExceptionUtils.getStackTrace(e));
                // Continue with other entities even if one fails
            }
        }

        return missingSchemas;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(
                new AuthField().setName(COMPANY_ID_KEY).setDataType("string").setLabel("Company Id").setHelpSummary("Contact Your Intacct Administrator for Company Id"),
                ConnectorHelper.getSupportedAuthPicker()
        );
    }

    @Override
    public Map<String, String> getEntityMappings() {
        //No default mappings
        return Map.of();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        //No default mappings
        return Map.of();
    }

    @Override
    public String getName() {
        return NAME;
    }

     public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/intacct.svg")
                .setDisplayName(DISPLAY_NAME)
                .setBackgroundColor("#F6FFF6")
                .setHelpUrl(helpArticlesBaseUrl + "/360055249432-Sage-Intacct-Setup");
    }
    @Override
    public String getCategory() {
        return "ERP";
    }

    private List<EntityData> completeRecord(SyncRequest request, List<EntityData> data) {
        for (EntityData record : data) {
            record.setId(record.getValueAsString(request.getEntitySchema().getIdField().getApiName()));
            record.setConnectorId(request.getConnector().getId());
            record.setName(request.getEntityName());

            if (NO_WM_ENTITIES.contains(request.getEntityName().toLowerCase())) {
                long syntheticTimestamp = System.currentTimeMillis();
                record.setLastModified(syntheticTimestamp);
                record.addValue("WHENCREATED", Instant.ofEpochMilli(syntheticTimestamp).toString());
            } else {
                long lastModified = IntacctRequest.toInstant(record.getValueAsString(request.getEntitySchema().getWatermarkField().getApiName())).toEpochMilli();
                record.setLastModified(lastModified);
            }

            request.getEntitySchema().getCreatedAtField().ifPresent(createdAtField -> {
                long createdAt = IntacctRequest.toInstant(record.getValueAsString(createdAtField.getApiName())).toEpochMilli();
                record.setCreatedAt(createdAt);
            });
        }
        resolveRecordNoReferences(request, data);
        return data;
    }

    void resolveRecordNoReferences(SyncRequest request, List<EntityData> data) {
        if (NO_WM_ENTITIES.contains(request.getEntityName().toLowerCase())) {
            resolveNoWmEntityReferences(request, data);
        } else {
            resolveRegularEntityReferences(request, data);
        }
    }

    private void resolveNoWmEntityReferences(SyncRequest request, List<EntityData> data) {
        AuthConfig authConfig = request.getConnector().getAuthConfig();
        Map<String, String> referenceRecords = request.getEntitySchema().getAttributes()
                .stream().filter(attributeSchema -> attributeSchema.getReferenceTo() != null)
                .collect(Collectors.toMap(AttributeSchema::getApiName, AttributeSchema::getReferenceTo));

        if (referenceRecords.isEmpty()) {
            return;
        }

        Map<String, List<String>> referenceToSearch = referenceRecords.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getValue,
                        referenceTo -> {
                            String referenceKey = referenceTo.getKey();
                            return data.stream()
                                    .map(EntityData::getValues)
                                    .map(values -> (String) values.getOrDefault(referenceKey, ""))
                                    .filter(StringUtils::isNotBlank)
                                    .collect(Collectors.toList());
                        },
                        (list1, list2) -> {
                            List<String> mergedList = new ArrayList<>(list1);
                            mergedList.addAll(list2);
                            return mergedList;
                        }
                ));

        Map<String, Map<String, String>> foundReferences = referenceToSearch.entrySet().stream()
                .filter(entry -> CollectionUtils.isNotEmpty(entry.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            String referencedEntity = entry.getKey();
                            List<String> idsToResolve = entry.getValue();

                            List<EntityData> innerRecords = new ArrayList<>();
                            for (List<String> partition : Lists.partition(idsToResolve, 100)) {
                                String idFieldName = referencedEntity.toUpperCase() + "ID";

                                try {
                                    IntacctRequest intacctRequest = IntacctRequest.queryByIds(SENDER_ID,
                                            SENDER_PWD,
                                            authConfig.getAccessToken(),
                                            Arrays.asList("RECORDNO", idFieldName),
                                            referencedEntity,
                                            idFieldName,
                                            partition,
                                            100);

                                    IntacctResponse response = performCall(authConfig.getEndpoint(), intacctRequest, request.getConnector());
                                    checkForErrors(response);

                                    InacctEntityPage page = response.getOperation().getResults().get(0).getRecords();
                                    innerRecords.addAll(page.getData());

                                } catch (Exception e) {
                                    log.warn("Failed to resolve references for entity '{}': {}. Skipping this lookup to avoid breaking parent sync.",
                                        referencedEntity, e.getMessage());
                                    log.error("Reference resolution error details for entity '{}': {}", referencedEntity, ExceptionUtils.getStackTrace(e));
                                }
                            }

                            Map<String, String> idToRecordNoMap = new HashMap<>();
                            try {
                                idToRecordNoMap = innerRecords.stream()
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toMap(
                                                EntityData::getId,
                                                e -> (String) e.getValues().get("RECORDNO"),
                                                (existingValue, newValue) -> existingValue
                                        ));

                            } catch (Exception e) {
                                log.warn("Failed to build ID->RECORDNO mapping for entity '{}': {}. References will remain unresolved.",
                                    referencedEntity, e.getMessage());
                                log.error("Mapping error details for entity '{}': {}", referencedEntity, ExceptionUtils.getStackTrace(e));
                            }

                            return idToRecordNoMap;
                        }
                ));

        int resolvedCount = 0;
        int totalReferences = 0;

        for (EntityData record : data) {
            referenceRecords.forEach((fieldName, referencedEntity) -> {
                String originalValue = (String) record.getValue(fieldName);
                if (StringUtils.isNotBlank(originalValue)) {
                    String resolvedValue = foundReferences.getOrDefault(referencedEntity, Map.of()).get(originalValue);
                    if (StringUtils.isNotBlank(resolvedValue)) {
                        record.addValue(fieldName, resolvedValue);
                        log.debug("Resolved reference: field '{}' value '{}' -> RECORDNO '{}'",
                            fieldName, originalValue, resolvedValue);
                    } else {
                        log.debug("Could not resolve reference: field '{}' value '{}' for entity '{}'",
                            fieldName, originalValue, referencedEntity);
                    }
                }
            });
        }

        log.debug("Completed reference resolution for NO_WM entity '{}'. Found references: {}",
            request.getEntityName(), foundReferences.keySet());
    }

    private void resolveRegularEntityReferences(SyncRequest request, List<EntityData> data) {
        resolveRecordIDReferences(request, data);
    }

    private void resolveRecordIDReferences(SyncRequest request, List<EntityData> data) {
        AuthConfig authConfig = request.getConnector().getAuthConfig();
        //Retrieve reference fields
        Map<String, String> referenceRecords = request.getEntitySchema().getAttributes()
                .stream().filter(attributeSchema -> attributeSchema.getReferenceTo() != null)
                .collect(Collectors.toMap(AttributeSchema::getApiName, AttributeSchema::getReferenceTo));
        // Create a reverse map with values as keys and a Set of original keys as values
        Map<String, Set<String>> reverseReferenceRecords = referenceRecords.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toSet())
                ));
        // Creates a Map of ID's to search by Domain Entity
        Map<String, List<String>> referenceToSearch = referenceRecords.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getValue, // Key extractor
                        referenceTo -> {
                            String referenceKey = referenceTo.getKey();
                            return data.stream()
                                    .map(EntityData::getValues)
                                    .map(values -> (String) values.getOrDefault(referenceKey, ""))
                                    .filter(StringUtils::isNotBlank)
                                    .collect(Collectors.toList());
                        },
                        // Merge function to handle duplicates by merging lists
                        (list1, list2) -> {
                            List<String> mergedList = new ArrayList<>(list1);
                            mergedList.addAll(list2);
                            return mergedList;
                        }
                ));
        //Creates a Map of fields of RECORDNO found and then group by Entity domain
        Map<String, Map<String, Map<String, String>>> result = new HashMap<>();
        referenceToSearch.entrySet().stream()
                .filter(entry -> CollectionUtils.isNotEmpty(entry.getValue()))
                .forEach(entry -> {
                    List<EntityData> innerRecords = fetchReferenceRecords(
                        entry.getKey(),
                        entry.getValue(),
                        authConfig,
                        request.getConnector()
                    );

                    Map<String, Map<String, String>> fieldsByRecordNo = new HashMap<>();
                    innerRecords.stream()
                            .filter(Objects::nonNull)
                            .map(EntityData::getValues)
                            .filter(e -> e.getOrDefault("RECORDNO", null) != null)
                            .forEach(values -> {
                                String recordno = (String) values.get("RECORDNO");
                                reverseReferenceRecords.get(entry.getKey())
                                        .forEach(field -> {
                                            //Not all reference fields are the same, this handle some known cases
                                            if (field.endsWith("ID")) {
                                                fieldsByRecordNo.computeIfAbsent(recordno, k -> new HashMap<>()).put(field, (String) values.get(field));
                                            }
                                            if (field.endsWith("NAME")) {
                                                fieldsByRecordNo.computeIfAbsent(recordno, k -> new HashMap<>()).put(field, (String) values.get("NAME"));
                                            }
                                            if (field.endsWith("ACCTNO")) {
                                                fieldsByRecordNo.computeIfAbsent(recordno, k -> new HashMap<>()).put(field, (String) values.get("ACCOUNTNO"));
                                            }

                                        });
                            });
                    result.put(entry.getKey(), fieldsByRecordNo);
                });

        for (EntityData record : data) {
            referenceRecords.forEach((key, value) -> {
                String recordByName = (String) record.getValue(key);
                if (recordByName != null) {
                    Map<String, Map<String, String>> valueMap = result.getOrDefault(value, Map.of());
                    Map<String, String> recordMap = valueMap.getOrDefault(recordByName, Map.of());
                    String recordNo = recordMap.getOrDefault(key, "");
                    record.addValue(key, recordNo);
                }
            });
        }
    }

    private List<EntityData> fetchReferenceRecords(String entityType, List<String> valueIds, AuthConfig authConfig, ConnectorInfo connector) {
        List<EntityData> records = new ArrayList<>();
        boolean isNoWmEntity = NO_WM_ENTITIES.contains(entityType.toLowerCase());

        for (List<String> partition : Lists.partition(valueIds, 100)) {
            try {
                IntacctRequest request = isNoWmEntity ?
                    createQueryByIdsRequest(entityType, partition, authConfig) :
                    createReadByIdsRequest(entityType, partition, authConfig);

                IntacctResponse response = performCall(authConfig.getEndpoint(), request, connector);

                if (isNoWmEntity) {
                    addRecordsFromResponse(response, records);
                } else {
                    checkForErrors(response);
                    addRecordsFromResponse(response, records);
                }
            } catch (Exception e) {
                log.warn("Failed to resolve {} entity {} references: {}",
                    isNoWmEntity ? "NO_WM" : "regular", entityType, e.getMessage());
                if (!isNoWmEntity) {
                    throw e;
                }
            }
        }
        return records;
    }

    private IntacctRequest createQueryByIdsRequest(String entityType, List<String> partition, AuthConfig authConfig) {
        String idField = entityType.toUpperCase() + "ID";
        return IntacctRequest.queryByIds(
            SENDER_ID, SENDER_PWD, authConfig.getAccessToken(),
            Arrays.asList("RECORDNO", idField), entityType, idField, partition, 100
        );
    }

    private IntacctRequest createReadByIdsRequest(String entityType, List<String> partition, AuthConfig authConfig) {
        return IntacctRequest.readByIds(SENDER_ID, SENDER_PWD, authConfig.getAccessToken(), entityType, partition);
    }

    private void addRecordsFromResponse(IntacctResponse response, List<EntityData> records) {
        if (response != null && response.getOperation() != null &&
            response.getOperation().getResults() != null &&
            !response.getOperation().getResults().isEmpty()) {

            var result = response.getOperation().getResults().get(0);
            if (result.getRecords() != null) {
                records.addAll(result.getRecords().getData());
            }
        }
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        throw new RuntimeException("OAuth Implicit Flow not supported by Intacct");
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        TestConnectionResponse testConnectionResponse = testConnection(connector, List.of());
        return testConnectionResponse.getAuthConfig();
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "";
    }

    class IntacctOffsetIterator extends DefaultDataOffsetIterator {
        private ValueHolder<Integer> totalCount = new ValueHolder<>();
        private static final int MAX_PAGE_SIZE = 1000;
        private SyncRequest request;

        public IntacctOffsetIterator(SyncRequest request) {
            super(request.getWatermark(), Math.max(0L, request.getWatermark().getOffset()), (wm, pageSize, offset) -> {
                String entity = request.getEntityName();
                AuthConfig authConfig = request.getConnector().getAuthConfig();
                log.debug("Fetching NO_WM entity {} with offset: {}, pageSize: {}", entity, offset, pageSize);
                List<String> selectFields = request.getEntitySchema().getAttributes().stream()
                        .filter(attributeSchema -> !attributeSchema.isSyncariDefined())
                        .map(AttributeSchema::getApiName)
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.toList());

                IntacctRequest queryRequest;
                try {
                    queryRequest = IntacctRequest.queryWithOffset(SENDER_ID,
                            SENDER_PWD,
                            authConfig.getAccessToken(),
                            selectFields,
                            entity,
                            offset.longValue(),
                            pageSize
                    );
                } catch (Exception e) {
                    log.error("Failed to create offset-based query for NO_WM entity {}. Ensure the entity supports RECORDNO field for ordering. Error: {}", entity, ExceptionUtils.getStackTrace(e));
                    String errorMsg = String.format("Failed to query NO_WM entity %s: %s", entity, ExceptionUtils.getStackTrace(e));
                    throw new NonRetriableException("INTACCT_NO_WM_QUERY_ERROR", errorMsg, "INTACCT_NO_WM_QUERY_ERROR");
                }

                IntacctResponse response = performCall(authConfig.getEndpoint(), queryRequest, request.getConnector());
                checkForErrors(response);

                InacctEntityPage records = response.getOperation().getResults().get(0).getRecords();
                if (records == null || records.getData() == null || records.getData().isEmpty()) {
                    log.debug("NO_WM entity {} reached end of data at offset {}. Next sync will start from offset 0.", entity, offset);
                    return new DataWithOffset(offset, 0L, new ArrayList<>(), List.of());
                }

                if (!records.getData().isEmpty() && !records.getData().get(0).has("RECORDNO")) {
                    log.warn("NO_WM entity {} does not have RECORDNO field. Offset-based pagination may not work correctly.", entity);
                }

                List<EntityData> data = completeRecord(request, records.getData());
                long syntheticTimestamp = request.getWatermark().getEnd();
                for (EntityData entityData : data) {
                    entityData.setLastModified(syntheticTimestamp);
                }
                log.debug("Assigned synthetic timestamp {} to {} records for NO_WM entity {}",
                          syntheticTimestamp, data.size(), entity);

                long nextOffset = offset + data.size();
                log.debug("NO_WM entity {} pagination: offset={}, returned={}, nextOffset={}",
                         entity, offset, data.size(), nextOffset);

                return new DataWithOffset(offset, nextOffset, data, List.of());
            }, List.of(), null,
                    request.getPageSize() == 0 ? MAX_PAGE_SIZE : Math.min(request.getPageSize(), MAX_PAGE_SIZE), request.getWatermark().getLimit());
            this.request = request;
            log.debug("Initializing IntacctOffsetIterator for NO_WM entity: {}", request.getEntityName());
        }


        @Override
        public Offset getOffsetInfo() {
            return new Offset(Offset.OffsetType.RECORD_COUNT, getEffectivePageSize());
        }
    }

    class InacctIterator extends DefaultDataIterator {
        private ValueHolder<Integer> totalCount = new ValueHolder<>();
        private static final int MAX_PAGE_SIZE = 1000;
        private SyncRequest request;

        public InacctIterator(SyncRequest request) {
            super(request.getWatermark(), 0L, null, List.of(), request.getEntitySchemaWithMappedFields().getWatermarkField(),
                    request.getPageSize() == 0 ? MAX_PAGE_SIZE : Math.min(request.getPageSize(), MAX_PAGE_SIZE), request.getWatermark().getLimit());
            this.request = request;
            this.generator = (wm, pageSize, offset) -> {
                String entity = request.getEntityName();
                AuthConfig authConfig = request.getConnector().getAuthConfig();
                return readByQuery(request, entity, authConfig, offset);
            };
        }

        public Pair<Long, Stream<EntityData>> readByQuery(SyncRequest request, String entity, AuthConfig authConfig, Long offset) {
            List<String> selectFields = request.getEntitySchema().getAttributes().stream()
                    .filter(attributeSchema -> !attributeSchema.isSyncariDefined())
                    .map(AttributeSchema::getApiName)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());

            selectFields = selectFields.stream().map(e -> e.replace("__", ".")).collect(Collectors.toList());

            IntacctRequest byWatermark = IntacctRequest.readByQuery(SENDER_ID,
                    SENDER_PWD,
                    authConfig.getAccessToken(),
                    entity, request.getEntitySchema().getWatermarkField().getApiName(),
                    Instant.ofEpochMilli(request.getWatermark().getStart()),
                    Instant.ofEpochMilli(request.getWatermark().getEnd()),
                    getEffectivePageSize(),
                    offset,
                    selectFields
            );
            IntacctResponse response = performCall(authConfig.getEndpoint(), byWatermark, request.getConnector());
            checkForErrors(response);
            InacctEntityPage records = response.getOperation().getResults().get(0).getRecords();
            if (!totalCount.hasValue()) {
                totalCount.set(records.getTotalCount());
            }
            return Pair.of((long) records.getData().size(), completeRecord(request, records.getData()).stream());
        }

        @Override
        public Offset getOffsetInfo() {
            return new Offset(Offset.OffsetType.RECORD_COUNT, getEffectivePageSize());
        }
    }
}
