package com.syncari.core.http.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Transient;

import com.syncari.connector.data.PaginationType;
import com.syncari.core.model.UUIDAuditModel;
import com.syncari.utils.KeyValue;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpSourceConfig extends UUIDAuditModel{
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
	@Transient
	private List<String> tags = new ArrayList<>();
	
	public void copyFrom(HttpSourceConfig other) {
		this.apiName = other.apiName;
        this.displayName = other.displayName;
        this.description = other.description;
        this.endpoint = other.endpoint;
        this.method = other.method;
        this.body = other.body;
        this.schema = other.schema;
        this.headers = other.headers;
        this.variables = other.variables;
        this.recordSelector = other.recordSelector;
        this.idSelector = other.idSelector;
        this.wmSelector = other.wmSelector;
        this.createdAtSelector = other.createdAtSelector;
        this.deletedFlagSelector = other.deletedFlagSelector;
        this.createdBySelector = other.createdBySelector;
        this.modifiedBySelector = other.modifiedBySelector;
        this.type = other.type;
        this.limitParam = other.limitParam;
        this.offsetParam = other.offsetParam;
        this.pageNumberParam = other.pageNumberParam;
        this.pageSizeParam = other.pageSizeParam;
        this.limitValue = other.limitValue;
        this.offsetValue = other.offsetValue;
        this.pageNumberValue = other.pageNumberValue;
        this.pageSize = other.pageSize;
        this.cursorType = other.cursorType;
        this.nextCursorSelector = other.nextCursorSelector;
        this.nextCursorParam = other.nextCursorParam;
        this.startValue = other.startValue;
        this.setUpdatedAt(other.getUpdatedAt());
        this.setUpdatedBy(other.getUpdatedBy());
    }
}