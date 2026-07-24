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
public class HttpSourceCursorLinkGenerator extends HttpSourceNoPaginationGenerator implements Function3<WatermarkInfo, Integer, String, DataWithCursor> {
	private SyncRequest request;
	private HttpSourcesHelper helper;
	private HttpSourceConfigInfo configInfo;
	private Map<String, Object> context;

	@Override
	public DataWithCursor apply(WatermarkInfo wm, Integer pageSize, String nextPageUrl) {
		if(!StringUtils.isBlank(configInfo.getPageSizeParam())) {
			context.put(configInfo.getPageSizeParam(), pageSize);
		}
		if(StringUtils.isBlank(nextPageUrl)) {
			return new DataWithCursor(nextPageUrl, "", List.of()); 
		}
		var copyHttpConfig = configInfo.copy().setEndpoint(nextPageUrl);
		var result = ConnectorHelper.withHttpErrorHandling(() -> helper.execute(request.getConnector(), copyHttpConfig, context, false));
    	List<EntityData> records = new ArrayList<EntityData>();
    	if(result.getBody() != null) {
    		records = getRecords(request, result, copyHttpConfig);
    	}
		return new DataWithCursor(nextPageUrl, getNextPageUrl(result, copyHttpConfig), records);
	}

	private String getNextPageUrl(HTTPSourceResult result, HttpSourceConfigInfo copyHttpConfig) {
		return JsonSchemaHelper.getFieldValueBySelector((Map<String, Object>) JsonSchemaHelper.jsonNodeToMap(result.getBody()),
				copyHttpConfig.getNextCursorSelector()).orElse("").toString();
	}

}
