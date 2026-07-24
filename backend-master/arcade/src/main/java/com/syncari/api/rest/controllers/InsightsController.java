package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.ErrorDTO;
import com.syncari.api.rest.controllers.data.insights.ChartVizData;
import com.syncari.api.rest.controllers.data.insights.DashboardDTO;
import com.syncari.api.rest.controllers.data.insights.DashboardDatacardReadDataDTO;
import com.syncari.api.rest.controllers.data.insights.DatacardDTO;
import com.syncari.api.rest.controllers.data.insights.DatasetDTO;
import com.syncari.api.rest.controllers.data.insights.DatasetTransformer;
import com.syncari.api.rest.controllers.data.insights.InsightsTransformer;
import com.syncari.api.rest.controllers.data.insights.LastVisitedDashboardDTO;
import com.syncari.api.rest.controllers.data.insights.VariableValueDTO;
import com.syncari.api.rest.controllers.data.insights.VizData;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.insights.DashboardVariableMapping;
import com.syncari.core.model.insights.Datacard;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.model.insights.Visualization;
import com.syncari.core.model.insights.VizType;
import com.syncari.core.model.insights.dataset.Variable;
import com.syncari.core.model.insights.dataset.VariableValue;
import com.syncari.core.model.insights.provider.ts.TSToken;
import com.syncari.core.service.*;
import com.syncari.utils.KeyValue;
import com.syncari.core.model.insights.dataset.Dataset;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.utils.I18n.i18n;
import static com.syncari.core.utils.ValidationUtils.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/insights")
public class InsightsController {

    @Autowired
    DatasetService datasetService;

    @Autowired
    InsightsService insightsService;

    @Autowired
    InsightsDashboardService dashboardService;

    @Autowired
    InsightsTransformer transformer;

    @Autowired
    DatasetTransformer datasetTransformer;

    @Autowired
    FeatureService featureService;

    @Autowired
    DatacardService datacardService;

    @Autowired
    InsightsProviderService tsService;

    @Autowired
    InsightsProviderIntegrator providerIntegrator;

    @Autowired
    DatastoreService datastoreService;

    @Secured(CREATE_DASHBOARD)
    @RequestMapping(method = RequestMethod.POST, value = "dashboard")
    public DashboardDTO createDashboard(@RequestBody DashboardDTO draft) {
        InsightsDashboard dashboard = transformer.toDashboard(draft);
        dashboard = dashboardService.createDashboardDraft(dashboard);
        return transformer.toDashboardDTO(dashboard, Collections.emptyList());
    }

    @Secured(PUBLISH_DASHBOARD)
    @RequestMapping(method = RequestMethod.POST, value = "dashboard/{dashboardId}/approve")
    public void approveDashboard(@PathVariable String dashboardId) {
        InsightsDashboard dashboard = dashboardService.getDashboard(dashboardId);
        dashboardService.approveDraftDashboard(dashboard);
    }

    @Secured(CREATE_DASHBOARD)
    @RequestMapping(method = RequestMethod.POST, value = "dashboard/{dashboardId}/createDraft")
    public DashboardDTO createDraftDashboardFor(@PathVariable String dashboardId) {
        InsightsDashboard dashboard = dashboardService.getDashboard(dashboardId);
        InsightsDashboard draft = dashboardService.createDraftFor(dashboard);
        return transformer.toDashboardDTO(draft, Collections.emptyList());
    }

    @Secured(DELETE_DASHBOARD)
    @RequestMapping(method = RequestMethod.DELETE, value = "dashboard/{dashboardId}/discard")
    public void discardDashboard(@PathVariable String dashboardId) {
        InsightsDashboard dashboard = dashboardService.getDashboard(dashboardId);
        dashboardService.discardDraftDashboard(dashboard);
    }

    @Secured(DELETE_DASHBOARD)
    @RequestMapping(method = RequestMethod.DELETE, value = "dashboard/{dashboardId}")
    public void deleteDashboard(@PathVariable String dashboardId) {
        var dashboard = dashboardService.findDashboard(dashboardId);
        dashboard.ifPresent(d -> dashboardService.deleteDashboard(d));
    }

