package com.syncari.core.http.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.EntityData;
import com.syncari.connector.HttpSourceConfigInfo;
import com.syncari.connector.data.HTTPSourceResult;
import com.syncari.connector.data.SyncRequest;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.token.XPathTokenResolver;
import com.syncari.core.utils.JsonSchemaHelper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
public class HttpSourceNoPaginationGenerator {
	
	public List<EntityData> getRecords(SyncRequest request, HTTPSourceResult result, HttpSourceConfigInfo httpSource) {
		List<Object> records = new ArrayList<Object>();
		Object bodyMap = JsonSchemaHelper.jsonNodeToMap(result.getBody());
		if(!StringUtils.isBlank(httpSource.getRecordSelector())) {
			if(bodyMap instanceof Map) {
				XPathTokenResolver xpathResolver = new XPathTokenResolver(httpSource.getRecordSelector().trim());
				var resolved = xpathResolver.resolveToken((Map<String, Object>) bodyMap);
				if (resolved.hasTokenSyntaxErrors() || !resolved.isKeyFoundInContext()) {
					log.error("XPath evaluation failed {}. Taking the entire response body as a single record", httpSource.getRecordSelector()); //FIXME Should we throw exception here?
					records.add(bodyMap);
				} else if(resolved.getResolvedValue() instanceof List) {
					records.addAll((List<Object>)resolved.getResolvedValue());
				} else if(resolved.getResolvedValue() != null) {
					records.add(resolved.getResolvedValue());
				}
			} else if(bodyMap instanceof List) {
				log.error("Cannot use xpath in a array"); //FIXME Should we throw exception here?
				records.addAll((List<Object>)bodyMap);
			}
			
		} else {
			log.debug("No record selector, taking entire response as a single record");
			if(bodyMap instanceof List) {
				records.addAll((List<Object>)bodyMap);
			} else {
				records.add(bodyMap);
			}
		}
		var noPageGenerator = new HttpSourceNoPaginationGenerator();
		return records.stream().filter(rec -> rec instanceof Map)
				.map(rec -> noPageGenerator.createRecord(request, (Map<String, Object>) rec, httpSource, result, bodyMap)).collect(Collectors.toList());
	}

	public EntityData createRecord(SyncRequest request, Map<String, Object> jsonRec, HttpSourceConfigInfo httpSource, HTTPSourceResult result, Object fullBody) {
        Map<String, Object> values = new HashMap<>();
        //Standard data
        values.put("headers", result.getHeaders().toSingleValueMap());
        values.put("status_code", result.getStatusCode());
        if(StringUtils.isNotBlank(httpSource.getSchema())) {
			try {
				values.put("schema_error", JsonSchemaHelper.validateJson(httpSource.getSchema(), new ObjectMapper().writeValueAsString(jsonRec)));
			} catch (JsonProcessingException e) {
				values.put("schema_error", e.getMessage());
			}
        }
        values.put("called_at", result.getCalledAt());
        String id = JsonSchemaHelper.getFieldValueBySelector(jsonRec, httpSource.getIdSelector()).orElse(DigestUtils.md5Hex(jsonRec.toString())).toString();
        values.put("id", id);
        jsonRec.forEach((k, v) -> {
            if(StringUtils.isBlank(k)) return;
            var attr = request.getEntitySchema().getField(k).orElse(null);
            var key = attr == null ? k : attr.getApiName();
            if(v != null && attr != null && attr.isMultiValueField()) {
                if(v instanceof List<?>) {
                  values.put(key, v);
                } else {
                  values.put(key, Arrays.stream(v.toString().trim().split(",")).map(v1 -> v1.trim()).collect(Collectors.toList()));
                }
            } else {
            	values.put(key, v);
            }
		});
        EntityData record = new EntityData().setValues(values);
        record.setId(id);
        JsonSchemaHelper.getFieldValueBySelector(jsonRec, httpSource.getWmSelector())
        .ifPresentOrElse(
            wm -> record.setLastModified(convertTimeToLong(wm)),
            () -> record.setLastModified(Math.min(result.getCalledAt().toInstant().toEpochMilli(), request.getWatermark().getEnd()))
        );
        JsonSchemaHelper.getFieldValueBySelector(jsonRec, httpSource.getCreatedAtSelector()).ifPresent(cat -> record.setCreatedAt(convertTimeToLong(cat)));
        JsonSchemaHelper.getFieldValueBySelector(jsonRec, httpSource.getDeletedFlagSelector()).ifPresent(b -> record.setDeleted(BooleanUtils.toBoolean(b.toString())));
        record.setName(request.getEntitySchema().getApiName());
        record.setConnectorId(request.getConnector().getId());
        return record;
    }
	
	
	private long convertTimeToLong(Object dt) {
		if(dt instanceof Number) {
			return ((Number) dt).longValue();
		} else {
			try {
				return Long.parseLong(dt.toString());
			} catch (Exception e) {
				var zdt = DatetimeType.VALUE.convert(dt);
				if(zdt != null) {
					return zdt.toInstant().toEpochMilli();
				}
			}
		}
		return 0L;
	}
}
