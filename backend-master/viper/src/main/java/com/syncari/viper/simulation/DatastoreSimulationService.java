package com.syncari.viper.simulation;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.SyncResponse;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.service.DatastoreService;

import java.util.List;
import java.util.Optional;

public class DatastoreSimulationService extends DatastoreService {

    @Override
    public void provision(String schema) {
        // Do nothing
    }

    @Override
    public boolean createEntity(EntityDefinition schema) {
        return false;
    }

    @Override
    public List<SyncResponse> execute(EntityDefinition def, long recordsToBePushed) {
        return List.of();
    }

    @Override
    public void deprovision(String schema) {
        // Do nothing
    }

    @Override
    public void deleteField(String entityDatastoreName, AttributeSchema attr) {
        // Do nothing
    }

    @Override
    public Optional<Connector> findActiveDatastore() {
        return Optional.empty();
    }
}
