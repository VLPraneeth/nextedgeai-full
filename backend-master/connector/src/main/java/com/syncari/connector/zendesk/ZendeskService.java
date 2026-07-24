package com.syncari.connector.zendesk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.seed.ZendeskSeed;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@Component(Constants.ZENDESK)
public class ZendeskService implements OauthAuthenticationService, CommonDataService, MetadataService, SynapseInfoService {
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;
    private static final Map<String, String> objName = Map.of("organization", "organizations", Constants.TICKET,
            "tickets", "user", "users", Constants.COMMENT, "comments");
    private static final String CREATE_MANY = "/api/v2/%s/create_many.json";
    private static final String UPDATE_MANY = "/api/v2/%s/update_many.json";
    private static final String CREATE = "/api/v2/%s.json";
    private static final String GET_BY_ID = "/api/v2/%s/show_many.json?ids=%s";
    private static final String DELETE_MANY = "/api/v2/%s/destroy_many.json?ids=%s";
    private static final String GET_BY_WATERMARK = "/api/v2/incremental/%s.json?start_time=%s&per_page=%s";
    private static final String COMMENT_GET_BY_WATERMARK = "/api/v2/tickets/%s/comments?include_inline_images=true";
    private static final String GET_FIELDS = "/api/v2/%s_fields.json";
    private static final String DELETE_FIELDS = "/api/v2/%s_fields/%s.json";
    private static final String GET_CUSTOM_OBJECTS = "/api/sunshine/objects/types";
    private static final int CLOCK_SKEW_TOLERANCE_SECS = 2 * 60;

    private static final Set<String> NON_TICKET_OBJECTS = Set.of("organization","user", Constants.COMMENT);

