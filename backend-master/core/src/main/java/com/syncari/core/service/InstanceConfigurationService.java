package com.syncari.core.service;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.DebugConfig;
import com.syncari.core.model.InstanceConfiguration;
import com.syncari.core.model.SharedItem;
import com.syncari.core.repositories.customer.InstanceConfigurationRepo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class InstanceConfigurationService {

    public static final int MAX_EXPIRY_SECS = 14400; // 4 hrs
    public static final int DEFAULT_EXPIRY_SECS = 3600; // 1 hrs
    public static final String INSTANCE_CONFIG_IPWHITELIST_KEY = "ipWhitelist";


    @Autowired
    InstanceConfigurationRepo instanceConfigurationRepo;

    @Autowired
    InsightsSharingService sharingService;

    public boolean isDebugModeEnabled() {
        Optional<InstanceConfiguration> debugModeOpt = instanceConfigurationRepo.findByKey(InstanceConfiguration.DEBUG_MODE);
        if (!debugModeOpt.isPresent()) return false;
        
        InstanceConfiguration debugMode = debugModeOpt.get();
        boolean debugModeVal = (boolean) debugMode.cast();
        if (!debugModeVal || debugMode.getUpdatedAt() == null) return debugModeVal;

        Optional<InstanceConfiguration> expirySecs = instanceConfigurationRepo.findByKey(InstanceConfiguration.DEBUG_MODE_EXPIRY_SECS);
        boolean expired = false;
        // Find when this debugMode flag expires.
        // if there is an explicit expirySecs set, use it. Otherwise use the updatedAt of the debugMode flag if its set.
        // Also if the expirySecs was not set while flipping the debugMode flag, do not use that expirySecs.
        if (expirySecs.isPresent() && expirySecs.get().getUpdatedAt() != null && 
                expirySecs.get().getUpdatedAt().compareTo(debugMode.getUpdatedAt()) > 0) {
            int expirySecsVal = Math.min((int) expirySecs.get().cast(), MAX_EXPIRY_SECS);
            expired = expirySecs.get().getUpdatedAt().toInstant().toEpochMilli() + expirySecsVal * 1000 < Instant.now().toEpochMilli();
        } else if (debugMode.getUpdatedAt() != null) {
            expired = debugMode.getUpdatedAt().toInstant().toEpochMilli() + DEFAULT_EXPIRY_SECS * 1000 < Instant.now().toEpochMilli();
        } else if (debugMode.getUpdatedAt() == null) {
            // If updatedAt is not present immediately expire debug mode.
            expired = true;
        }

        // If expired, reset the debugMode to false and return it.
        if (expired) {
            debugMode.setValue(false);
            instanceConfigurationRepo.save(debugMode);
            return false;    
        }

        return debugModeVal;
    }

    public void enableDebugMode(int expirySecs) {
        expirySecs = Math.min(expirySecs, MAX_EXPIRY_SECS);
        InstanceConfiguration debugMode = instanceConfigurationRepo.findByKey(InstanceConfiguration.DEBUG_MODE).get();
        debugMode.setValue(true);
        instanceConfigurationRepo.save(debugMode);
        InstanceConfiguration debugModeExpirySecs = instanceConfigurationRepo.findByKey(InstanceConfiguration.DEBUG_MODE_EXPIRY_SECS).get();
        debugModeExpirySecs.setValue(expirySecs);
        instanceConfigurationRepo.save(debugModeExpirySecs);
    }

    public void disableDebugMode() {
        InstanceConfiguration debugMode = instanceConfigurationRepo.findByKey(InstanceConfiguration.DEBUG_MODE).get();
        debugMode.setValue(false);
        instanceConfigurationRepo.save(debugMode);
        InstanceConfiguration debugModeExpirySecs = instanceConfigurationRepo.findByKey(InstanceConfiguration.DEBUG_MODE_EXPIRY_SECS).get();
        debugModeExpirySecs.setValue(60);
        instanceConfigurationRepo.save(debugModeExpirySecs);
    }

    public DebugConfig getDebugConfig() {
        List<InstanceConfiguration> configs = instanceConfigurationRepo.findByKeyIn(
                Arrays.asList(InstanceConfiguration.DEBUG_MODE, InstanceConfiguration.DEBUG_MODE_EXPIRY_SECS)
        );

        Map<String, InstanceConfiguration> configMap = configs.stream()
                .collect(Collectors.toMap(InstanceConfiguration::getKey, c -> c));

        InstanceConfiguration debugMode = configMap.get(InstanceConfiguration.DEBUG_MODE);
        InstanceConfiguration expirySecs = configMap.get(InstanceConfiguration.DEBUG_MODE_EXPIRY_SECS);

        if (debugMode == null) {
            return DebugConfig.builder()
                    .enabled(false)
                    .expirySeconds(DEFAULT_EXPIRY_SECS)
                    .remainingSeconds(0)
                    .build();
        }

        boolean enabled = debugMode.cast();
        int expirySecsValue = expirySecs != null ? (int) expirySecs.cast() : DEFAULT_EXPIRY_SECS;
        Instant updatedAt = debugMode.getUpdatedAt() != null ? debugMode.getUpdatedAt().toInstant() : null;

        long remainingSeconds = 0;
        if (enabled && updatedAt != null) {
            int expirySecsVal = Math.min(expirySecsValue, MAX_EXPIRY_SECS);
            long expiryTimeMillis = updatedAt.toEpochMilli() + expirySecsVal * 1000L;

            long remainingMillis = expiryTimeMillis - Instant.now().toEpochMilli();
            remainingSeconds = remainingMillis > 0 ? remainingMillis / 1000 : 0;

            // If expired, mark as disabled
            if (remainingSeconds == 0) {
                enabled = false;
            }
        }

        return DebugConfig.builder()
                .enabled(enabled)
                .expirySeconds(expirySecsValue)
                .updatedAt(updatedAt)
                .remainingSeconds(remainingSeconds)
                .build();
    }

    public void updateDebugConfig(DebugConfig debugConfig) {
        int expirySecs = Math.min(debugConfig.getExpirySeconds(), MAX_EXPIRY_SECS);

        List<InstanceConfiguration> configs = instanceConfigurationRepo.findByKeyIn(
                Arrays.asList(InstanceConfiguration.DEBUG_MODE, InstanceConfiguration.DEBUG_MODE_EXPIRY_SECS)
        );

        Map<String, InstanceConfiguration> configMap = configs.stream()
                .collect(Collectors.toMap(InstanceConfiguration::getKey, c -> c));

        InstanceConfiguration debugMode = configMap.get(InstanceConfiguration.DEBUG_MODE);
        if (debugMode == null) {
            debugMode = new InstanceConfiguration();
            debugMode.setKey(InstanceConfiguration.DEBUG_MODE);
        }
        debugMode.setValue(debugConfig.isEnabled());

        InstanceConfiguration debugModeExpirySecs = configMap.get(InstanceConfiguration.DEBUG_MODE_EXPIRY_SECS);
        if (debugModeExpirySecs == null) {
            debugModeExpirySecs = new InstanceConfiguration();
            debugModeExpirySecs.setKey(InstanceConfiguration.DEBUG_MODE_EXPIRY_SECS);
        }
        debugModeExpirySecs.setValue(debugConfig.isEnabled() ? expirySecs : 60);

        instanceConfigurationRepo.saveAll(Arrays.asList(debugMode, debugModeExpirySecs));
    }

    public InstanceConfiguration saveDomainsinInstanceConfiguration(InstanceConfiguration instanceConfiguration, String key){
        Optional<InstanceConfiguration> instanceConfigurationOptional = getInstanceConfigurationByKey(key);
        InstanceConfiguration result;
        if (instanceConfigurationOptional.isPresent()){
            deleteDeletedDomainsRecord(instanceConfiguration, instanceConfigurationOptional);
            InstanceConfiguration ic = instanceConfigurationOptional.get();
            List<String> allowedDomainsNew =  (List<String>) instanceConfiguration.getValue();
            ic.setValue(allowedDomainsNew.stream().distinct().collect(Collectors.toList()));
            result = instanceConfigurationRepo.save(ic);
        }else{
            deleteDeletedDomainsRecord(instanceConfiguration, instanceConfigurationOptional);
            result = instanceConfigurationRepo.save(instanceConfiguration);
        }
        return result;
    }

    public InstanceConfiguration saveInstanceConfiguration(InstanceConfiguration instanceConfiguration, String key){
        Optional<InstanceConfiguration> instanceConfigurationOptional = getInstanceConfigurationByKey(key);
        InstanceConfiguration result;
        if (instanceConfigurationOptional.isPresent()){
            InstanceConfiguration ic = instanceConfigurationOptional.get();
            delete(ic);
            String allowedIps = (String)instanceConfiguration.getValue();
            ic.setValue(allowedIps);
            result = instanceConfigurationRepo.save(ic);
        }else{
            result = instanceConfigurationRepo.save(instanceConfiguration);
        }
        return result;
    }

    public List<String> getDeletedDomains(InstanceConfiguration instanceConfiguration,Optional<InstanceConfiguration> instanceConfigurationDb){
        List<String> allowedDomainsNew =  (List<String>) instanceConfiguration.getValue();
        if (CollectionUtils.isEmpty(allowedDomainsNew)){
            return List.of();
        }
        if (instanceConfigurationDb.isPresent()){
            InstanceConfiguration ic = instanceConfigurationDb.get();
            List<String> existingDomains = (List<String>)ic.getValue();
            if (CollectionUtils.isEmpty(existingDomains)){
                return findAllDomainsNotAllowedFromRecords(allowedDomainsNew);
            }else{
                return existingDomains.stream().filter(e -> !allowedDomainsNew.contains(e)).collect(Collectors.toList());
            }
        }
       return findAllDomainsNotAllowedFromRecords(allowedDomainsNew);

    }

    private List<String> findAllDomainsNotAllowedFromRecords(List<String> allowedDomainsNew){
        List<SharedItem> sharedItems = sharingService.findAllInsightsSharedItemsForGivenInstance();
        List<String> emails = sharedItems.stream().map(s -> s.getRecipientsEmailId()).collect(Collectors.toList());
        List<String> nonEmptyEmails = emails.stream().filter(e -> StringUtils.isNotEmpty(e)).collect(Collectors.toList());
        List<String> allDomainsinRecords = nonEmptyEmails.stream().map(e -> e.split("@")[1]).collect(Collectors.toList());
        return allDomainsinRecords.stream().filter(a -> !allowedDomainsNew.contains(a)).collect(Collectors.toList());
    }

    private List<SharedItem> deleteDeletedDomainsRecord(InstanceConfiguration instanceConfiguration, Optional<InstanceConfiguration> instanceConfigurationDb){
        List<String> allowedDomainsNew =  (List<String>) instanceConfiguration.getValue();
        List<String> domains = getDeletedDomains(instanceConfiguration, instanceConfigurationDb);
        if (CollectionUtils.isNotEmpty(allowedDomainsNew)){
            return sharingService.deleteDomainsSharedItem(domains,SyncariContext.getSyncariId(),true);
        }else{
            // if new allowed domains list is empty then do not delete records, it means allowing all domains
            return sharingService.deleteDomainsSharedItem(domains,SyncariContext.getSyncariId(),false);
        }
    }

    public Optional<InstanceConfiguration> getInstanceConfigurationByKey(String key){
        return instanceConfigurationRepo.findByKey(key);
    }

    public void delete(InstanceConfiguration instanceConfiguration){
        instanceConfigurationRepo.deleteById(instanceConfiguration.getId());
    }
    
}
