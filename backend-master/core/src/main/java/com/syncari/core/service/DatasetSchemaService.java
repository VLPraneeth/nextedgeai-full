package com.syncari.core.service;

import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.User;
import com.syncari.core.model.insights.NoQueryFunction;
import com.syncari.core.model.insights.Projection;
import com.syncari.core.model.insights.QField;
import com.syncari.core.model.insights.QueryFunction;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.model.insights.dataset.DatasetFrom;
import com.syncari.core.model.insights.provider.ts.TSMetadataListItemInput;
import com.syncari.core.model.insights.provider.ts.TSMetadataSearchReq;
import com.syncari.core.model.insights.provider.ts.TSMetadataSearchResponse;
import com.syncari.core.model.insights.provider.ts.TSMetadataType;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.DatasetRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class DatasetSchemaService {

    private static final String SYNTHETIC_WATERMARK_API_NAME = "syncariDefinedUpdatedAt";
    private static final String SYNTHETIC_WATERMARK_DISPLAY_NAME = "Syncari Defined Updated At";

    @Autowired
    SchemaService schemaService;

    @Autowired
    DatasetRepo datasetRepo;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    FeatureService featureService;

    @Autowired
    InsightsProviderService insightsProviderService;
    @Autowired
    InsightsProviderIntegrator insightsProviderIntegrator;

    public void createDatasetSyncariSourceSchema(Dataset dataset) {
        String apiName = dataset.getName();
        String displayName = dataset.getDisplayName();
        List<AttributeDefinition> attributeDefinitions = parseFields(dataset, true);

        List<AttributeDefinition> sourceParams = new ArrayList<>();
        sourceParams.add(createOrderBySourceParam());

        EntityDefinition entityDefinition = schemaService.createDatasetAsSourceSchema(dataset.getId(), apiName, displayName,
                attributeDefinitions, sourceParams, true);
        dataset.setEntityDefinitionId(entityDefinition.getId());
        log.info("Saving edefId {} to dataset {} with datasetid {}", entityDefinition.getId(), dataset.getName(), dataset.getId());
        datasetRepo.save(dataset);
    }

    public EntityDefinition fetchDatasetSchema(Dataset dataset) {
        String apiName = dataset.getName();
        String displayName = dataset.getDisplayName();
        boolean isCreate = dataset.getEntityDefinitionId() == null;
        List<AttributeDefinition> attributeDefinitions = parseFields(dataset, isCreate);
        Optional<Connector> datasetConnector = connectorService.getDatasetConnector();
        if (datasetConnector.isEmpty()) {
            throw new SyncariValidationException(i18n("dataset_connector_does_not_exist"));
        }
        EntityDefinition entityDefinition = buildEntityDefinition(datasetConnector.get(), dataset.getId(), apiName, displayName, true);
        entityDefinition.setAttributes(attributeDefinitions);
        return entityDefinition;
    }

    private EntityDefinition buildEntityDefinition(Connector connector, String datasetId, String apiName, String displayName, boolean readOnly) {
        EntityDefinition entityDefinition = new EntityDefinition(apiName, displayName);
        entityDefinition.setReadOnly(readOnly);
        entityDefinition.setDraftStatus(DraftStatus.APPROVED);
        entityDefinition.setConnectorId(connector.getId());
        entityDefinition.setConnectorTypeId(connector.getMetadataId());
        entityDefinition.setStatus(Status.ACTIVE);
        entityDefinition.setAdditionalProperties(constructAdditionalProperties(entityDefinition.getAdditionalProperties(), datasetId));
        return entityDefinition;
    }

    private Map<String, Object> constructAdditionalProperties(Map<String, Object> existingAdditionalProperties, String datasetId) {
        Map<String, Object> additionalProperties = existingAdditionalProperties;
        if (existingAdditionalProperties == null) {
            additionalProperties = new HashMap<>();
        }
        if (!additionalProperties.containsKey("datasetId")) {
            additionalProperties.put("datasetId", datasetId);
        }
        return additionalProperties;
    }

    public void updateDatasetSyncariSourceSchema(Dataset dataset) {
        String apiName = dataset.getName();
        String displayName = dataset.getDisplayName();
        List<AttributeDefinition> attributeDefinitions = parseFields(dataset, false);

        List<AttributeDefinition> sourceParams = new ArrayList<>();
        sourceParams.add(createOrderBySourceParam());

        EntityDefinition entityDefinition = schemaService.updateDatasetAsSourceSchema(apiName, displayName, attributeDefinitions,
                sourceParams, true, dataset.getEntityDefinitionId(), dataset.getId());
        dataset.setEntityDefinitionId(entityDefinition.getId());
        datasetRepo.save(dataset);
    }

    public void deleteDatasetSyncariSourceSchema(Dataset dataset) {
        schemaService.deleteDatasetAsSourceSchema(dataset.getEntityDefinitionId());
    }

    private List<AttributeDefinition> parseFields(Dataset dataset, boolean isCreate) {
        AttributeDefinition watermarkField = new AttributeDefinition();

        if (isCreate) {
            watermarkField.setApiName(SYNTHETIC_WATERMARK_API_NAME);
            watermarkField.setDisplayName(SYNTHETIC_WATERMARK_DISPLAY_NAME);
        } else {
            Optional<AttributeDefinition> existingWatermark = findExistingSyntheticWatermarkField(dataset.getEntityDefinitionId());
            if (existingWatermark.isPresent()) {
                watermarkField.setApiName(existingWatermark.get().getApiName());
                watermarkField.setDisplayName(existingWatermark.get().getDisplayName());
            } else {
                watermarkField.setApiName(SYNTHETIC_WATERMARK_API_NAME);
                watermarkField.setDisplayName(SYNTHETIC_WATERMARK_DISPLAY_NAME);
            }
        }

        watermarkField.setDataType(new DatetimeType());
        watermarkField.setUpdatable(false);
        watermarkField.setWatermarkField(true);
        watermarkField.setSyncariDefined(true);
        watermarkField.setStatus(Status.ACTIVE);
        List<AttributeDefinition> attributeDefinitions = new ArrayList<>(List.of(watermarkField));
        DatasetConfig config = dataset.getDatasetConfig();
        config.getProjectionsList().forEach(projection -> {
            AttributeDefinition attributeDefinition = new AttributeDefinition();
            attributeDefinition.setApiName(sanitizeApiName(projection.getAliasName()));
            attributeDefinition.setDataStoreName(sanitizeApiName(projection.getAliasName()));
            attributeDefinition.setDisplayName(projection.getAliasName());
            attributeDefinition.setDataType(DatatypeFactory.getDatatype(projection.getFunction().getDataType()));
            attributeDefinition.setStatus(Status.ACTIVE);
            attributeDefinition.setUpdatable(false);
            attributeDefinitions.add(attributeDefinition);
        });
        return attributeDefinitions;
    }

    private Optional<AttributeDefinition> findExistingSyntheticWatermarkField(String entityDefinitionId) {
        List<AttributeDefinition> existingAttributes = schemaService.getAttributesByEntityId(entityDefinitionId);
        return existingAttributes.stream()
            .filter(attr -> attr.isWatermarkField() && attr.isSyncariDefined())
            .findFirst();
    }

    public List<String> createDatasetsFromAllEntities(Optional<String> userToCreate){
        if (!featureService.isEnabled(Features.InsightsProvider) && (!featureService.isEnabled(Features.Insights))) return List.of();
        Connector syncariConnector = connectorService.getSyncariConnector();
        List<EntityDefinition> entityDefinitionList = schemaService.getEntities(syncariConnector.getId());
        List<Dataset> datasets = new ArrayList<>();
        List<String> insightsProviderIds = new ArrayList<>();
        for (EntityDefinition entityDefinition : entityDefinitionList) {
            Optional<Dataset> dst = datasetRepo.findApprovedByName(entityDefinition.getApiName());
            dst.ifPresentOrElse(d -> {
                log.info("Dataset {} already exists", entityDefinition.getApiName());
                if (StringUtils.isNotEmpty(d.getInsightsProviderId())){
                    insightsProviderIds.add(d.getInsightsProviderId());
                }
            },()->{
                Dataset dataset = new Dataset();
                DatasetConfig config = new DatasetConfig();
                dataset.setDisplayName(entityDefinition.getDisplayName());
                dataset.setName(entityDefinition.getApiName());
                dataset.setVersion("V2");
                dataset.setDatasetType(Dataset.DatasetType.TABLE);
                dataset.setDraftStatus(DraftStatus.APPROVED);
                DatasetFrom from = new DatasetFrom().setDatasetId(entityDefinition.getId()).setApiName(entityDefinition.getApiName()).setDatasetType(DatasourceType.ENTITY).setAlias(entityDefinition.getDisplayName()).setDisplayName(entityDefinition.getDisplayName());
                List<Projection> projections = convertEntityAttribsToProjections(entityDefinition.getActiveAttributes());
                config.setFromDatasets(List.of(from));
                config.setProjectionsList(projections);
                dataset.setDatasetConfig(config);
                dataset.setUpdatedAt(new Date());
                dataset.setCreatedAt(new Date());
                dataset.setCreatedBy(SyncariContext.getUser().getId());
                dataset.setUpdatedBy(SyncariContext.getUser().getId());
                TSMetadataSearchReq req = new TSMetadataSearchReq();
                TSMetadataListItemInput itemInput = new TSMetadataListItemInput().setIdentifier(dataset.getName()).setType(TSMetadataType.LOGICAL_TABLE.name());
                req.setMetadata(List.of(itemInput));
                HttpHeaders headers = insightsProviderService.getHeaders(userToCreate,60L);
                List<TSMetadataSearchResponse> searchResponses = insightsProviderService.searchMetadata(req,Optional.of(TSService.TS_ADMIN_USER),headers);
                Optional<TSMetadataSearchResponse> searchResponse = searchResponses.stream().findFirst();
                searchResponse.ifPresent(sr -> {
                    String providerId = sr.getMetadata_id();
                    dataset.setInsightsProviderId(providerId);
                    if (!insightsProviderIds.contains(providerId)){
                        insightsProviderIds.add(providerId);
                        datasets.add(dataset);
                    }else{
                        log.info("There are duplicate entity definition returned with same api name {}", entityDefinition.getApiName());
                    }
                });
            });
        }
        datasetRepo.saveAll(datasets);
        for (Dataset ds : datasets) {
            this.createDatasetSyncariSourceSchema(ds);
        }
        insightsProviderIntegrator.shareWithDMGroup(insightsProviderIds,Optional.of("LOGICAL_TABLE"),true);
        insightsProviderIntegrator.shareWithNoneGroup(insightsProviderIds,Optional.of("LOGICAL_TABLE"));
        insightsProviderIntegrator.changeOwnerToTSAdmin(insightsProviderIds,Optional.of("LOGICAL_TABLE"));

        return insightsProviderIds;
    }

    public void createDatasetFromSyncariEntity(EntityDefinition entityDefinition){
        if (!featureService.isEnabled(Features.InsightsProvider)) return;
        // if there is already an existing approved entity dataset exists then delete that and create a new one. Keep the existing datasetid
        Optional<Dataset> datasetOpt =  datasetRepo.findApprovedByName(entityDefinition.getApiName());
        datasetOpt.ifPresentOrElse(d -> {
            DatasetConfig config = new DatasetConfig();
            DatasetFrom from = new DatasetFrom().setDatasetId(entityDefinition.getId()).setApiName(entityDefinition.getApiName()).setDatasetType(DatasourceType.ENTITY).setAlias(entityDefinition.getDisplayName()).setDisplayName(entityDefinition.getDisplayName());
            List<Projection> projections = convertEntityAttribsToProjections(entityDefinition.getActiveAttributes());
            config.setFromDatasets(List.of(from));
            config.setProjectionsList(projections);
            d.setDatasetConfig(config);
            d.setUpdatedAt(new Date());
            d.setUpdatedBy(SyncariContext.getUser().getId());
            var saved = datasetRepo.save(d);
            this.createDatasetSyncariSourceSchema(saved);
        },()-> {
            Dataset dataset = new Dataset();
            dataset.setName(entityDefinition.getApiName());
            dataset.setDisplayName(entityDefinition.getDisplayName());
            dataset.setDatasetType(Dataset.DatasetType.TABLE);
            dataset.setVersion("V2");
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();
            DatasetFrom from = new DatasetFrom().setDatasetId(entityDefinition.getId()).setApiName(entityDefinition.getApiName()).setDatasetType(DatasourceType.ENTITY).setAlias(entityDefinition.getDisplayName()).setDisplayName(entityDefinition.getDisplayName());
            List<Projection> projections = convertEntityAttribsToProjections(entityDefinition.getActiveAttributes());
            config.setFromDatasets(List.of(from));
            config.setProjectionsList(projections);
            dataset.setDatasetConfig(config);
            dataset.setUpdatedAt(new Date());
            dataset.setCreatedAt(new Date());
            dataset.setCreatedBy(SyncariContext.getUser().getId());
            dataset.setUpdatedBy(SyncariContext.getUser().getId());
            // Store metadata id to dataset
            User user = SyncariContext.getUser();
            String insightsProviderUserName = StringUtils.isNotEmpty(user.getInsightsProviderUserName()) ? user.getInsightsProviderUserName() : TSService.TS_ADMIN_USER;
            TSMetadataSearchReq req = new TSMetadataSearchReq();
            TSMetadataListItemInput itemInput = new TSMetadataListItemInput().setIdentifier(dataset.getName()).setType(TSMetadataType.LOGICAL_TABLE.name());
            req.setMetadata(List.of(itemInput));
            HttpHeaders headers = insightsProviderService.getHeaders(Optional.of(insightsProviderUserName),60L);
            List<TSMetadataSearchResponse> searchResponses = insightsProviderService.searchMetadata(req,Optional.of(insightsProviderUserName),headers);
            Optional<TSMetadataSearchResponse> searchResponse = searchResponses.stream().findFirst();
            searchResponse.ifPresent(sr -> {
                String providerId = sr.getMetadata_id();
                dataset.setInsightsProviderId(providerId);
            });
            var saved = datasetRepo.save(dataset);
            this.createDatasetSyncariSourceSchema(saved);
            insightsProviderIntegrator.shareWithDMGroup(List.of(dataset.getInsightsProviderId()),Optional.of("LOGICAL_TABLE"),true);
            insightsProviderIntegrator.shareWithNoneGroup(List.of(dataset.getInsightsProviderId()),Optional.of("LOGICAL_TABLE"));
            insightsProviderIntegrator.changeOwnerToTSAdmin(List.of(dataset.getInsightsProviderId()),Optional.of("LOGICAL_TABLE"));
        });
    }

    private List<Projection> convertEntityAttribsToProjections(List<AttributeDefinition> attributeDefinitions) {
        List<Projection> projections = new ArrayList<>();
        List<AttributeDefinition> attributesExcludingExternalId = attributeDefinitions.stream().filter(a -> !(a.getDataType().getName().equalsIgnoreCase("externalId"))).collect(Collectors.toList());
        attributesExcludingExternalId.forEach(a -> {
            Projection projection = new Projection();
            QueryFunction qf = new NoQueryFunction();
            qf.setDataType(a.getDataType().getName());
            qf.setAlias(a.getApiName());
            QField qField = new QField().setName(a.getApiName()).setDatasetId(a.getEntityId()).setType(QField.Type.ENTITY).setDataType(a.getDataType().getName());
            qf.setColumns(List.of(qField));
            projection.setFunction(qf);
            projection.setDataType(a.getDataType().getName());
            projection.setAliasName(a.getApiName());
            projections.add(projection);
        });

        return projections;
    }

    public static AttributeDefinition createOrderBySourceParam() {
        AttributeDefinition orderBy = new AttributeDefinition();
        orderBy.setApiName("orderBy");
        orderBy.setDisplayName("Order By");
        orderBy.setDescription("This will be applied over the dataset query/configuration during sync");
        orderBy.setDataType(new StringType());
        orderBy.setNillable(false);
        orderBy.setStatus(Status.ACTIVE);
        return orderBy;
    }

    public String sanitizeApiName(String key) {
        return key.toLowerCase().replaceAll("[\\s()]", "_").replaceAll("^_+|_+$", "");
    }
}