    @Secured(UPDATE_DASHBOARD)
    @RequestMapping(method = RequestMethod.PUT, value = "dashboard/{dashboardId}")
    public void updateDashboard(@PathVariable String dashboardId, @RequestBody DashboardDTO dashboardDTO) {
        InsightsDashboard dashboard = transformer.toDashboard(dashboardDTO);
        dashboardService.updateDashboard(dashboardId, dashboard);
    }

    @Secured(READ_INSIGHTS)
    @RequestMapping(method = RequestMethod.GET, value = "/dashboard")
    public List<DashboardDTO> getDashboards() {
        List<InsightsDashboard> dashboards = dashboardService.getAllDashboards();

        Map<String, InsightsDashboard> draftsMap = dashboards.stream().filter(d -> d.getParentId() != null)
                .collect(Collectors.toMap(d -> d.getParentId(), d -> d));
        return dashboards.stream().filter(d -> d.getParentId() == null).map(d -> {
            DashboardDTO dto = transformer.toDashboardDTO(d, Collections.EMPTY_LIST);
            if(d.isApproved() && draftsMap.containsKey(d.getId())){
                dto.setDraft(transformer.toDashboardDTO(draftsMap.get(d.getId()), Collections.EMPTY_LIST));
            }
            return dto;
        }).collect(Collectors.toList());
    }


    @Secured(READ_INSIGHTS)
    @RequestMapping(method = RequestMethod.GET, value = "/liveboards")
    public Map<String, String> getProviderDashboards() {
        return providerIntegrator.listLiveboards();
    }

    @Secured(READ_INSIGHTS)
    @RequestMapping(method = RequestMethod.GET, value = "/dashboard/lastVisited")
    public LastVisitedDashboardDTO getLastVisitedUserDashboard(){
        InsightsDashboard dashboard = insightsService.getLastVisitedUserDashboard(SyncariContext.getUser().getId());
        LastVisitedDashboardDTO dto = new LastVisitedDashboardDTO();
        if (null != dashboard){
            if(dashboard.hasPublishedParent()){
                // for deep linking draft still uses the published id
                dto.setLastVisitedDashboardId(dashboard.getParentId());
                dto.setUseNestedDraft(true);
            } else {
                dto.setLastVisitedDashboardId(dashboard.getId());
                dto.setUseNestedDraft(false);
            }
        }
        return dto;
    }

    @Secured(READ_INSIGHTS)
    @RequestMapping(method = RequestMethod.POST, value = "/dashboard/lastVisited")
    public void setLastVisitedUserDashboard(@RequestBody LastVisitedDashboardDTO lastVisitedDashboard){
        InsightsDashboard dashboard = dashboardService.getDashboard(lastVisitedDashboard.getLastVisitedDashboardId());
        if(lastVisitedDashboard.isUseNestedDraft()){
            var draft = dashboardService.findDraft(dashboard);
            if(draft.isPresent()){
                dashboard = draft.get();
            }
        }
        insightsService.setLastVisitedUserDashboard(SyncariContext.getUser().getId(), dashboard.getId());
    }

    @Secured(READ_INSIGHTS)
    @RequestMapping(method = RequestMethod.GET, value = "/dashboard/{dashboardId}")
    public DashboardDTO getDashboard(@PathVariable String dashboardId) {
        InsightsDashboard dashboard = insightsService.getDashboard(dashboardId);
        List<Datacard> datacards = dashboard.getDataCardIds().stream().map(d -> datacardService.getSeededOrFromDataset(d)).collect(Collectors.toList());
        return transformer.toDashboardDTO(dashboard, datacards);
    }

    // TODO: remove GET request once UI makes change to move over to POST call
    @Deprecated
    @Secured({READ_INSIGHTS, READ_ALL_SHARED_DASHBOARD})
    @RequestMapping(method = RequestMethod.GET, value = "/dashboard/{dashboardId}/datacard/{datacardId}")
    public DatacardDTO getDatacard(@PathVariable String dashboardId, @PathVariable String datacardId) {
        return getDatacard(dashboardId, datacardId, new HashMap<>());
    }

    @Secured({READ_INSIGHTS, READ_ALL_SHARED_DASHBOARD})
    @RequestMapping(method = RequestMethod.GET, value = "/dashboard/variables/{dashboardId}")
    public List<DashboardVariableMapping> getDashboardPreference(@PathVariable String dashboardId) {
        return dashboardService.getDashboardVariableMappings(dashboardId);
    }

