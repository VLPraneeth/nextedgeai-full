package com.syncari.api.rest.controllers.data;

import com.syncari.connector.config.AuthConfig;
import com.syncari.core.model.ConnectorSchemaSetting;
import com.syncari.core.model.misc.ApiConfig;
import com.syncari.core.model.misc.AsyncStatus;
import com.syncari.core.model.misc.ConnectorSetting;
import com.syncari.core.model.misc.ConnectorStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class CredentialResponse {
	private String id;
	private String name;
	private String metadataId;
	private ConnectorStatus status;
	private String errorMessage;
	private String errorDetails;
	private AuthConfig authConfig;
	protected String createdBy;
	protected String updatedBy;
	protected Date createdAt;
	protected Date updatedAt;
}
