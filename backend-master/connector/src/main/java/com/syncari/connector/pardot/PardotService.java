package com.syncari.connector.pardot;

import static com.syncari.utils.I18n.i18n;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.darksci.pardot.api.auth.*;
import javax.annotation.PostConstruct;

import com.darksci.pardot.api.ConfigurationBuilder;
import com.darksci.pardot.api.PardotClient;
import com.darksci.pardot.api.config.Configuration;
import com.darksci.pardot.api.response.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.google.common.collect.Lists;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.ListBasedIterator;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;

import com.syncari.utils.Sleeper;
import org.apache.commons.collections.CollectionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.PARDOT)
public class PardotService implements AuthenticationService, CommonDataService, MetadataService, SynapseInfoService {
    
    @Autowired
    DateUtil dateUtil;

    public static final String BUSINESS_ID_AUTH_FIELD = "businessId";
    public static final String TIME_ZONE_ID = "timeZoneId";

    public ObjectMapper mapper;

    @PostConstruct
    public void init() {
        mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwdClientIdSecret());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField businessID = new AuthField();
        businessID.setDataType("text");
        businessID.setName(BUSINESS_ID_AUTH_FIELD);
        businessID.setLabel(i18n("pardot_business_label"));
        businessID.setHelpSummary(i18n("pardot_business_help"));
        AuthField timeZone = new AuthField();
        timeZone.setDataType("text");
        timeZone.setName(TIME_ZONE_ID);
        timeZone.setLabel(i18n("pardot_timezone_label"));
        timeZone.setHelpSummary(i18n("pardot_timezone_help"));
        return List.of(businessID, timeZone, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public boolean validate(ConnectorInfo connector) {
        String zoneId = connector.getMetaConfig().getOrDefault(PardotService.TIME_ZONE_ID, "").toString();
        try {
            ZoneId z = ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            throw new RuntimeException(i18n("pardot_invalid_timezone_id"));
        }
        return true;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        List<AttributeSchema> attributes = Lists.newArrayList();
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            // noop
            attributes = getClient(config).getProspectCustomFields();
        } catch (Exception e) {
            log.error("Failed to connect due to {} ", e.getMessage(), e);
            handleAuthenticationErrorMessage(response, e);
            return response;
        }
        return new TestConnectionResponse();
    }

    @Override
    public String getCategory() {
        return "Accounting";
    }

    @Override
    public String getName() {
        return Constants.PARDOT;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/pardot.svg")
                .setDisplayName("Pardot")
                .setBackgroundColor("#F0FBFF")
                .setHelpUrl(helpArticlesBaseUrl + "/360056162551-Pardot-Setup");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19208736084244";
    }

    public int getPageSize() {
        return PardotV4Client.PAGE_SIZE;
    }

