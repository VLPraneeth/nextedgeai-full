package com.syncari.core.service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.datastore.Datastore;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.query.InsightsQueryBuilder;
import com.syncari.core.model.*;
import com.syncari.core.model.insights.*;
import com.syncari.core.model.insights.dataset.*;
import com.syncari.core.repositories.customer.DatacardRepo;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.repositories.customer.InsightsDashboardRepo;
import com.syncari.core.repositories.customer.InsightsUserPreferenceRepo;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Slf4j
@Component
public class InsightsService {

    @Autowired
    InsightsDashboardRepo dashboardRepo;

    @Autowired
    DatacardRepo datacardRepo;

    @Autowired
    InsightsQueryBuilder queryBuilder;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    DatasetRepo datasetRepo;

    @Autowired
    FeatureService featureService;

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    DatacardService datacardService;

    @Autowired
    DatasetService datasetService;

    @Autowired
    InsightsUserPreferenceRepo insightsUserPreferenceRepo;

    @Autowired
    UserService userService;

    @Autowired
    InsightsSharingService sharingService;


    public void provision(){
        // Provision insights
        // Step 1: refresh schema and create missing fields
        schemaService.getSyncariEntities().forEach(entity -> {
            entity.setAttributes(schemaService.getAttributesByEntityId(entity.getId()));
            datastoreService.createEntity(entity);
        });
    }

    public InsightsDashboard getDashboard(String dashboardId){
        return findDashboard(dashboardId).orElseThrow(() -> new NotFoundException(InsightsDashboard.class, "id", dashboardId));
    }

    public Optional<InsightsDashboard> findDashboard(String dashboardId){
        return dashboardRepo.findById(dashboardId);
    }

    public void setLastVisitedUserDashboard(String userId, String dashboardId){
        // validate dashboard if exists
        validateCondition(findDashboard(dashboardId).isEmpty(), String.format("Invalid dashboard with Id %s", dashboardId));
        User user = userService.findUserById(userId)
                .orElseThrow(() -> new NotFoundException(User.class, "userId", userId));
        validateCondition(!user.isActive(), "User is not Active");
        var insightsUserPref =  insightsUserPreferenceRepo.findByUserId(userId)
                .orElse(new InsightsUserPreference().setUserId(userId));
        insightsUserPref.setLastVisitedDashboardId(dashboardId);
        insightsUserPreferenceRepo.save(insightsUserPref);
    }

    public InsightsDashboard getLastVisitedUserDashboard(String userId){
        Optional<InsightsUserPreference> insightsUserPreference = insightsUserPreferenceRepo.findByUserId(userId);
        if(insightsUserPreference.isPresent()){
            String dashboardId = insightsUserPreference.get().getLastVisitedDashboardId();
            Optional<InsightsDashboard> dashboard = findDashboard(dashboardId);
            validateCondition(dashboard.isEmpty(), String.format("Default dashboard with Id %s does not exist", dashboardId));
            return dashboard.get();
        }
        return null;
    }

    public List<Variable> getUserDashboardDatacardVariable(String userId, String dashboardId, String datacardId) {
        validateCondition(findDashboard(dashboardId).isEmpty(), String.format("Invalid dashboard with Id %s", dashboardId));
        User user = userService.findUserById(userId)
                .orElseThrow(() -> new NotFoundException(User.class, "userId", userId));
        validateCondition(!user.isActive(), "User is not Active");
        var insightsUserPref =  insightsUserPreferenceRepo.findByUserId(userId)
                .orElse(new InsightsUserPreference().setUserId(userId));
        var datacardViewerPref = insightsUserPref.getDatacardViewerPreferences();
        if (null != datacardViewerPref) {
            var datacardPref = datacardViewerPref.stream().filter(p ->
                p.getDashboardId().equalsIgnoreCase(dashboardId) && p.getDatacardId().equalsIgnoreCase(datacardId)
            ).collect(Collectors.toList());
            if (null != datacardPref && datacardPref.size() > 0) {
                var pref = datacardPref.get(0).getDatacardVariables();
                return pref;
            }
        }
        return null;
     }

