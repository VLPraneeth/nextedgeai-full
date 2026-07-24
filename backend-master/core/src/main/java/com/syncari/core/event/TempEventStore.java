package com.syncari.core.event;

public interface TempEventStore {

    void provision(String syncariId, String tableName, String tempTableName);

    void deprovision(String syncariId, String tableName, String tempTableName);

    void verifyProvisioned(String syncariId, String tableName, String tempTableName);

}
