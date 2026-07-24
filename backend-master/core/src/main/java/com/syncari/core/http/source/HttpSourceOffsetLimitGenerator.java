package com.syncari.core.http.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.lambda.function.Function3;

import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.EntityData;
import com.syncari.connector.HttpSourceConfigInfo;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.WatermarkInfo;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class HttpSourceOffsetLimitGenerator extends HttpSourceNoPaginationGenerator implements Function3<WatermarkInfo, Integer, Long, DataWithOffset> {
	private SyncRequest request;
	private HttpSourcesHelper helper;
	private HttpSourceConfigInfo configInfo;
	private Map<String, Object> context;

	@Override
	public DataWithOffset apply(WatermarkInfo wm, Integer pageSize, Long offset) {
		context.put(configInfo.getOffsetParam(), offset);
		context.put(configInfo.getLimitParam(), pageSize);
		var result = ConnectorHelper.withHttpErrorHandling(() -> helper.execute(request.getConnector(), configInfo, context, false));
    	List<EntityData> records = new ArrayList<EntityData>();
    	if(result.getBody() != null) {
    		records = getRecords(request, result, configInfo);
    	}
		return new DataWithOffset(offset, offset + pageSize, records, List.of());
	}

}
