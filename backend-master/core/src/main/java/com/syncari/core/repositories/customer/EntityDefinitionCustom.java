package com.syncari.core.repositories.customer;

import com.syncari.core.model.EntityDefinition;

import java.util.List;

public interface EntityDefinitionCustom {

    public List<EntityDefinition> findByConnectorIdAndDraftStatus(String connectorId, String draftStatus, String entityId, int limit);
}