    @Secured({READ_INSIGHTS, READ_ALL_SHARED_DASHBOARD})
    @RequestMapping(method = RequestMethod.POST, value = "/dashboard/variables/{dashboardId}")
    public void setDashboardPreference(@PathVariable String dashboardId, @RequestBody List<DashboardVariableMapping> dashboardVariableMapping) {
        dashboardService.setDashboardVariableMappings(dashboardId, dashboardVariableMapping);
    }

    @Secured({READ_INSIGHTS,READ_ALL_SHARED_DASHBOARD})
    @RequestMapping(method = RequestMethod.POST, value = "/dashboard/{dashboardId}/datacard/{datacardId}/readData")
    public VizData getDatacardWithPagination(@PathVariable String dashboardId, @PathVariable String datacardId, @RequestBody DashboardDatacardReadDataDTO readData) {
        InsightsDashboard dashboard = insightsService.getDashboard(dashboardId);
        Datacard datacard = datacardService.getSeededOrFromDataset(datacardId);
        DatacardDTO datacardDTO = transformer.toDatacardDTO(dashboard, datacard);
        validateCondition(!StringUtils.isEmpty(datacard.getErrorMsg()), i18n("invalid_datacard_configuration", datacard.getName()));
        Visualization viz = datacard.getContents().get(0);
        Dataset dataset =  datasetService.getDataset(viz.getConfig().getDatasetId());

        Map<String, Variable> datacardVariables = getDatacardDashboardVariables(datacardDTO, dashboardId, datacardId, dataset.getVariablesMap());
        viz.getConfig().setVariablesMap(datacardVariables);

        if (!datacard.isSeeded()) {
            datastoreService.refreshTokensAndUpdateThoughtSpotConnection();
        }

        List<Map<String, Object>> data = Collections.emptyList();
        var offset = Long.valueOf(readData.getPageCursor().getCursor());
        offset = datasetService.getOffsetBasedOnDirection(offset, readData.getPageCursor().getDirection(), readData.getPageCursor().getPageSize());

        data = insightsService.retrieveDataForVisualization(viz, Optional.empty(), readData.getPageCursor().getPageSize(), offset);
        var vizData = transformer.toVizData(viz, data);

        DatasetDTO datasetDTO = datasetTransformer.transformToDTO(dataset);
        var datasetPageInfo = datasetService.addPageInfo(Long.valueOf(data.size()), offset, datasetTransformer.transformToDatasetForCount(datasetDTO),
            readData.getPreviousTotalCount()
        );

        ((ChartVizData)vizData).setPageInfo(datasetPageInfo);
        return vizData;
    }

    @Secured({READ_INSIGHTS,READ_ALL_SHARED_DASHBOARD})
    @RequestMapping(method = RequestMethod.POST, value = "/dashboard/{dashboardId}/updateDashboardVariablePreferences")
    public void batchUpdateDashboardDatacardPrefs(@PathVariable String dashboardId, @RequestBody Map<String, Map<String, VariableValueDTO>> dcConfigsDTO) {
        Map<String, Map<String, Variable>> dcConfigs = new HashMap<>();

        dcConfigsDTO.forEach((datacardId, datacardVariable) -> {
            Map<String, Variable> variables = new HashMap<>();
            var userVariables = insightsService.getUserDashboardDatacardVariable(SyncariContext.getUser().getId(), dashboardId, datacardId);
            // Add the existing user pref is exists
            if (null != userVariables) {
                userVariables.forEach(variable -> {
                    variables.put(variable.getApiName(), variable);
                });
            }
            datacardVariable.forEach((apiName, variableValueDTO) -> {
                VariableValue variableValue = datasetTransformer.toVariableValue(variableValueDTO);
                // Update our user pref
                variables.put(apiName, new Variable()
                        .setVariableValue(variableValue)
                        .setDatatype(variableValue.getDatatype())
                        .setApiName(apiName));
            });
            dcConfigs.put(datacardId, variables);
        });

        insightsService.setUserDashboardDatacardsVariables(SyncariContext.getUser().getId(), dashboardId, dcConfigs);
    }

