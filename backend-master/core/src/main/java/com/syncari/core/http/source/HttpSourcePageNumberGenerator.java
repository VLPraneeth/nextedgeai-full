package com.syncari.core.http.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;

import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.EntityData;
import com.syncari.connector.HttpSourceConfigInfo;
import com.syncari.connector.data.DataWithPageNumber;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.WatermarkInfo;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class HttpSourcePageNumberGenerator extends HttpSourceNoPaginationGenerator implements Function3<WatermarkInfo, Integer, Integer, DataWithPageNumber> {
	private SyncRequest request;
	private HttpSourcesHelper helper;
	private HttpSourceConfigInfo configInfo;
	private Map<String, Object> context;

	@Override
	public DataWithPageNumber apply(WatermarkInfo wm, Integer pageSize, Integer pageNumber) {
		context.put(configInfo.getPageNumberParam(), pageNumber);
		if(StringUtils.isNotBlank(configInfo.getPageSizeParam())) {
			context.put(configInfo.getPageSizeParam(), pageSize);
		}
		var result = ConnectorHelper.withHttpErrorHandling(() -> helper.execute(request.getConnector(), configInfo, context, false));
    	List<EntityData> records = new ArrayList<EntityData>();
    	if(result.getBody() != null) {
    		records = getRecords(request, result, configInfo);
    	}
		return new DataWithPageNumber(pageNumber, pageNumber + 1, records, List.of());
	}

}
