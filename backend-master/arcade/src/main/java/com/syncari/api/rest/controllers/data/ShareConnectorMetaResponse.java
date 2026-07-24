package com.syncari.api.rest.controllers.data;

import java.util.List;
import com.syncari.connector.ConnectorSharingScope;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ShareConnectorMetaResponse {
	private ConnectorSharingScope scope;
	private List<String> instances;
}
