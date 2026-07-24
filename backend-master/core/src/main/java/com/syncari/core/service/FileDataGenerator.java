package com.syncari.core.service;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.utils.CSVOptions;
import com.syncari.utils.CsvUtils;
import com.syncari.utils.TextUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;

import java.nio.channels.Channels;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@AllArgsConstructor
@Slf4j
public class FileDataGenerator implements Function3<WatermarkInfo, Integer, Long, DataWithOffset> {
	private CsvUtils csvUtils;
	private TextUtil textUtil;
	private SyncRequest request;
	private List<Blob> files;

	@Override
	public DataWithOffset apply(WatermarkInfo wm, Integer pageSize, Long offset) {
		Date start = Date.from(Instant.ofEpochMilli(wm.getStart()));
		Date end = Date.from(Instant.ofEpochMilli(wm.getEnd()));
		
		List<Blob> filteredList = new ArrayList<>();
		for (Blob file : files) {
			Date lastUpdated = new Date(file.getUpdateTime());
			log.info("FileDataGenerator received {} file with {}", file.getName(), lastUpdated);
			if (lastUpdated.equals(start)
				|| lastUpdated.equals(end)
				|| (lastUpdated.after(start) && lastUpdated.before(end))) {
				filteredList.add(file);
				log.info("Adding {} file as modified", file.getName());
			}
		}
		int index = 0;
		int max = pageSize;
		List<EntityData> records = new ArrayList<>();
		for(Blob file: filteredList) {
			ReadChannel reader = file.reader();
	        log.info(format("File with name %s successfully read", file.getName()));
	        try {
				boolean withTrim = getWithTrimProps(request);
				var parser = csvUtils.getCSVParser(Channels.newInputStream(reader), new CSVOptions()
						.withTrim(withTrim)
				);
	        	for(CSVRecord rec : parser) {
	        		if(index < offset) {
	        			//skip till offset
	        			index++;
					} else if (max > 0) {
						EntityData record = createRecord(rec, file, withTrim);
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
			}
		}
		return new DataWithOffset(offset, offset + pageSize, records, List.of());
	}

	private boolean getWithTrimProps(SyncRequest request) {
		boolean result = (boolean) request.getEntitySchema().getAdditionalProperties().getOrDefault(Constants.WITH_TRIM, true);
		log.info("value of is trim : {}", result);
		return result;
	}

	private EntityData createRecord(CSVRecord next, Blob file, boolean withTrim) {
        Map<String, Object> values = new HashMap<>();
		next.toMap().forEach((k, v) -> {
            if(StringUtils.isBlank(k)) return;
            var attr = request.getEntitySchema().getFieldByDisplayName(k).orElse(null);
            // If not found by display name, try by API name (handles header variations like spaces vs underscores)
            if (attr == null) {
                String apiName = textUtil.createApiName(k);
                attr = request.getEntitySchema().getField(apiName).orElse(null);
            }
            var key = attr == null ? k : attr.getApiName();
            if(v != null && attr != null && attr.isMultiValueField()) {
				if(StringUtils.isNotBlank(v)) {
					values.put(key, Arrays.stream((withTrim ? v.trim() : v).split(",")).map(v1 -> (withTrim ? v1.trim() : v1)).collect(Collectors.toList()));
				}
				else {
					values.put(key, null);
				}
            } else {
            	values.put(key, v == null ? null : (withTrim ? v.trim() : v));
            }
		});
        EntityData record = new EntityData().setValues(values);
        String id = getId(values);
        Long watermark = file.getUpdateTime();
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
