package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import com.syncari.core.model.ConnectorSchemaSetting;
import com.syncari.core.repositories.SyncariRepo;

public interface ConnectorSettingRepo extends SyncariRepo<ConnectorSchemaSetting> {
	List<ConnectorSchemaSetting> findByToConnectorId(String toConnectorId);
	
	Optional<ConnectorSchemaSetting> findByFromEntityId(String fromEntityId);
	
	List<ConnectorSchemaSetting> findBySyncariEntityId(String syncariEntityId);

	List<ConnectorSchemaSetting> findByFromConnectorId(String fromConnectorId);
}
