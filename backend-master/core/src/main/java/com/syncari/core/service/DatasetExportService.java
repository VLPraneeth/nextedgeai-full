package com.syncari.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.data.DatastoreFieldMetadata;
import com.syncari.connector.database.DatabaseService;
import com.syncari.core.SyncariContext;
import com.syncari.core.abac.AbacContext;
import com.syncari.core.abac.AbacService;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.model.Event;
import com.syncari.core.model.abac.Permission;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.model.insights.CountQueryFunction;
import com.syncari.core.model.insights.Projection;
import com.syncari.core.model.insights.QField;
import com.syncari.core.model.insights.QueryFunction;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.model.insights.dataset.DatasetExport;
import com.syncari.core.model.insights.dataset.DatasetFrom;
import com.syncari.core.repositories.customer.DatasetExportRepo;
import com.syncari.utils.I18n;
import com.syncari.utils.file.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class DatasetExportService {

    @Autowired
    ObjectMapper mapper;

    @Autowired
    Publisher publisher;

    @Autowired
    private GCSFileManager gcsFileManager;

    @Autowired
    private FileUtil fileUtil;

    @Autowired
    DatasetExportRepo datasetExportRepo;

    @Autowired
    DatasetService datasetService;

    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;

    @Autowired
    AppConfig appConfig;
    
    @Autowired
    AbacService abac;

    public static Integer PAGE_SIZE_AND_OFFSET = 1000;

    /**
     * Add export event to the generic queue and add record in db
     * @param dataset
     */
    public void exportDatasetAsync(Dataset dataset){
        abac.check(new AbacContext().withAction(Permission.EXECUTE)
            .withResourceType(ResourceType.DATASET).withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")), dataset);
        DatasetExport export = new DatasetExport().setDatasetId(dataset.getId()).setDatasetToBeExported(dataset)
                .setRequestedTime(Instant.now()).setUserId(SyncariContext.getUser().getId())
                .setExpiredTime(Instant.now().plus(30, ChronoUnit.DAYS))
                .setStatus(DatasetExport.DatasetExportStatus.PENDING).setUserName(SyncariContext.getUser().getName()).setDatasetToBeExported(dataset);
        DatasetExport saved = datasetExportRepo.save(export);
        String datasetExportJobId = saved.getId();
        try {
            Event event = new Event().setType(EventTypes.EXPORT_DATASET).setDetails(Map.of("datasetId", dataset.getId(),
                    "userId", SyncariContext.getUser().getId(),"userName", SyncariContext.getUser().getName(), "datasetExportJobId", datasetExportJobId));
            Message msg = new Message(SyncariContext.getSyncariId(), event);
            String eventString = mapper.writeValueAsString(msg);
            log.info(String.format("Sending Message: %s", eventString));
            publisher.publishToGenericQueue(eventString);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new SyncariValidationException("Error during export dataset. Please contact Syncari support");
        }
    }

    public List<DatasetExport> getExportJobs(String datasetId){
        validateCondition(StringUtils.isEmpty(datasetId),I18n.i18n("dataset_id_null"));
        List<DatasetExport> notExpiredExportedDatasetJobs = datasetExportRepo.findAllByDatasetId(datasetId).stream().filter(exportJob ->
            Instant.now().minusMillis(exportJob.getExpiredTime().toEpochMilli()).toEpochMilli() < 0
        ).collect(Collectors.toList());
        return notExpiredExportedDatasetJobs;
    }

    public void cancelExportJobs(String exportJobId){
        validateCondition(StringUtils.isEmpty(exportJobId),I18n.i18n("export_id_null"));
        Optional<DatasetExport> jobToCancel = datasetExportRepo.findById(exportJobId);
        validateCondition(jobToCancel.isPresent() && !jobToCancel.get().getStatus().name().equalsIgnoreCase(DatasetExport.DatasetExportStatus.PENDING.name()),I18n.i18n("export_job_cannot_cancel"));
        jobToCancel.ifPresent(job -> {
            job.setStatus(DatasetExport.DatasetExportStatus.CANCELLED);
            datasetExportRepo.save(job);
        });
    }

    public void deleteExportJobs(String exportJobId){
        validateCondition(StringUtils.isEmpty(exportJobId),I18n.i18n("export_id_null"));
        Optional<DatasetExport> jobToDelete = datasetExportRepo.findById(exportJobId);
        validateCondition(!jobToDelete.isPresent(),I18n.i18n("export_job_not_exists"));
        jobToDelete.ifPresent(job -> {
            String fileLinkinGCS = job.getExportedFileLink();
            try{
                gcsFileManager.delete(fileLinkinGCS);
            }catch (Exception e){
                log.error("Exception occurred while deleting file from GCS {}", ExceptionUtils.getStackTrace(e));
                String body = String.format(I18n.i18n("dataset_export_delete_error"), exportJobId, fileLinkinGCS, e.getMessage());
                emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), I18n.i18n("dataset_export_error_subject"),
                        body);
            }
            datasetExportRepo.deleteById(exportJobId);
        });
    }

    public DatasetExport saveDatasetExport(DatasetExport exportJob){
        return datasetExportRepo.save(exportJob);
    }

    public Optional<DatasetExport> findByExportJobId(String exportJobId){
        return datasetExportRepo.findById(exportJobId);
    }

    public List<DatasetExport> findAllByDatasetIdAndStatus(String exportJobId, List<DatasetExport.DatasetExportStatus> status){
        return datasetExportRepo.findAllByDatasetIdAndStatusIn(exportJobId, status.stream().map(x -> x.name()).collect(Collectors.toList()));
    }

    public List<DatasetExport> findAllByDatasetId(String datasetId){
        return datasetExportRepo.findAllByDatasetId(datasetId);
    }

    public List<DatasetExport> findAll(){
        return datasetExportRepo.findAll();
    }
    /**
     * Add export event to the generic queue and add record in db
     * @param dataset
     * @param exportedJobId
     */
    public Optional<String> exportDatasetDataToGCS(Dataset dataset, String exportedJobId){
        Long pageSize = 0l;
        int limit = PAGE_SIZE_AND_OFFSET;
        String name = exportedJobId + ".csv";
        String fixedFileName = fileUtil.sanitizeFileName(name);
        String fullyQualifiedFileName = SyncariContext.getSyncariId() + "/DatasetExportData/" + fixedFileName;
        Map<String, Object> dataMap = datasetService.readDataWithPagination(dataset, Map.of(), limit,pageSize);
        List<Map<String, Object>> dataPart = (List<Map<String, Object>>)dataMap.getOrDefault("data", List.of());
        // Todo Look for option how to append data into GCS file.
        List<Map<String, Object>> allData = new LinkedList<>();
        allData.addAll(dataPart);
        Map<String, Object> allDataMap = new HashMap<>();
        allDataMap.put("columns", dataMap.getOrDefault("columns", List.of()));
        while(CollectionUtils.isNotEmpty(dataPart)){
            pageSize += PAGE_SIZE_AND_OFFSET;
            dataMap = datasetService.readDataWithPagination(dataset, Map.of(), limit,pageSize);
            dataPart = (List<Map<String, Object>>)dataMap.getOrDefault("data", List.of());
            allData.addAll(dataPart);
        }

        allDataMap.put("data", allData);
        return createFile(fullyQualifiedFileName,allDataMap);
    }

    public Optional<String> createFile(String filePath, Map<String, Object> dataMap) {
        List<DatastoreFieldMetadata> datastoreFieldMetadata = (List<DatastoreFieldMetadata>)dataMap.getOrDefault("columns", List.of());
        List<Map<String, Object>> extractedData = (List<Map<String, Object>>)dataMap.getOrDefault("data", List.of());
        List<String> columns = datastoreFieldMetadata.stream().map(meta -> "\"" +meta.getAliasName() + "\"").collect(Collectors.toList());
        StringBuilder csvData = new StringBuilder();
        csvData.append(columns.stream().collect(Collectors.joining(",", "", "\n")));
        extractedData.forEach(eD -> {
            if (MapUtils.isNotEmpty(eD)){
                csvData.append(eD.values().stream().map(v -> {
                    if (null != v){
                        return "\"" + v.toString() + "\"";
                    }
                    return "";
                }).collect(Collectors.joining(",","","\n")));
            }else{
                log.error("Extracted Map data is empty for columns {}", columns);
            }
        });
        gcsFileManager.write(new ByteArrayInputStream(csvData.toString().getBytes()), filePath);
        return Optional.of(filePath);
    }

    public Dataset transformToDatasetForCount(Dataset dataset){
        validateCondition(null == dataset, "Dataset cannot be empty for this request");
        validateCondition(null == dataset.getDatasetConfig(), "Dataset Configuration cannot be empty for this request");
        DatasetConfig config = dataset.getDatasetConfig();
        if (!dataset.isSQLMode()){
            config.validate();
        }
        String displayName = UUID.randomUUID().toString();
        Dataset countDataset = new Dataset().setDisplayName(displayName);

        String datasetIdFrom = dataset.getId();
        DatasetFrom datasetFrom = new DatasetFrom().setDataset(dataset).setDatasetType(DatasourceType.DATASET)
                .setApiName(dataset.getDisplayName()).setDisplayName(dataset.getDisplayName()).setDatasetId(StringUtils.isNotEmpty(datasetIdFrom) ? datasetIdFrom : "");
        DatasetConfig countDSConfig = new DatasetConfig().setFromDatasets(List.of(datasetFrom));
        Map<String, String> fromMap = new HashMap<>();
        fromMap.put("", dataset.getDisplayName());
        List<Projection> projections = config.getProjectionsList();
        QField qfield = new QField().setType(QField.Type.DATASET).setName("*");
        Projection projection = new Projection();
        QueryFunction qf = new CountQueryFunction().setColumns(List.of(qfield)).setAlias("totalCount");
        projection.setAliasName("totalCount");
        projection.setFunction(qf);
        countDSConfig.setProjectionsList(List.of(projection));
        countDSConfig.setFromDatasets(List.of(datasetFrom));
        countDSConfig.setConfigMode(config.getConfigMode());
        countDataset.setDatasetConfig(countDSConfig);
        if (StringUtils.isNotEmpty(dataset.getRawQuery())) {
            countDataset.setRawQuery(String.format(DatabaseService.COUNT_WITH_INNERQUERY, dataset.getRawQuery()));
        }
        countDataset.setVariablesMap(dataset.getVariablesMap());
        return countDataset;
    }
}
