package com.syncari.connector.intercom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.*;
import com.syncari.utils.DateUtil;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@Component(Constants.INTERCOM)
public class IntercomService implements AuthenticationService, CommonDataService, MetadataService, SynapseInfoService , WebhookService {

    public static final String CONTACT = "contact";

    public static final String COMPANY = "company";

    public static final String TICKET = "ticket";

    public static final String CONVERSATION = "conversation";

    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";
    public static final String ID = "id";
    public static final String CUSTOM_ATTRIBUTES = "custom_attributes";
    public static final String X_HUB_SIGNATURE = "x-hub-signature";
    public static final String WEBHOOK_URL = "/api/v1/webhooks/" + Constants.INTERCOM;
    public static final String APP_ID = "app_id";

    public static final String CLIENT_SECRET = "clientSecret";
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;

    public static final String API_HOST_URL = "https://api.intercom.io";

    private static final String DESCRIBE_URL = "%s/data_attributes";
    public static final List<String> SUPPORTED_OBJECTS = List.of(CONTACT, COMPANY, TICKET, CONVERSATION);

    public static final Map<String, List<String>> MULTI_VALUED_ATTRS = Map.ofEntries(
            Map.entry(CONTACT, List.of("tags", "companies" ))
    );


    // 60 is max page size for few entities like company.
    public static final int API_MAX_PAGESIZE = 60;
    private static final String CRUD_ID_URL = "%s/%s/%s";
    private static final String CRUD_URL = "%s/%s";

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getApiKey());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField clientSecretField = ConnectorHelper.getClientSecretField();
        clientSecretField.setRequired(false);

        return List.of(ConnectorHelper.getEndpointField(), getApplicationId(), clientSecretField
                , ConnectorHelper.getSupportedAuthPicker());
    }


    public static AuthField getApplicationId() {
        AuthField applicationId = new AuthField();
        applicationId.setDataType("text");
        applicationId.setName(APP_ID);
        applicationId.setLabel("Application Id");
        applicationId.setRequired(false);
        return applicationId;
    }

    @Override
    public String getCategory() {
        return "Accounting";
    }

    @Override
    public String getName() {
        return Constants.INTERCOM;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19200294053908";
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/intercom.svg")
                .setDisplayName("Intercom")
                .setBackgroundColor("#F2F9FF")
                .setHelpUrl(helpArticlesBaseUrl + "/9676693296788-Intercom-Setup");
    }

    private JsonParserConfig getSingleJsonConfig() {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
    }

    protected IntercomRestClient getClient() {
        return new IntercomRestClient(getSingleJsonConfig(), mapper);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            ResponseEntity<String> data = getClient()
                .getResponse(String.format(DESCRIBE_URL, getHost(config)), config.getAuthConfig());
            log.info("Data received " + data);
        } catch (Exception e) {
            log.error("Intercom testConnection failed due to " + e.getMessage(), e);
            handleAuthenticationErrorMessage(response, e);
        }
        return response;
    }
    @Override
    public void handleAuthenticationErrorMessage(TestConnectionResponse response, Exception e) {
        response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
        String message = TestConnectionResponse.AUTH_FAILED_MESSAGE;
        Set<String> errors = new HashSet<>();
        if (e != null) {
            String error = getErrorMessage(e);
            if (error==null && e.getCause() != null && e.getCause().getMessage() != null) {
                error = getErrorMessage(e.getCause());
            }
            if (error==null && ExceptionUtils.getRootCause(e) != null && ExceptionUtils.getRootCause(e).getMessage() != null) {
                error = getErrorMessage(ExceptionUtils.getRootCause(e));
            }
            if (error==null && e.getMessage() != null){
                error = e.getMessage();
            }
            if(error != null){
                errors.add(error);
            }
        }

        extractErrorIfJSON(errors);
        // if no error messages from synapses, just use it
        if (message.equalsIgnoreCase(TestConnectionResponse.AUTH_FAILED_MESSAGE)) {
            message += " " + (StringUtils.isNotBlank(response.getMessage()) ? response.getMessage() : "Please verify credentials and try again.");
        }
        response.setMessage(message);
        if(errors.isEmpty()) {
            response.setErrors(List.of(message));
        }
        else {
            response.setErrors(new ArrayList<>(errors));
        }
    }

    private void extractErrorIfJSON(Set<String> errors) {
        Set<String> errorClone = new HashSet<>(errors);
        for(String error: errorClone) {
            if(isJSONValid(error)) {
                ObjectMapper mapper = new ObjectMapper();
                try {
                    Map<String, Object> map = mapper.readValue(error, Map.class);
                    if(map.containsKey("message") && map.get("message") instanceof String) {
                        errors.remove(error);
                        errors.add((String)map.get("message"));
                    }
                } catch (JsonProcessingException ex) {
                    // noop
                }
            }
        }
    }

    private static boolean isJSONValid(String jsonInString ) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(jsonInString);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String getErrorMessage(Throwable ex) {

        if(ex instanceof NonRetriableException){
            NonRetriableException nonRetriableException = (NonRetriableException) ex;
            String errorCode = nonRetriableException.getErrorCode();
            switch (errorCode) {
                case "ACCESS_DENIED":
                    return "Incorrect API key provided for authentication";
                case "BAD_ENDPOINT":
                    return "Invalid EndpointURL provided";
            }
        }
        return  null;
    }


    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        String entity = request.getEntity();

        if (TICKET.equals(entity)) {
            return Optional.of(IntercomSeed.getSeedEntitySchema(entity));
        }

        ResponseEntity<String> data = getClient()
                .getResponse(String.format(DESCRIBE_URL, getHost(request.getConnector())), request.getConnector().getAuthConfig());

        return Optional.of(entitySchemaFromAPI(data).get(entity));
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        ResponseEntity<String> data = getClient()
            .getResponse(String.format(DESCRIBE_URL, getHost(request.getConnector())), request.getConnector().getAuthConfig());
        Map<String, EntitySchema> schemaMap = entitySchemaFromAPI( data);
        List<EntitySchema> list = new ArrayList<>(schemaMap.values());
        list.removeIf( e -> !SUPPORTED_OBJECTS.contains(e.getApiName()));
        list.add(describe(new DescribeRequest(request.getConnector(), TICKET)).get());
        return list;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize,
                                                                               cursor) -> {

            String url = String.format(CRUD_URL, getHost(request.getConnector()), plural(request.getEntityName()));

            if(CONTACT.equals(request.getEntityName()) || TICKET.equals(request.getEntityName())){
                url+="/search";
            }

            return getClient().getDataWithCursor(url, request, pageSize, cursor);
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();

        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(request.getWatermark(),
                request.getWatermark().getChangeStream(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                pgSize, request.getWatermark().getLimit(), true);
        return new FetchResponse(request.getWatermark(), iterator);

    }


    public static String plural(String entityName) {

        if(COMPANY.equals(entityName)){
            return "companies";
        }
        return entityName+"s";
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        Set<String> ids = new HashSet<>(request.getIds());
        List<EntityData> results = new ArrayList<>();
        for(String id: ids) {
            String url = format(CRUD_ID_URL,  getHost(request.getConnector()),  plural(request.getEntityName()), id);
            List<EntityData> result = getClient().getById(url, request);
            results.addAll(result);
        }
        return results;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    private static final Map  entityDataToMap(EntityData data, EntitySchema schema){
        Map results = new HashMap<>();
        data.getValues().forEach((k, v) -> {
            Optional<AttributeSchema> field = schema.getField(k);
            if(field.isPresent()) {
                AttributeSchema attr = field.get();
                String fieldname = attr.getApiName();
                Object value = v;
                if(attr.getDataType().equals("datetime") && v != null){
                    if(v instanceof ZonedDateTime) {
                        value = ((ZonedDateTime) v).toInstant().getEpochSecond();
                    } else {
                        try {
                            value = Long.parseLong(v.toString()) / 1000;
                        } catch (Exception e) {
                            value = DateUtil.convertDateTime(v.toString()).toInstant().getEpochSecond();
                        }
                    }
                }
                if(fieldname.startsWith(CUSTOM_ATTRIBUTES)){
                    results.putIfAbsent(CUSTOM_ATTRIBUTES, new HashMap<String,Object>());
                    String cAttribute = fieldname.substring("custom_attributes.".length(), fieldname.lastIndexOf("_"));
                    Map<String,Object> cAttributesMap = (Map<String, Object>) results.get(CUSTOM_ATTRIBUTES);
                    cAttributesMap.put(cAttribute, value);

                } else {
                    results.put(fieldname, value);
                }

            } else {
                // For conversation entity, include fields even if not in schema
                // This is needed for create operations where fields like 'from', 'body' may not be in the schema
                if(CONVERSATION.equals(schema.getApiName())){
                    results.put(k, v);
                    log.debug("Including field {} not in schema for conversation entity", k);
                } else {
                    log.warn("Field %s not found", k);
                }
            }
        });
        return results;

    }

    @Override
    public SyncResponse create(SyncRequest request) {
        return createOrUpdate(request , HttpMethod.POST);
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return createOrUpdate(request , HttpMethod.PUT);
    }

    private SyncResponse createOrUpdate(SyncRequest request, HttpMethod method) {

        SyncResponse response = new SyncResponse();
        EntitySchema schema = request.getEntitySchema();

        if(schema == null) {
            throw new RuntimeException("Entity Schema cannot be empty");
        }

        AuthConfig authConfig = request.getConnector().getAuthConfig();


        List<EntityData> entityDatas = request.getData().get(request.getConnector().getId());
        entityDatas.stream().forEach(entityData -> {
            ResponseEntity<String> resp = null;
            try {
                HttpHeaders headers = getClient().doGetHeaders(authConfig);

                String url = String.format(CRUD_URL,getHost(request.getConnector()), plural(request.getEntityName()));

                Map values = entityDataToMap(entityData, schema);
                String body = mapper.writeValueAsString(values);

                if (method.equals(HttpMethod.POST)){
                    resp = getClient().postRaw(headers, url, body, authConfig);
                } else if (method.equals(HttpMethod.PUT)){
                    url+="/"+entityData.getId();
                    resp = getClient().put(headers, url, body, authConfig);
                }

                String respBody = resp.getBody();

                Map responseMap = mapper.readValue(respBody, Map.class);
                String id = (String) responseMap.get(ID);

                handleExtraUpdatesForContact(id , request, values , responseMap);

                response.getResults().add(new Result(true, id, entityData.getSyncariEntityId()));

            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
                Result result = new Result(false, null, entityData.getSyncariEntityId());
                result.addError(e.getMessage());
                response.getResults().add(result);
            }
        });

        return response;

    }


    private void handleExtraUpdatesForContact(String id, SyncRequest request, Map values, Map postResponse) {
        String entityName = request.getEntityName();
        if(!CONTACT.equals(entityName)) {
            return;
        }

        // proceed only for 'contact' entity.
        handleTagsForContact(id,request, values, postResponse);
        handleCompaniesForContact(id,request, values, postResponse);

    }

    private void handleTagsForContact(String id, SyncRequest request, Map values, Map postResponse) {
        String tags = "tags";

        if (!values.containsKey(tags)) return;

        AuthConfig authConfig = request.getConnector().getAuthConfig();
        HttpHeaders headers = getClient().getHeaders(authConfig);

        // handle tags update
        // https://api.intercom.io/contacts/62e03b45d83cdf27d3d9528e
        String url = String.format(CRUD_ID_URL,getHost(request.getConnector()), plural(request.getEntityName()), id);

        List<String> existingTags= getExistingTagsFromContact(postResponse);

        List<String> newTags = Optional.ofNullable((List<String>) values.get(tags)).orElse(List.of());

        // https://api.intercom.io/contacts/62e03b45d83cdf27d3d9528e/tags
        String addUrl = url + "/"+tags;
        Collection<String> tagsToAdd = CollectionUtils.subtract(newTags, existingTags);
        // Can add only 1 'tag' at a time for a 'contact'
        for (String tagToAdd: tagsToAdd) {
            try {
                String body = mapper.writeValueAsString(Map.of(ID, tagToAdd));
                getClient().postRaw(headers, addUrl, body, authConfig);

            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }


        Collection<String> tagsToRemove = CollectionUtils.subtract( existingTags, newTags);
        // Can add only 1 'tag' at a time for a 'contact'
        for (String tagToRemove: tagsToRemove) {
            // https://api.intercom.io/contacts/62e03b45d83cdf27d3d9528e/tags/7128832
            String deleteUrl = url + "/" + tags + "/"+tagToRemove;
            getClient().delete(headers, deleteUrl, authConfig);

        }

    }


    private void handleCompaniesForContact(String id, SyncRequest request, Map values, Map postResponse) {

        String companies = "companies";
        if (!values.containsKey(companies)) return;

        AuthConfig authConfig = request.getConnector().getAuthConfig();
        HttpHeaders headers = getClient().getHeaders(authConfig);

        // handle tags update
        // https://api.intercom.io/contacts/62e03b45d83cdf27d3d9528e
        String url = String.format(CRUD_ID_URL,getHost(request.getConnector()), plural(request.getEntityName()), id);


        List<String> existingCompanies = getExistingCompaniesFromContact(postResponse);

        List<String> newCompanies = (List<String>) values.getOrDefault(companies, List.of());

        // https://api.intercom.io/contacts/62e03b45d83cdf27d3d9528e/companies
        String addUrl = url + "/"+companies;
        Collection<String> companiesToAdd = CollectionUtils.subtract(newCompanies, existingCompanies);
        // Can add only 1 'company' at a time for a 'contact'
        for (String companyToAdd: companiesToAdd) {
            try {
                String body = mapper.writeValueAsString(Map.of(ID, companyToAdd));
                getClient().postRaw(headers, addUrl, body, authConfig);

            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        Collection<String> companiesToRemove = CollectionUtils.subtract( existingCompanies, newCompanies);
        // Can add only 1 'company' at a time for a 'contact'
        for (String companyToRemove: companiesToRemove) {
            // https://api.intercom.io/contacts/62e03b45d83cdf27d3d9528e/companies/62e280eeaff67de770ea8d76
            String deleteUrl = url + "/" + companies + "/" + companyToRemove;
            getClient().delete(headers, deleteUrl, authConfig);

        }

    }

    private List<String> getExistingTagsFromContact(Map responseMap) {
        return getAsListFromContact(responseMap, "tags");
    }

    private List<String> getAsListFromContact(Map responseMap, String field) {
        Map tags = (Map) responseMap.get(field);
        List<Map> data = (List) tags.get("data");
        return data.stream().map( d -> {
            return (String)d.get("id");

        }).collect(Collectors.toList());

    }


    private List<String> getExistingCompaniesFromContact(Map responseMap) {
        return getAsListFromContact(responseMap, "companies");

    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return getClient().deleteRecords(CRUD_ID_URL, request);
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
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in " + this.getUIMetadata().getDisplayName()  + " yet");
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    static Object getHost(ConnectorInfo config) {
		return StringUtils.isBlank(config.getEndpoint()) ? API_HOST_URL : config.getEndpoint();
	}

    private Map<String, EntitySchema> entitySchemaFromAPI(ResponseEntity<String> resp) {
        List<EntitySchema> entitySchemas = new ArrayList<>();
        if (resp.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException(resp.getBody());
        }
        Map respMap;
        try {
            respMap = mapper.readValue(resp.getBody(), Map.class);
        } catch (JsonProcessingException e1) {
            throw new RuntimeException("Failed to read entities.", e1);
        }
        Map<String, EntitySchema>  schemaMap = new LinkedHashMap<>();
        List<Map<String, Object>> attributes = (ArrayList<Map<String, Object>>) respMap.get("data");
        attributes.forEach(attribute -> {
            String model = attribute.get("model").toString();  //capitaliseStart(
            String full_name = attribute.get("full_name").toString();
            String label = attribute.get("label").toString();
            String data_type = attribute.get("data_type").toString();
            if("date".equalsIgnoreCase(data_type)){
                data_type = "datetime";
            }
            if("float".equalsIgnoreCase(data_type)) {
                data_type = "double";
            }
            boolean custom = Boolean.valueOf(attribute.get("custom").toString());
            boolean api_writable = Boolean.valueOf(attribute.get("api_writable").toString());
            boolean archived = Boolean.valueOf(attribute.get("archived").toString());

            if (custom){
                String customId = attribute.get("id").toString();
                full_name = full_name + "_" + customId;
            }

            schemaMap.putIfAbsent(model, new EntitySchema(model, model));
            EntitySchema entitySchema = schemaMap.get(model);
            AttributeSchema attributeSchema = new AttributeSchema(full_name, data_type).setDisplayName(label)
                    .setInitializable(false).setUpdateable(api_writable).setCustom(custom);
            if(archived){
                attributeSchema.setStatus(Status.INACTIVE);
            }

            if(ID.equals(full_name)){
                attributeSchema.setIdField(true).setSystem(true).setUnique(true).setNillable(false);
            } else if(CREATED_AT.equals(full_name)){
                attributeSchema.setCreatedAtField(true).setNillable(false);
            } else if(UPDATED_AT.equals(full_name)){
                attributeSchema.setWatermarkField(true).setNillable(false);
            }
            if (model.equalsIgnoreCase("company") && ("company_id".equalsIgnoreCase(full_name))){
                attributeSchema.setUpdateable(true);
            }
            entitySchema.addField(attributeSchema);

        });

        // add missed attributes
        EntitySchema contactSchema = schemaMap.get(CONTACT);

        // Add 'tags' attribute for 'contact' entity
        AttributeSchema tags = new AttributeSchema("tags", "id").setDisplayName("Tags")
                .setInitializable(true).setUpdateable(true).setMultiValueField(true);
        contactSchema.addField(tags);

        // Add 'companies' attribute for 'contact' entity
        AttributeSchema companies = new AttributeSchema("companies", "reference").setDisplayName("Companies").setReferenceTo(COMPANY)
                .setReferenceTargetField(ID).setInitializable(true).setUpdateable(true).setMultiValueField(true);
        contactSchema.addField(companies);


        return schemaMap;
    }

    private void handleInvalidJson() {
        throw new RuntimeException("Invalid request. The eventdata json is invalid");
    }

    @Override
    public String extractIdentifier(WebhookRequest request) {

        try {
            Map<String, Object> map = mapper.readValue(request.getBody(), Map.class);
            if(map.containsKey(APP_ID)) {
                return (String)map.get(APP_ID);
            } else {
                handleInvalidJson();
            }
        } catch (JsonProcessingException e) {
            handleInvalidJson();
        }
        return "";
    }

    @Override
    public String getIdentifier(ConnectorInfo config) {
        return config.getMetaConfig().get(APP_ID).toString();
    }

    @Override
    public String getEndpoint() {
        return WEBHOOK_URL;
    }

    private void validateCaller(WebhookRequest request) {

        if(!request.getHeaders().containsKey(X_HUB_SIGNATURE)){
            throw new RuntimeException("Invalid request. The signature header not present.");
        }

        String signature = request.getHeaders().get(X_HUB_SIGNATURE).toString();

        Map<String, Object> metaConfig = request.getConfig().getMetaConfig();
        String clientSecret = (String) metaConfig.get(CLIENT_SECRET);
        if(StringUtils.isEmpty(clientSecret)){
            throw new RuntimeException("Webhook request processing failed. Synapse config-value for 'clientSecret' is empty.");
        }
        String expectedSignature = "sha1=" + TextUtil.hmacSha1InHex(request.getBody(), clientSecret);

        if(!expectedSignature.equalsIgnoreCase(signature)){
            throw new RuntimeException("Invalid request. The signatures do not match.");
        }
    }

    private static final Map<String, String> SUPPORTED_EVENTS = Map.of("contact.deleted", CONTACT);

    @Override
    public List<EventData> parseEventData(WebhookRequest request) {
        log.info("parseEventData:"+request);

        validateCaller(request);
        List<EventData> response = new ArrayList<>();
        try {
            Map<String, Object> map = mapper.readValue(request.getBody(), Map.class);
            if(map.containsKey("topic") && SUPPORTED_EVENTS.containsKey((String)map.get("topic"))) {
                Map<String, Object> data = (Map<String, Object>) map.get("data");
                Map<String, Object> item = (Map<String, Object>) data.get("item");
                String entityName = (String) item.get("type");

                if(CONTACT.equalsIgnoreCase(entityName)) {

                    String id = (String) item.get("id");

                    EntityData entityData = new EntityData(entityName);
                    entityData.setId(id);
                    entityData.setConnectorId(request.getConfig().getId());
                    entityData.setDeleted(true);
                    Long updatedAt = (Long)map.get("created_at");
                    if (null != updatedAt){
                        entityData.setLastModified(updatedAt);
                    }else{
                        entityData.setLastModified(ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).toInstant().toEpochMilli());
                    }
                    response.add(new EventData().setData(entityData).setOperation(Operation.delete));
                }

            }
        } catch (JsonProcessingException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Invalid request. The eventdata json is invalid");
        }
        log.info("Parsed {} records for intercom", response.size());
        return response;
    }
}
