package com.syncari.viper.simulation;

import com.syncari.core.model.Connector;
import com.syncari.core.model.FieldChange;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.core.service.TransactionLogService;
import com.syncari.utils.DateUtil;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Date;
import java.util.List;

import static com.syncari.connector.Operation.merge;

public class SimulatedTransactionLogService extends TransactionLogService {
    public SimulatedTransactionLogService() {
        super(new TransactionLogRepoSimulationImpl(), new DateUtil(), null);
    }

    @Override
    public void addDestination(String transactionLogId, Connector connector, String externalId, boolean isSkipped, boolean isError, String details) {
        //do nothing
    }

    @Override
    public void setExternalOutgoingValue(String transactionLogId, List<FieldChange> externalValues) {
        //do nothing
    }

    @Override
    public com.syncari.core.model.pagination.Page<TransactionLog> findMergesByBatchId(String batchId, Date start, PageCursor cursor) {
        return new com.syncari.core.model.pagination.Page<TransactionLog>(new PageInfo(), List.of());
    }
    
    @Override
    public List<TransactionLog> log(List<TransactionLog> logs) {
    	//do nothing
    	return logs;
    }
    
    @Override
    public TransactionLog log(TransactionLog log) {
    	//do nothing
    	return log;
    }

    public List<TransactionLog> findByTransactionLogIds(List<String> transactionLogs, long start) {
        return List.of();
    }
}
