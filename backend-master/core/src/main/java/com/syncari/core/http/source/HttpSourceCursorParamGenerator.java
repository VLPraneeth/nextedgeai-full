package com.syncari.core.http.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;

import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.EntityData;
import com.syncari.connector.HttpSourceConfigInfo;
import com.syncari.connector.data.DataWithCursor;
import com.syncari.connector.data.HTTPSourceResult;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.core.utils.JsonSchemaHelper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class HttpSourceCursorParamGenerator extends HttpSourceNoPaginationGenerator implements Function3<WatermarkInfo, Integer, String, DataWithCursor> {
	public static String EMPTY_START_VALUE_TOKEN = "<<empty_start_value>>";
	private SyncRequest request;
	private HttpSourcesHelper helper;
	private HttpSourceConfigInfo configInfo;
	private Map<String, Object> context;

	@Override
	public DataWithCursor apply(WatermarkInfo wm, Integer pageSize, String nextCursorValue) {
		if(!StringUtils.isBlank(configInfo.getPageSizeParam())) {
			context.put(configInfo.getPageSizeParam(), pageSize);
		}
		if(StringUtils.isBlank(nextCursorValue)) {
			return new DataWithCursor(nextCursorValue, "", List.of()); 
		}
		if(!nextCursorValue.equalsIgnoreCase(EMPTY_START_VALUE_TOKEN)) {
			context.put(configInfo.getNextCursorParam(), nextCursorValue);
		}
		var result = ConnectorHelper.withHttpErrorHandling(() -> helper.execute(request.getConnector(), configInfo, context, false));
    	List<EntityData> records = new ArrayList<EntityData>();
    	if(result.getBody() != null) {
    		records = getRecords(request, result, configInfo);
    	}
		return new DataWithCursor(nextCursorValue, getNextCursorValue(result, configInfo), records);
	}

	private String getNextCursorValue(HTTPSourceResult result, HttpSourceConfigInfo copyHttpConfig) {
		return JsonSchemaHelper.getFieldValueBySelector((Map<String, Object>) JsonSchemaHelper.jsonNodeToMap(result.getBody()),
				copyHttpConfig.getNextCursorSelector()).orElse("").toString();
	}

}
