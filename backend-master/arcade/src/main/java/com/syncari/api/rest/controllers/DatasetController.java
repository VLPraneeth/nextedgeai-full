package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.insights.*;
import com.syncari.connector.data.DatastoreFieldMetadata;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.insights.QField;
import com.syncari.core.model.insights.QueryConfig;
import com.syncari.core.model.insights.dataset.*;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.*;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Slf4j
@RestController
@RequestMapping("/api/v1/insights/datasets")
public class DatasetController {

    @Autowired
    DatasetTransformer transformer;

    @Autowired
    DatasetService datasetService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    DatacardService datacardService;

    @Autowired
    InsightsDashboardService dashboardService;

    @Autowired
    UserService userService;

    @Autowired
    DatasetExportService datasetExportService;

    @Autowired
    FileDataService fileDataService;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    ConnectorService connectorService;

    @Secured(CREATE_DATASET)
    @RequestMapping(method = RequestMethod.POST)
    public DatasetDTO createDataset(@RequestBody DatasetDTO draft) {
        Dataset dataset = transformer.transformToDataset(draft);
        dataset.setDraftStatus(DraftStatus.APPROVED);
        dataset = datasetService.createDataset(dataset);
        return transformer.transformToDTO(dataset);
    }

    @Secured(DELETE_DATASET)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{datasetId}/delete")
    public void deleteDatatset(@PathVariable String datasetId) {
        Dataset dataset = datasetService.getDataset(datasetId);
        datasetService.deleteDatasetInInsightsProvider(dataset);
        datasetService.deleteDataset(dataset);
    }