    @Override
    public FetchResponse getDeletedByWatermark(SyncRequest request) {
        return ConnectorHelper.withHttpErrorHandling(() -> {
            PardotV4Client pardotV4Helper = getClient(request.getConnector());
            List<EntityData> result = pardotV4Helper.queryDeletedByFilter(request, getPageSize(), 0);
            return new FetchResponse(request.getWatermark(), new ListBasedIterator(result,request.getWatermark()));
        });
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        return ConnectorHelper.withHttpErrorHandling(() -> {
            PardotV4Client pardotV4Helper = getClient(request.getConnector());
            Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
                List<EntityData> result = pardotV4Helper.queryByFilter(request, getPageSize(), offset);
                return Pair.of(Long.valueOf(result.size()), result.stream());
            };
            PardotIterator iterator = new PardotIterator(request.getWatermark(),
                    request.getWatermark().getOffset(), generator, new ArrayList<>(),
                    request.getEntitySchema().getWatermarkField(), getPageSize(),
                    request.getWatermark().getLimit());
            return new FetchResponse(request.getWatermark(), iterator);
        });
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        return ConnectorHelper.withHttpErrorHandling(() -> {
            return getClient(request.getConnector()).queryByIds(request);
        });
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return ConnectorHelper.withHttpErrorHandling(() -> {
            // Campaign and prospectAccount objects do not have updated_at exposed in the API calls. So just send 0 to begin from epoch.
            if (PardotV4Client.CAMPAIGN.equalsIgnoreCase(request.getEntityName()) || 
                PardotV4Client.PROSPECT_ACCOUNT.equalsIgnoreCase(request.getEntityName())) {
                return 0L;
            }
            List<EntityData> firstRecord = getClient(request.getConnector()).queryByFilter(request, 1, 0);
            return (firstRecord.size() > 0) ? firstRecord.get(0).getLastModified() : 0L;
        });
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        return ConnectorHelper.withHttpErrorHandling(() -> {
            return getClient(request.getConnector()).create(request);
        });
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return ConnectorHelper.withHttpErrorHandling(() -> {
            return getClient(request.getConnector()).update(request);
        });
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return ConnectorHelper.withHttpErrorHandling(() -> {
            return getClient(request.getConnector()).delete(request);
        });
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        return ConnectorHelper.withHttpErrorHandling(() -> {
            List<EntitySchema> entitySchemas = describeAll(
                new DescribeAllRequest(request.getConnector(), List.of(request.getEntity())));
            return (CollectionUtils.isNotEmpty(entitySchemas)) ? Optional.of(entitySchemas.get(0)) : Optional.empty();
        });
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        return ConnectorHelper.withBackoffAndErrorHandling(() -> {
            List<EntitySchema> entitySchemas = Lists.newArrayList();
            if (CollectionUtils.isNotEmpty(request.getEntities())) {
                entitySchemas = getClient(request.getConnector()).getSeededEntitySchemas().stream()
                    .filter(s -> request.getEntities().contains(s.getApiName())).collect(Collectors.toList());
            } else {
                entitySchemas = getClient(request.getConnector()).getSeededEntitySchemas();
            }
            entitySchemas.forEach(x -> {
                x.getAttributes().stream()
                        .forEach(i -> {
                            if (i.isIdField()) {
                                i.setNillable(false);
                                i.setUpdateable(false);
                                i.setUnique(true);
                                i.setSystem(true);
                            }
                            if (i.isWatermarkField()) {
                                i.setNillable(false);
                                i.setUpdateable(false);
                                i.setSystem(true);
                            }
                        });
                if (PardotV4Client.PROSPECT.equalsIgnoreCase(x.getApiName())) {
                    x.addFields(getClient(request.getConnector()).getProspectCustomFields());
                }
            });
            return entitySchemas;
        }, getSleeper());
    }

    public Sleeper getSleeper() {
        return (int minBackoffMillis, int maxBackOffMillis) -> minBackoffMillis + new Random().nextInt(maxBackOffMillis - minBackoffMillis);
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support delete field");
    }

    public PardotV4Client getClient(ConnectorInfo connector) {
        if (connector == null) return null;
        return PardotV4Client.builder().client(getPardotClient(connector)).connector(connector).dateUtil(dateUtil)
                .mapper(mapper).build();
    }

    public PardotClient getPardotClient(ConnectorInfo connector) {
        final ConfigurationBuilder builder = Configuration.newBuilder()
                .withSsoLogin(connector.getAuthConfig().getUserName(), connector.getAuthConfig().getPassword(),
                    connector.getAuthConfig().getClientId(), connector.getAuthConfig().getClientSecret(),
                    (connector.getMetaConfig() == null) ? "" : connector.getMetaConfig().getOrDefault(BUSINESS_ID_AUTH_FIELD, "").toString())
                .withApiVersion4();
        return new PardotClient(builder);
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in " + this.getUIMetadata().getDisplayName() + " yet");
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }
}

class PardotIterator extends DefaultDataIterator {

    public static final int MAX_RECORDS_LIMIT = 2000;
    public PardotIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long,
            Pair<Long, Stream<EntityData>>> generator, List<EntityData> data, AttributeSchema watermarkField,int pageSize, int maxRecords) {
        super(baseWatermark, offset, generator, data, watermarkField,pageSize,maxRecords > 0 ? maxRecords : MAX_RECORDS_LIMIT);
    }

    protected long nextOffset(Pair<Long, Stream<EntityData>> results, List<EntityData> data) {
        // if no data is retrieved meaning the window is exhausted - reset the offset else add increment offset by data size
        return data.isEmpty() || isLastPage() ? 0 : offset + data.size();
    }

    public List<EntityData> next(){
        var temp = data;
        if (!data.isEmpty()) {
            EntityData entityData = data.get(data.size() - 1);
            lastWatermark = getWatermarkValue(entityData);
            super.baseWatermark.setStart(lastWatermark);
            totalRecordsFetched+=data.size();
        }
        data = new ArrayList<>();

        return temp;
    }

    @Override
    public long getLastOffset() {
        return offset;
    }
}
