package com.syncari.core.service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.ParamValue;
import com.syncari.connector.data.DatastoreFieldMetadata;
import com.syncari.connector.data.DatastoreTableMetadata;
import com.syncari.connector.datastore.Datastore;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.abac.AbacContext;
import com.syncari.core.abac.AbacService;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.insights.query.InsightsQueryBuilder;
import com.syncari.core.model.*;
import com.syncari.core.model.abac.Permission;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.model.insights.*;
import com.syncari.core.model.insights.dataset.*;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.repositories.customer.CustomDatasetRepoImpl;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.utils.QueryBuilderUtil;
import com.syncari.utils.I18n;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component
public class DatasetService extends DraftService<Dataset>{

    @Autowired
    DatasetRepo datasetRepo;

    @Autowired
    DatacardService datacardService;

    @Autowired
    TagService tagService;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    InsightsQueryBuilder queryBuilder;

    @Autowired
    SchemaService schemaService;

    @Autowired
    ComponentDependencyService dependencyService;

    @Autowired
    CustomDatasetRepoImpl customDatasetRepo;

    @Autowired
    MappingGraphRepo mappingGraphRepo;

    @Autowired
    EntityDefinitionRepo entityDefinitionRepo;

    @Autowired
    DatasetSchemaService datasetSchemaService;

    @Autowired
    InsightsProviderService insightsProviderService;

    @Autowired
    InsightsProviderIntegrator insightsProviderIntegrator;

    @Autowired
    FeatureService featureService;
    
    @Autowired
    AbacService abac;

    public static final int READ_SAMPLE_LIMIT = 20;
    public static final String CTE = "WITH %s AS (%s) select * from %s limit %s offset %s";
    public static final String CTE_BASIC_MODE = "WITH %s AS (%s) select * from %s";
    public static final String CTE_WITH_ORDER_BY = "WITH %s AS (%s) select * from %s order by %s limit %s offset %s";
    public static final String CTE_BASIC_WITH_ORDER_BY = "WITH %s AS (%s) select * from %s order by %s";
    private final int DEFAULT_MAX_LIMIT = 2000;

    // create apiName using displayName if not provided
    public void setDatasetName(Dataset dataset){
        validateDataset(dataset);
        validateCondition(!StringUtils.isEmpty(dataset.getId()), i18n("dataset_with_id_already_exists"), dataset.getId());
        String apiName = StringUtils.isEmpty(dataset.getName())
                ? TextUtil.createApiName(dataset.getDisplayName())
                : dataset.getName();
        // check for duplicate name and add numbered suffix
        Set<String> existingDatsetNames = getAllDatasetsWithVersion().stream().map(d -> d.getName()).collect(Collectors.toSet());
        int i = 1;
        while(existingDatsetNames.contains(apiName)){
            apiName = apiName + "_" + i++;
        }
        dataset.setName(apiName);
    }

