package com.syncari.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoDatabase;
import com.syncari.core.cloudfunctions.CloudFunctionManager;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.event.store.repo.BigQueryTransactionLogRepo;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.repositories.syncari.*;
import com.syncari.core.service.*;
import com.syncari.core.service.cache.CacheLoaderService;
import com.syncari.core.sync.EntitySourceHelper;
import com.syncari.core.utils.CustomerMongoUtils;
import com.syncari.utils.TextUtil;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import redis.clients.jedis.JedisPooled;

public class MigrationContext {
    private static ThreadLocal<ApplicationContext> applicationContextThreadLocal = new ThreadLocal<>();
    private static ThreadLocal<String> syncariIdLocal = new ThreadLocal<>();

    public static void setApplicationContext(ApplicationContext applicationContext) {
        applicationContextThreadLocal.set(applicationContext);
    }

    public static void setSyncariId(String syncariId) {
        syncariIdLocal.set(syncariId);
    }

    public static String getSyncariId() {
        return syncariIdLocal.get();
    }

    public static CustomStagedExternalRecordRepo getStagedExternalRecordRepo() {
        return  applicationContextThreadLocal.get().getBean(StagedExternalRecordRepo.class);
    }

    public static EntitySourceHelper getEntitySourceHelper() {
        return  applicationContextThreadLocal.get().getBean(EntitySourceHelper.class);
    }

    public static CustomerMongoUtils getCustomerMongoUtils() {
        return  applicationContextThreadLocal.get().getBean(CustomerMongoUtils.class);
    }
    
    public static MongoDatabase getSyncariDB() {
		return applicationContextThreadLocal.get().getBean("syncariMongoTemplate", MongoTemplate.class)
				.getMongoDbFactory().getDb("syncaridb");
    }

    public static AttributeRepo getAttributeRepo() {
        return applicationContextThreadLocal.get().getBean("attributeRepo", AttributeRepo.class);
    }

    public static OrganizationRepo getOrganizationRepo() {
        return applicationContextThreadLocal.get().getBean(OrganizationRepo.class);
    }

    public static MappingNodeRepo getMappingNodeRepo() {
        return  applicationContextThreadLocal.get().getBean(MappingNodeRepo.class);
    }

    public static MappingGraphRepo getMappingGraphRepo() {
        return  applicationContextThreadLocal.get().getBean(MappingGraphRepo.class);
    }

    public static FragmentRepo getFragmentRepo() {
        return  applicationContextThreadLocal.get().getBean(FragmentRepo.class);
    }

    public static EdgeRepo getEdgeRepo() {
        return  applicationContextThreadLocal.get().getBean(EdgeRepo.class);
    }

    public static ActionDefinitionRepo getActionDefinitionRepo() {
        return  applicationContextThreadLocal.get().getBean(ActionDefinitionRepo.class);
    }

    public static EntityRepoService getRepoService() {
        return  applicationContextThreadLocal.get().getBean(EntityRepoService.class);
    }
    
    public static EntityDefinitionRepo getEntityDefinitionRepo() {
        return  applicationContextThreadLocal.get().getBean("entityDefinitionRepo", EntityDefinitionRepo.class);
    }
    
    public static TextUtil getTextUtil() {
        return  applicationContextThreadLocal.get().getBean(TextUtil.class);
    }
    
    public static EventStore getEventStore() {
        return  applicationContextThreadLocal.get().getBean("bigQueryEventStore",EventStore.class);
    }
    public static BigQueryTransactionLogRepo getBigQueryTransactionLogStore() {
        return  applicationContextThreadLocal.get().getBean("bigQueryTransactionLogRepo", BigQueryTransactionLogRepo.class);
    }
    public static BigQueryHelper getBigQueryHelper() {
        return  applicationContextThreadLocal.get().getBean("bigQueryHelper",BigQueryHelper.class);
    }

    public static ReferenceDataService getReferenceDataService(){
        return  applicationContextThreadLocal.get().getBean(ReferenceDataService.class);
    }
    
    public static SchemaService getSchemaService(){
        return  applicationContextThreadLocal.get().getBean(SchemaService.class);
    }

    public static ResyncDetailRepo getResyncDetailRepo(){
        return  applicationContextThreadLocal.get().getBean(ResyncDetailRepo.class);
    }

    public static ProvisioningService getProvisioningService() {
        return  applicationContextThreadLocal.get().getBean(ProvisioningService.class);
    }

    public static FeatureService getFeatureService() {
        return  applicationContextThreadLocal.get().getBean(FeatureService.class);
    }

    public static FeatureRepo getFeatureRepo() {
        return  applicationContextThreadLocal.get().getBean(FeatureRepo.class);
    }

    public static DatastoreService getDatastoreService() {
        return  applicationContextThreadLocal.get().getBean(DatastoreService.class);
    }

    public static EncryptionService getEncryptionService() {
        return  applicationContextThreadLocal.get().getBean(EncryptionService.class);
    }

    public static ConnectorService getConnectorService() {
        return  applicationContextThreadLocal.get().getBean(ConnectorService.class);
    }

    public static WatermarkService getWatermarkService() {
        return  applicationContextThreadLocal.get().getBean(WatermarkService.class);
    }


    public static DfiRuleAssignmentService getDfiRuleAssignmentService() {
        return  applicationContextThreadLocal.get().getBean(DfiRuleAssignmentService.class);
    }

    public static DatasetService getDatasetService() {
        return applicationContextThreadLocal.get().getBean(DatasetService.class);
    }

    public static DatasetSchemaService getDatasetSchemaService() {
        return applicationContextThreadLocal.get().getBean(DatasetSchemaService.class);
    }