    public static int API_MAX_PAGESIZE = 200;
    public static int MAX_LEN = 65536;
    public static Set<String> TEXT_FIELDS = Set.of("text", "textarea");
    public static Set<String> NO_CUSTOM_FIELDS = Set.of("comment");

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of(Constants.ACCOUNT.toLowerCase(), "organization", Constants.TICKET.toLowerCase(), "ticket",
                Constants.USER.toLowerCase(), "user");
    }
    
    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return ZendeskSeed.getAttributeMappings(entityApiName);
    }
    
    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        AuthMetadata oAuth = new AuthMetadata(AuthType.Oauth,
                List.of(ConnectorHelper.getClientIdField(), ConnectorHelper.getClientSecretField()), "OAuth", "");
        AuthMetadata oAuthToken = new AuthMetadata(AuthType.OauthToken,
                List.of(ConnectorHelper.getTokenField(), ConnectorHelper.getClientSecretField()), "OAuth Token", "");
        return List.of(oAuth, oAuthToken);
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getEndpointField(), ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public int clockSkewTolerance(ConnectorInfo connectorInfo) { return CLOCK_SKEW_TOLERANCE_SECS; }

    @Override
    public String getCategory() {
        return "Customer Success";
    }
    
    @Override
    public String getName() {
        return Constants.ZENDESK;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/zendesk.svg")
                .setDisplayName("Zendesk")
                .setBackgroundColor("#F1FDFF")
                .setHelpUrl(helpArticlesBaseUrl + "/360052204852-Zendesk-Setup");

    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        // For comments, we need to fetch comments for each ticket
        String plural = Constants.COMMENT.equalsIgnoreCase(request.getEntityName()) ? "tickets" : objName.get(request.getEntityName());
        // Resetting the endDate to skew tolerance clock
        Long skewEndDate = Instant.now().minus(CLOCK_SKEW_TOLERANCE_SECS, ChronoUnit.SECONDS).toEpochMilli();
        if (request.getWatermark().getEnd() > skewEndDate) {
            request.getWatermark().setEnd(skewEndDate);
        }

        if (request.getWatermark().getStart() > skewEndDate) {
            request.getWatermark().setStart(skewEndDate);
        }

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize,
                changeStream) -> {
            String url = changeStream;
            // If for first page, changeStream will be empty, in which case, begin the cursor iteration.
            if (StringUtils.isEmpty(url)) {
                url = format(request.getConnector().getEndpoint() + GET_BY_WATERMARK, plural, request.getWatermark().getStart()/1000, pageSize);
            }
            try {
                DataWithCursor dataWithCursor = get(url, request, plural);
                if (Constants.COMMENT.equalsIgnoreCase(request.getEntityName())) {
                    return fetchComments(dataWithCursor, request);
                } else {
                    return dataWithCursor;
                }
            } catch (RetriableException e) {
                try {
                    long sleep = 30000 * new Random().nextInt(5);
                    log.info("Retrying after {} milliseconds....", sleep);
                    Thread.sleep(sleep);
                    return get(url, request, plural);
                } catch (InterruptedException ex) {
                    log.error("Error putting thread to sleep. Retrying now");
                    return get(url, request, plural);
                }
            }
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();

        boolean ignorePageSize = NON_TICKET_OBJECTS.contains(request.getEntityName());

        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(request.getWatermark(),
                request.getWatermark().getChangeStream(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                pgSize, request.getWatermark().getLimit(), ignorePageSize);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private DataWithCursor fetchComments(DataWithCursor tickets, SyncRequest request) {
        DataWithCursor commentsCursor = new DataWithCursor(tickets.getPrevPageURL(), tickets.getNextPageURL(), new ArrayList<>());
        tickets.getData().forEach(ticket -> {
            if (!StringUtils.isBlank(ticket.getValueAsString("status")) && ticket.getValueAsString("status").equalsIgnoreCase("deleted")) {
                return;
            }
            String ticketId = ticket.getValue("id").toString();
            List<EntityData> commentsForTicket = new ArrayList<>();
            DataWithCursor comments = get(String.format(request.getConnector().getEndpoint() + COMMENT_GET_BY_WATERMARK, ticketId), request, "comments");
            commentsForTicket.addAll(comments.getData());
            while (comments.getNextPageURL() != null) {
                comments = get(comments.getNextPageURL(), request, "comments");
                commentsForTicket.addAll(comments.getData());
            }
            commentsForTicket.forEach(comment -> {
                comment.setId(ticketId + "_" + comment.getId());
                comment.addValue("ticket_id", ticketId);
                comment.addValue("attachmentDetails", comment.getValue("attachments"));
                comment.setLastModified(ticket.getLastModified());
                commentsCursor.getData().add(comment);
            });
        });
        return commentsCursor;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        String plural = objName.get(request.getEntityName());
        List<String> ids = getIds(request);
        List<EntityData> result = new ArrayList<>();
        List<List<String>> partitions = Lists.partition(ids, 100);
        for (List<String> partition : partitions) {
            if(Constants.COMMENT.equalsIgnoreCase(request.getEntityName())) {
                partition.forEach(id -> {
                    if(!id.contains("_")) {
                        return;
                    }
                    String[] parts = id.split("_");
                    String ticketId = parts[0];
                    DataWithCursor ticketCursor = new DataWithCursor("", "", List.of(new EntityData(Constants.TICKET).addValue("id", ticketId)));
                    DataWithCursor comments = fetchComments(ticketCursor, request);
                    result.addAll(comments.getData().stream()
                            .filter(comment -> comment.getId().equalsIgnoreCase(id))
                            .collect(Collectors.toList()));
                });
            } else {
                String idsAsString = String.join(", ",
                        partition.stream().map(i -> String.format("%s", i)).collect(Collectors.toList()));
                result.addAll(get(
                        String.format(request.getConnector().getEndpoint() + GET_BY_ID, plural, idsAsString), request, plural).getData());
            }
        }
        return result;
    }

    @Override
    public DocumentResponse getFileContents(DocumentRequest request) {
        DocumentResponse resp = new DocumentResponse(null, request.getFileMetadata());
        if (request.getFileMetadata() != null) {
            if (Constants.COMMENT.equalsIgnoreCase(request.getEntityName())) {
                Object attachments = request.getFileMetadata().getValue("attachmentDetails");
                if (attachments != null && attachments instanceof List && ((List) attachments).size() > 0) {
                    List attachmentsObj = (List) attachments;
                    for (Object attachment : attachmentsObj) {
                        if (attachment == null) continue;
                        String contentUrl = null;
                        log.debug("attachment {}", attachment);
                        try {
                            Map<String, String> properties = (Map) attachment;
                            if(properties.get("content_url") != null && properties.get("file_name") != null) {
                                contentUrl = properties.get("content_url").toString();
                                InputStream fileContents = new URL(contentUrl).openStream();
                                String fileName = sanitizeFileName(properties.get("file_name"));
                                resp.getContentMap().put(fileName, fileContents);
                                // Add syncariFileLink to attachmentDetails
                                String filePath = String.format("%s/%s_%s_%s_%s_%s", request.getConnector().getInstanceId(),
                                        request.getConnector().getId(), "comment", "attachments", request.getFileMetadata().getId(), fileName);
                                properties.put("syncariFileLink", filePath);
                            }
                        } catch (IOException e) {
                            log.error("exception {} for url {} and stacktrace {}", e.getMessage(), contentUrl, ExceptionUtils.getStackTrace(e));
                        }
                    }
                }
            }

        }

        return resp;
    }

    private String sanitizeFileName(String name) {
        if(name == null) return null;
        return StringUtils.normalizeSpace(name);
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        String plural = objName.get(request.getEntityName());
        List<EntityData> result = get(String.format(request.getConnector().getEndpoint() + GET_BY_WATERMARK, plural,
                request.getWatermark().getStart(), API_MAX_PAGESIZE), request, plural).getData();
        return Instant
                .parse(result.get(0).getValue(request.getEntitySchema().getWatermarkField().getApiName()).toString())
                .toEpochMilli();
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19177606013204";
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        String plural = objName.get(request.getEntityName());
        ZendeskRestClient restClient = getClient(getSingleJsonConfig(request.getEntityName()));
        SyncResponse response = new SyncResponse(true);
        List<EntityData> toBeCreated = request.getData().get(request.getConnector().getId());
        if (toBeCreated == null || toBeCreated.isEmpty()) {
            log.info("Nothing to be created for zendesk");
            return response;
        }
        log.info(format("Calling create for zendesk with size %s", toBeCreated.size()));
        for (EntityData data : toBeCreated) {
            // Zendesk requires ticket description to be set via a comment field.
            mapTicketDescriptionToComment(data);
            addCustomFields(request.getEntityName(), request.getEntitySchema(), data);
            if(request.getEntityName().equalsIgnoreCase(Constants.COMMENT)) {
                String ticketId = data.getValueAsString("ticket_id");
                if(StringUtils.isBlank(ticketId)) {
                    response.getResults().add(createErrorResult(data, "Missing ticket id"));
                    continue;
                }

                Map<String, Object> comment = new HashMap<>(data.getValues());
                comment.remove("ticket_id");

                List<String> attachments = (List<String>) data.getValue("attachments");
                List<String> filenames = (List<String>) data.getValue("filenames");
                if(attachments != null && filenames != null) {
                    try {
                        if(attachments.size() != filenames.size()) {
                            response.getResults().add(createErrorResult(data, "Provided list of attachments and filenames do not match"));
                            continue;
                        }
                        AtomicBoolean uploadError = new AtomicBoolean(false);
                        List<String> attachmentTokens = new ArrayList<>();
                        for(int i = 0; i < attachments.size(); i++) {
                            String attachment = attachments.get(i);
                            String filename = filenames.get(i);
                            if(StringUtils.isBlank(attachment) || StringUtils.isBlank(filename)) {
                                response.getResults().add(createErrorResult(data, String.format("Invalid attachment or filename provided - %s, %s", attachment, filename)));
                                uploadError.set(true);
                                break;
                            }
                            String attachmentToken = restClient.uploadAttachment(request, attachment, filename);
                            if (StringUtils.isBlank(attachmentToken)) {
                                response.getResults().add(createErrorResult(data, "Upload of attachment failed"));
                                uploadError.set(true);
                                break;
                            }
                            attachmentTokens.add(attachmentToken);
                        }
                        if(uploadError.get()) continue;
                        if(!attachmentTokens.isEmpty()) {
                            comment.put("uploads", attachmentTokens);
                        }
                    } catch (Exception e) {
                        response.getResults().add(createErrorResult(data, e.getMessage()));
                        continue;
                    }
                }

                Map<String, Object> payload = Map.of("ticket", Map.of("comment", comment));

                try {
                    ResponseEntity<String> d = restClient.put(String.format(request.getConnector().getEndpoint() + "/api/v2/tickets/%s.json", ticketId), payload,
                            request.getConnector().getAuthConfig());
                    String id = fetchId(d, data.getValues(), ticketId);
                    if (StringUtils.isBlank(id)) {
                        response.getResults().add(new Result(false, null, data.getSyncariEntityId()).setErrors(List.of(String.format("Missing comment id in create response - %s", d.getBody()))));
                    } else {
                        response.getResults().add(new Result(true, id, data.getSyncariEntityId()));
                    }
                } catch (Exception e) {
                    response.getResults().add(new Result(false, null, data.getSyncariEntityId()).setErrors(List.of(e.getMessage())));
                }
            } else {
                EntityData d = restClient.post(String.format(request.getConnector().getEndpoint() + CREATE, plural), Map.of(request.getEntityName(), toFieldValues(data)),
                        request.getConnector().getAuthConfig());
                response.getResults().add(new Result(true, d.getValue("id").toString(), data.getSyncariEntityId()));
            }
        }
        return response;
    }

    private Result createErrorResult(EntityData data, String errorMessage) {
        return new Result(false, null, data.getSyncariEntityId())
                .setErrors(List.of(errorMessage));
    }

    private String fetchId(ResponseEntity<String> response, Map<String, Object> payload, String ticketId) {
        try {
            Map<String, Object> responseMap = mapper.readValue(response.getBody(), Map.class);
            Map<String, Object> auditMap = (Map<String, Object>) responseMap.get("audit");
            if (auditMap != null) {
                String createdAtStr = (String) auditMap.get("created_at");
                if (createdAtStr != null) {
                    Instant createdAt = Instant.parse(createdAtStr);
                    Instant now = Instant.now();
                    long diffInSeconds = now.getEpochSecond() - createdAt.getEpochSecond();

                    // Check if the event happened within the last 30 seconds. Even if create is not successfull, we get 200 OK back from ZD with older events. Need additional checking to make sure the create succeeded
                    if (diffInSeconds >= 30)  {
                        log.error("The created_at timestamp is NOT within the last 30 seconds.");
                        throw new RuntimeException("The created_at timestamp is NOT within the last 30 seconds means the comment was not successfully created");
                    }
                } else {
                    throw new RuntimeException("created_at is null in zendesk response.");
                }
                List<Map<String, Object>> eventsList = (List<Map<String, Object>>) auditMap.get("events");
                if (eventsList == null || eventsList.isEmpty()) {
                    throw new RuntimeException("No events in zendesk response. Comment was not created successfully");
                }
                for (Map<String, Object> event : eventsList) {
                    Object eventId = event.get("id");
                    if (eventId != null ) {
                        String body = (String) payload.get("body");
                        String htmlBody = (String) payload.get("html_body");
                        if(StringUtils.isNotBlank(body) && event.containsKey("body") && body.equalsIgnoreCase((String)event.get("body"))) {
                            return ticketId + "_" + eventId;
                        }
                        if(StringUtils.isNotBlank(htmlBody) && event.containsKey("html_body") && htmlBody.equalsIgnoreCase((String)event.get("html_body"))) {
                            return ticketId + "_" + eventId;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private void mapTicketDescriptionToComment(EntityData data) {
        if (Constants.TICKET.equalsIgnoreCase(data.getName())){
            if(data.has("description")) {
                String description = data.getValueAsString("description");
                data.addValue("comment", Map.of("body", description));
                data.remove("description");
            }
            else if(data.has("subject")) {
                String subject = data.getValueAsString("subject");
                data.addValue("comment", Map.of("body", subject));
            }else{
                data.addValue("comment", Map.of("body", "N/A"));
            }
        }
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        if(request.getEntityName().equalsIgnoreCase(Constants.COMMENT)) {
            log.info("Update not supported for Comment entity");
            return new SyncResponse(false);
        }
            String plural = objName.get(request.getEntityName());
            List<EntityData> dataList = request.getData().get(request.getConnector().getId());
            SyncResponse updateResp = new SyncResponse(true);
            var partitioned = Lists.partition(dataList, 100);
            partitioned.forEach(partition -> {
                try {
                    List<Map<String, Object>> data = new ArrayList<>();
                    partition.stream().forEach(e -> {
                        addCustomFields(request.getEntityName(), request.getEntitySchema(), e);
                        e.addValue("id", Long.valueOf(e.getId()));
                        Map<String, Object> fieldValues = toFieldValues(e);
                        data.add(fieldValues);
                    });
                    String dataAsString = mapper.writeValueAsString(Map.of(plural,data));
                    ZendeskRestClient restClient = getClient(getBatchJsonConfig(plural));
                    ResponseEntity<String> resp = restClient.put(String.format(request.getConnector().getEndpoint() + UPDATE_MANY, plural), 
                            dataAsString,
                            request.getConnector().getAuthConfig());
                    restClient.pollForUpdateResponse(updateResp, resp.getBody(), request.getConnector().getAuthConfig(), partition);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
            return updateResp;
    }

    private Map<String, Object> toFieldValues(EntityData e) {
        Map<String,Object> fieldValues = new HashMap<>();
        e.getValues().forEach((apiName, value)->{
            fieldValues.put(toFieldId(apiName),value);
        });
        return fieldValues;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        if(request.getEntityName().equalsIgnoreCase(Constants.COMMENT)) {
            log.info("Delete not supported for Comment entity");
            return new SyncResponse(false);
        }
        String plural = objName.get(request.getEntityName());
        String idsAsString = String.join(", ",
                getIds(request).stream().map(i -> String.format("%s", i)).collect(Collectors.toList()));
        SyncariEntityDataRestClient restClient = getClient(getBatchJsonConfig(plural));
        restClient.delete(String.format(request.getConnector().getEndpoint() + DELETE_MANY, plural, idsAsString),
                request.getConnector().getAuthConfig());
        return new SyncResponse(true);
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        String entityName = request.getEntity();
        SyncariEntityDataRestClient restClient = getClient(getBatchJsonConfig(entityName + "_fields"));
        EntitySchema seedSchema = ZendeskSeed.getSeedEntitySchema(entityName);
        EntitySchema entity = seedSchema == null ? new EntitySchema(entityName, entityName) : seedSchema;
        if (NO_CUSTOM_FIELDS.contains(entityName)) {
            return Optional.of(entity);
        }
        List<EntityData> result = restClient.get(
                String.format(request.getConnector().getEndpoint() + GET_FIELDS, entityName),
                request.getConnector().getAuthConfig());
        result.forEach(r -> {
            String apiName = NON_TICKET_OBJECTS.contains(entityName) ? r.getValueAsString("key") : getTicketApiName(r);
            String displayName = r.getValue("title").toString();
            boolean required = r.getValueOptional("required").map(v -> Boolean.valueOf(v.toString())).orElse(false);
            AttributeSchema attributeSchema = createAttr(apiName, displayName, r.getValue("type").toString(), r.getValues());
            attributeSchema.setNillable(!required);
            if (r.getValue("removable") != null && Boolean.valueOf(r.getValue("removable").toString())) {
                attributeSchema.setCustom(true);
            }
            if(NON_TICKET_OBJECTS.contains(entityName)) {
                attributeSchema.setCustom(true);
            }

            if(!entity.hasField(apiName)) {
                entity.addField(attributeSchema);
            }
        });
        return Optional.of(entity);
    }

    private String getTicketApiName(EntityData r) {
        String type = r.getValueAsString("type");
        switch (type){
            case "subject": return "subject";
            case "description": return "description";
            case "priority": return "priority";
            case "status": return "status";
            case "group": return "group_id";
            case "assignee": return "assignee_id";
            case "tickettype": return "type";
            default:return "zd_"+r.getValueAsString("id");
        }
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> entities = new ArrayList<>();
        // Get custom fields on Standard objects
        objName.forEach((k, v) -> {
            DescribeRequest req = new DescribeRequest(request.getConnector(), k);
            entities.add(describe(req).get());
        });

        // Get Custom objects
        try {
            SyncariEntityDataRestClient restClient = getClient(getBatchJsonConfig("data"));
            List<EntityData> result = restClient.get(request.getConnector().getEndpoint() + GET_CUSTOM_OBJECTS,
                    request.getConnector().getAuthConfig());
            result.forEach(r -> {
                String apiName = r.getValue("key").toString();
                EntitySchema ent = new EntitySchema(apiName, apiName);
                Map schema = (Map) r.getValue("schema");
                Map<String, Map> properties = (Map<String, Map>) schema.get("properties");
                for (Entry<String, Map> entry : properties.entrySet()) {
                    AttributeSchema attr = createAttr(entry.getKey(), entry.getKey(),
                            entry.getValue().get("type").toString(), r.getValues());
                    ent.addField(attr);
                }
                AttributeSchema customObjectId = createAttr("zendeskId", "Zendesk ID","string", r.getValues());
                customObjectId.setIdField(true);
                ent.addField(customObjectId);
                entities.add(ent);
            });
        } catch (HttpClientErrorException e) {
            if (HttpStatus.NOT_FOUND == e.getStatusCode()) {
                log.warn(format("Sunshine not enabled for %s", request.getConnector().getName()));
                return entities;
            }
            throw e;
        } catch (NonRetriableException e) {
            if (ErrorCodes.BAD_ENDPOINT.name().equalsIgnoreCase(e.getErrorCode())) {
                log.warn(format("Sunshine not enabled for %s", request.getConnector().getName()));
                return entities;
            }
            throw e;
        }

        return entities;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""),mapper);
        String url = String.format(request.getConnector().getEndpoint() + GET_FIELDS, request.getEntityName());

        Map<String, Object> data = new HashMap<>();
        data.put("title", request.getSchema().getApiName());
        data.put("type", request.getSchema().getDataType());

        try {

            String valueAsString = mapper.writeValueAsString(data);
            String body = String.format("{ \"%s_field\": %s }", request.getEntityName(), valueAsString);

            ResponseEntity<String> responseEntity = restClient.getTemplate().exchange(url, HttpMethod.POST,
                    new HttpEntity(body, restClient.getHeaders(request.getConnector().getAuthConfig())), String.class);
            JsonNode response = mapper.readTree(responseEntity.getBody());
            JsonNode fields = response.findValue(request.getEntityName() + "_field");
            request.getSchema().setExternalId(fields.findValue("id").asText());
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }

        return request.getSchema();
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""),mapper);
        String url = String.format(request.getConnector().getEndpoint() + DELETE_FIELDS, request.getEntityName(),
                request.getExternalFieldId());

        ResponseEntity<String> responseEntity = restClient.getTemplate().exchange(url, HttpMethod.DELETE,
                new HttpEntity(restClient.getHeaders(request.getConnector().getAuthConfig())), String.class);
        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw new NonRetriableException(responseEntity.getStatusCode().getReasonPhrase(), responseEntity.getBody(),
                    String.valueOf(responseEntity.getStatusCode().value()));
        }
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "/oauth/authorizations/new?response_type=code&redirect_uri={{redirect_uri}}&client_id={{client_id}}&scope=read write";
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        Map<String, String> map = Map.of(
                DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.REFRESH_TOKEN,
                DefaultAuthTokenHandler.REFRESH_TOKEN, config.getRefreshToken(),
                DefaultAuthTokenHandler.CLIENT_ID, config.getClientId(), 
                DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret(),
                "scope", "read write"
        );
        
        return tokenHandler.refreshToken(config, connector.getEndpoint() + "/oauth/tokens", map);
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        Map<String, String> map = Map.of("grant_type", "authorization_code", "code", oAuthRequest.getCode(), "client_id",
                oAuthRequest.getConfig().getClientId(), "client_secret", oAuthRequest.getConfig().getClientSecret(),
                "redirect_uri", oAuthRequest.getRedirectUri(), "scope", "read");

        return tokenHandler.getAccessToken(oAuthRequest.getEndpoint() + "/oauth/tokens", map);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            DescribeAllRequest request = new DescribeAllRequest(config,
                    objName.keySet().stream().collect(Collectors.toList()));
            describeAll(request);
            log.info(format("Successfully authenticated zendesk connection for %s", config.getName()));
        } catch (Exception e) {
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    private AttributeSchema createAttr(String apiName, String displayName, String type, Map values) {
        AttributeSchema attr = new AttributeSchema();
        attr.setApiName(apiName);
        attr.setDisplayName(displayName);
        attr.setDataType(type);
        if(values.containsKey("relationship_target_type") && "lookup".equalsIgnoreCase(type)) {
            Object target = values.get("relationship_target_type");
            if(target != null) {
                attr.setDataType("reference");
                attr.setReferenceTo(target.toString().replace("zen:",""));
                attr.setReferenceTargetField("id");
            }
        }
        if(type != null && TEXT_FIELDS.contains(type.toLowerCase())) {
            attr.setLength(MAX_LEN);
        }
        return attr;
    }

    private List<String> getIds(SyncRequest request) {
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        return entityList.stream().map(e -> e.getId()).collect(Collectors.toList());
    }

    private DataWithCursor get(String url, SyncRequest request, String plural) {
        ZendeskRestClient restClient = getClient(getBatchJsonConfig(plural));
        DataWithCursor result = restClient.getDataWithCursor(url, request.getConnector().getAuthConfig());
        result.getData().forEach(r -> {
            r.setId(r.getValue("id").toString());
            r.setName(request.getEntityName());
            if (StringUtils.isBlank(r.getValueAsString("updated_at"))) {
                r.setLastModified(ZonedDateTime.parse(r.getValueAsString("created_at")).toEpochSecond() * 1000);
            } else {
                r.setLastModified(ZonedDateTime.parse(r.getValueAsString("updated_at")).toEpochSecond() * 1000);
            }
            r.setDeleted("deleted".equals(r.getValue("status")));
            r.setConnectorId(request.getConnector().getId());
            retrieveCustomFields(request.getEntityName(), r);
            updateFieldNames(r, request.getEntitySchema());
        });
        return result;
    }

    private void updateFieldNames(EntityData record, EntitySchema entitySchema) {
        entitySchema.getAttributes().forEach(attribute ->{
            String key = toFieldId(attribute.getApiName());
            //only when the record has the id-based key,we want to change the key to zd_ prefix based key
            //otherwise, we will endup overwriting a custom field value
            if(record.has(key)) {
                record.addValue(attribute.getApiName(), record.getValues().remove(key));
            }
        });
    }

    private void retrieveCustomFields(String entityName, EntityData result){
        var customFieldValues = result.getValue("custom_fields");
        if(customFieldValues instanceof List){
            ((List) customFieldValues).forEach(customField ->{
                Map fieldValue = (Map) customField;
                result.addValue("zd_"+fieldValue.get("id"), fieldValue.get("value"));
            });
        }
        var entitySpecificCustomFieldValues = result.getValue(entityName + "_fields");
        if(entitySpecificCustomFieldValues instanceof Map) {
            ((Map) entitySpecificCustomFieldValues).forEach((k, v) -> result.addValue(k.toString(), v));
        }
    }

    private void addCustomFields(String entityName, EntitySchema schema, EntityData data){
        List<AttributeSchema> customFields = schema.getCustomFields();

        Map<String, Object> customFieldValues = new HashMap<>();
        customFields.forEach(customField -> {
            if(data.has(customField.getApiName())){
                String fieldId = toFieldId(customField.getApiName());
                if(customField.getDataType().equalsIgnoreCase("date")) {
                    Date date = (Date) data.getValue(customField.getApiName());
                    customFieldValues.put(fieldId, DateUtil.format(date, DateUtil.dateOnlyFormat));
                }
                else {
                    customFieldValues.put(fieldId, data.getValue(customField.getApiName()));
                }
                data.remove(customField.getApiName());
            }
        });
        if(!customFieldValues.isEmpty()) {
            if("ticket".equalsIgnoreCase(entityName)) {
                //tickets have a special custom_fields field
                data.addValue("custom_fields", customFieldValues);
            }else{
                //org has organization_fields
                data.addValue(entityName+"_fields", customFieldValues);
            }
        }
    }

    private String toFieldId(String apiName) {
        return apiName.replaceFirst("zd_","");
    }

    private JsonParserConfig getBatchJsonConfig(String plural) {
        return new JsonParserConfig(plural, plural + "[{i}]", null, "id", true, plural + "[{i}].__key__");
    }

    private JsonParserConfig getSingleJsonConfig(String plural) {
        return new JsonParserConfig(plural, plural, null, "id", true, plural + ".__key__");
    }

    public ZendeskRestClient getClient(JsonParserConfig config) {
        return new ZendeskRestClient(config, mapper);
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in zendesk yet");
    }

}