    public void setUserDashboardDatacardVariable(String userId, String dashboardId, String datacardId, List<Variable> datacardVariables) {
        // validate dashboard if exists
        validateCondition(findDashboard(dashboardId).isEmpty(), String.format("Invalid dashboard with Id %s", dashboardId));
        User user = userService.findUserById(userId)
                .orElseThrow(() -> new NotFoundException(User.class, "userId", userId));
        validateCondition(!user.isActive(), "User is not Active");
        var insightsUserPref =  insightsUserPreferenceRepo.findByUserId(userId)
                .orElse(new InsightsUserPreference().setUserId(userId));
        var datacardViewerPref = insightsUserPref.getDatacardViewerPreferences();

        if (null == datacardViewerPref) {
            insightsUserPref.setDatacardViewerPreferences(List.of(createDatacardViewerPref(dashboardId, datacardId, datacardVariables)));
        } else {
            var prefs = datacardViewerPref.stream().filter(p -> (p.getDashboardId().equalsIgnoreCase(dashboardId) && p.getDatacardId().equalsIgnoreCase(datacardId))).collect(Collectors.toList());
            if (prefs.size() > 0) {
                prefs.get(0).setDatacardVariables(datacardVariables);
            } else {
                datacardViewerPref.add(createDatacardViewerPref(dashboardId, datacardId, datacardVariables));
            }
        }
        insightsUserPreferenceRepo.save(insightsUserPref);
    }

    public void setUserDashboardDatacardsVariables(String userId, String dashboardId,  Map<String, Map<String, Variable>> dcConfigs) {
        dcConfigs.forEach((datacardId, variablesMap) -> {
            List<Variable> variables = new ArrayList<>();
            variablesMap.forEach((apiName, variable) -> {
                variables.add(variable);
            });
            setUserDashboardDatacardVariable(SyncariContext.getUser().getId(), dashboardId, datacardId, variables);
        });
    }

    private DatacardViewerPreference createDatacardViewerPref(String dashboardId, String datacardId, List<Variable> datacardVariables) {
        var newDatacardViewerPref = new DatacardViewerPreference();
        newDatacardViewerPref.setDashboardId(dashboardId);
        newDatacardViewerPref.setDatacardId(datacardId);
        newDatacardViewerPref.setDatacardVariables(datacardVariables);
        return newDatacardViewerPref;
    }

    public List<Map<String, Object>> retrieveDataForSeededVisualization(Visualization viz){
        ConnectorInfo connectorInfo = datastoreService.toConnectorInfo(datastoreService.findActiveDatastore());
        // Step 1 - Build query
        // TODO: get author and viewer settings and build query
        String query = buildQuery(viz.getConfig(), viz.getType());

        // Step 2: validate query
        boolean isValidQuery = queryBuilder.validateQuery(query);
        validateCondition(!isValidQuery, String.format("Invalid Query %s", query));

        // Step 3: execute query and retrieve data
        List<Map<String, Object>> data = getVizData(connectorInfo, viz, query);
        return data;
    }
    public List<Map<String, Object>> retrieveDataForVisualization(Visualization viz, Optional<Dataset> datasetOpt) {
        return retrieveDataForVisualization(viz, datasetOpt, viz.getConfig().getLimit(), 0l);
    }

    public List<Map<String, Object>> retrieveDataForVisualization(Visualization viz, Optional<Dataset> datasetOpt, int limit, Long offset){
        // Step 1 - retrieve data from dataset
        // TODO: get author and viewer settings and build query
        Dataset dataset = null;
        List<Variable> datasetVariables = new ArrayList<>();
        Map<String, VariableValue> varMap = new HashMap<>();
         new HashMap<>();
        if (datasetOpt.isPresent()){
            dataset = datasetOpt.get();
            if (MapUtils.isNotEmpty(dataset.getVariablesMap())){
                datasetVariables.addAll(dataset.getVariablesMap().values());
            }
        }else{
            dataset = datasetService.getDataset(viz.getConfig().getDatasetId());
            datasetVariables.addAll(datasetService.getVariables(viz.getConfig().getDatasetId()));
        }
        //get variables set in viz
        Map<String, Variable> datacardvariables = viz.getConfig().getVariablesMap();
        datasetVariables.forEach(var -> {
            if(datacardvariables.containsKey(var.getApiName())){
                varMap.put(var.getApiName(), datacardvariables.get(var.getApiName()).getVariableValue());
            } else {
                varMap.put(var.getApiName(), var.getVariableValue());
            }
        });
        Map<String, Object> dataMap;
        if (dataset.isSQLMode()){
            dataMap = datasetService.readSampleDataForQuery(dataset,varMap, limit, offset,null);
        }else{
            dataMap = datasetService.readDataWithPagination(dataset, varMap, limit, offset);
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>)dataMap.get("data");
        return data;
    }

