package com.syncari.viper.simulation;

import com.syncari.core.event.store.EventStore;
import com.syncari.core.event.store.FieldDefinition;
import com.syncari.core.model.Event;
import com.syncari.core.model.PipelineStats;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.model.misc.SyncLog;

import java.util.List;

public class SimulationEventStore implements EventStore {

    @Override
    public void provision(String syncariId, String tableName) {

    }

    @Override
    public void provision(String syncariId) {

    }

    @Override
    public void deprovision(String syncariId) {

    }

    @Override
    public void verifyProvisioned(String syncariId) {

    }

    @Override
    public void insert(List<Event> events) {

    }

    @Override
    public void insertSyncLogs(List<SyncLog> txnLogs) {

    }

    @Override
    public void insertErrorLogs(List<SyncError> errorLogs) {

    }

    @Override
    public List<TransactionLog> insertTransactionLogs(List<TransactionLog> logs) {

        return logs;
    }

    @Override
    public void addFieldToTable(FieldDefinition def) {

    }

}
