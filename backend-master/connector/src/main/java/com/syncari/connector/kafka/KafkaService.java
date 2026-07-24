package com.syncari.connector.kafka;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component(Constants.KAFKA)
public class KafkaService implements MetadataService, SynapseInfoService, CommonDataService, OauthAuthenticationService {

    private static final int API_MAX_PAGESIZE = 2000;
    public static final String ID = "id";
    public static final String HEADERS = "headers";
    @Autowired
    KafkaClient kafkaClient;

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            kafkaClient.listTopics(config);
        } catch (Exception e) {
            log.error("Encountered exception during Kafka testConnection: {}", e.getMessage(), e);
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            List<String> errors = new ArrayList<>();
            errors.add(e.getMessage());
            response.setErrors(errors);
            response.setMessage("Connection failed. Please verify the Kafka configuration and retry.");
        }
        return response;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest describeRequest) {
        String entityName = describeRequest.getEntity();
        ConnectorInfo config = describeRequest.getConnector();
        try {
            EntitySchema schema = kafkaClient.getSchema(config, entityName);
            if (schema != null) {
                addFabricatedFields(schema);
                return Optional.of(schema);
            }
        } catch (Exception e) {
            log.error("Error describing Kafka entity {}: {}", entityName, e.getMessage(), e);
        }
        
        return Optional.empty();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest describeAllRequest) {
        ConnectorInfo config = describeAllRequest.getConnector();
        List<EntitySchema> schemas = new ArrayList<>();
        try {
            Map topics = kafkaClient.listTopics(config);

            for (Object topic : topics.keySet()) {
                Optional<EntitySchema> schemaOpt = describe(new DescribeRequest(config, topic.toString()));
                schemaOpt.ifPresent(schemas::add);
            }
        } catch (Exception e) {
            log.error("Error describing Kafka entities {}", ExceptionUtils.getStackTrace(e));
        }

        return schemas;
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        List<AuthMetadata> authTypes = new ArrayList<>();

        // SASL/PLAIN authentication
        List<AuthField> saslAuthFields = new ArrayList<>();
        AuthField usernameField = new AuthField();
        usernameField.setName("userName");
        usernameField.setLabel("Username");
        usernameField.setDataType("Text");
        usernameField.setRequired(true);
        usernameField.setDescription("SASL username");
        saslAuthFields.add(usernameField);
        
        AuthField passwordField = new AuthField();
        passwordField.setName("password");
        passwordField.setLabel("Password");
        passwordField.setDataType("Password");
        passwordField.setRequired(true);
        passwordField.setDescription("SASL password");
        saslAuthFields.add(passwordField);
        
        AuthMetadata saslAuth = new AuthMetadata(AuthType.UserPassword, saslAuthFields, "SASL", "SASL Authentication");
        authTypes.add(saslAuth);
        
        return authTypes;
    }

    @Override
    public List<AuthField> getConfigureFields() {
        List<AuthField> configureFields = new ArrayList<>();
        AuthField bootstrapServersField = new AuthField();
        bootstrapServersField.setName(KafkaClient.BOOTSTRAP_SERVERS);
        bootstrapServersField.setLabel("Bootstrap Servers");
        bootstrapServersField.setDataType("Text");
        bootstrapServersField.setRequired(true);
        bootstrapServersField.setDescription("Comma-separated list of host:port pairs");
        configureFields.add(bootstrapServersField);

        AuthField topicField = new AuthField();
        topicField.setName(KafkaClient.TOPIC);
        topicField.setLabel("Topic(s)");
        topicField.setDataType("Text");
        topicField.setRequired(false);
        topicField.setDescription("Kafka topic to consume from. Use * or leave empty for all topics or a comma seperated topic list for a subset.");
        configureFields.add(topicField);

        return configureFields;
    }

    @Override
    public String getName() {
        return "kafka";
    }

    @Override
    public String getCategory() {
        return "Messaging";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata()
                .setIconPath("/assets/icons/logos/kafka.svg")
                .setDisplayName("Apache Kafka")
                .setBackgroundColor("#000000")
                .setHelpUrl(helpArticlesBaseUrl + SYNAPSE_COMING_SOON_ARTICLE);
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }
    
    @Override
    public List<Capability> getCapabilities() {
        return List.of(
                Capability.getByWatermark,
                Capability.schemaCreateField,
                Capability.schemaEditInSyncari
        );
    }

    @Override
    public boolean isSink() {
        // Kafka synapse is read-only, not a sink
        return false;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize,
                                                                               cursor) -> {
            DataWithCursor data = kafkaClient.getData(request.getConnector(), cursor, pageSize, request.getEntityName(),
                    getConsumerId(request.getPipeline()));
            wm.setChangeStream(data.getNextPageURL());
            wm.setStreamState(new StreamState().setPreviousCursor(data.getNextPageURL()));
            if(data.getData().size() < pageSize) {
                // If the fetched results are less than page size it means we drained all records
                return new DataWithCursor(data.getPrevPageURL(), null, data.getData());
            }
            return data;
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();
        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(request.getWatermark(),
                request.getWatermark().getChangeStream(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                pgSize, request.getWatermark().getLimit(), true);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        // For Kafka, we don't have a reliable way to get the first message's timestamp
        // without consuming messages, so return the current time
        return Instant.EPOCH.toEpochMilli();
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        return kafkaClient.getDataByIds(request.getConnector(), request.getIds(), request.getEntityName(),
                getConsumerId(request.getPipeline()));
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        kafkaClient.create(request.getConnector(), getConsumerId(request.getPipeline()), entityList, request.getEntityName());
        SyncResponse syncResponse = new SyncResponse();
        syncResponse.setSuccess(true);
        return syncResponse;
    }

    @Override
    public void close(CloseContext context) {
        kafkaClient.commit(context.getConnectorInfo(), context.getWatermarkInfo(), context.getEntityName(), getConsumerId(context.getPipeline()));
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        throw new NonRetriableException(
            ErrorCodes.NOT_SUPPORTED, 
            "Updating records is not supported for Kafka synapse (read-only)", 
            HttpStatus.METHOD_NOT_ALLOWED.toString()
        );
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        throw new NonRetriableException(
            ErrorCodes.NOT_SUPPORTED, 
            "Deleting records is not supported for Kafka synapse (read-only)", 
            HttpStatus.METHOD_NOT_ALLOWED.toString()
        );
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        return null;
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        return null;
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return null;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new NonRetriableException(
                ErrorCodes.NOT_SUPPORTED,
                "Creating objects is not supported for Kafka synapse",
                HttpStatus.METHOD_NOT_ALLOWED.toString()
        );
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new NonRetriableException(
                ErrorCodes.NOT_SUPPORTED,
                "Creating fields is not supported for Kafka synapse",
                HttpStatus.METHOD_NOT_ALLOWED.toString()
        );
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new NonRetriableException(
                ErrorCodes.NOT_SUPPORTED,
                "Deleting fields is not supported for Kafka synapse",
                HttpStatus.METHOD_NOT_ALLOWED.toString()
        );
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    private static void addFabricatedFields(EntitySchema schema) {
        if(!schema.hasField(Constants.SYNCARI_FABRICATED_WATERMARKFIELD)) {
            final AttributeSchema watermarkField = new AttributeSchema(Constants.SYNCARI_FABRICATED_WATERMARKFIELD, "datetime")
                    .setDisplayName("Syncari Fabricated Watermark Field")
                    .setUpdatedAtField(true)
                    .setWatermarkField(true)
                    .setSystem(true);
            schema.addField(watermarkField);
        } else {
            AttributeSchema wmField = schema.getField(Constants.SYNCARI_FABRICATED_WATERMARKFIELD).get();
            wmField.setWatermarkField(true);
            wmField.setSystem(true);
            wmField.setUpdatedAtField(true);
            wmField.setDataType("datetime");
        }
        if(!schema.hasField(ID)) {
            final AttributeSchema idField = new AttributeSchema(ID, "string")
                    .setDisplayName("Id")
                    .setIdField(true)
                    .setSystem(true);
            schema.addField(idField);
        } else {
            AttributeSchema idField = schema.getField(ID).get();
            idField.setIdField(true);
            idField.setSystem(true);
        }
        if(!schema.hasField(HEADERS)) {
            final AttributeSchema headerField = new AttributeSchema(HEADERS, "object")
                    .setDisplayName("Headers");
            schema.addField(headerField);
        } else {
            schema.getField(HEADERS).get().setDataType("object");
        }
    }

    private String getConsumerId(Pipeline pipeline) {
        return pipeline.getInstanceId()+"_"+pipeline.getDraftStatus()+"_"+pipeline.getApiName();
    }

}