    private List<Map<String, Object>> getVizData(ConnectorInfo connectorInfo, Visualization viz, String query){
        Map<String, String> fields = viz.getConfig().getColumns().stream().collect(Collectors.toMap(c -> c.getAlias(), c ->c.getDisplayFormat()));
        Datastore datastore = datastoreService.getService(connectorInfo);
        return datastore.retrieveData(connectorInfo, query, fields);
    }

    public void validatePreRequisites(VizConfig vizConfig){
        validateCondition((connectorService.getSyncariDatastore().isPresent() && !datastoreService.isAnyDatastoreActive()), I18n.i18n("no_datastore_active"));
        List<PipelineDependency> pipelineDependencies = vizConfig.getPipelineDependencies();
        Connector syncariConnector = connectorService.getSyncariConnector();
        List<EntityDefinition> syncariEntities = schemaService.getSyncariEntities();
        List<MappingGraph> pipelines = mappingGraphService.retrieveEntityGraphsLite();
        List<String> missingFPs = new ArrayList<>();
        List<String> missingEPs = new ArrayList<>();
        for(PipelineDependency dep : pipelineDependencies){
            // validate if entity exists
            Optional<EntityDefinition> entity = syncariEntities.stream().filter(e -> e.getApiName().equals(dep.getEntity())).findFirst();
            if(!entity.isPresent()){
                missingEPs.add(dep.getEntity());
                continue;
            }
            // validate if entity is mapped
            Optional<MappingGraph> ep = pipelines.stream().filter(g -> g.getTargetId().equals(entity.get().getId()) && g.isApproved()).findFirst();
            if(!ep.isPresent()){
                missingEPs.add(entity.get().getDisplayName());
                continue;
            }

            // validate if all fields exists
            List<AttributeDefinition> attribs = schemaService.getActiveAttributes(syncariConnector.getId(), entity.get().getApiName());
            for(String reqAttrib : dep.getAttributes()){
                // validate if field exists
                var field = attribs.stream().filter(a -> a.getApiName().equalsIgnoreCase(reqAttrib)).findFirst();
                if(!field.isPresent()){
                    missingFPs.add(entity.get().getDisplayName()+"."+reqAttrib);
                }
                // validate if field is mapped
                Optional<MappingGraph> fp = mappingGraphService.retrieveApprovedAttributeGraph(field.get().getId());
                if(!fp.isPresent()){
                    missingFPs.add(entity.get().getDisplayName()+"."+field.get().getDisplayName());
                }
            }
        }

        if(!missingEPs.isEmpty()){
            throw new SyncariValidationException(I18n.i18n("datacard_missing_entities"), String.join(", ", missingEPs));
        }

        if(!missingFPs.isEmpty()){
            throw new SyncariValidationException(I18n.i18n("datacard_missing_fields"), String.join(", ", missingFPs));
        }
    }

    public String buildQuery(VizConfig config, VizType type){
        ConnectorInfo connectorInfo = datastoreService.toConnectorInfo(datastoreService.findActiveDatastore());
        Map<String, VariableValue> variableValuesMapTobeused = new HashMap<>();
        Dataset dataset = datasetRepo.findById(config.getDatasetId()).orElseThrow(() -> new NotFoundException(Dataset.class, "id", config.getDatasetId()));
        if (MapUtils.isNotEmpty(dataset.getVariablesMap())){
            Map<String, Variable> variableMap = dataset.getVariablesMap();
            variableMap.keySet().forEach(k -> {
                if(!variableValuesMapTobeused.containsKey(k)) {
                    variableValuesMapTobeused.put(k, variableMap.get(k).getVariableValue());
                }
            });
        }

        //get variables set in viz
        Map<String, Variable> datacardvariables = config.getVariablesMap();
        variableValuesMapTobeused.forEach((k,v)-> {
            if(datacardvariables.containsKey(k)){
                variableValuesMapTobeused.put(k, datacardvariables.get(k).getVariableValue());
            }
        });
        Map<String, Datatype> variableLeftDatatype = new HashMap<>();
        String query = queryBuilder.buildQuery(buildQueryConfig(config, type), connectorInfo, Optional.of(dataset.getId()),variableValuesMapTobeused, variableLeftDatatype);
        return datasetService.replaceVariablesWithValues(query,variableValuesMapTobeused,variableLeftDatatype);
    }