    public static DatasetRepo getDatasetRepo() {
        return applicationContextThreadLocal.get().getBean(DatasetRepo.class);
    }

    public static DatacardRepo getDatacardRepo() {
        return applicationContextThreadLocal.get().getBean(DatacardRepo.class);
    }

    public static DatacardService getDatacardService() {
        return applicationContextThreadLocal.get().getBean(DatacardService.class);
    }

    public static InsightsDashboardService getDashboardService() {
        return applicationContextThreadLocal.get().getBean(InsightsDashboardService.class);
    }

    public static InsightsDashboardRepo getInsightDashboardRepo() {
        return applicationContextThreadLocal.get().getBean(InsightsDashboardRepo.class);
    }
    
    public static ConnectorMetadataService getConnectorMetaDataService() {
        return  applicationContextThreadLocal.get().getBean(ConnectorMetadataService.class);
    }

    public static FunctionService getFunctionService() {
        return applicationContextThreadLocal.get().getBean(FunctionService.class);
    }

    public static AsyncJobService getAsyncJobService() {
        return applicationContextThreadLocal.get().getBean(AsyncJobService.class);
    }

    public static UserService getUserService() {
        return applicationContextThreadLocal.get().getBean(UserService.class);
    }

    public static SubscriptionService getSubscriptionService() {
        return applicationContextThreadLocal.get().getBean(SubscriptionService.class);
    }

    public static UserRepo getUserRepo(){
        return  applicationContextThreadLocal.get().getBean(UserRepo.class);
    }

    public static EntityRepo getEntityRepo(){
        return  applicationContextThreadLocal.get().getBean(EntityRepo.class);
    }

    public static EntityDatabaseRepo getEntityDatabaseRepo(){
        return  applicationContextThreadLocal.get().getBean(EntityDatabaseRepo.class);
    }

    public static EntityCacheRepo getEntityCacheRepo(){
        return  applicationContextThreadLocal.get().getBean(EntityCacheRepo.class);
    }

    public static ConnectorRepo getConnectorRepo() {
        return applicationContextThreadLocal.get().getBean(ConnectorRepo.class);
    }

    public static AppConfig getAppConfig(){
        return  applicationContextThreadLocal.get().getBean(AppConfig.class);
    }

    public static CacheLoaderService getCacheLoaderService(){
        return  applicationContextThreadLocal.get().getBean(CacheLoaderService.class);
    }

    public static ComponentDependencyService getComponentDependencyService(){
        return  applicationContextThreadLocal.get().getBean(ComponentDependencyService.class);
    }

    public static JedisPooled getRedisClient(){
        return  applicationContextThreadLocal.get().getBean("redisClient", JedisPooled.class);
    }

    public static ResyncService getResyncService() {
        return  applicationContextThreadLocal.get().getBean("resyncService", ResyncService.class);
    }

    public static ConnectorMetadataRepo getConnectorMetadataRepo() {
        return  applicationContextThreadLocal.get().getBean(ConnectorMetadataRepo.class);
    }

    public static RequeueService getRequeueService() {
        return  applicationContextThreadLocal.get().getBean(RequeueService.class);
    }

    public static MappingGraphService getMappingGraphService() {
        return  applicationContextThreadLocal.get().getBean(MappingGraphService.class);
    }

    public static IdMappingRepo getIdMappingRepo() {
        return  applicationContextThreadLocal.get().getBean(IdMappingRepo.class);
    }

    public static CloudFunctionManager getCloudFunctionManager() {
        return  applicationContextThreadLocal.get().getBean(CloudFunctionManager.class);
    }

    public static PipelineTestRepo getPipelineTestRepo() {
        return applicationContextThreadLocal.get().getBean(PipelineTestRepo.class);
    }

    public static QuickStartRepo getQuickstartRepo() {
        return applicationContextThreadLocal.get().getBean(QuickStartRepo.class);
    }

    public static LayoutRepo getLayoutRepo() {
        return applicationContextThreadLocal.get().getBean(LayoutRepo.class);
    }

    public static PlanRepo getPlanRepo() {
        return applicationContextThreadLocal.get().getBean(PlanRepo.class);
    }

    public static void clear() {
        applicationContextThreadLocal.remove();
        syncariIdLocal.remove();
    }

    public static BatchRepo getBatchRepo() {
        return applicationContextThreadLocal.get().getBean(BatchRepo.class);
    }
    
    public static UserPreferenceRepo getUserPreferenceRepo(){
      return  applicationContextThreadLocal.get().getBean(UserPreferenceRepo.class);
    }
    
    public static LayoutService getLayoutService() {
      return applicationContextThreadLocal.get().getBean(LayoutService.class);
    }
    
    public static ObjectMapper getMapper() {
      return applicationContextThreadLocal.get().getBean(ObjectMapper.class);
    }

    public static TSService getTSService() {
        return applicationContextThreadLocal.get().getBean(TSService.class);
    }
    public static InsightsProviderIntegrator getInsightsProviderIntegrator() {
        return applicationContextThreadLocal.get().getBean(InsightsProviderIntegrator.class);
    }

    public static PipelineTestService getPipelineTestService() {
        return applicationContextThreadLocal.get().getBean(PipelineTestService.class);
    }

    public static GhostAccessAuditRepo getGhostAccessAuditRepo() {
        return applicationContextThreadLocal.get().getBean(GhostAccessAuditRepo.class);
    }

    public static ErrorNotificationConfigRepo getErrorNotificationConfigRepo() {
        return applicationContextThreadLocal.get().getBean(ErrorNotificationConfigRepo.class);
    }

    public static LockRepo getLockRepo() {
        return applicationContextThreadLocal.get().getBean(LockRepo.class);
    }

}
