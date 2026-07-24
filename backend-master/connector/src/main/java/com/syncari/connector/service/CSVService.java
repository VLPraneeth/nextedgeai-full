package com.syncari.connector.service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.CSVStorageIterator;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.utils.*;
import com.syncari.utils.file.File;
import com.syncari.utils.file.FileManager;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class CSVService implements FileSystemService {
    private static final String WM_FIELD = "lastmodifiedtime";
    private static final String FILE_NAME_FORMAT = "%s/%s_%s_%s.csv";
    protected static final String SLASH = "/";
    public static final String SYNCARI_ID = "SyncariId";
    public static final String USE_DISPLAY_NAME = "useDisplayName";
    public static final String DEFAULT_FILE_MATCH_PATTERN = "(?i)(.*\\.csv|.*\\.txt)";
    public static final String BASE_FOLDER_PATH = "baseFolderPath";
    public static final String MATCHING_FOLDERS = "matchingFolders";
    public static final String MATCHING_FILES = "matchingFiles";
    public static final String SKIP_LINES_PATTERN = "skipLinesPattern";
    public static final String HAS_HEADER = "hasHeader";

    private final FileManager fileManager;
    private final Storage storage;
    private final CsvUtils csvUtils = new CsvUtils();


    public CSVService(FileManager fileManager, Storage storage) {
        this.fileManager = fileManager;
        this.storage = storage;
    }

    private static String getConfig(String propName, String defaultValue, ConnectorInfo connector) {
        return connector.getMetaConfig().getOrDefault(propName, defaultValue).toString();
    }

    private static String getConfig(String propName, ConnectorInfo connector) {
        return connector.getMetaConfig().getOrDefault(propName, "").toString();
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        BatchJob job = new BatchJob();
        final ConnectorInfo connector = request.getConnector();
        String fullPath = getConfig(BASE_FOLDER_PATH, connector) + "/" + request.getEntityName();
        List<File> ls = fileManager.list(fullPath, getConfig("fileSelectorPattern", connector));
        for (File e : ls) {
            if (e.getLastModified() > request.getWatermark().getStart()) {
                if (!e.isDirectory()) {
                    job.getDownloadedFielURLs().add(fullPath + "/" + e.getName());
                    log.info("Adding {} file as modified", fullPath + "/" + e.getName());
                }
            }
        }
        return getCSVRecordsByWatermark(request, storage, job);
    }

    protected FetchResponse getCSVRecordsByWatermark(SyncRequest request, Storage storage, BatchJob job) {
        final char delimiter = getDelimiter(request.getConnector());
        final boolean hasHeader = hasHeader(request.getConnector());
        final String skipLinesPattern = getConfig(SKIP_LINES_PATTERN, request.getConnector());
        final boolean validSkipLinePattern = StringUtils.isNotBlank(skipLinesPattern);
        final CSVOptions csvOptions = new CSVOptions()
                .withHeader(hasHeader)
                .withDelimiter(delimiter)
                .withSkipLinePattern(skipLinesPattern);
        int pageSize = request.getPageSize() > 0 ? Math.min(request.getPageSize(), 1000) : 1000;
        CSVStorageIterator iterator = new S3CSVStorageIterator(storage, job, pageSize, request, csvOptions);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private static boolean hasHeader(ConnectorInfo connector) {
        return Boolean.parseBoolean(getConfig(HAS_HEADER, "true", connector));
    }

    private static Character getDelimiter(ConnectorInfo connector) {
        String separator = getConfig("delimiter", ",", connector);
        if (StringUtils.isEmpty(separator)) {
            return ',';
        } else if ("\\t".equals(separator)) {
            return '\t';
        }
        return separator.charAt(0);
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        DescribeAllRequest req = new DescribeAllRequest(request.getConnector(), List.of());
        return describeAll(req).stream().filter(e -> e.getApiName().equalsIgnoreCase(request.getEntity())).findFirst();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> schemaList = new ArrayList<>();
        final ConnectorInfo connector = request.getConnector();

        String fileFormat = getConfig("fileFormat", "CSV", connector);
        //TODO: use fileFormat for CSV and FILE
        return describeCSVEntities(request, connector, fileManager, schemaList);
    }

    private List<EntitySchema> describeCSVEntities(DescribeAllRequest request, ConnectorInfo connector, FileManager manager, List<EntitySchema> schemaList) {
        String baseFolder = getConfig(BASE_FOLDER_PATH, connector);
        String matchingFolders = getConfig(MATCHING_FOLDERS, connector);

        final List<File> list = manager.list(baseFolder, matchingFolders);
        for (File e : list) {
            if (e.isDirectory() && !".".equalsIgnoreCase(e.getName()) && !"..".equalsIgnoreCase(e.getName())) {
                String entityName = e.getName().replace(baseFolder, "").replace(SLASH, "");
                EntitySchema entitySchema = new EntitySchema(entityName, StringUtils.capitalize(entityName));
                Map<String, String> columns = getAttributes(manager, e.getName(), connector);
                if (columns.isEmpty()) {
                    log.warn("Folder {} is empty, skipping", e.getName());
                    continue;
                }
                for (Map.Entry<String, String> field : columns.entrySet()) {
                    AttributeSchema attr = new AttributeSchema(TextUtil.createApiName(field.getKey()), field.getValue());
                    attr.setDisplayName(field.getKey());
                    entitySchema.addField(attr);
                }
                if (!entitySchema.hasField("lastModifiedTime")) {
                    AttributeSchema attr = new AttributeSchema("lastModifiedTime", "datetime");
                    attr.setDisplayName("Last Modified Time");
                    entitySchema.addField(attr);
                }
                AttributeSchema wm = entitySchema.getField("lastModifiedTime").get();
                wm.setDataType("datetime");
                wm.setWatermarkField(true);
                wm.setUpdateable(false);
                addFileMetaFields(entitySchema);
                schemaList.add(entitySchema);
                log.debug("Adding {} entity to schema as modified", e.getName());
            }
        }
        return schemaList;
    }

    private static void addFileMetaFields(EntitySchema entitySchema) {
        if (!entitySchema.hasField("__recordNumber")) {
            AttributeSchema attr = new AttributeSchema("__recordNumber", "integer");
            attr.setDisplayName("Record Number");
            entitySchema.addField(attr);
        }
        if (!entitySchema.hasField("__file")) {
            AttributeSchema attr = new AttributeSchema("__file", "string");
            attr.setDisplayName("File URL");
            entitySchema.addField(attr);
        }
        if (!entitySchema.hasField("__isFirstRecord")) {
            AttributeSchema attr = new AttributeSchema("__isFirstRecord", "boolean");
            attr.setDisplayName("Is First Record");
            entitySchema.addField(attr);
        }
        if (!entitySchema.hasField("__isLastRecord")) {
            AttributeSchema attr = new AttributeSchema("__isLastRecord", "boolean");
            attr.setDisplayName("Is Last Record");
            entitySchema.addField(attr);
        }
    }
    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("sftp does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("sftp does not support delete field");
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        throw new NotSupportedException("sftp does not support getbyids");
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        return writeFile(request, false, "create", getConfig(BASE_FOLDER_PATH, request.getConnector()));
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return writeFile(request, false, "update", getConfig(BASE_FOLDER_PATH, request.getConnector()));
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return writeFile(request, false, "delete", getConfig(BASE_FOLDER_PATH, request.getConnector()));
    }

    @Override
    @SneakyThrows
    public EntitySchema createObject(CreateObjectRequest request) {
        String baseFolder = getConfig(BASE_FOLDER_PATH, request.getConnector());
        fileManager.createDirectory(baseFolder + (baseFolder.endsWith(SLASH) ? "" : SLASH) + request.getSchema().getApiName());
        SyncRequest req = new SyncRequest().Builder(request.getConnector(), request.getSchema());
        createFile(req, baseFolder);
        return describe(new DescribeRequest(request.getConnector(), request.getSchema().getApiName())).get();
    }

    @Override
    public void deleteObject(DeleteObjectRequest request) {
        String baseFolder = getConfig(BASE_FOLDER_PATH, request.getConnector());
        storage.delete(baseFolder + (baseFolder.endsWith(SLASH) ? "" : SLASH) + request.getEntityName());
    }


    @SneakyThrows
    private Map<String, String> getAttributes(FileManager manager, String entityName, ConnectorInfo connector) {
        String folderName = getConfig(BASE_FOLDER_PATH, connector) + "/" + entityName;
        String matchingFiles = getConfig(MATCHING_FILES, connector);
        String skipLinesPattern = getConfig(SKIP_LINES_PATTERN, connector);
        boolean hasHeader = hasHeader(connector);
        final char delimiter = getDelimiter(connector);
        final String matchingFilePattern = getMatchingFilePattern(matchingFiles);
        List<File> ls = new ArrayList<>(manager.list(folderName, matchingFilePattern));
        // Sort files by last modified date descending to get latest file first
        // Files with lastModified <= 0 (not set) are placed at the end
        ls.sort((a, b) -> {
            boolean aValid = a.getLastModified() > 0;
            boolean bValid = b.getLastModified() > 0;
            if (!aValid && !bValid) return 0;
            if (!aValid) return 1;
            if (!bValid) return -1;
            return Long.compare(b.getLastModified(), a.getLastModified());
        });
        for (File e : ls) {
            if (!e.isDirectory()) {
                InputStream stream = new BOMInputStream(manager.readFile(folderName + "/" + e.getName()));
                return csvUtils.detectDatatypes(stream, new CSVOptions()
                        .withSkipLinePattern(skipLinesPattern)
                        .withHeader(hasHeader)
                        .withDelimiter(delimiter)
                );
            }
        }
        return Map.of();
    }

    private static String getMatchingFilePattern(String matchingFiles) {
        return StringUtils.isBlank(matchingFiles) ? DEFAULT_FILE_MATCH_PATTERN : matchingFiles;
    }

    private static boolean isTextFile(File e, String matchingFiles) {
        return e.getName().endsWith(".csv") || e.getName().endsWith(".txt");
    }

    public void writeFile(ConnectorInfo connector, InputStream inputStream, String fileName, String baseFolder) {
        try {
            String fullFileName = baseFolder + (baseFolder.endsWith(SLASH) ? "" : SLASH) + fileName;
            storage.write(inputStream, fullFileName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public SyncResponse writeFile(SyncRequest request, boolean createUUID, String operation, String baseFolder) {
        SyncResponse response = new SyncResponse();
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            List<String> attributes = new ArrayList<>();
            List<String> displayNames = new ArrayList<>();
            List<Result> results = new ArrayList<>();
            AttributeSchema wm = request.getEntitySchema().hasWatermarkField() ? request.getEntitySchema().getWatermarkField() : request.getEntitySchema().getField(WM_FIELD).get();
            attributes.add(SYNCARI_ID);
            displayNames.add(SYNCARI_ID);
            boolean useDisplayName = (boolean) request.getConnector().getMetaConfig().getOrDefault(USE_DISPLAY_NAME, false);
            request.getEntitySchema().getAttributes().stream().forEach(a -> {
                attributes.add(a.getApiName());
                displayNames.add(a.getDisplayName());
            });
            CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(stream), CSVFormat.DEFAULT
                    .withHeader(useDisplayName ? displayNames.toArray(new String[attributes.size()]) : attributes.toArray(new String[attributes.size()])));
            for (EntityData d : request.getData().get(request.getConnector().getId())) {
                String externalId = createUUID ? UUID.randomUUID().toString() : d.getId();
                List<String> values = new ArrayList<>();
                values.add(externalId);
                attributes.stream().filter(a -> !SYNCARI_ID.equalsIgnoreCase(a)).forEach(a -> {
                    if (wm.getApiName().equalsIgnoreCase(a)) {
                        values.add(ZonedDateTime.now().toString());
                    } else {
                        values.add(d.getValueAsString(a));
                    }
                });
                csvPrinter.printRecord(values.toArray(new String[values.size()]));
                results.add(new Result(true, externalId, d.getSyncariEntityId()));
            }
            csvPrinter.flush();
            String fileName = String.format(FILE_NAME_FORMAT, baseFolder + (baseFolder.endsWith(SLASH) ? "" : SLASH) + request.getEntityName(), request.getEntityName(), operation, DateUtil.format(new Date()));
            log.debug("Writing {} records to file {}", request.getData().get(request.getConnector().getId()).size(), fileName);
            InputStream fileStream = new ByteArrayInputStream(stream.toByteArray());
            storage.write(fileStream, fileName);
            response.getResults().addAll(results);
        } catch (Exception e) {
            response.setSuccess(false);
            log.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
        return response;
    }

    protected void createFile(SyncRequest request, String baseFolder) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            List<String> attributes = request.getEntitySchema().getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList());
            CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(stream), CSVFormat.DEFAULT
                    .withHeader(attributes.toArray(new String[attributes.size()])));
            csvPrinter.flush();
            String fileName = String.format("%s/%s.csv", baseFolder + (baseFolder.endsWith(SLASH) ? "" : SLASH) + request.getEntityName(), request.getEntityName());
            log.info("Created file {} with {} columns", fileName, attributes.size());
            InputStream fileStream = new ByteArrayInputStream(stream.toByteArray());
            storage.write(fileStream, fileName);
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
    }
}

