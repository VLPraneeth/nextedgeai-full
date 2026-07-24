package com.syncari.core.model;

import com.syncari.core.ApiType;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Data
@Accessors(chain = true)
@Slf4j
public class ApiErrorLog extends UUIDAuditModel {
	private String body;
	private String connectorId;
	private String eventId;
	private String error;
	private ApiType apiType = ApiType.webhook;
}
