package com.syncari.connector.service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.Result;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.service.def.FileService;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Storage;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseFileService implements FileService {
    private static final String WM_FIELD = "lastmodifiedtime";
    private static final String FILE_NAME_FORMAT = "%s/%s_%s_%s.csv";
    protected static final String SLASH = "/";
    public static final String SYNCARI_ID = "SyncariId";
    public static final String USE_DISPLAY_NAME = "useDisplayName";

    @Autowired
    Transformer transformer;
    @Autowired
    TextUtil textUtil;

    public void writeFile(ConnectorInfo connector, InputStream inputStream, String fileName, String baseFolder) {
        try {
            String fullFileName = baseFolder + (baseFolder.endsWith(SLASH) ? "" : SLASH) + fileName;
            Storage storage = getFileManager(connector);
            storage.write(inputStream, fullFileName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected SyncResponse writeFile(SyncRequest request, boolean createUUID, String operation, String baseFolder) {
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
            for(EntityData d : request.getData().get(request.getConnector().getId())) {
                String externalId = createUUID ? UUID.randomUUID().toString() : d.getId();
                List<String> values = new ArrayList<>();
                values.add(externalId);
                attributes.stream().filter(a -> !SYNCARI_ID.equalsIgnoreCase(a)).forEach(a -> {
                    if(wm.getApiName().equalsIgnoreCase(a)) {
                        values.add(ZonedDateTime.now().toString());
                    } else {
                        values.add(d.getValueAsString(a));
                    }
                });
                csvPrinter.printRecord(values.toArray(new String[values.size()]));
                results.add(new Result(true, externalId, d.getSyncariEntityId()));
            }
            csvPrinter.flush();
            Storage storage = getFileManager(request.getConnector());
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

    protected void createFile(SyncRequest request, String baseFolder)  {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            List<String> attributes = request.getEntitySchema().getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toList());
            CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(stream), CSVFormat.DEFAULT
                    .withHeader(attributes.toArray(new String[attributes.size()])));
            csvPrinter.flush();
            Storage storage = getFileManager(request.getConnector());
            String fileName = String.format("%s/%s.csv", baseFolder + (baseFolder.endsWith(SLASH) ? "" : SLASH) + request.getEntityName(), request.getEntityName());
            log.info("Created file {} with {} columns", fileName, attributes.size());
            InputStream fileStream = new ByteArrayInputStream(stream.toByteArray());
            storage.write(fileStream, fileName);
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
    }

    protected abstract Storage getFileManager(ConnectorInfo connector);
}