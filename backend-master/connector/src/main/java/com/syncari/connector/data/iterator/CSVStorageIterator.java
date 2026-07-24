package com.syncari.connector.data.iterator;

import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.BatchJob;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.database.CompositeKeyHelper;
import com.syncari.utils.CSVOptions;
import com.syncari.utils.CsvUtils;
import com.syncari.utils.RewindableCSVParser;
import com.syncari.utils.Storage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.syncari.utils.ExceptionUtils.rethrow;

@Slf4j
public class CSVStorageIterator extends StorageBasedIterator {
    protected boolean hasHeader;
    private CSVOptions options;
    protected RewindableCSVParser parser;
    protected char separator = ',';
    protected CsvUtils csvUtils = new CsvUtils();

    public CSVStorageIterator(Storage storage, BatchJob job, int pageSize, SyncRequest request, CSVOptions options) {
        super(storage, job, pageSize, request);
        this.options = options;
    }
    public CSVStorageIterator(Storage storage, BatchJob job, int pageSize, SyncRequest request, boolean hasHeader) {
        super(storage, job, pageSize, request);
        this.hasHeader = hasHeader;
    }

    public CSVStorageIterator(Storage storage, BatchJob job, int pageSize, SyncRequest request, boolean hasHeader, char separator) {
        super(storage, job, pageSize, request);
        this.hasHeader = hasHeader;
        this.separator = separator;
    }

    protected void fetchNextPage() {
        currentBatch.ifPresent(batch -> {
            createParserIfNeeded(batch);
            int max = pageSize;
            List<EntityData> records = new ArrayList<>();
            final List<String> headers = CsvUtils.getHeaders(parser, getCsvOptions());
            Iterator<CSVRecord> iterator = parser.iterator();

            while (iterator.hasNext() && max > 0) {
                CSVRecord csvRecord = iterator.next();
                EntityData record = createRecord(csvRecord, headers);
                if(!record.getValues().isEmpty()) {
                    records.add(record);
                }
                max--;
            }
            currentPage = records;
            if(!iterator.hasNext()){
                if (!currentPage.isEmpty()) {
                    currentPage.get(currentPage.size() - 1).addValue("__isLastRecord", true);
                }
                currentBatch = Optional.empty();
                rethrow(()->parser.close());
            }
        });

    }


    protected void createParserIfNeeded(InputStream dataStream) {
        if (parser == null|| parser.isClosed()) {
            parser = rethrow(() -> createParser(dataStream));
        }
    }

    protected RewindableCSVParser createParser(InputStream dataStream) throws IOException {
        final CSVOptions csvFormat = getCsvOptions();
        return new RewindableCSVParser(csvUtils.getCSVParser(dataStream, csvFormat));
    }

    private CSVOptions getCsvOptions() {
        final CSVOptions csvFormat = options == null ? new CSVOptions()
                .withHeader(hasHeader).withDelimiter(separator) : options;
        return csvFormat;
    }

    protected EntityData createRecord(CSVRecord next, List<String> headers) {
        Map<String, Object> values = new HashMap<>();
        toMap(next, headers).forEach((k, v) -> {
            if(StringUtils.isBlank(k)) return;
            var key = request.getEntitySchema().getFieldByDisplayName(k).map(AttributeSchema::getApiName).orElse(k);
            values.put(key, v == null ? null : v.trim());
		});
        values.put("__recordNumber", next.getRecordNumber());
        values.put("__file", currentURL.orElse(null));
        values.put("__isFirstRecord", next.getRecordNumber() == 1);
        //set this to false bhy default. The caller will override the value if needed
        values.put("__isLastRecord", false);
        EntityData record = new EntityData().setValues(values);
        String id = getId(values);
        Long watermark = getWatermark(values);
        record.setId(id);
        record.setLastModified(watermark);
        record.setName(request.getEntitySchema().getApiName());
        record.setConnectorId(request.getConnector().getId());
        return record;
    }

    protected static Map<String, String> toMap(CSVRecord next, List<String> headers) {
        if (headers.isEmpty()) {
            return next.toMap();
        } else {
            Map<String, String> recordMap = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                recordMap.put(headers.get(i), next.get(i));
            }
            return recordMap;
        }
    }

    protected String getId(Map<String, Object> values) {
        final AttributeSchema idField = request.getEntitySchema().getIdField();
        if (idField.isCompositeKey()) {
            final EntityData entityData = new EntityData(request.getEntitySchema().getApiName()).setValues(values);
            return new CompositeKeyHelper().composeIdKeys(entityData, request.getEntitySchema());
        } else {
            return Objects.toString(values.get(idField.getApiName().toLowerCase()), null);
        }
    }

    protected Long getWatermark(Map<String, Object> values) {
        String watermarkFieldName = request.getEntitySchema().getWatermarkField().getApiName();
        try {
            return ConnectorHelper.convert(Objects.toString(values.get(watermarkFieldName.toLowerCase()))).toInstant().toEpochMilli();
        } catch (Exception ne) {
            log.error(ne.getMessage(), ne);
            return request.getWatermark().getStart();
        }
    }
}
