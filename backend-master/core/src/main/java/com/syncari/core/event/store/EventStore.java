package com.syncari.core.event.store;

import java.util.*;

import com.google.cloud.bigquery.StandardSQLTypeName;
import com.syncari.core.model.Event;
import com.syncari.core.model.PipelineStats;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.model.misc.SyncLog;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

public interface EventStore {
	/**
	 * Add a new column to the specified eventstore table
	 * Will warn and skip adding a column , if table is not present, or column is already present
	 * @param syncariId
	 * @param tableName
	 * @param fieldName
	 * @param type
	 */

	void provision(String syncariId, String tableName);

	void provision(String syncariId);

	void deprovision(String syncariId);

	void verifyProvisioned(String syncariId);
	
	void insert(List<Event> events);

	void insertSyncLogs(List<SyncLog> txnLogs);

	void insertErrorLogs(List<SyncError> errorLogs);

	List<TransactionLog> insertTransactionLogs(List<TransactionLog> logs);

	void addFieldToTable(FieldDefinition def);

}