    @Secured({READ_INSIGHTS})
    @RequestMapping(method = RequestMethod.POST, value = "/provider/token")
    public String getCurrentOrgCurrentUserTSToken() {
        String tsCurrentUsername = SyncariContext.getUser().getInsightsProviderUserName();
        String tsCurrentOrgId = SyncariContext.getOrganziation().getInsightsProviderOrgId();
        if (StringUtils.isEmpty(tsCurrentOrgId)){
            throw new SyncariValidationException("Current Org does not exists in insights provider");
        }
        if (StringUtils.isEmpty(tsCurrentUsername)){
            throw new SyncariValidationException("Current User does not exists in insights provider");
        }
        TSToken token =  tsService.getBearerToken(tsCurrentUsername, tsCurrentOrgId,86400L); // 24 hrs token for UI
        return token.getToken();
    }


    @Secured({CREATE_DASHBOARD})
    @RequestMapping(method = RequestMethod.POST, value = "/provider/share/{metadataType}/{metadataId}")
    public void shareWithCurrentInstanceGroups(@PathVariable String metadataId,@PathVariable String metadataType) {
        providerIntegrator.shareWithDMGroup(List.of(metadataId),Optional.of(metadataType), true);
        providerIntegrator.shareWithNoneGroup(List.of(metadataId),Optional.of(metadataType));
        providerIntegrator.changeOwnerToTSAdmin(List.of(metadataId),Optional.of(metadataType));
    }

    @Secured({READ_INSIGHTS,READ_ALL_SHARED_DASHBOARD})
    @RequestMapping(method = RequestMethod.POST, value = "/dashboard/{dashboardId}/datacard/{datacardId}")
    public DatacardDTO getDatacard(@PathVariable String dashboardId, @PathVariable String datacardId, @RequestBody Map<String, VariableValueDTO> dcConfig) {
        InsightsDashboard dashboard = insightsService.getDashboard(dashboardId);
        Datacard datacard = datacardService.getSeededOrFromDataset(datacardId);
        DatacardDTO datacardDTO = transformer.toDatacardDTO(dashboard, datacard);

        validateCondition(!StringUtils.isEmpty(datacard.getErrorMsg()), i18n("invalid_datacard_configuration", datacard.getName()));
        // retrieve and add data to datacard
        // TODO: this is based on assumption there is only one visualization in datacard
        Visualization viz = datacard.getContents().get(0);
        ErrorDTO error = null;
        try {
            insightsService.validatePreRequisites(viz.getConfig());
        } catch (SyncariValidationException ex){
            error = new ErrorDTO();
            error.setTitle("Missing Requirements");
            error.setBody(ex.getMessage());
            log.error(ex.getMessage(), ex);
            VizData vizData = new ChartVizData();
            vizData.setError(error);
            datacardDTO.getContents().setData(vizData);
            return datacardDTO;
        }

        // Add the variable values from the dataset variable map
        Dataset dataset =  datasetService.getDataset(viz.getConfig().getDatasetId());
        var datasetVariableMap = dataset.getVariablesMap();
        var datacardConfig = datacardDTO.getConfiguration();
        if (null != datasetVariableMap && null != datacardConfig) {
            datasetVariableMap.forEach((k, v) -> {
                datacardConfig.put(k, v.getVariableValue());
            });
        }

        Map<String, Variable> updatedVarMap = getDatacardDashboardVariables(datacardDTO, dashboardId, datacardId, datasetVariableMap);

        List<Variable> variables = new ArrayList<>();
        viz.getConfig().getVariablesMap().forEach((k, v) -> {
            if (null == datasetVariableMap || !datasetVariableMap.containsKey(k)) return;
            if(dcConfig != null && dcConfig.containsKey(k)){
                VariableValueDTO varDTO = dcConfig.get(k);
                var variableValue = datasetTransformer.toVariableValue(varDTO);
                v.setVariableValue(variableValue);
                var vari = new Variable()
                    .setVariableValue(variableValue)
                    .setApiName(v.getApiName())
                    .setDatatype(v.getDatatype())
                    .setRequired(v.isRequired())
                    .setDisplayName(v.getDisplayName())
                    .setHelpText(v.getHelpText())
                    .setUpdatable(v.isUpdatable()).setMultiValueField(v.isMultiValueField());
                variables.add(vari);
                // Update the variable values from this request
                datacardDTO.getConfiguration().put(k, variableValue);
                updatedVarMap.put(k, vari);
                return;
            } else if (!updatedVarMap.containsKey(k)) {
                // Do not overwrite any user saved variable values
                updatedVarMap.put(k, v);
            }
            // Save existing variables back
            variables.add(v);
        });

        if (null != dcConfig && dcConfig.size() > 0) {
            // Save the variable values to the user preference
            insightsService.setUserDashboardDatacardVariable(SyncariContext.getUser().getId(), dashboardId, datacardId, variables);
        }
        viz.getConfig().setVariablesMap(updatedVarMap);

        if (!datacard.isSeeded()) {
            datastoreService.refreshTokensAndUpdateThoughtSpotConnection();
        }

        List<Map<String, Object>> data = Collections.emptyList();
        try {
            if(datacard.isSeeded()) {
                data = insightsService.retrieveDataForSeededVisualization(viz);
            } else {
                // run the dataset query -> get data -> extract relevant columns
                data = insightsService.retrieveDataForVisualization(viz, Optional.empty());
            }
        } catch (Exception ex){
            error = new ErrorDTO();
            error.setTitle("Error retrieving data");
            error.setBody(ex.getMessage());
            log.error(ex.getMessage(), ex);
        }

        var vizData = transformer.toVizData(viz, data);

        // Add page info for table viz type
        if (viz.getType().equals(VizType.TABLE)) {
            Long recordCount = Long.valueOf(data.size());
            dataset.setVariablesMap(updatedVarMap);
            DatasetDTO datasetDTO = datasetTransformer.transformToDTO(dataset);
            var datasetPageInfo = datasetService.addPageInfo(recordCount, Long.valueOf(0),
                datasetTransformer.transformToDatasetForCount(datasetDTO), null
            );
            ((ChartVizData)vizData).setPageInfo(datasetPageInfo);
        }
        vizData.setError(error);

        datacardDTO.getContents().setData(vizData);

        // Remove any variable thats is not valid
        return removeInvalidConfiguration(datacardDTO, datacardConfig, dataset);
    }

