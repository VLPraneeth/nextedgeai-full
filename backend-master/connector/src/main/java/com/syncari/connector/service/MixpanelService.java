package com.syncari.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component(Constants.MIXPANEL)
public class MixpanelService implements CommonDataService, MetadataService, SynapseInfoService {
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getEndpointField(), ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "Accounting";
    }

    @Override
    public String getName() {
        return Constants.MIXPANEL;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/mixpanel.svg")
                .setDisplayName("Mixpanel")
                .setBackgroundColor("#EDF3FF")
                .setHelpUrl(helpArticlesBaseUrl + SYNAPSE_COMING_SOON_ARTICLE);
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        return null;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        return null;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
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
        return new SyncResponse(true);
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        return Optional.empty();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        return null;
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
    public String getDisabledMessage() {
        return "Coming Soon";
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    SyncariEntityDataRestClient getClient(JsonParserConfig config) {
        return new SyncariEntityDataRestClient(config,mapper);
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
}
