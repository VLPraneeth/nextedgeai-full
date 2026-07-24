package com.syncari.viper;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.*;
import com.syncari.core.service.FeatureService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.Stack;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public class ViperContext {
    private Organization organization;
    boolean updateWatermark = true;
    private boolean testMode;
    private boolean simulationMode;
    private boolean debugMode = false;
    private boolean realTimeMode;
    private String contextSyncRunId;
    private Long syncStartTime = Instant.now().toEpochMilli();
    private String currentSyncCycleId;
    private String streamManagerRunId;
    private ApplicationContext applicationContext;

    public ViperContext setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        return this;
    }


    public Instance getInstance() {
        return instance;
    }

    public void setInstance(Instance instance) {
        this.instance = instance;
    }

    private Instance instance;

    private User user;


    private Stack<ViperContext> contextStack=new Stack<>();

    public ViperContext copy(){
        return new ViperContext(organization,instance,user).setUpdateWatermark(updateWatermark).setTestMode(testMode).setSyncStartTime(syncStartTime)
                .setContextSyncRunId(contextSyncRunId).setDebugMode(debugMode).setSimulationMode(simulationMode).setApplicationContext(applicationContext).setRealTimeMode(realTimeMode)
                .setStreamManagerRunId(streamManagerRunId);
    }

    public ViperContext setStreamManagerRunId(String streamManagerRunId) {
        this.streamManagerRunId = streamManagerRunId;
        MDC.put("streamManagerRunId", streamManagerRunId);
        return this;
    }

    public static ViperContext fromCurrentContext(){
        if(SyncariContext.getOrganziation()!=null) {
            return new ViperContext(SyncariContext.getOrganziation(), SyncariContext.getInstance(), SyncariContext.getUser());
        }
        return null;
    }
    public ViperContext(Organization organization, Instance instance, User user) {
        this.organization = organization;
        this.instance = instance;
        this.user = user;
    }


    public static ViperContext of(Organization organization, Instance instance, User user){
        return new ViperContext(organization, instance, user);
    }
    public String getDatabase() {
        return instance.getResource(ResourceType.DATABASE).map(resource -> resource.getConfiguration().get("database")).orElseThrow();
    }

    public <R> R with(Supplier<R> func){
        setSyncariContext();
        try {
            return func.get();
        }finally {
            resetSyncariContext();
        }
    }

    public void with(Runnable block){
        try {
            setSyncariContext();
            block.run();
        }finally {
            resetSyncariContext();
        }
    }


    /**
     * Set & Reset functions honor current syncari contexts. on set, previous context is pushed to stack and current
     * global context is set with this ViperContext. On reset, current global context is reset, and previous global context is restored, if one was present
     */
    public void setSyncariContext(){
        ViperContext current = fromCurrentContext();
        if(current!=null) {
            contextStack.push(current);
        }
        SyncariContext.resetAll();
        SyncariContext.setInstance(getInstance());
        SyncariContext.setOrganziation(getOrganization());
        SyncariContext.setUser(getUser());
        SyncariContext.setReadOnlyOp(isSimulationMode());

        // get features
        if (applicationContext != null) {
            FeatureService featureService = applicationContext.getBean(FeatureService.class);
            if (featureService != null) {
                SyncariContext.getInstance().setFeatures(featureService.getEnabledFeatures().stream().collect(Collectors.toMap(Feature::getName, Function.identity())));
            }
        } else {
            log.debug("Application context is null, set appropriate context");
        }

        if (StringUtils.isNotEmpty(streamManagerRunId)) {
            MDC.put("streamManagerRunId", streamManagerRunId);
        }
        MDC.put("subscription", getOrganization().getName());
        MDC.put("instance", getInstance().getName());
        MDC.put("syncariId", getInstance().getSyncariId());
        MDC.put("syncCycleId", getContextSyncRunId());
        MDC.put("runMode", getRunMode());
        MDC.put("debugMode", debugMode ? "true" : "false");
        MDC.put("currentSyncCycleId", getCurrentSyncCycleId());
    }

    private String getRunMode() {
        if(isSimulationMode()){
            return "runMode=simulation";
        }else if(isTestMode()){
            return "runMode=livetest";
        } else if (isRealTimeMode()) {
            return "runMode=realtime";
        }else{
            return "runMode=live";
        }
    }

    public void resetSyncariContext(){
        SyncariContext.resetAll();
        MDC.remove("subscription");
        MDC.remove("instance");
        MDC.remove("syncariId");
        MDC.remove("syncCycleId");
        MDC.remove("debugMode");
        MDC.remove("currentSyncCycleId");
        if(!contextStack.empty()){
            contextStack.pop().setSyncariContext();
        }

    }


    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }
    
    public ViperContext setUpdateWatermark(boolean updateWatermark) {
        this.updateWatermark = updateWatermark;
        return this;
    }
    
    public boolean updateWatermark() {
        return this.updateWatermark;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ViperContext setTestMode(boolean testMode) {
        this.testMode = testMode;
        return this;
    }

    public boolean isTestMode() {
        return testMode;
    }

    public boolean isSimulationMode() {
        return simulationMode;
    }

    public ViperContext setSimulationMode(boolean simulationMode) {
        this.simulationMode = simulationMode;
        return this;
    }

    public ViperContext setContextSyncRunId(String contextSyncRunId) {
        this.contextSyncRunId = contextSyncRunId;
        // We have to put this here to be picked up immediately.
        MDC.put("syncCycleId", contextSyncRunId);
        return this;
    }

    public ViperContext setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        MDC.put("debugMode", debugMode ? "true" : "false");
        return this;
    }

    public String getContextSyncRunId() {
        return contextSyncRunId;
    }

    public ViperContext setSyncStartTime(Long syncStartTime) {
        this.syncStartTime = syncStartTime;
        return this;
    }

    public Long getSyncStartTime() {
        return syncStartTime;
    }

    public String getCurrentSyncCycleId() {
        return currentSyncCycleId;
    }

    public boolean isRealTimeMode() {
        return realTimeMode;
    }

    public ViperContext setRealTimeMode(boolean realTimeMode) {
        this.realTimeMode = realTimeMode;
        return this;
    }


    public ViperContext setCurrentSyncCycleId(String currentSyncCycleId) {
        this.currentSyncCycleId = currentSyncCycleId;
        return this;
    }
}