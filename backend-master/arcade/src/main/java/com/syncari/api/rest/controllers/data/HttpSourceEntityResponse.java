package com.syncari.api.rest.controllers.data;

import java.util.List;
import java.util.Map;

import com.syncari.connector.data.PaginationType;
import com.syncari.utils.KeyValue;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpSourceEntityResponse {
	private String id;
	private String metaId;
    private String apiName;
    private String displayName;
    private String description;
    private List<String> tags = List.of();
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
    private int limitValue;
    private int offsetValue;
    private String pageNumberParam;
    private int pageNumberValue;
    private String pageSizeParam;
    private int pageSize;
    private String cursorType;
    private String nextCursorSelector;
    private String nextCursorParam;
    private String startValue;
}
