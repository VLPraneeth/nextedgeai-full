package com.syncari.connector.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.utils.CsvUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;

import java.time.Instant;
import java.util.*;

import static java.lang.String.format;

@AllArgsConstructor
@Slf4j
public class S3Generator implements Function3<WatermarkInfo, Integer, Long, DataWithOffset> {
	private CsvUtils csvUtils;
	private SyncRequest request;
	private AmazonS3 client;
	private String bucket;
	private List<S3ObjectSummary> files;

	@Override
	public DataWithOffset apply(WatermarkInfo wm, Integer pageSize, Long offset) {
		Date start = Date.from(Instant.ofEpochMilli(wm.getStart()));
		Date end = Date.from(Instant.ofEpochMilli(wm.getEnd()));
		
		List<S3ObjectSummary> filteredList = new ArrayList<>();
		for (S3ObjectSummary file : files) {
			Date lastUpdated = file.getLastModified();
			log.info("S3Generator received {} file with {}", file.getKey(), lastUpdated);
			if (lastUpdated.equals(start)
				|| lastUpdated.equals(end)
				|| (lastUpdated.after(start) && lastUpdated.before(end))) {
				filteredList.add(file);
				log.info("Adding {} file as modified", file.getKey());
			}
		}
		int index = 0;
		int max = pageSize;
		List<EntityData> records = new ArrayList<>();
		for(S3ObjectSummary file: filteredList) {
			S3Object f = null;
			try {
				f = client.getObject(new GetObjectRequest(bucket, file.getKey()));
				log.info(format("File with name %s successfully read", file.getKey()));
                var parser = csvUtils.getCSVParser(f.getObjectContent());
	        	for(CSVRecord rec : parser) {
	        		if(index < offset) {
	        			//skip till offset
	        			index++;
					} else if (max > 0) {
						EntityData record = createRecord(rec, file);
						if (!record.getValues().isEmpty()) {
							records.add(record);
						}
						max--;
					} else {
						break;
					}
	        	}
	        }catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				if (f != null) {
					try {
						f.close();
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
		return new DataWithOffset(offset, offset + pageSize, records, List.of());
	}
	
	private EntityData createRecord(CSVRecord next, S3ObjectSummary file) {
        Map<String, Object> values = new HashMap<>();
		next.toMap().forEach((k, v) -> {
            if(StringUtils.isBlank(k)) return;
            var key = request.getEntitySchema().getFieldByDisplayName(k).map(AttributeSchema::getApiName).orElse(k);
            values.put(key, v == null ? null : v.trim());
		});
        EntityData record = new EntityData().setValues(values);
        String id = getId(values);
        Long watermark = file.getLastModified().getTime();
        record.setId(id);
        record.setLastModified(watermark);
        record.setName(request.getEntitySchema().getApiName());
        record.setConnectorId(request.getConnector().getId());
        return record;
    }
	
	private String getId(Map<String, Object> values) {
        String idFieldName = request.getEntitySchema().getIdField().getApiName();
        return Objects.toString(values.get(idFieldName.toLowerCase()),null);
    }

}
