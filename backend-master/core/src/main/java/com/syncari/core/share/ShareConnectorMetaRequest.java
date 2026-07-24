package com.syncari.core.share;

import java.util.List;
import com.syncari.connector.ConnectorSharingScope;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ShareConnectorMetaRequest {
	private ConnectorSharingScope scope;
	private List<String> instances;
}
