package com.syncari.connector.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.custom.CloudFunctionInfo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WebhookRequest {
	String body;
	ConnectorInfo config;
	Map<String, Object> headers;
	Map<String, Object> params;
    private CloudFunctionInfo cloudFunctionInfo;
    List<String> activeEntities = new ArrayList<>();
}
