package com.syncari.core.repositories.syncari;

import com.syncari.core.model.ConnectorMetadata;
import com.syncari.utils.Pair;

import java.util.List;

public interface CustomConnectorMetadataRepo {
    Pair<List<ConnectorMetadata>, Boolean> retrieveConnectorsPaginated(String connectorId, int limit);
}
