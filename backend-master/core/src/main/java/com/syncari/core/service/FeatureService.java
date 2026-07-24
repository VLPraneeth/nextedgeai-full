package com.syncari.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.AsyncJob;
import com.syncari.core.model.Event;
import com.syncari.core.model.Feature;
import com.syncari.core.model.misc.FeatureStage;
import com.syncari.core.model.misc.FeatureStatus;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.FeatureRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class FeatureService {
    @Autowired
    FeatureRepo featureRepo;
    @Autowired
    AsyncJobService asyncJobService;

    @Autowired
    InsightsService insightsService;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    Publisher publisher;

    @Autowired
    ConnectorService connectorService;
    
    LoadingCache<String, List<Feature>> featureCache = CacheBuilder.newBuilder().maximumSize(100000).expireAfterWrite(15, TimeUnit.MINUTES)
            .build(new CacheLoader<>() {
                @Override
                public List<Feature> load(String syncariId) {
                    return featureRepo.findAll();
                }
            });

    public boolean isEnabled(Features feature, boolean useCache) {
        if(feature == null) return false;
        if (useCache) {
            return featureCache.getUnchecked(SyncariContext.getSyncariId()).stream()
                    .filter(f -> f.name.equals(feature.name()))
                    .findFirst().map(f -> f.isActive()).orElse(false);
        }
        return isEnabled(feature);
    }

    public boolean isEnabled(Features feature) {
        if(feature == null) return false;
        Optional<Feature> byName = featureRepo.findByName(feature.name());
        return byName.map(f -> f.status == FeatureStatus.active).orElse(false);
    }

    public Feature getFeatureByName(Features feature) {
        return featureRepo.findByName(feature.name())
                .orElseThrow(() -> new NotFoundException(Feature.class, "name", feature.name()));
    }

    public Feature getFeatureByName(Features feature, boolean useCache) {

        if (useCache) {
            var optFeature = featureCache.getUnchecked(SyncariContext.getSyncariId()).stream()
                    .filter(f -> f.name.equals(feature.name()))
                    .findFirst();
            if(optFeature.isPresent()) return optFeature.get();
        }

        return getFeatureByName(feature);
    }

    public Feature getOrCreateFeatureByName(Features feature) {
        // getFeatureByName of else initialize it in inactive state
        return featureRepo.findByName(feature.name())
                .orElseGet(() -> setStatus(feature, FeatureStatus.inactive));
    }
    
    public Feature enableFeature(Features feature) {
        // TODO: handle this better as part of feature manager
        switch (feature) {
            case Insights:
                validateCondition(isEnabled(Features.Insights),i18n("insights_already_enabled"));
                if (!isEnabled(Features.Datastore)){
                    Event event = new Event().setType(EventTypes.ENABLE_INSIGHTS).setDetails(Map.of("syncariid", SyncariContext.getSyncariId(), "enabledBy", SyncariContext.getUser().getEmail()));
                    Message msg = new Message(SyncariContext.getSyncariId(), event);
                    try {
                        String eventString = mapper.writeValueAsString(msg);
                        log.info(String.format("Sending Message: %s", eventString));
                        publisher.publishToGenericQueue(eventString);
                    } catch (JsonProcessingException e) {
                        log.error("Exception occurred while sending message {}", ExceptionUtils.getStackTrace(e));
                    }
                    return setStatus(feature, FeatureStatus.activating);
                }
                break;
            case InsightsProvider:
                validateCondition(isEnabled(Features.InsightsProvider),i18n("insights_already_enabled"));
                if (!isEnabled(Features.Datastore) || (!isEnabled(Features.InsightsProvider))){
                    Event event = new Event().setType(EventTypes.ENABLE_INSIGHTS_PROVIDER).setDetails(Map.of("syncariid", SyncariContext.getSyncariId(), "enabledBy", SyncariContext.getUser().getEmail()));
                    Message msg = new Message(SyncariContext.getSyncariId(), event);
                    try {
                        String eventString = mapper.writeValueAsString(msg);
                        log.info(String.format("Sending Message: %s", eventString));
                        publisher.publishToGenericQueue(eventString);
                    } catch (JsonProcessingException e) {
                        log.error("Exception occurred while sending message {}", ExceptionUtils.getStackTrace(e));
                    }
                    return setStatus(feature, FeatureStatus.activating);
                }
                break;
            case Datastore:
                break;
            case BRAND:
                connectorService.invalidateSyncariConnectorCache();
                break;
        }
        return setStatus(feature, FeatureStatus.active);
    }

    public Feature activateFeature(Features feature){
        switch (feature) {
            case Insights:
                return setStatus(feature, FeatureStatus.active);
            case InsightsProvider:
                return setStatus(feature, FeatureStatus.active);
            case Datastore:
                return setStatus(feature, FeatureStatus.active);
            //case InsightsAdvanceDataset:
              //  return setStatus(feature, FeatureStatus.active);
        }
        return null;
    }

    private Feature setStatus(Features feature, FeatureStatus status){
        Optional<Feature> byName = featureRepo.findByName(feature.name());
        Feature f;
        if(byName.isEmpty()) {
            f = new Feature(feature.name(), FeatureStage.internal, status);
        } else {
            f = byName.get();
            f.setStatus(status);
        }
        featureCache.invalidate(SyncariContext.getSyncariId());
        return featureRepo.save(f);
    }
    
    public Feature disableFeature(Features feature) {
        // TODO: handle this better as part of feature manager enhancements
        switch (feature) {
            case Insights:
                validateCondition(!SyncariContext.getUser().isSuperAdmin() && !SyncariContext.getUser().isSystemUser(), i18n("no_permission_to_change_feature"));
                break;
            case InsightsProvider:
                validateCondition(!SyncariContext.getUser().isSuperAdmin() && !SyncariContext.getUser().isSystemUser(), i18n("no_permission_to_change_feature"));
                break;
            case Datastore:
                validateCondition(!SyncariContext.getUser().isSuperAdmin() && !SyncariContext.getUser().isSystemUser(), i18n("no_permission_to_change_feature"));
                break;
            case BRAND:
                connectorService.invalidateSyncariConnectorCache();
                break;
        }
        return setStatus(feature, FeatureStatus.inactive);
    }

    public void deleteFeature(Features feature) {
        // TODO: handle this better as part of feature manager enhancements
        Optional<Feature> byName = featureRepo.findByName(feature.name());
        byName.ifPresent(f ->
                featureRepo.deleteById(f.getId()));
    }

    public Feature saveFeature(Feature f) {
        featureCache.invalidate(SyncariContext.getSyncariId());
        return featureRepo.save(f);
    }

    public List<Feature> getEnabledFeatures() {
        return featureRepo.findAll().stream().filter(f -> f.status == FeatureStatus.active).collect(Collectors.toList());
    }

    public List<Feature> getAllFeatures() {
        List<Feature> results = new ArrayList<>();
        List<String> features = Arrays.stream(Features.values()).filter(f -> !f.isHidden()).map(f -> f.name()).collect(Collectors.toList());
        List<Feature> storedFeatures = featureRepo.findAll();
        List<String> storedFeatureNames = storedFeatures.stream().map(f -> f.name).collect(Collectors.toList());
        // Add all valid features
        for (Feature feature : storedFeatures) {
            if(features.contains(feature.getName()) && feature.stage != FeatureStage.GA) {
                results.add(feature);
            }
        }
        for (String name : features) {
            if(!storedFeatureNames.contains(name)) {
                results.add(new Feature().setName(name).setStage(FeatureStage.beta).setStatus(FeatureStatus.inactive));
            }
        }

        return results;
    }

}