    public Dataset createDataset(Dataset dataset) {
        abac.check(new AbacContext().withAction(Permission.CREATE_DATASET)
            .withResourceType(ResourceType.GLOBAL).withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")), dataset);
        // build api name
        this.setDatasetName(dataset);

        // set dataset type to WORKSHEET, do not use the passed type, Passed dataset type could be table in terms of copy dataset - escalation SYN-19351
        dataset.setDatasetType(Dataset.DatasetType.WORKSHEET);

        // code to create dataset in insights provider
        this.createOrUpdateDatasetInInsightsProvider(dataset,true);

        if (!dataset.isSQLMode()){
            validateProjectionsAlias(dataset.getDatasetConfig().getProjectionsList());
        }
        dataset.setDraftStatus(DraftStatus.APPROVED);
        // update dataset Projections and from datasets
        if (dataset.isSQLMode()){
            readSampleDataForQuery(dataset,Map.of(), READ_SAMPLE_LIMIT, 0l,null);
        }
        var saved = datasetRepo.save(dataset);

        // save tags
        var tagMap = dataset.getTags().stream().collect(Collectors.toMap(t -> t.getName(), t -> t.getValue()));
        List<Tag> tags = tagService.assign(tagMap, Taggable.dataset, dataset.getId());
        dataset.setTags(tags);

        // update component dependencies
        updateDatasetDependencies(saved);

        datasetSchemaService.createDatasetSyncariSourceSchema(dataset);

        return dataset;
    }


    public void deleteDatasetInInsightsProvider(Dataset dataset){
        abac.check(new AbacContext()
            .withResourceType(ResourceType.DATASET)
            .withAction(Permission.DELETE)
            .withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")), dataset);
        if (featureService.isEnabled(Features.InsightsProvider) && !dataset.isDatasetTableType()){
            try{
                HttpHeaders headers = insightsProviderService.getHeaders(Optional.empty(),60L); // Creating 5 mins token, for searching connection and create/update connection
                insightsProviderService.deleteDataset(dataset, Optional.empty(),headers);
            }catch (Exception e){
                log.error("Dataset in provider is not deleted {}", ExceptionUtils.getStackTrace(e));
            }
        }
    }

    public void createOrUpdateDatasetInInsightsProvider(Dataset dataset, boolean isCreate){
        log.debug("Dataset Id is {}", dataset.getId());
        if (!isCreate) {
          abac.check(new AbacContext()
              .withResourceType(ResourceType.DATASET)
              .withAction(Permission.UPDATE)
              .withThrowException(true)
              .withThrowExceptionMessage(i18n("abac_permission_error")), dataset);
        }
        if (!featureService.isEnabled(Features.InsightsProvider)) return;
        Optional.ofNullable(dataset.getId()).ifPresent(id -> {
            Optional<Dataset> dbDataset = datasetRepo.findById(id);
            log.debug("DB dataset is {}", dbDataset);
            dbDataset.ifPresent(d -> {
                log.debug("DB dataset InsightsProviderId is {}", d.getInsightsProviderId());
                // update dataset with insights provider id
                dataset.setInsightsProviderId(d.getInsightsProviderId());
                dataset.setDatasetType(d.getDatasetType());
            });
        });
        if (dataset.isDatasetTableType()){
            return;
        }
        if (!dataset.isSQLMode()){
            QueryConfig queryConfig =  this.buildQueryConfigFromDataset(dataset);
            Map<String, VariableValue> variableValuesMapTobeused = this.mergeVariablesValues(dataset, Map.of());
            String query = this.buildDatasetQuery(dataset, variableValuesMapTobeused,queryConfig, new HashMap<>());
            dataset.setRawQuery(query);
        }
        // if there are variables and query contains variable usage then cannot be created in TS
        if (MapUtils.isNotEmpty(dataset.getVariablesMap()) && (null != dataset.getRawQuery()) && (dataset.getRawQuery().contains("{{"))) {
            log.info("Dataset {} with id is not updated or created as it contains variable in query", dataset.getId(), dataset.getName());
            return;
        }

        String connectionName = String.format(InsightsProviderIntegrator.CONNECTION_NAME_FORMAT,SyncariContext.getSyncariId().toUpperCase());
        boolean isRealCreate = isCreate;
        if (!isCreate){
            Optional<Dataset> datasetFromDb = this.findDataset(dataset.getId());
            // If dataset in db is type table, no need to update dataset in insights. This is a table.
            if(datasetFromDb.isPresent() && datasetFromDb.get().isDatasetTableType()){
                return;
            }
            if(datasetFromDb.isPresent()){
                dataset.setInsightsProviderId(datasetFromDb.get().getInsightsProviderId());
                dataset.setInsightsProviderSQLViewId(datasetFromDb.get().getInsightsProviderSQLViewId());
            }
            isRealCreate = isCreate || StringUtils.isEmpty(datasetFromDb.get().getInsightsProviderId());
        }

        datastoreService.refreshTokensAndUpdateThoughtSpotConnection();

        HttpHeaders headers = insightsProviderService.getHeaders(Optional.empty(),240L); // Creating 5 mins token, for searching connection and create/update connection
        Map<String, String> tsDatasetIds = insightsProviderService.createOrUpdateDataset(dataset,connectionName, Optional.empty(), isRealCreate,headers);
        if (null == tsDatasetIds){
            throw new SyncariValidationException("Dataset is not created, please reach out to support");
        }
        String sqlViewId = tsDatasetIds.get("SQL_VIEW_ID");
        String workSheetId = tsDatasetIds.get("WORKSHEET_ID");
        if (StringUtils.isNotEmpty(sqlViewId) && StringUtils.isNotEmpty(workSheetId)){
            insightsProviderIntegrator.shareWithDMGroup(List.of(workSheetId), Optional.of("LOGICAL_TABLE"),true);
            insightsProviderIntegrator.shareWithNoneGroup(List.of(workSheetId), Optional.of("LOGICAL_TABLE"));
            insightsProviderIntegrator.changeOwnerToTSAdmin(List.of(workSheetId), Optional.of("LOGICAL_TABLE"));
        }
        dataset.setInsightsProviderSQLViewId(sqlViewId);
        dataset.setInsightsProviderId(workSheetId);
    }

    /**
     * Discard the draft version of insights datasets
     * @param draft
     */
    public void discardDraftDataset(Dataset draft){
        validateCondition(!draft.isDraft(), i18n("dataset_discard_failed_no_draft"), draft.getDisplayName());
        log.info("Discarding dataset draft {}", draft.getDisplayName());
        deleteDataset(draft);
    }

    public void deleteDatasetAndUpdateConnection(String datasetName){
        if (featureService.isEnabled(Features.InsightsProvider)){
            String userToUse = TSService.TS_ADMIN_USER;
            Optional<Dataset> ds = this.findDatasetByName(datasetName);
            ds.ifPresent(d -> this.deleteDataset(d));
            try{
                insightsProviderIntegrator.createOrUpdateConnection(SyncariContext.getOrganziation().getId(), Optional.of(userToUse),false);
            }catch (Exception e){
                log.error("Exception occurred while updating connection in TS {}", e);
            }
        }
    }

    /**
     * Delete the given insights datasets
     *
     * @param dataset
     */
    public void deleteDataset(Dataset dataset) {
        validateCondition(null == dataset, i18n("dataset_null_delete_failed"));
        validateCondition(StringUtils.isEmpty(dataset.getId()), i18n("dataset_null_delete_failed"));
        validateCondition(dataset.isSeeded(), i18n("dataset_seeded_cannot_delete"));

        // find all dependent datacards and add their name in message
        List<Datacard> dependentDatacards = new ArrayList<>();
        List<Dataset> dependentDatasets = new ArrayList<>();
        List<MappingGraph> dependentPipelines = new ArrayList<>();
        List<EntityDefinition> dependentEntities = new ArrayList<>();
        getDatasetDependencies(dataset.getId()).forEach(d -> {
            if (ComponentType.dataset.equals(d.getFromComponent())) {
                findDataset(d.getFromId()).ifPresent(ds -> {
                    dependentDatasets.add(ds);
                });
            } else if (ComponentType.datacard.equals(d.getFromComponent())) {
                // retrieve datacard if exists and add it as dependency
                datacardService.findDatacard(d.getFromId()).ifPresent(datacard -> {
                    dependentDatacards.add(datacard);
                });
            } else if (ComponentType.pipeline.equals(d.getFromComponent())) {
                // retrieve pipeline if exists and add it as dependency
                mappingGraphRepo.findById(d.getFromId()).ifPresent(pipeline -> {
                    dependentPipelines.add(pipeline);
                });
            } else if (ComponentType.entity.equals(d.getFromComponent())) {
                // retrieve entities if exists and add it as dependency
                entityDefinitionRepo.findById(d.getFromId()).ifPresent(entity -> {
                    dependentEntities.add(entity);
                });
            }
        });
        List<String> dependentDatacardNames = dependentDatacards.stream().map(d -> d.getDisplayName()).collect(Collectors.toList());
        List<String> dependentDatasetNames = dependentDatasets.stream().map(d -> d.getDisplayName()).collect(Collectors.toList());
        List<String> dependentPipelineNames = dependentPipelines.stream().map(d -> d.getName()).collect(Collectors.toList());
        List<String> dependentEntityNames = dependentEntities.stream().map(d -> d.getDisplayName()).collect(Collectors.toList());
        validateCondition(CollectionUtils.isNotEmpty(dependentDatacards) || CollectionUtils.isNotEmpty(dependentDatasets),
                i18n("dataset_referred_in_other_datacards",
                        String.join(", ", dependentDatacardNames),
                        String.join(", ", dependentDatasetNames)));
        validateCondition(CollectionUtils.isNotEmpty(dependentPipelines),
                i18n("dataset_referred_in_other_sync_pipelines",
                        String.join(", ", dependentPipelineNames)));
        validateCondition(CollectionUtils.isNotEmpty(dependentEntityNames),
                i18n("dataset_referred_in_other_entities",
                        String.join(", ", dependentEntityNames)));

        log.info("Deleting dataset {}", dataset.getDisplayName());
        abac.check(new AbacContext()
            .withResourceType(ResourceType.DATASET)
            .withAction(Permission.DELETE)
            .withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")), dataset);
        delete(dataset);
        // delete all tags associated with the draft
        tagService.removeTagsFor(Taggable.dataset, dataset.getId());

        datasetSchemaService.deleteDatasetSyncariSourceSchema(dataset);

        // delete all dependencies of this dataset
        dependencyService.deleteDependenciesBy(dataset.getId(), ComponentType.dataset);
        // delete all dependencis dataset depends on
        dependencyService.deleteDependenciesOn(dataset.getId(), ComponentType.dataset);
    }



    /**
     * Approve the draft version of insights dataset
     * @param draft
     */
    public Dataset approveDraftDataset(Dataset draft, boolean validateQuery){
        validateCondition(!draft.isDraft(), i18n("dataset_approve_failed_no_draft"), draft.getDisplayName());
        validateDataset(draft);
        validateProjectionsAlias(draft.getDatasetConfig().getProjectionsList());
        // execute query before approving data to validate if query is not throwing any error.
        if (validateQuery){
            readSampleData(draft, Map.of());
        }
        // add more validations if any
        log.info("Approving dataset draft {}", draft.getDisplayName());
        var approved = approveDraft(draft);

        // associate draft tags to approved dataset if ids are different
        List<Tag> tags = tagService.updateTagIds(draft.getId(), approved.getId(), Taggable.dataset);
        approved.setTags(tags);
        return approved;
    }

    @Override
    public Dataset createDraftFor(Dataset model) {
        validateCondition(model.isSeeded(), i18n("dataset_create_draft_seeded"));
        validateCondition(!model.isApproved(), i18n("dataset_create_draft_missing_published"));
        validateCondition(hasDraft(model), i18n("dataset_draft_exists"), model.getDisplayName());
        validateProjectionsAlias(model.getDatasetConfig().getProjectionsList());
        Dataset draft = super.createDraftFor(model);
        // associate draft tags with approved dataset if ids are different but not remove from draft
        List<Tag> tags = tagService.cloneTags(model.getId(), draft.getId(), Taggable.dataset);
        draft.setTags(tags);
        return draft;
    }

    public Optional<Dataset> findDataset(String datasetId){
        return datasetRepo.findById(datasetId);
    }

    public Optional<Dataset> findDatasetByName(String name){
        return datasetRepo.findApprovedByName(name);
    }

    public Dataset getDataset(String datasetId){
        return findDataset(datasetId).orElseThrow(() -> new NotFoundException(Dataset.class, "id", datasetId));
    }

    public List<Dataset> getAllDatasetsWithVersion(){
      return (List<Dataset>) abac.check(new AbacContext().withAction(Permission.READ)
          .withResourceType(ResourceType.DATASET), datasetRepo.findAllDatasetsWithVersion());
    }

    public List<Dataset> getAllApprovedDatasetsWithVersion(){
      return (List<Dataset>) abac.check(new AbacContext().withAction(Permission.READ)
          .withResourceType(ResourceType.DATASET), datasetRepo.findAllApprovedDatasetsWithVersion());
    }

    public List<Dataset> getAllActiveDatasets(){
        return datasetRepo.findAllActiveDatasets();
    }

    public List<Dataset> getAllDraftDatasets(){
        return datasetRepo.findAllDraftDatasets();
    }

    public List<Dataset> getAllUserCreatedDatasets(){
        return datasetRepo.findAllActiveNonSeededDatasets();
    }


    public List<Dataset> getAllApprovedDatasetsFromPageCursor(PageCursor pageCursor){
        List<Dataset> datasets = List.of();
        if (null != pageCursor.getCursor()){
            datasets =  customDatasetRepo.findAllApprovedAndGreaterThanId(pageCursor.getCursor(), pageCursor.getPageSize());
        }else{
            datasets =  customDatasetRepo.findAllApprovedWithLimit(pageCursor.getPageSize());

        }
        return (List<Dataset>) abac.check(new AbacContext().withAction(Permission.READ)
            .withResourceType(ResourceType.DATASET), datasets);
    }


    /**
     * update datset
     * @param datasetId String
     * @param incoming Dataset
     */
    public Dataset updateDataset(String datasetId, Dataset incoming){
        validateCondition((null == incoming), I18n.i18n("dataset_dto_null"));
        validateCondition(StringUtils.isEmpty(datasetId),I18n.i18n("datset_id_null"));
        validateDataset(incoming);
        Dataset existing = getDataset(datasetId);
        abac.check(new AbacContext()
            .withResourceType(ResourceType.DATASET)
            .withAction(Permission.UPDATE)
            .withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")), existing);
        // add more validations if any
        log.debug("Updating dataset draft {}", existing.getDisplayName());
        existing.setDisplayName(incoming.getDisplayName());
        existing.setDescription(incoming.getDescription());
        existing.setSeeded(incoming.isSeeded());
        existing.setVersion(incoming.getVersion());
        existing.setName(incoming.getName());
        existing.setDatasetConfig(incoming.getDatasetConfig());
        existing.setVariablesMap(incoming.getVariablesMap());
        existing.setRawQuery(incoming.getRawQuery());
        existing.setInsightsProviderId(incoming.getInsightsProviderId());
        existing.setInsightsProviderSQLViewId(incoming.getInsightsProviderSQLViewId());
        existing.getDatasetConfig().setConfigMode(incoming.getDatasetConfig().getConfigMode());
        validateProjectionsAlias(existing.getDatasetConfig().getProjectionsList());

        // update dataset Projections and fromdatasets for sql mode
        if (existing.isSQLMode()){
            readSampleDataForQuery(existing,Map.of(), READ_SAMPLE_LIMIT, 0l,null);
        }

        Dataset updated = datasetRepo.save(existing);

        // save the newly added tags and delete the removed tags
        List<Tag> incomingTags = incoming.getTags();
        tagService.updateTagsFor(existing.getId(), Taggable.dataset, incomingTags);

        // update component dependencies
        updateDatasetDependencies(updated);

        datasetSchemaService.updateDatasetSyncariSourceSchema(updated);

        return updated;
    }

    /**
     * Updates the component dependencies for dataset
     * @param dataset
     */
    public void updateDatasetDependencies(Dataset dataset){
        List<ComponentDependency> dependencies = new ArrayList<>();
        if(null == dataset.getDatasetConfig())  return;
        dataset.getDatasetConfig().getFromDatasets().forEach(datasource -> {
            ComponentType depCompType = DatasourceType.DATASET.equals(datasource.getDatasetType()) ? ComponentType.dataset : ComponentType.entity;
            var dep = new ComponentDependency(dataset.getId(), ComponentType.dataset, datasource.getDatasetId(), depCompType);
            dependencies.add(dep);
        });
        dependencyService.updateDependenciesFor(dataset.getId(), ComponentType.dataset, dependencies);
    }


    @Override
    protected DraftableRepo<Dataset> getDraftableRepo() {
        return datasetRepo;
    }

    @Override
    protected void processArchived(Dataset archived) {
       archived.setName(format("%s_%s_%s", archived.getName(), archived.getId(), DELETED));
    }

    public List<AggFunctions> getAllFunctions(){
        // get all functions in AggFunctions except NONE (NoQueryFunction)
        return EnumSet.allOf(AggFunctions.class)
                .stream()
                .filter(f -> !AggFunctions.NONE.equals(f))
                .collect(Collectors.toList());
    }

    public static String formatOrderByItem(String orderByItem) {
        if (orderByItem == null || orderByItem.trim().isEmpty()) {
            return "";
        }

        String trimmed = orderByItem.trim();
        String upperTrimmed = trimmed.toUpperCase();

        if (upperTrimmed.endsWith(" ASC") || upperTrimmed.endsWith(" DESC")) {
            int lastSpaceIndex = trimmed.lastIndexOf(' ');
            String column = trimmed.substring(0, lastSpaceIndex).trim();
            String direction = trimmed.substring(lastSpaceIndex + 1).trim();

            // Quote the column name and keep direction unquoted
            return "\"" + column + "\" " + direction;
        } else {
            // No direction specified, just quote the column name
            return "\"" + trimmed + "\"";
        }
    }

    private String getQueryToExecute(String query, List<String> orderBy){
        String cteName = "syn_ds_"+System.currentTimeMillis()+"_cte";
        if (orderBy.isEmpty())
            return query;
        else
            return String.format(CTE_BASIC_WITH_ORDER_BY, cteName, query, cteName, String.join(", ", orderBy.stream()
                    .map(DatasetService::formatOrderByItem).collect(Collectors.toList())));
    }

    private String getQueryToExecuteWithLimitAndOffset(String query, List<String> orderBy, int limit, Long offset){
        String cteName = "syn_ds_"+System.currentTimeMillis()+"_cte";
        if (orderBy.isEmpty())
            return String.format(CTE, cteName, query, cteName, limit, offset);
        else
            return String.format(CTE_WITH_ORDER_BY, cteName, query, cteName, String.join(", ", orderBy.stream()
                    .map(DatasetService::formatOrderByItem).collect(Collectors.toList())), limit, offset);
    }

    public   Map<String, Object> readSampleData(Dataset dataset, Map<String, VariableValue> additionalVariableValues){
        int limit = dataset.getDatasetConfig()!= null ? dataset.getDatasetConfig().getLimit() : 0;
        if (dataset.isSQLMode()){
            return readSampleDataForQuery(dataset,additionalVariableValues,(limit == 0 || limit > 500) ? READ_SAMPLE_LIMIT : limit,0l,null);
        }
        return  readDataWithPagination(dataset, additionalVariableValues, (limit == 0 || limit > 500) ? READ_SAMPLE_LIMIT : limit,0l);
    }

    public  Map<String, Object> readDataWithPagination(Dataset dataset, Map<String, VariableValue> variableValuesMap, int limit, Long offset, List<String> orderBy){
      abac.check(new AbacContext().withAction(Permission.EXECUTE)
          .withResourceType(ResourceType.DATASET).withThrowException(true)
          .withThrowExceptionMessage(i18n("abac_permission_error")), dataset);
        if (dataset.isSQLMode()){
            return readSampleDataForQuery(dataset, variableValuesMap, limit, offset, null);
        }
        validateDataset(dataset);
        validateCondition((null == dataset.getDatasetConfig()), I18n.i18n("dataset_from_empty"));
        validateProjectionsAlias(dataset.getDatasetConfig().getProjectionsList());
        QueryConfig queryConfig =  buildQueryConfigFromDataset(dataset);
        Map<String, Object> columnsAndData = new HashMap();

        if (null != queryConfig){
            ConnectorInfo connectorInfo = datastoreService.toConnectorInfo(datastoreService.findActiveDatastore());
            assert (null != connectorInfo);

            // Set limit to dataset limit if dataset limit is smaller then vizconfig passed limit.
            if ((queryConfig.getLimit() > 0) && (queryConfig.getLimit() < limit)){
                limit = queryConfig.getLimit();
            }
            // For read sample data set the limit to 20 if it is not defined or if it is more 500
            if(limit > 0) {
                queryConfig.setLimit(Math.min(limit, DEFAULT_MAX_LIMIT));
            }
            if (offset > 0){
                queryConfig.setOffset(offset);
            }
            try{
                Map<String, VariableValue> variableValuesMapTobeused = mergeVariablesValues(dataset, variableValuesMap);
                Map<String,Datatype> variableLeftDatatype = new HashMap<>();
                String queryToExecute = buildDatasetQuery(dataset, variableValuesMapTobeused,queryConfig,variableLeftDatatype);

                // replace variable with its value
                queryToExecute = replaceVariablesWithValues(queryToExecute,variableValuesMapTobeused,variableLeftDatatype);
                // Step 2: validate query
                boolean isValidQuery = queryBuilder.validateQuery(queryToExecute);
                validateCondition(!isValidQuery, String.format("Generated query %s is not valid.", queryToExecute));
                Datastore datastore = datastoreService.getService(connectorInfo);
                Map<String, String> entityWithAliasMap = new HashMap<>();
                List<DatasetFrom> fromDatasets = queryConfig.getFromDatasets();
                fromDatasets.forEach(fD -> {
                    entityWithAliasMap.put(fD.getDatasetId(), StringUtils.isNotEmpty(fD.getAlias())? fD.getAlias() : fD.getDatastoreName());
                });
                List<QueryField> queryFields = toQueryFieldsFromProjectionList(dataset.getDatasetConfig().getProjectionsList(), null, entityWithAliasMap);
                List<DatastoreFieldMetadata> fields = new LinkedList<>();
                queryFields.forEach(c -> {
                    DatastoreFieldMetadata datastoreFieldMetadata = new DatastoreFieldMetadata();
                    datastoreFieldMetadata.setFieldExpression(c.getQueryFunction().buildExpression("\"", entityWithAliasMap));
                    datastoreFieldMetadata.setAliasName(c.getAlias());
                    datastoreFieldMetadata.setDisplayFormat(null != c.getDisplayFormat() ? c.getDisplayFormat() : StringUtils.isNotEmpty(c.getQueryFunction().getDataType()) ? c.getQueryFunction().getDataType()
                            : "string");
                    fields.add(datastoreFieldMetadata);
                });
                columnsAndData.put("columns", fields);
                String cteToExecute = getQueryToExecute(queryToExecute, orderBy); // add order by if orderBy exists
                columnsAndData.put("data", datastore.retrievePairData(connectorInfo, cteToExecute, fields));
                return columnsAndData;
            }catch (SyncariValidationException e){
                log.error("SyncariValidationException occurred with message {}", e.getMessage());
                throw e;
            }catch (NonRetriableException e){
                log.error("Could not execute query, there may be something wrong in configuration {}", ExceptionUtils.getStackTrace(e));
                if (e.getErrorCode().equals(ErrorCodes.DATA_NOT_FOUND.name())){
                    throw new SyncariValidationException(i18n("dataset_no_data"));
                }else{
                    throw new SyncariValidationException(i18n("dataset_query_failure", e.getMessage()));
                }
            }  catch (Exception exception){
                log.error("Could not execute query, there may be something wrong in configuration {}", ExceptionUtils.getStackTrace(exception));
                throw new SyncariValidationException(i18n("dataset_query_failure", exception.getMessage()));
            }
        }
        return Map.of();
    }

    public  Map<String, Object> readDataWithPagination(Dataset dataset, Map<String, VariableValue> variableValuesMap, int limit, Long offset){
        return readDataWithPagination(dataset, variableValuesMap, limit, offset, List.of());
    }

    public Map<String, Object> readFromDatasetQuery(Dataset dataset, int limit, Long offset, List<String> orderBy) {
        if (!dataset.isSQLMode()) {
            return Map.of();
        }
        ConnectorInfo connectorInfo = datastoreService.toConnectorInfo(datastoreService.findActiveDatastore());
        assert (null != connectorInfo);
        //using timestamp as if this fails due to cte name conflict, there are high chances that this succeeds while retrying.
        String queryToExecute = getQueryToExecuteWithLimitAndOffset(dataset.getRawQuery(), orderBy, limit, offset);
        Map<String, Object> columnsAndData = new HashMap<>();
        try{
            queryBuilder.validateQuery(queryToExecute);
            Datastore datastore = datastoreService.getService(connectorInfo);
            List<DatastoreFieldMetadata> fields = new LinkedList<>();
            Set<DatastoreTableMetadata> tableMetadataSet = new LinkedHashSet<>();
            columnsAndData.put("data", datastore.retrievePairData(connectorInfo, queryToExecute, Map.of(), fields,tableMetadataSet));
            columnsAndData.put("columns", fields);
            updateDataset(dataset, fields, tableMetadataSet);
        }catch (SyncariValidationException e){
            log.error("SyncariValidationException occurred for dataset {} with query {} with message {}", dataset.getId(), queryToExecute, e.getMessage());
            throw e;
        }catch (Exception exception){
            log.error("Could not execute query {} for dataset {}, there may be something wrong in configuration {}", queryToExecute, dataset.getId(), ExceptionUtils.getStackTrace(exception));
            throw new SyncariValidationException(i18n("dataset_query_failure", exception.getMessage()));
        }
        return columnsAndData;
    }

    public Map<String, Object> readSampleDataForQuery(Dataset dataset,Map<String, VariableValue> variableValuesMap,int limit, Long offset, String prependCtx){
        ConnectorInfo connectorInfo = datastoreService.toConnectorInfo(datastoreService.findActiveDatastore());
        assert (null != connectorInfo);
        String queryToExecute = dataset.getRawQuery();
        Map<String, Object> columnsAndData = new HashMap();


        try{
            // Step 2: validate query
            StringBuffer query = new StringBuffer(queryToExecute);
            if ((limit > 0) && (!queryToExecute.toLowerCase().contains("limit"))){
                query.append(" limit " + limit);
            }
            if ((offset > 0) && (!queryToExecute.toLowerCase().contains("offset"))){
                query.append(" offset " + offset);
            }
            if (StringUtils.isNotEmpty(prependCtx)){
                query.insert(0, prependCtx);
            }
            Map<String, VariableValue> mergedVariables = mergeVariablesValues(dataset,variableValuesMap);
            log.info("Merged Variables {}" , mergedVariables);
            queryBuilder.validateQuery(query.toString());
            Datastore datastore = datastoreService.getService(connectorInfo);
            List<DatastoreFieldMetadata> fields = new LinkedList<>();
            Set<DatastoreTableMetadata> tableMetadataSet = new LinkedHashSet<>();
            Map<Integer, ParamValue> paramValues = new HashMap<>();
            String preparedStatement = generatePreparedStmtAndParams(query.toString(),paramValues,mergedVariables);
            log.info("paramValues of prepared statement {}" , paramValues);
            // this is to retrieve pair data
            columnsAndData.put("data", datastore.retrievePairData(connectorInfo, preparedStatement,paramValues, fields,tableMetadataSet));
            columnsAndData.put("columns", fields);

            // Update datasetconfig projections and table information
            updateDataset(dataset, fields, tableMetadataSet);
        }catch (SyncariValidationException e){
            log.error("SyncariValidationException occurred with message {}", e.getMessage());
            throw e;
        }catch (NonRetriableException e){
            if (e.getErrorCode().equals(ErrorCodes.TABLE_NOT_FOUND.name())){
                // write logic to check if tablename provided exists as dataset or not, if it exists and make a recursive call to same method  after updating queries to use CTX
                String relationName = getRelationFromErrorMessage(e.getMessage());
                Optional<Dataset> innerDataset = this.findDatasetByName(relationName);
                if (innerDataset.isPresent()) {
                    String innerQueryCtx = this.findInnerDatasetQueryAsCTE(innerDataset.get(),relationName, prependCtx);
                    if (StringUtils.isNotEmpty(innerQueryCtx)){
                        Map<String, VariableValue> variableValuesMapTobeused = this.mergeVariablesValues(innerDataset.get(), variableValuesMap);
                        Map<String, Object> result =  readSampleDataForQuery(dataset, variableValuesMapTobeused, limit, offset,innerQueryCtx);
                        List<DatasetFrom> fromList = new ArrayList<>();
                        fromList.addAll(dataset.getDatasetConfig().getFromDatasets());
                        fromList.add(new DatasetFrom().setDatasetId(innerDataset.get().getId()).setDatasetType(DatasourceType.DATASET)
                                .setDisplayName(innerDataset.get().getDisplayName()).setApiName(innerDataset.get().getName()).setAlias(innerDataset.get().getDisplayName()));

                       this.mergeVariablesToDataset(dataset, innerDataset.get().getVariablesMap());
                        dataset.getDatasetConfig().setFromDatasets(fromList);
                        return result;
                    }else{
                        throw e;
                    }
                }else{
                    throw e;
                }
            }else if (e.getErrorCode().equals(ErrorCodes.DATA_NOT_FOUND.name())){
                throw new SyncariValidationException(i18n("dataset_no_data"));
            }else{
                log.error("Could not execute query, there may be something wrong in configuration {}", ExceptionUtils.getStackTrace(e));
                throw new SyncariValidationException(i18n("dataset_query_failure", e.getMessage()));
            }
        }  catch (Exception exception){
            log.error("Could not execute query, there may be something wrong in configuration {}", ExceptionUtils.getStackTrace(exception));
            throw new SyncariValidationException(i18n("dataset_query_failure", exception.getMessage()));
        }
        return columnsAndData;
    }

    private String getRelationFromErrorMessage(String errorMessage) {
        String schemaName = datastoreService.getSyncariSchema(SyncariContext.getSyncariId());
        int beginIndex = errorMessage.indexOf("relation \"") + "relation \"".length();
        int endIndex = errorMessage.indexOf("\" does ");
        String relationName = errorMessage.substring(beginIndex, endIndex);
        if (relationName.contains(schemaName)){
            relationName = relationName.split(".")[1];
        }
        return relationName;
    }

    private String findInnerDatasetQueryAsCTE(Dataset innerDataset,String relationName, String prependCtx){
        StringBuilder ctx = new StringBuilder();
        if (innerDataset.isSQLMode()){
            String query = innerDataset.getRawQuery();
            ctx.append(datastoreService.generateCTE(relationName, query));
        }else{
            QueryConfig queryConfig =  this.buildQueryConfigFromDataset(innerDataset);
            Map<String, VariableValue> variableValuesMapTobeused = this.mergeVariablesValues(innerDataset, Map.of());
            String query = this.buildDatasetQuery(innerDataset, variableValuesMapTobeused,queryConfig, new HashMap<>());
            ctx.append(datastoreService.generateCTE(relationName, query));
        }
        if (StringUtils.isNotEmpty(prependCtx)){
            ctx.insert(0, prependCtx);
        }
        return ctx.toString();
    }

    private String generatePreparedStmtAndParams(String query, Map<Integer, ParamValue> paramValues, Map<String, VariableValue> variableValueMap){
        String []splittedArray = StringUtils.substringsBetween(query, "{{","}}");
        if (ArrayUtils.isNotEmpty(splittedArray) && MapUtils.isNotEmpty(variableValueMap)){
            for (int i = 0; i < splittedArray.length; i++) {
                if (query.contains(splittedArray[i])  && (variableValueMap.containsKey(splittedArray[i]))){
                    VariableValue value = variableValueMap.get(splittedArray[i]);
                    String val = QueryBuilderUtil.getValue(value.getDefaultValue());
                    ParamValue paramValue = new ParamValue().setParamValue(getVariableValue(value,splittedArray[i],Map.of())).
                            setParamName(splittedArray[i]).setParamNumber(i).setParamDataType(value.getDatatype());
                    paramValues.put(i,paramValue);
                    if (query.contains("%{{"+splittedArray[i]+"}}%")){
                        paramValue.setParamValue("%"+val+"%");
                    }else if (!query.contains("%{{"+splittedArray[i]+"}}%") && query.contains("{{"+splittedArray[i]+"}}%")){
                        paramValue.setParamValue(val + "%");
                    }else if (!query.contains("%{{"+splittedArray[i]+"}}%") && (!query.contains("{{"+splittedArray[i]+"}}%")) && query.contains("%{{"+splittedArray[i]+"}}")){
                        paramValue.setParamValue("%" + val );
                    }
                    query = query.replaceFirst("[']*[%]*\\{\\{" +splittedArray[i] +"\\}\\}[%]*[']*","?");
                }
            }
        }
        return query;
    }

    public String replaceVariablesWithValues(String query, Map<String, VariableValue> variableValueMap, Map<String, Datatype> variableLeftDataType){
        String []splittedArray = StringUtils.substringsBetween(query, "{{","}}");
        if (ArrayUtils.isNotEmpty(splittedArray) && MapUtils.isNotEmpty(variableValueMap)){
            for (int i = 0; i < splittedArray.length; i++) {
               if (query.contains(splittedArray[i]) && (variableValueMap.containsKey(splittedArray[i]))){
                   VariableValue value = variableValueMap.get(splittedArray[i]);
                   Object converted = getVariableValue(value, splittedArray[i], variableLeftDataType);
                   query = query.replace("{{" +splittedArray[i] +"}}",converted.toString());
                }
            }
        }
        return query;
    }

    private Object getVariableValue(VariableValue val, String varName,Map<String, Datatype> variableLeftDataType){
        String valueToBeReplacedWith = QueryBuilderUtil.getValue(val.getDefaultValue());
        if (isVariableDatetime(varName,val.getDatatype())){
            valueToBeReplacedWith = QueryBuilderUtil.getExpressionForCurrentAnnotation(valueToBeReplacedWith);
        }

        if (MapUtils.isNotEmpty(variableLeftDataType) && (null != variableLeftDataType.get(varName))){
            return convert(variableLeftDataType.get(varName), valueToBeReplacedWith);
        }else if (null != val.getDatatype()){
            return convert(DatatypeFactory.getDatatype(val.getDatatype()), valueToBeReplacedWith);
        }else{
            return valueToBeReplacedWith;
        }
    }

    private Object convert(Datatype datatype, String value){
        Object converted = getDataTypeSpecificConversion(datatype.convert(StringUtils.strip(value, "\'")));
        if (StringUtils.isNotEmpty(value) && (null == converted)){
            return StringUtils.strip(value, "\'");
        }
        return converted;
    }

    Object getDataTypeSpecificConversion(Object converted) {
        if(converted == null) return converted;
        if(converted instanceof ZonedDateTime) {
            return Date.from(((ZonedDateTime)converted).toInstant());
        }
        return converted;
    }


    private boolean isVariableDatetime(Object val, String dataType){
        if (QueryBuilderUtil.isValueVariable(val)){
            return ((StringUtils.isNotEmpty(dataType)) && (dataType.equalsIgnoreCase("datetime")));
        }
        return false;
    }

    private Dataset updateDataset(Dataset dataset, List<DatastoreFieldMetadata> fields,Set<DatastoreTableMetadata> tableMetadataSet){
        DatasetConfig config = dataset.getDatasetConfig();
        List<Projection> projections = new ArrayList<>();
        List<DatasetFrom> fromDatasets = new ArrayList<>();
        String syncariConnectorId = connectorService.getSyncariConnector().getId();
        List<EntityDefinition> edefs = schemaService.getEntities(syncariConnectorId);
        Map<String, String> eIdDsNameMap =  edefs.stream().collect(Collectors.toMap(k -> k.getDataStoreName().toLowerCase(), v -> v.getId()));

        tableMetadataSet.forEach(t -> {
            if (MapUtils.isNotEmpty(eIdDsNameMap) && (null != eIdDsNameMap.get(t.getTableName()))){
                DatasetFrom from = new DatasetFrom().setApiName(t.getTableName())
                        .setDisplayName(t.getAlias()).setAlias(t.getAlias()).setSchemaName(t.getSchemaName()).setDatasetType(DatasourceType.ENTITY);
                from.setDatasetId(eIdDsNameMap.get(t.getTableName()));
                fromDatasets.add(from);
            }
        });
        fields.forEach(f -> {
            Projection p = new Projection().setAliasName(f.getAliasName()).setDataType(f.getDataType());
            QField qField = new QField().setDataType(f.getDataType()).setName(f.getApiName()).setType(QField.Type.ENTITY).setDatasourceAlias(f.getTableName());
            QueryFunction queryFunction = new NoQueryFunction().setFunction(AggFunctions.NONE).setDataType(f.getDataType()).setAlias(f.getAliasName());
            if (MapUtils.isNotEmpty(eIdDsNameMap) && (null != eIdDsNameMap.get(f.getTableName()))){
                qField.setDatasetId(eIdDsNameMap.get(f.getTableName()));

            }
            queryFunction.setColumns(List.of(qField));
            p.setFunction(queryFunction);
            projections.add(p);
        });

        config.setProjectionsList(projections);
        config.setFromDatasets(fromDatasets);
        dataset.setDatasetConfig(config);
        return dataset;
    }

    public Map<String, VariableValue> mergeVariablesValues(Dataset dataset, Map<String, VariableValue> variableValuesMap){
        Map<String, VariableValue> variableValuesMapTobeused = new HashMap<>();
        if (MapUtils.isNotEmpty(variableValuesMap)){
            variableValuesMapTobeused.putAll(variableValuesMap);
        }
        if (MapUtils.isNotEmpty(dataset.getVariablesMap())){
            Map<String, Variable> variableMap = dataset.getVariablesMap();
            variableMap.keySet().forEach(k -> {
                if(!variableValuesMapTobeused.containsKey(k)) {
                    variableValuesMapTobeused.put(k, variableMap.get(k).getVariableValue());
                }
            });

            // check if all variable values are provided
            dataset.getVariablesMap().forEach((k, v) -> {
                validateCondition(!variableValuesMapTobeused.containsKey(k), i18n("error_dataset_missing_variable_value", k));
                var val = variableValuesMapTobeused.get(k).getDefaultValue();
                if ((null != dataset.getDatasetConfig()) && MapUtils.isNotEmpty(dataset.getDatasetConfig().getPredicate()) &&
                        (dataset.getDatasetConfig().getPredicate().toString().contains(k))){
                    validateCondition(Objects.isNull(val) || StringUtils.isEmpty(val.toString()), i18n("error_dataset_missing_variable_value", k));
                }
                if (dataset.isSQLMode() && dataset.getRawQuery().contains(k)){
                    validateCondition(Objects.isNull(val) || StringUtils.isEmpty(val.toString()), i18n("error_dataset_missing_variable_value", k));
                }
            });
        }
        return variableValuesMapTobeused;
    }

    public void mergeVariablesToDataset(Dataset dataset, Map<String, Variable> variableMap){
        Map<String, Variable> variableMapTobeused = new HashMap<>();
        if (MapUtils.isNotEmpty(variableMap)){
            variableMapTobeused.putAll(variableMap);
        }
        if (MapUtils.isNotEmpty(dataset.getVariablesMap())){
            Map<String, Variable> vMap = dataset.getVariablesMap();
            vMap.keySet().forEach(k -> {
                if(!variableMapTobeused.containsKey(k)) {
                    variableMapTobeused.put(k, vMap.get(k));
                }
            });
        }
        dataset.setVariablesMap(variableMapTobeused);
    }

    public String buildDatasetQuery(Dataset dataset, Map<String, VariableValue> variableValuesMapTobeused,
                                    QueryConfig queryConfig,Map<String, Datatype> variableLeftTypes){
        if (!dataset.isSQLMode()){
            validateDataset(dataset);
            validateCondition((null == dataset.getDatasetConfig()), I18n.i18n("dataset_from_empty"));
            validateProjectionsAlias(dataset.getDatasetConfig().getProjectionsList());
        }

        // build variable map for data
        if (null != queryConfig){
            ConnectorInfo connectorInfo = datastoreService.toConnectorInfo(datastoreService.findActiveDatastore());
            assert (null != connectorInfo);
            try{
                String queryToExecute = queryBuilder.buildQuery(queryConfig, connectorInfo, Optional.ofNullable(dataset.getId()), variableValuesMapTobeused, variableLeftTypes);
                // Step 2: validate query
                boolean isValidQuery = queryBuilder.validateQuery(queryToExecute);
                validateCondition(!isValidQuery, String.format("Generated query %s is not valid.", queryToExecute));
                return queryToExecute;
            }catch (SyncariValidationException e){
                log.error("SyncariValidationException occurred with message {}", e.getMessage());
                throw e;
            }catch (NonRetriableException e){
                log.error("Could not build query, there may be something wrong in configuration {}", ExceptionUtils.getStackTrace(e));
                throw new SyncariValidationException(i18n("dataset_query_failure", e.getMessage()));
            }  catch (Exception exception){
                log.error("Could not build query, there may be something wrong in configuration {}", ExceptionUtils.getStackTrace(exception));
                throw new SyncariValidationException(i18n("dataset_query_failure", exception.getMessage()));
            }
        }
        return null;
    }

    public Long getOffsetBasedOnDirection(Long offset, PageDirection pageDirection, Integer limit){
        switch (pageDirection){
            case next:return offset;
            case previous:return Math.max(0,offset-limit);
            default:return offset;
        }
    }

    public DatasetPageInfo addPageInfo(Long recordsCount, Long offset, Dataset datasetForCount, Long totalCount){
        DatasetPageInfo pageInfo = new DatasetPageInfo();
        final Long endOffset = offset+recordsCount;
        if (offset == 0){
            Map<String, Object> dataAndCols = readSampleData(datasetForCount, Map.of());
            List<Map<String, Object>> dataMap = (List<Map<String, Object>>)dataAndCols.getOrDefault("data", List.of());
            dataMap.stream().findFirst().ifPresentOrElse(countMap -> {
                if (null != countMap.get("totalCount")){
                    pageInfo.setTotalCount(Long.valueOf(countMap.get("totalCount").toString()));
                    Long tCount = Long.valueOf(countMap.get("totalCount").toString());
                    pageInfo.setHasMore(endOffset < tCount);
                    pageInfo.setHasPrevious(false);
                }
            },()-> log.info("Could not calculate count for dataset {}", datasetForCount));
        }else {
            pageInfo.setTotalCount(totalCount);
            pageInfo.setHasMore(endOffset < totalCount);
            pageInfo.setHasPrevious(true);
        }
        pageInfo.setStart(""+offset);
        pageInfo.setEnd(""+endOffset);
        return pageInfo;
    }


    public List<DateGroupByOption> getAllGroupByTimeOptions(Optional<String> dataType){
        if (dataType.isPresent()){
            // get filtered group by data type
            return EnumSet.allOf(DateGroupByOption.class)
                    .stream().filter(f -> dataType.get().equalsIgnoreCase(f.getCompatibleDataType()))
                    .collect(Collectors.toList());
        }else{
            // get all group of time grain options
            return EnumSet.allOf(DateGroupByOption.class)
                    .stream()
                    .collect(Collectors.toList());
        }
    }

    public QueryConfig buildQueryConfigFromDataset(Dataset dataset){
        assert (null != dataset);
        DatasetConfig datasetConfig = dataset.getDatasetConfig();
        assert (null != datasetConfig);

        List<Projection> projectionList = datasetConfig.getProjectionsList();
        Map<String, String> datasetIdWithAliasMap = new HashMap<>();
        datasetConfig.getFromDatasets().forEach(f -> {
            datasetIdWithAliasMap.put(f.getDatasetId(), StringUtils.isNotEmpty(f.getAlias()) ? f.getAlias() : f.getApiName());
        });
        QueryConfig queryConfig = new QueryConfig().setColumns(toQueryFieldsFromProjectionList(projectionList, null,datasetIdWithAliasMap));
        queryConfig.setFromDatasets(datasetConfig.getFromDatasets());

        Map<String, Object> predicateFromDataset = datasetConfig.getPredicate();

        // set grouping columns
        queryConfig.setGroupingColumns(datasetConfig.getAggregate());
        Map<String, Object> predicateMap = new HashMap<>();

        if (CollectionUtils.isNotEmpty(datasetConfig.getOrder())){
            queryConfig.setSortList(datasetConfig.getOrder());
        }
        // Map date filter to QueryConfig predicate And operator
        if (MapUtils.isNotEmpty(predicateFromDataset)){
            predicateMap.putAll(predicateFromDataset);
            queryConfig.setPredicate(predicateMap);
        }
        List<Join> joins = datasetConfig.getJoin();
        if (CollectionUtils.isNotEmpty(joins) && (null != joins.stream().findFirst().get().getDatasetFieldFrom())){
            queryConfig.setJoins(joins);
        }

        int limit = datasetConfig.getLimit();
        if (limit > 0){
            queryConfig.setLimit(limit);
        }
        queryConfig.setGroup(datasetConfig.isGroup());
        return queryConfig;
    }

    public List<QueryField> toQueryFieldsFromProjectionList(List<Projection> projections, String displayFormat, Map<String, String> entityIdWithAlias){
        List<QueryField> queryFields = new LinkedList<>();
        projections.forEach(projection -> {
            QueryFunction queryFunction = projection.getFunction();
            if (queryFunction instanceof NoQueryFunction){
                QueryField field = new SimpleQField();
                field.setQueryFunction(queryFunction);
                field.setDisplayFormat(displayFormat);
                queryFields.add(field);
            }else{
                QueryField field = new ComplexQField();
                field.setQueryFunction(queryFunction);
                field.setDisplayFormat(displayFormat);
                queryFields.add(field);
            }
            if (StringUtils.isEmpty(queryFunction.getAlias())){
                queryFunction.setAlias(queryFunction.buildExpression("\"", entityIdWithAlias).replace("\"", "").trim());
            }
            log.info("QueryFunction used is {}", queryFunction );

        });
        return queryFields;
    }

    public Variable createVariable(String datasetId, Variable var){
        validateCondition(StringUtils.isEmpty(datasetId), I18n.i18n("datasetid_empty"));
        validateCondition(null == var, I18n.i18n("variable_null"));
        Optional<Dataset> dataset = datasetRepo.findById(datasetId);
        validateCondition(!dataset.isPresent(), I18n.i18n("datasetid_empty"));
        Variable varCopy = var.makeCopy();
        dataset.ifPresent(ds -> {
            Map<String, Variable> allVars = ds.getVariablesMap();
            if (MapUtils.isEmpty(allVars)){
                allVars = new HashMap<>();
            }
            // Create a APIname
            String varApiname = TextUtil.createApiName(varCopy.getDisplayName());
            int i = 1;
            while (allVars.containsKey(varApiname) && (i < 4)){
                varApiname = schemaService.populateApiNameWithCounter(varApiname);
                i++;
            }
            // TODO: Validation (required variable defaultValue and others)
            varCopy.setApiName(varApiname);
            varCopy.setUpdatable(true); // all variables are updatable in dataset
            allVars.put(varApiname,varCopy);
            ds.setVariablesMap(allVars);
            datasetRepo.save(ds);
        });
        return varCopy;
    }

    public Variable updateVariable(String datasetId, Variable var){
        validateCondition(StringUtils.isEmpty(datasetId), I18n.i18n("datasetid_empty"));
        validateCondition(null == var, I18n.i18n("variable_null"));
        // Apiname should exists for variables update
        String varApiname = var.getApiName();
        validateCondition(StringUtils.isEmpty(varApiname), I18n.i18n("apiName_empty"));
        Optional<Dataset> dataset = datasetRepo.findById(datasetId);
        Variable varCopy = var.makeCopy();
        dataset.ifPresent(ds -> {
            Map<String, Variable> allVars = ds.getVariablesMap();
            if (MapUtils.isEmpty(allVars)){
                allVars = new HashMap<>();
            }
            allVars.put(varApiname,varCopy);
            ds.setVariablesMap(allVars);
            // TODO: Validation (required variable defaultValue and others)
            datasetRepo.save(ds);
        });
        return varCopy;
    }

    public List<Variable> getVariables(String datasetId){
        validateCondition(StringUtils.isEmpty(datasetId), I18n.i18n("datasetid_empty"));
        Optional<Dataset> dataset = datasetRepo.findById(datasetId);
        final List<Variable> allVariables = new ArrayList<>();
        dataset.ifPresent(ds -> {
            Map<String, Variable> allVars = ds.getVariablesMap();
            if (MapUtils.isNotEmpty(allVars)){
                allVariables.addAll(allVars.values().stream().collect(Collectors.toList()));
            }
        });
        return allVariables;
    }

    public Variable getVariable(String datasetId, String variableApiName){
        validateCondition(StringUtils.isEmpty(datasetId), I18n.i18n("datasetid_empty"));
        validateCondition(StringUtils.isEmpty(variableApiName), I18n.i18n("apiName_empty"));
        Optional<Dataset> dataset = datasetRepo.findById(datasetId);
        Variable result = null;
        if (dataset.isPresent()){
            Dataset ds = dataset.get();
            Map<String, Variable> allVars = ds.getVariablesMap();
            result = allVars.get(variableApiName);
        }
        return result;
    }

    public Variable deleteVariable(String datasetId, String variableApiName){
        validateCondition(StringUtils.isEmpty(datasetId), I18n.i18n("datasetid_empty"));
        validateCondition(StringUtils.isEmpty(variableApiName), I18n.i18n("apiName_empty"));
        validateCondition(isVariableUsedInAnyDatacard(datasetId, variableApiName), I18n.i18n("variable_used_indatacard"));
        Optional<Dataset> dataset = datasetRepo.findById(datasetId);
        Variable result = null;
        if (dataset.isPresent()){
            Dataset ds = dataset.get();
            Map<String, Variable> allVars = ds.getVariablesMap();
            result = allVars.get(variableApiName);
            if (null != result){
                allVars.remove(variableApiName);
                dataset.get().setVariablesMap(allVars);
                datasetRepo.save(dataset.get());
            }
        }
        return result;
    }
    // Todo: Implement this method
    public boolean isVariableUsedInAnyDatacard(String datasetId, String varApiName){
        return false;
    }

    private void validateDataset(Dataset dataset){
        // TODO: remove this condition once all seeded datasets are migrated to newer model
        validateCondition((null == dataset), I18n.i18n("dataset_dto_null"));
        if(dataset.isSeeded()) return; // dont validate seeded dataset as they are going to be migrated to newer version
        validateCondition((StringUtils.isEmpty(dataset.getDisplayName())), I18n.i18n("dataset_empty_displayname"));
        validateCondition(dataset.getDatasetConfig() == null, I18n.i18n("error_dataset_missing_config"));
        if (dataset.isSQLMode()) return;
        dataset.getDatasetConfig().validate();
    }

    private void validateProjectionsAlias(List<Projection> projections){
        List<String> allProjAliases = projections.stream().map(p -> p.getAliasName()).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(allProjAliases)){
            List<String> aliasNames = findDuplicates(allProjAliases);
            validateCondition((CollectionUtils.isNotEmpty(aliasNames)), I18n.i18n("same_aliasname_dataset"));
        }
    }

    private List<String> findDuplicates(List<String> duplicates){
       return duplicates.stream().distinct().filter(entry -> Collections.frequency(duplicates, entry) > 1).collect(Collectors.toList());
    }

    public List<Join> fetchAutoJoins(List<DatasetFrom> existingSources, List<DatasetFrom> newDataSources){
        List<Join> existingJoins = this.fetchAutoJoinSuggestions(existingSources);
        List<DatasetFrom> combinedDataSources = new ArrayList<>();
        combinedDataSources.addAll(existingSources);
        combinedDataSources.addAll(newDataSources);

        List<Join> combinedJoins = this.fetchAutoJoinSuggestions(combinedDataSources);
        // remove existingJoins from combinedJoins
        combinedJoins.removeAll(existingJoins);
        return combinedJoins;
    }

    public List<Join> fetchAutoJoinSuggestions(List<DatasetFrom> entitySources) {
        if ((CollectionUtils.isEmpty(entitySources)) || (entitySources.size() == 1)){
            return List.of();
        }
        Set<Join> joins = new LinkedHashSet<>();
        // collecting it into list
        List<String> entities = entitySources.stream().map(d -> d.getApiName()).collect(Collectors.toList());
        List<Reference> references = new ArrayList<>();
        entities.forEach(entity -> {
            schemaService.getSyncariEntityByName(entity).ifPresent(ed -> {
                references.addAll(schemaService.getReferringAttributes(ed));
            });
        });
        Map<String, Long> frequencyCounter = entitySources.stream().collect(Collectors.groupingBy(d -> d.getDatasetId(), Collectors.counting()));

        Map<String, String> datasetIdAliasMap = new HashMap<>();
        entitySources.forEach(es -> {
           datasetIdAliasMap.put(es.getDatasetId(), StringUtils.isNotEmpty(es.getAlias())? es.getAlias() : "temp_from_"+es.getDatasetId());
        });

        references.forEach(ref -> {
            // check if both from and to entities are in the list
            if(entities.contains(ref.getFromEntity().getApiName()) && entities.contains(ref.getToEntity().getApiName())){
                // check if its self reference and include only if there are more than one occurence in list
                if(ref.getFromEntity().getApiName().equals(ref.getToEntity().getApiName())){
                    // TODO: add self join
                    /*if(Collections.frequency(entities, ref.getFromEntity().getApiName()) > 1)*/
                    // for now skip self join
                } else {
                    Join join = new Join();
                    join.setDatasetFieldFrom(new QField().setDatasetId(ref.getFromAttribute().getEntityId()).setName(ref.getFromAttribute().getApiName())
                            .setDatasourceAlias(datasetIdAliasMap.getOrDefault(ref.getFromAttribute().getEntityId(),"")));
                    join.setDatasetFieldTo(new QField().setDatasetId(ref.getToAttribute().getEntityId()).setName(ref.getToAttribute().getApiName())
                            .setDatasourceAlias(datasetIdAliasMap.getOrDefault(ref.getToAttribute().getEntityId(),"")));
                    join.setJoinType(JoinType.Inner);
                    joins.add(join);
                }
            }
        });
        // Self join logic
        if (MapUtils.isNotEmpty(frequencyCounter) && CollectionUtils.isNotEmpty(frequencyCounter.values().stream().filter(f -> f > 1).collect(Collectors.toList()))){
            List<Map.Entry<String, Long>> entitiesEntry = frequencyCounter.entrySet().stream().filter(e -> e.getValue() > 1).collect(Collectors.toList());
            // Iterate entities for more than 1 and build datasetId ->  aliasMap to add self joins
            Map<String, List<String>> aliasMap = new HashMap<>();
            entitiesEntry.forEach(e -> {
                String dsId = e.getKey();
                List<DatasetFrom> fromList = entitySources.stream().filter(eS -> eS.getDatasetId().equals(dsId)).collect(Collectors.toList());
                if (!aliasMap.containsKey(dsId)){
                    aliasMap.put(dsId,fromList.stream().map(fL -> fL.getAlias()).collect(Collectors.toList()));
                }
            });
            aliasMap.entrySet().forEach(es -> {
                String dsId = es.getKey();
                List<String> aliases = es.getValue();
                for (int i =0; i < aliases.size()-1;i++){
                    Join join = new Join();
                    join.setDatasetFieldFrom(new QField().setDatasetId(dsId).setName("syncariid").setDatasourceAlias(aliases.get(i)));
                    join.setDatasetFieldTo(new QField().setDatasetId(dsId).setName("syncariid").setDatasourceAlias(aliases.get(i+1)));
                    join.setJoinType(JoinType.Inner);
                    joins.add(join);
                }
            });
        }
        return  new ArrayList<>(joins);
    }

    public List<ComponentDependency> getDatasetDependencies(String datasetId){
        return dependencyService.findDependenciesFor(datasetId, ComponentType.dataset);
    }
}
