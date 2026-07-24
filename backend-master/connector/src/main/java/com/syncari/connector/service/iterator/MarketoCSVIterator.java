package com.syncari.connector.service.iterator;

import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.BatchJob;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.iterator.CSVStorageIterator;
import com.syncari.utils.Storage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
public class MarketoCSVIterator extends CSVStorageIterator {
    public MarketoCSVIterator(Storage storage, BatchJob job, int pageSize, SyncRequest request, boolean hasHeader) {
        super(storage, job, pageSize, request, hasHeader);
    }

    @Override
    protected EntityData createRecord(CSVRecord next, List<String> headers) {
        Map<String, Object> values = new HashMap<>();
        toMap(next, headers).forEach((k, v) -> {
            if(StringUtils.isBlank(k) || !request.getEntitySchema().hasField(k)) return;
            Optional<AttributeSchema> attributeSchemaOptional = request.getEntitySchema().getField(k);
            if(!attributeSchemaOptional.isPresent()) return;
            AttributeSchema attributeSchema = attributeSchemaOptional.get();
            switch (attributeSchema.getDataType()) {
                case "date":
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    try {
                        if (v != null && !v.equalsIgnoreCase("null")) {
                            Date parsedDate = sdf.parse(v);
                            values.put(k, parsedDate);
                        }
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case "datetime":
                    if (v != null && !v.equalsIgnoreCase("null")) {
                        long l = ZonedDateTime.parse(v).toEpochSecond() * 1000;
                        values.put(k, l);
                    }
                    break;
                default:
                    values.put(k, v);
                    break;
            }
        });
        EntityData record = new EntityData().setValues(values);
        String id = getId(values);
        Long watermark = getWatermark(values);
        Long createdAt = getCreatedAt(values);
        record.setId(id);
        record.setLastModified(watermark);
        record.setCreatedAt(createdAt);
        record.setName(request.getEntitySchema().getApiName());
        record.setConnectorId(request.getConnector().getId());
        return record;
    }

    @Override
    protected String getId(Map<String, Object> values) {
        String idFieldName = request.getEntitySchema().getIdField().getApiName();
        return Objects.toString(values.get(idFieldName),null);
    }

    @Override
    protected Long getWatermark(Map<String, Object> values) {
        String watermarkFieldName = request.getEntitySchema().getWatermarkField().getApiName();
        try {
            return ConnectorHelper.convert(Objects.toString(values.get(watermarkFieldName))).toInstant().toEpochMilli();
        } catch (Exception ne) {
            log.error(ne.getMessage(), ne);
            return request.getWatermark().getStart();
        }
    }

    private Long getCreatedAt(Map<String, Object> values) {
        try {
            return ConnectorHelper.convert(Objects.toString(values.get("createdAt"))).toInstant().toEpochMilli();
        } catch (Exception ne) {
            log.error(ne.getMessage(), ne);
            return request.getWatermark().getStart();
        }
    }
}