    public QueryConfig buildQueryConfig(VizConfig vizConfig, VizType vizType){
        if (null == vizConfig){
            return null;
        }
        Dataset dataset = datasetRepo.findById(vizConfig.getDatasetId()).orElseThrow(() -> new NotFoundException(Dataset.class, "id", vizConfig.getDatasetId()));
        String version = dataset.getVersion();
        if (StringUtils.isNotEmpty(version)){
            return buildQueryConfigV1(vizConfig, dataset);
        }else{
            return buildQueryConfigV0(vizConfig, dataset);
        }

    }
    private QueryConfig buildQueryConfigV0(VizConfig vizConfig, Dataset dataset){
        QueryConfig queryConfig = new QueryConfig().setColumns(vizConfig.getColumns())
                .setFromDatasets(dataset.getDatasetConfig().getFromDatasets())
                .setLimit(vizConfig.getLimit());

        if (null != vizConfig.getChildVizConfig()){
            QueryConfig childConfig = buildQueryConfig(vizConfig.getChildVizConfig(), VizType.NONE);
            queryConfig.setChildQueryConfig(childConfig);
        }

        DateFilter dateFilter = vizConfig.getDateFilter();
        queryConfig.setGroupingColumns(vizConfig.getGroupingColumns());
        Map<String, Object> predicateMap = new HashMap<>();

        if (CollectionUtils.isNotEmpty(vizConfig.getSortList())){
            queryConfig.setSortList(vizConfig.getSortList());
        }
        // Map date filter to QueryConfig predicate And operator
        if (null != dateFilter){
            QueryField field = dateFilter.getField();
            log.info("DateFilter is not null and its value is {} for vizConfig {} and datasetId {}", dateFilter, vizConfig.getName(), dataset.getId());
            if (null != dateFilter.getDateRange()){
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                Map<String, Object> startDatepred = Map.of(
                        "left", Map.of("datatype", "string", "type", "variable", "value", "\"" + field.getName() + "\""),
                        "operator", "gte",
                        "right", Map.of("type", "literal", "value", dateFilter.getDateRange().getStart().format(formatter))
                );
                Map<String, Object> endDatepred = Map.of(
                        "left", Map.of("datatype", "string", "type", "variable", "value","\"" + field.getName() + "\""),
                        "operator", "lte",
                        "right", Map.of("type", "literal", "value", dateFilter.getDateRange().getEnd().format(formatter))
                );
                if (MapUtils.isNotEmpty(vizConfig.getPredicate())){
                    predicateMap.putAll(Map.of("predicates", List.of(startDatepred, endDatepred,vizConfig.getPredicate()),"operator", "AND"));
                }else{
                    predicateMap.putAll(Map.of("predicates", List.of(startDatepred, endDatepred),"operator", "AND"));
                }
            }
        }else{
            if (MapUtils.isNotEmpty(vizConfig.getPredicate())){
                predicateMap.putAll(vizConfig.getPredicate());
            }
        }
        queryConfig.setPredicate(predicateMap);
        List<Join> joins = dataset.getDatasetConfig().getJoin();
        if (CollectionUtils.isNotEmpty(joins) && (null != joins.stream().findFirst().get().getDatasetFieldFrom())){
            queryConfig.setJoins(joins);
        }
        int limit = dataset.getDatasetConfig().getLimit();
        if (limit > 0){
            queryConfig.setLimit(limit);
        }
        queryConfig.setGroup(dataset.getDatasetConfig().isGroup());
        return queryConfig;
    }