    @Secured(DELETE_DATASET)
    @RequestMapping(method = RequestMethod.DELETE, value = "/delete")
    public void deleteDatatsets(@RequestBody List<String> datasetIds) {
        validateCondition(CollectionUtils.isEmpty(datasetIds),"Dataset Ids list cannot be empty");
        datasetIds.forEach(dsId -> {
            log.info("Deleting dataset id {}", dsId);
            Dataset dataset = datasetService.getDataset(dsId);
            datasetService.deleteDatasetInInsightsProvider(dataset);
            datasetService.deleteDataset(dataset);
        });
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.GET)
    public List<DatasetDTO> getDatasets() {
        List<Dataset> datasets = datasetService.getAllDatasetsWithVersion();
        // return only published datacards until we cleanup all drafts
        return datasets.stream()
                .filter(d -> d.isApproved())
                .map(d -> transformer.transformToDTO(d))
                .collect(Collectors.toList());
    }


    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.GET,value = "/insightsprovider")
    public List<DatasetDTO> getInsightsProviderDatasets() {
        List<Dataset> datasets = datasetService.getAllDatasetsWithVersion();
        // return only published datacards until we cleanup all drafts
        return datasets.stream()
                .filter(d -> d.isApproved() && (((StringUtils.isNotEmpty(d.getVersion())) && (d.getVersion().equalsIgnoreCase("V2")))
                        && (StringUtils.isNotEmpty(d.getInsightsProviderId()) || (MapUtils.isNotEmpty(d.getVariablesMap()) && StringUtils.isNotEmpty(d.getRawQuery())
                        && d.getRawQuery().contains("{{")))))
                .map(d -> transformer.transformToDTO(d))
                .collect(Collectors.toList());
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.GET, value = "/{datasetId}")
    public DatasetDTO getDataset(@PathVariable String datasetId) {
        validateCondition(StringUtils.isEmpty(datasetId),I18n.i18n("datset_id_null"));
        Dataset dataset =  datasetService.getDataset(datasetId);
        return transformer.transformToDTO(dataset);
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.GET, value = "/getSchema")
    public String getSchema() {
        return datastoreService.getSyncariSchema(SyncariContext.getSyncariId());
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.GET, value = "/{datasourceId}/{datasourceType}")
    public DatasourceDTO getDataSourceDetails(@PathVariable String datasourceId,@PathVariable String datasourceType, @RequestParam String datasourceAlias) {
        validateCondition(StringUtils.isEmpty(datasourceId),I18n.i18n("datset_id_null"));
        if (QField.Type.ENTITY.name().equals(datasourceType)){
            EntityDefinition entityDefinition =  schemaService.getEntity(datasourceId);
            List<AttributeDefinition> atts = entityDefinition.getAttributes();
            AttributeDefinition syncariid = new AttributeDefinition().setDisplayName("NextEdge ID").setDataType(StringType.VALUE).setApiName("syncariid").setEntityId(entityDefinition.getId()).setDataStoreName("syncariid");
            syncariid.setId(ObjectId.get().toHexString());
            atts.add(syncariid);
            return transformer.transformToDatasourceDTOForEntityDef(entityDefinition,datasourceAlias);

        }else{
            Dataset dataset =  datasetService.getDataset(datasourceId);
            return transformer.transformToDatasourceDTO(dataset, datasourceAlias);
        }
    }

    @Secured(UPDATE_DATASET)
    @RequestMapping(method = RequestMethod.PUT, value = "/{datasetId}")
    public DatasetDTO updateDataset(@PathVariable String datasetId, @RequestBody DatasetDTO datasetDTO) {
        validateCondition(StringUtils.isEmpty(datasetId),I18n.i18n("dataset_id_null"));
        validateCondition((null == datasetDTO), I18n.i18n("dataset_dto_null"));
        validateCondition((null == datasetDTO.getDatasetConfig()), I18n.i18n("datasetconfig_empty"));
        Dataset dataset = transformer.transformToDataset(datasetDTO);
        datasetService.createOrUpdateDatasetInInsightsProvider(dataset,false);
        Dataset updatedDataset = datasetService.updateDataset(datasetId, dataset);
        return transformer.transformToDTO(updatedDataset);
    }

    @Secured(EXPORT_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/{datasetId}/export")
    public void exportDataset(@PathVariable String datasetId, @RequestBody DatasetDTO datasetDTO) {
        validateCondition(StringUtils.isEmpty(datasetId),I18n.i18n("dataset_id_null"));
        validateCondition((null == datasetDTO), I18n.i18n("dataset_dto_null"));
        validateCondition((null == datasetDTO.getDatasetConfig()), I18n.i18n("datasetconfig_empty"));
        List<DatasetExport> allPendingAndCompletedJobsByDatasetId = datasetExportService.findAllByDatasetIdAndStatus(datasetId, List.of(DatasetExport.DatasetExportStatus.PENDING,DatasetExport.DatasetExportStatus.INPROGRESS, DatasetExport.DatasetExportStatus.COMPLETED));
        List<DatasetExport> unexpiredJobs = allPendingAndCompletedJobsByDatasetId.stream().filter(d -> Instant.now().minusMillis(d.getExpiredTime().toEpochMilli()).toEpochMilli() < 0).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(unexpiredJobs)){
            validateCondition(unexpiredJobs.size() >= 10, I18n.i18n("export_jobs_already_highlimit"));
        }
        Dataset dataset = transformer.transformToDataset(datasetDTO);
        if (StringUtils.isEmpty(dataset.getId())){
            dataset.setId(datasetId);
        }
        datasetExportService.exportDatasetAsync(dataset);
    }

    @Secured({VIEW_EXPORT_JOBS})
    @RequestMapping(method = RequestMethod.GET, value = "/{datasetId}/exportJobs")
    public List<DatasetExportJobDTO> getExportJobs(@PathVariable String datasetId) {
        return datasetExportService.getExportJobs(datasetId)
                .stream()
                .map(f -> transformer.datasetExportJobDTO(f))
                .collect(Collectors.toList());
    }

    @Secured({CANCEL_EXPORT})
    @RequestMapping(method = RequestMethod.POST, value = "/cancel/{exportJobId}")
    public void cancelExportJobs(@PathVariable String exportJobId) {
        datasetExportService.cancelExportJobs(exportJobId);
    }

    @Secured({DELETE_EXPORT})
    @RequestMapping(method = RequestMethod.DELETE, value = "/delete/{exportJobId}")
    public void deleteExportJobs(@PathVariable String exportJobId) {
        datasetExportService.deleteExportJobs(exportJobId);
    }

    @Secured({DOWNLOAD_EXPORTED_DATASET})
    @GetMapping("/download/{exportJobId}")
    public ResponseEntity<Resource> download(@PathVariable String exportJobId) throws IOException {
        validateCondition(StringUtils.isEmpty(exportJobId), I18n.i18n("file_path_null"));
        Optional<DatasetExport> datasetExport = datasetExportService.findByExportJobId(exportJobId);
        validateCondition(!datasetExport.isPresent(), I18n.i18n("exported_file_not_present"));
        Long epochMilli = Instant.now().minusMillis(datasetExport.get().getExpiredTime().toEpochMilli()).toEpochMilli();
        validateCondition(epochMilli > 0, I18n.i18n("exported_file_link_expired"));
        String filePath = datasetExport.get().getExportedFileLink();
        validateCondition(StringUtils.isEmpty(filePath), I18n.i18n("file_path_null"));
        InputStreamResource resource = new InputStreamResource(fileDataService.getFileContentByFilePaths(filePath));
        String fileName = datasetExport.get().getDatasetId() + "_"+exportJobId;
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }


    @Secured({CREATE_DATASET, UPDATE_DATASET})
    @RequestMapping(method = RequestMethod.GET, value = "/functions")
    public List<DatasetFunctionDTO> getFunctions() {
        return datasetService.getAllFunctions()
                .stream()
                .map(f -> transformer.toFunctionDTO(f))
                .collect(Collectors.toList());
    }

    @Secured({CREATE_DATASET, UPDATE_DATASET, VIEW_DATASET, READ_INSIGHTS})
    @RequestMapping(method = RequestMethod.GET, value = "/timegrainoptions")
    public List<DatasetGroupByTimeGrainOptionsDTO> getTimeGrainOptions(@RequestParam Optional<String> dataType) {
        return datasetService.getAllGroupByTimeOptions(dataType)
                .stream()
                .map(f -> transformer.toGroupByOptionDTO(f))
                .collect(Collectors.toList());
    }


    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "{datasetId}/readSampleData")
    public DatasetSampleDTO readSampleData(@PathVariable String datasetId, @RequestBody Map<String, VariableValueDTO> variableDefaultValueDtoMap) {
        validateCondition((connectorService.getSyncariDatastore().isPresent() && !datastoreService.isAnyDatastoreActive()), I18n.i18n("no_datastore_active"));
        Map<String, VariableValue> variableValueMap = new HashMap<>();
        variableDefaultValueDtoMap.forEach((k,v) -> {
            variableValueMap.put(k, transformer.toVariableValue(v));
        });
        Dataset dataset = datasetService.getDataset(datasetId);
        DatasetConfig config = dataset.getDatasetConfig();
        assert (null != config);
        assert (CollectionUtils.isNotEmpty(config.getProjectionsList()));
        Map<String, Object> dataAndCols =  datasetService.readSampleData(dataset, variableValueMap);
        DatasetSampleDTO dto = new DatasetSampleDTO();
        dto.setColumns(transformer.toDatasetSampleColumnsDTOSFromDatastoreMetadata((List<DatastoreFieldMetadata>)dataAndCols.get("columns")));
        dto.setData(transformer.toDatasetSampleDataDTOS((List<Map<String, Object>>)dataAndCols.get("data")));
        return dto;
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/getQuery")
    public DatasetDTO getQuery(@RequestBody DatasetDTO dataset) {
        Dataset datasetLocal = transformer.transformToDataset(dataset);
        DatasetConfig config = datasetLocal.getDatasetConfig();
        assert (null != config);
        assert (CollectionUtils.isNotEmpty(config.getProjectionsList()));
        assert (CollectionUtils.isNotEmpty(config.getFromDatasets()));
        QueryConfig queryConfig =  datasetService.buildQueryConfigFromDataset(datasetLocal);
        Map<String, VariableValue> variableValuesMapTobeused = datasetService.mergeVariablesValues(datasetLocal, Map.of());
        String query = datasetService.buildDatasetQuery(datasetLocal, variableValuesMapTobeused,queryConfig, new HashMap<>());
        dataset.setSql(query);
        return dataset;
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/getDatasetFromQuery")
    public DatasetDTO getDatasetFromQuery(@RequestBody DatasetDTO dataset) {
        Dataset datasetLocal = transformer.transformToDataset(dataset);
        assert (StringUtils.isNotEmpty(datasetLocal.getRawQuery()));
        assert (datasetLocal.isSQLMode());
        datasetService.readSampleDataForQuery(datasetLocal,Map.of(),0,0l,null);
        return transformer.transformToDTO(datasetLocal);
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/preview")
    public DatasetSampleDTO preview(@RequestBody DatasetDTO dataset) {
        Dataset datasetLocal = transformer.transformToDataset(dataset);
        DatasetConfig config = datasetLocal.getDatasetConfig();
        assert (null != config);
        assert (CollectionUtils.isNotEmpty(config.getProjectionsList()));
        assert (CollectionUtils.isNotEmpty(config.getFromDatasets()));
        Map<String, Object> dataAndCols =  datasetService.readSampleData(datasetLocal, Map.of());
        DatasetSampleDTO dto = new DatasetSampleDTO();
        dto.setColumns(transformer.toDatasetSampleColumnsDTOSFromDatastoreMetadata((List<DatastoreFieldMetadata>)dataAndCols.get("columns")));
        dto.setData(transformer.toDatasetSampleDataDTOS((List<Map<String, Object>>)dataAndCols.get("data")));
        return dto;
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/previewWithQuery")
    public DatasetSampleDTO previewWithQuery(@RequestBody DatasetDTO dataset) {
        Dataset datasetLocal = transformer.transformToDataset(dataset);
        String rawQuery = datasetLocal.getRawQuery();
        assert (null != rawQuery);
        Map<String, Object> dataAndCols =  datasetService.readSampleDataForQuery(datasetLocal,Map.of(), DatasetService.READ_SAMPLE_LIMIT, 0l,null);
        DatasetSampleDTO dto = new DatasetSampleDTO();
        dto.setColumns(transformer.toDatasetSampleColumnsDTOSFromDatastoreMetadata((List<DatastoreFieldMetadata>)dataAndCols.get("columns")));
        dto.setData(transformer.toDatasetSampleDataDTOS((List<Map<String, Object>>)dataAndCols.get("data")));
        return dto;
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/readDataWithQuery")
    public DatasetSampleDTO readDataWithQuery(@RequestBody DatasetReadDataDTO datasetReadData) {
        DatasetDTO datasetDTO = datasetReadData.getDataset();
        PageCursor pageCursor = datasetReadData.getPageCursor();
        Dataset datasetLocal = transformer.transformToDataset(datasetDTO);
        DatasetConfig config = datasetLocal.getDatasetConfig();
        assert (null != config);
        Integer limit = pageCursor.getPageSize();
        assert (null != limit);
        assert (null != pageCursor.getCursor());
        Long offset = 0l;
        try{
            offset = Long.valueOf(pageCursor.getCursor());
            offset = datasetService.getOffsetBasedOnDirection(offset, pageCursor.getDirection(), limit);
        }catch (Exception e){
            log.error("Cursor {} is not parsable to long, always returns first page", pageCursor.getCursor());
        }
        Map<String, Object> dataAndCols =  datasetService.readSampleDataForQuery(datasetLocal,Map.of(),limit,offset,null);
        long recordCount = ((List<Map<String, Object>>)dataAndCols.get("data")).size();
        DatasetSampleDTO dto = new DatasetSampleDTO();
        dto.setColumns(transformer.toDatasetSampleColumnsDTOSFromDatastoreMetadata((List<DatastoreFieldMetadata>)dataAndCols.get("columns")));
        dto.setData(transformer.toDatasetSampleDataDTOS((List<Map<String, Object>>)dataAndCols.get("data")));
        dto.setPageInfo(datasetService.addPageInfo(recordCount,offset,transformer.transformToDatasetForCount(datasetDTO), datasetReadData.getPreviousTotalCount()));
        return dto;
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/readData")
    public DatasetSampleDTO readData(@RequestBody DatasetReadDataDTO datasetReadData) {
        DatasetDTO datasetDTO = datasetReadData.getDataset();
        PageCursor pageCursor = datasetReadData.getPageCursor();
        Dataset datasetLocal = transformer.transformToDataset(datasetDTO);
        DatasetConfig config = datasetLocal.getDatasetConfig();
        assert (null != config);
        assert (CollectionUtils.isNotEmpty(config.getProjectionsList()));
        assert (CollectionUtils.isNotEmpty(config.getFromDatasets()));
        Integer limit = pageCursor.getPageSize();
        assert (null != limit);
        assert (null != pageCursor.getCursor());
        Long offset = 0l;
        try{
            offset = Long.valueOf(pageCursor.getCursor());
            offset = datasetService.getOffsetBasedOnDirection(offset, pageCursor.getDirection(), limit);
        }catch (Exception e){
            log.error("Cursor {} is not parsable to long, always returns first page, exception is {}", pageCursor.getCursor(), ExceptionUtils.getStackTrace(e));
        }
        Map<String, Object> dataAndCols =  datasetService.readDataWithPagination(datasetLocal, Map.of(),limit,offset);
        long recordCount = ((List<Map<String, Object>>)dataAndCols.get("data")).size();
        DatasetSampleDTO dto = new DatasetSampleDTO();
        dto.setColumns(transformer.toDatasetSampleColumnsDTOSFromDatastoreMetadata((List<DatastoreFieldMetadata>)dataAndCols.get("columns")));
        dto.setData(transformer.toDatasetSampleDataDTOS((List<Map<String, Object>>)dataAndCols.get("data")));
        dto.setPageInfo(datasetService.addPageInfo(recordCount,offset,transformer.transformToDatasetForCount(datasetDTO), datasetReadData.getPreviousTotalCount()));
        return dto;
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/count")
    public DatasetSampleDTO previewCount(@RequestBody DatasetDTO dataset) {
        Dataset datasetLocal = transformer.transformToDatasetForCount(dataset);
        DatasetConfig config = datasetLocal.getDatasetConfig();
        assert (null != config);
        assert (CollectionUtils.isNotEmpty(config.getProjectionsList()));
        Map<String, Object> dataAndCols =  datasetService.readSampleData(datasetLocal, Map.of());
        DatasetSampleDTO dto = new DatasetSampleDTO();
        dto.setColumns(transformer.toDatasetSampleColumnsDTOSFromDatastoreMetadata((List<DatastoreFieldMetadata>)dataAndCols.get("columns")));
        dto.setData(transformer.toDatasetSampleDataDTOS((List<Map<String, Object>>)dataAndCols.get("data")));
        return dto;
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/countWithQuery")
    public DatasetSampleDTO previewCountWithQuery(@RequestBody DatasetDTO dataset) {
        Dataset datasetLocal = transformer.transformToDatasetForCount(dataset);
        DatasetConfig config = datasetLocal.getDatasetConfig();
        assert (null != config);
        assert (CollectionUtils.isNotEmpty(config.getProjectionsList()));
        Map<String, Object> dataAndCols =  datasetService.readSampleDataForQuery(datasetLocal,Map.of(), DatasetService.READ_SAMPLE_LIMIT, 0l,null);
        DatasetSampleDTO dto = new DatasetSampleDTO();
        dto.setColumns(transformer.toDatasetSampleColumnsDTOSFromDatastoreMetadata((List<DatastoreFieldMetadata>)dataAndCols.get("columns")));
        dto.setData(transformer.toDatasetSampleDataDTOS((List<Map<String, Object>>)dataAndCols.get("data")));
        return dto;
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.GET, value = "{datasetId}/readSampleData")
    public DatasetSampleDTO readSampleData(@PathVariable String datasetId) {
        Dataset dataset = datasetService.getDataset(datasetId);
        DatasetConfig config = dataset.getDatasetConfig();
        assert (null != config);
        assert (CollectionUtils.isNotEmpty(config.getProjectionsList()));
        Map<String, Object> dataAndCols =  datasetService.readSampleData(dataset, Map.of());
        DatasetSampleDTO dto = new DatasetSampleDTO();
        dto.setColumns(transformer.toDatasetSampleColumnsDTOSFromDatastoreMetadata((List<DatastoreFieldMetadata>)dataAndCols.get("columns")));
        dto.setData(transformer.toDatasetSampleDataDTOS((List<Map<String, Object>>)dataAndCols.get("data")));
        return dto;
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.GET, value = "/datasourcesInfo")
    public List<DatasetFromDTO> getDatasources(@RequestParam boolean withEntityInfo) {

        List<DatasetFromDTO> result = new ArrayList<>();
        if (withEntityInfo){
            Schema schema = schemaService.getSyncariSchema(true, false);
            if (null != schema){
                schema.getEntities().forEach(e -> {
                    DatasetFromDTO dto = new DatasetFromDTO();
                    dto.setDisplayName(e.getDisplayName());
                    dto.setDescription(e.getDescription());
                    dto.setApiName(e.getApiName());
                    dto.setDatasetId(e.getId());
                    dto.setDatasetType(DatasourceType.ENTITY);
                    result.add(dto);
                });
            }
        }
        List<Dataset> allApprovedDatasets = datasetService.getAllApprovedDatasetsWithVersion();
        allApprovedDatasets.forEach(ds -> {
            DatasetFromDTO dto = new DatasetFromDTO();
            dto.setDisplayName(ds.getDisplayName());
            dto.setDescription(ds.getDescription());
            dto.setApiName(ds.getName());
            dto.setDatasetType(DatasourceType.DATASET);
            dto.setDatasetId(ds.getId());
            result.add(dto);
        });
        return result;
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.GET, value = "/datasourcesInfo/insightsProvider")
    public List<DatasetFromDTO> getInsightsProviderDatasources(@RequestParam boolean withEntityInfo) {

        List<DatasetFromDTO> result = new ArrayList<>();
        if (withEntityInfo){
            Schema schema = schemaService.getSyncariSchema(true, false);
            if (null != schema){
                schema.getEntities().forEach(e -> {
                    DatasetFromDTO dto = new DatasetFromDTO();
                    dto.setDisplayName(e.getDisplayName());
                    dto.setDescription(e.getDescription());
                    dto.setApiName(e.getApiName());
                    dto.setDatasetId(e.getId());
                    dto.setDatasetType(DatasourceType.ENTITY);
                    result.add(dto);
                });
            }
        }
        List<Dataset> allApprovedDatasets = datasetService.getAllApprovedDatasetsWithVersion();
        List<Dataset> insightsProviderDatasets = allApprovedDatasets.stream().filter(d -> d.isApproved() && (StringUtils.isNotEmpty(d.getInsightsProviderSQLViewId()) && StringUtils.isNotEmpty(d.getInsightsProviderId()))).collect(Collectors.toList());
        insightsProviderDatasets.forEach(ds -> {
            DatasetFromDTO dto = new DatasetFromDTO();
            dto.setDisplayName(ds.getDisplayName());
            dto.setDescription(ds.getDescription());
            dto.setApiName(ds.getName());
            dto.setDatasetType(DatasourceType.DATASET);
            dto.setDatasetId(ds.getId());
            result.add(dto);
        });
        return result;
    }

    @Secured(CREATE_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/{datasetId}/createVariable")
    public VariableDTO createVariable(@PathVariable String datasetId,@RequestBody VariableDTO variabledto) {
        Variable variable = transformer.toVariable(variabledto);
        Variable var = datasetService.createVariable(datasetId, variable);
        return transformer.toVariableDTO(Optional.ofNullable(datasetId), var);
    }

    @Secured(UPDATE_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/{datasetId}/updateVariable")
    public VariableDTO updateVariable(@PathVariable String datasetId,@RequestBody VariableDTO variabledto) {
        Variable variable = transformer.toVariable(variabledto);
        Variable var = datasetService.updateVariable(datasetId, variable);
        return transformer.toVariableDTO(Optional.ofNullable(datasetId), var);
    }

    @Secured(DELETE_DATASET)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{datasetId}/deleteVariable/{varApiName}")
    public VariableDTO deleteVariable(@PathVariable String datasetId,@PathVariable String varApiName) {
        Variable var = datasetService.deleteVariable(datasetId, varApiName);
        return transformer.toVariableDTO(Optional.ofNullable(datasetId), var);
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.GET, value = "/{datasetId}/getVariables")
    public List<VariableDTO> getVariables(@PathVariable String datasetId) {
        List<Variable> allVariables = datasetService.getVariables(datasetId);
        return transformer.transformVariables(datasetId, allVariables);
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.GET, value = "/{datasetId}/getVariable/{varApiName}")
    public VariableDTO getVariable(@PathVariable String datasetId,@PathVariable String varApiName) {
        Variable variable = datasetService.getVariable(datasetId, varApiName);
        return transformer.toVariableDTO(Optional.ofNullable(datasetId), variable);
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/join/suggestions")
    public List<JoinDTO> getAutoJoinSuggestions(@RequestBody List<DatasetFromDTO> fromDataSources) {
        List<DatasetFrom> entitySources = fromDataSources.stream().filter(d -> DatasourceType.ENTITY.equals(d.getDatasetType()))
                .map(d -> transformer.toDatasetFrom(d)).collect(Collectors.toList());

        List<Join> joins = datasetService.fetchAutoJoinSuggestions(entitySources);
        return joins.stream().map(j -> transformer.toJoinDTO(j)).collect(Collectors.toList());
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/autojoin")
    public List<JoinDTO> getAutoJoins(@RequestBody AutoJoinDTO autoJoinDataSources) {
        List<DatasetFromDTO>  existingSources = autoJoinDataSources.getExistingDataSources();
        List<DatasetFromDTO>  newDataSources = autoJoinDataSources.getNewDataSources();
        List<DatasetFrom> existingEntitySources = existingSources.stream().filter(d -> DatasourceType.ENTITY.equals(d.getDatasetType()))
                .map(d -> transformer.toDatasetFrom(d)).collect(Collectors.toList());

        List<DatasetFrom> newEntitySources = newDataSources.stream().filter(d -> DatasourceType.ENTITY.equals(d.getDatasetType()))
                .map(d -> transformer.toDatasetFrom(d)).collect(Collectors.toList());

        List<Join> joins = datasetService.fetchAutoJoins(existingEntitySources, newEntitySources);
        return joins.stream().map(j -> transformer.toJoinDTO(j)).collect(Collectors.toList());
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/autotimegrain")
    public ProjectionDTO getTimeGrainProjectionForGroup(@RequestBody GroupByDTO groupTimeGrain) {
        return transformer.buildProjectionForTimeGrainGrouping(groupTimeGrain);
    }

    @Secured(VIEW_DATACARD)
    @RequestMapping(method = RequestMethod.GET, value = "/{datasetId}/dependencies")
    public List<InsightsDependencyDTO> getDatasetdDependencies(@PathVariable String datasetId){
        List<InsightsDependencyDTO> dependencies = new ArrayList<>();
        datasetService.getDatasetDependencies(datasetId).forEach(d -> {
            // retrieve dataset if its used in some other dataset
            if(ComponentType.dataset.equals(d.getFromComponent())){
                datasetService.findDataset(d.getFromId()).ifPresent(dataset -> {
                    InsightsDependencyDTO dep = new InsightsDependencyDTO();
                    dep.setId(d.getFromId());
                    dep.setType(d.getFromComponent());
                    dep.setDraftStatus(dataset.getDraftStatus());
                    dep.setNestedDraft(false);
                    dep.setName(dataset.getDisplayName());
                    if (!StringUtils.isBlank(dataset.getCreatedBy())) {
                        userService.findUserById(dataset.getCreatedBy()).ifPresent(u -> dep.setAuthor(u.getName()));
                    }
                    dependencies.add(dep);
                });
            } else if(ComponentType.datacard.equals(d.getFromComponent())) {
                // retrieve datacard if exists and add it as dependency
                datacardService.findDatacard(d.getFromId()).ifPresent(datacard -> {
                    InsightsDependencyDTO dep = new InsightsDependencyDTO();
                    dep.setId(d.getFromId());
                    dep.setType(d.getFromComponent());
                    dep.setDraftStatus(datacard.getDraftStatus());
                    dep.setNestedDraft(false);
                    dep.setName(datacard.getDisplayName());
                    if (!StringUtils.isBlank(datacard.getCreatedBy())) {
                        userService.findUserById(datacard.getCreatedBy()).ifPresent(u -> dep.setAuthor(u.getName()));
                    }
                    dependencies.add(dep);

                    // Find corresponding dashboard deps and add it to list
                    datacardService.getDatacardDependencies(datacard.getId()).forEach(dc -> {
                        // retrieve dashboard if exists
                        dashboardService.findDashboard(dc.getFromId()).ifPresent(dashboard -> {
                            InsightsDependencyDTO dashDep = new InsightsDependencyDTO();
                            dashDep.setId(dc.getFromId());
                            dashDep.setType(dc.getFromComponent());
                            dashDep.setDraftStatus(dashboard.getDraftStatus());
                            if (dashboard.hasPublishedParent()) {
                                // for deep linking draft still uses the published id
                                dashDep.setId(dashboard.getParentId());
                                dashDep.setNestedDraft(true);
                            }
                            dashDep.setName(dashboard.getDisplayName());
                            if (!StringUtils.isBlank(dashboard.getCreatedBy())) {
                                userService.findUserById(dashboard.getCreatedBy()).ifPresent(u -> dashDep.setAuthor(u.getName()));
                            }
                            dependencies.add(dashDep);
                        });

                    });
                });
            }

        });
        return dependencies;
    }
}
