package com.syncari.connector;

import java.util.List;
import java.util.Map;

import com.syncari.connector.data.PaginationType;
import com.syncari.utils.KeyValue;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpSourceConfigInfo{
	private String apiName;
    private String displayName;
    private String description;
    private String endpoint;
    private String method;
    private String body;
    private String schema;
    private Map<String, String> headers;
    private List<KeyValue> variables;
    private String recordSelector;
    private String idSelector;
    private String wmSelector;
    private String createdAtSelector;
    private String deletedFlagSelector;
    private String createdBySelector;
    private String modifiedBySelector;
    private PaginationType type;
	private String limitParam;
	private String offsetParam;
	private String pageNumberParam;
	private String pageSizeParam;
	private Integer limitValue;
	private Integer offsetValue;
	private Integer pageNumberValue;
	private Integer pageSize;
	private String cursorType;
	private String nextCursorSelector;
	private String nextCursorParam;
	private String startValue;
	
	public HttpSourceConfigInfo copy() {
        HttpSourceConfigInfo copy = new HttpSourceConfigInfo();
        copy.setApiName(this.apiName)
            .setDisplayName(this.displayName)
            .setDescription(this.description)
            .setEndpoint(this.endpoint)
            .setMethod(this.method)
            .setBody(this.body)
            .setSchema(this.schema)
            .setHeaders(this.headers)
            .setVariables(this.variables)
            .setRecordSelector(this.recordSelector)
            .setIdSelector(this.idSelector)
            .setWmSelector(this.wmSelector)
            .setCreatedAtSelector(this.createdAtSelector)
            .setDeletedFlagSelector(this.deletedFlagSelector)
            .setCreatedBySelector(this.createdBySelector)
            .setModifiedBySelector(this.modifiedBySelector)
            .setType(this.type)
            .setLimitParam(this.limitParam)
            .setOffsetParam(this.offsetParam)
            .setPageNumberParam(this.pageNumberParam)
            .setPageSizeParam(this.pageSizeParam)
            .setLimitValue(this.limitValue)
            .setOffsetValue(this.offsetValue)
            .setPageNumberValue(this.pageNumberValue)
            .setPageSize(this.pageSize)
            .setCursorType(this.cursorType)
            .setNextCursorSelector(this.nextCursorSelector)
            .setNextCursorParam(this.nextCursorParam)
            .setStartValue(this.startValue);
        return copy;
    }
}