package com.syncari.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.PendoFeedbackRestClient;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.seed.PendoFeedbackSeed;
import com.syncari.connector.service.seed.PendoSeed;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Slf4j
@Component(Constants.PENDO_FEEDBACK)
public class PendoFeedbackService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService {
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil dateUtil;

    public static final String PENDO_URL = "https://api.feedback.us.pendo.io/";
    public static final String WM_END_POINT = "%s?start=%s&order_dir=asc&order_by=last_seen";
    public static final String PENDO_GET_BY_ID = "%s/%s";
    public static final Integer PENDO_30_MIN_CLOCK_SKEW_IN_SEC = 30*60;
    public static final List<String> getByIdEntities = List.of("account", "vote", "feature");

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getApiKey());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "Product Usage";
    }

    @Override
    public String getName() {
        return Constants.PENDO_FEEDBACK;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/pendo.svg")
                .setDisplayName("Pendo Feedback")
                .setBackgroundColor("#FFF6F9")
                .setHelpUrl(helpArticlesBaseUrl + "/20479356576788-Pendo-Feedback-Synapse-Setup");
    }

    @Override
    public int clockSkewTolerance(ConnectorInfo connectorInfo) {
        return PENDO_30_MIN_CLOCK_SKEW_IN_SEC;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();
        try {
            SyncRequest syncRequest = new SyncRequest().setConnector(config).setEntitySchema(new EntitySchema("account"));
            EntityData entity = new EntityData("account").setId("123");
            syncRequest.getData().put(config.getId(), List.of(entity));
            getByIds(syncRequest);
        } catch (Exception e) {
            result.setCode(e.getMessage());
            result.setErrors(List.of(e.getMessage()));
            result.setMessage(e.getMessage());
        }
        return result;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator = (wm, pageSize, lastProcessedWM) -> {
            long currentStart = wm.getOffset();
            String entityName = request.getEntitySchema().getPluralName();
            String requestBody = String.format(WM_END_POINT, entityName, currentStart);
            List<EntityData> result = new ArrayList<>();
            List<EntityData> response = getClient().get(PENDO_URL + requestBody, request.getConnector().getAuthConfig(), request);
            result.addAll(response);
            return new DataWithOffset(currentStart, currentStart + result.size(), result, new ArrayList<>());
        };

        DefaultDataOffsetIterator iterator = new DefaultDataOffsetIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), 100, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> records = new ArrayList<>();
        if(!getByIdEntities.contains(request.getEntityName().toLowerCase())) {
            throw  new RuntimeException("Get by ids not supported for "+request.getEntityName());
        }
        for(Map.Entry<String, List<EntityData>> entry : request.getData().entrySet()){
            for(EntityData ed: entry.getValue()) {
                String getByIdUrl = PENDO_URL + String.format(PENDO_GET_BY_ID, request.getEntityName()+"s", ed.getId());
                getById(getByIdUrl, request).ifPresent(record -> {
                    records.add(record);
                });
            };
        };
        return records;
    }

    protected Optional<EntityData> getById(String url,  SyncRequest request) {
        try {
            Optional<ResponseEntity<String>> response = Optional.empty();
            response =  Optional.of(getClient().getResponse(url, request.getConnector().getAuthConfig()));
            if(response.isEmpty()) return Optional.empty();
            ReadContext context = JsonPath.parse(response.get().getBody());
            Map row = context.json();
            return Optional.of(getClient().extractRow(request, row, request.getConnector().getAuthConfig()));
        } catch (NonRetriableException | RetriableException e) {
            if(ErrorCodes.BAD_ENDPOINT.name().equals(e.getErrorCode()) || ErrorCodes.DATA_NOT_FOUND.name().equals(e.getErrorCode())
                    || "410".equals(e.getStatusCode())){
                log.warn("Skipping {} record corresponding to url {} with error {}", request.getEntityName(), url, e.getErrorCode());
            }else {
                throw e;
            }
        }
        return Optional.empty();
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        throw new RuntimeException("create not supported for pendo feedback");
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        throw new RuntimeException("update not supported for this pendo feedback");
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        throw new RuntimeException("delete not supported for pendo feedback");
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        return Optional.ofNullable(PendoFeedbackSeed.getSeedEntitySchema(request.getEntity()));
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        Set<String> entities = PendoFeedbackSeed.objPluralMap.keySet();
        List<EntitySchema> schemaList = new ArrayList<>();
        entities.forEach(e -> {
            EntitySchema entity = PendoFeedbackSeed.getSeedEntitySchema(e);
            schemaList.add(entity);
        });
        return schemaList;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support delete field");
    }

    private JsonParserConfig getSingleJsonConfig() {
        return new JsonParserConfig(null, null, null, "id", true, null);
    }

    public PendoFeedbackRestClient getClient() {
        return new PendoFeedbackRestClient(getSingleJsonConfig(), mapper, dateUtil);
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

    @Override
    public boolean isSink() {
        return false;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "20479115580308";
    }
}