    private DatacardDTO removeInvalidConfiguration(DatacardDTO datacardDTO, KeyValue datacardConfig, Dataset dataset) {
        var datasetVariableMap = dataset.getVariablesMap();
        var variableMap = datacardDTO.getContents().getConfiguration().getVariablesMap();

        // Make sure datacard.configuration only have valid values
        if (null != datacardConfig) {
            datacardConfig.keySet().removeAll(variableMap.entrySet().stream()
                .filter(v-> null == datasetVariableMap ? true : !datasetVariableMap.containsKey(v.getValue().getApiName())
                ).map(v -> v.getKey()).collect(Collectors.toList()));
            datacardDTO.setConfiguration(datacardConfig);
        }
        // Make sure datacard.configurationMeta only have valid values
        datacardDTO.setConfigurationMeta(datacardDTO.getConfigurationMeta().stream()
            .filter(v -> null == datasetVariableMap ? false : datasetVariableMap.containsKey(v.getName())).collect(Collectors.toList()));
        // Make sure datacard.contents.configuration.variablesMap only have valid values
        variableMap.keySet().removeAll(variableMap.entrySet().stream()
            .filter(v-> null == datasetVariableMap ? true : !datasetVariableMap.containsKey(v.getValue().getApiName())
            ).map(v -> v.getKey()).collect(Collectors.toList()));
        datacardDTO.getContents().getConfiguration().setVariablesMap(variableMap);
        return datacardDTO;
    }

    private Map<String, Variable> getDatacardDashboardVariables(DatacardDTO datacardDTO, String dashboardId, String datacardId, Map<String, Variable> validVariableMap) {
        Map<String, Variable> updatedVarMap = new HashMap<>();
        if (MapUtils.isNotEmpty(validVariableMap)){
            updatedVarMap.putAll(validVariableMap);
        }
        var userVariables = insightsService.getUserDashboardDatacardVariable(SyncariContext.getUser().getId(), dashboardId, datacardId);
        if (null != userVariables && null != validVariableMap) {
            // Add the user saved variable values
            userVariables.stream().forEach(userVar -> {
                if (!validVariableMap.containsKey(userVar.getApiName())) {
                    return;
                }
                updatedVarMap.put(userVar.getApiName(), userVar);
                datacardDTO.getConfiguration().put(userVar.getApiName(), userVar.getVariableValue());
            });
        }
        return updatedVarMap;
    }
}