    private QueryConfig buildQueryConfigV1(VizConfig vizConfig, Dataset dataset){
        QueryConfig queryConfig = new QueryConfig().setColumns(vizConfig.getColumns())
                .setLimit(vizConfig.getLimit());

        List<DatasetFrom> datasetFroms = dataset.getDatasetConfig().getFromDatasets();
        queryConfig.setFromDatasets(datasetFroms);

        Map<String, Object> predicateFromDataset = dataset.getDatasetConfig().getPredicate();

        if (null != vizConfig.getChildVizConfig()){
            QueryConfig childConfig = buildQueryConfig(vizConfig.getChildVizConfig(), VizType.NONE);
            queryConfig.setChildQueryConfig(childConfig);
        }

        DateFilter dateFilter = vizConfig.getDateFilter();
        queryConfig.setGroupingColumns(vizConfig.getGroupingColumns());
        Map<String, Object> predicateMap = new HashMap<>();

        if (CollectionUtils.isNotEmpty(vizConfig.getSortList())){
            queryConfig.setSortList(vizConfig.getSortList());
        }
        // Map date filter to QueryConfig predicate And operator
        if (null != dateFilter){
            QueryField field = dateFilter.getField();
            if (null != dateFilter.getDateRange()){
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                Map<String, Object> startDatepred = Map.of(
                        "left", Map.of("datatype", "string", "type", "variable", "value", "\"" + field.getName() + "\""),
                        "operator", "gte",
                        "right", Map.of("type", "literal", "value", dateFilter.getDateRange().getStart().format(formatter))
                );
                Map<String, Object> endDatepred = Map.of(
                        "left", Map.of("datatype", "string", "type", "variable", "value","\"" + field.getName() + "\""),
                        "operator", "lte",
                        "right", Map.of("type", "literal", "value", dateFilter.getDateRange().getEnd().format(formatter))
                );
                if (MapUtils.isNotEmpty(vizConfig.getPredicate())){
                    predicateMap.putAll(Map.of("predicates", List.of(startDatepred, endDatepred,vizConfig.getPredicate()),"operator", "AND"));
                }else{
                    predicateMap.putAll(Map.of("predicates", List.of(startDatepred, endDatepred),"operator", "AND"));
                }
            }
        }else{
            if (MapUtils.isNotEmpty(vizConfig.getPredicate())){
                predicateMap.putAll(vizConfig.getPredicate());
            }
            if (MapUtils.isNotEmpty(predicateFromDataset)){
                predicateMap.putAll(predicateFromDataset);
            }
        }
        queryConfig.setPredicate(predicateMap);
        List<Join> joins = dataset.getDatasetConfig().getJoin();
        if (CollectionUtils.isNotEmpty(joins) && (null != joins.stream().findFirst().get().getDatasetFieldFrom())){
            queryConfig.setJoins(joins);
        }
        int limit = dataset.getDatasetConfig().getLimit();
        if (limit > 0){
            queryConfig.setLimit(limit);
        }
        queryConfig.setGroup(dataset.getDatasetConfig().isGroup());
        return queryConfig;
    }

    public Datacard updateDatacard(Datacard updatedDatacard){
        // TODO: validate
        // TODO - add more updatable configs
        // updatable configs are:
        // 1. datacard name
        // 2. visualization name
        // 3. dateFilter
        String datacardId = updatedDatacard.getId();
        // retrieve existing datacard
        Datacard existing = datacardRepo.findById(datacardId).orElseThrow(() -> new NotFoundException(Datacard.class, "id", datacardId));
        //Datacard existing = datacardService.getSeededOrFromDataset(updatedDatacard.getId());
        existing.setDisplayName(updatedDatacard.getDisplayName());
        existing.getConfiguration().setDateRange(updatedDatacard.getConfiguration().getDateRange());

        existing.getContents().forEach(viz -> {
            var updatedViz = updatedDatacard.getContents().stream().filter(v -> v.getName().equals(viz.getName())).findFirst()
                    .orElseThrow(() -> new RuntimeException(String.format("Unable to find visualization with name %s", viz.getName())));
            updateVisualization(viz, updatedViz, updatedDatacard.getConfiguration().getDateRange());
        });

        datacardRepo.save(existing);
        return existing;
    }

    private void updateVisualization(Visualization existing, Visualization updated, DateRange dateRange){
        // TODO: validate
        // update vizname and datefilter. Additional config updates yet to be added
        existing.setDisplayName(updated.getDisplayName());
        updateVizConfig(existing.getConfig(), updated.getConfig(), dateRange);

    }

    private void updateVizConfig(VizConfig existing, VizConfig updated, DateRange dateRange){
        // Add date filter only when viz has datetimeField set
        if ((null != existing.getDateFilter()) && (null != existing.getDateFilter().getField()) && (null != dateRange)){
            existing.setDateFilter(new DateFilter().setField(existing.getDateFilter().getField()).setDateRange(dateRange));
        }
    }
}
