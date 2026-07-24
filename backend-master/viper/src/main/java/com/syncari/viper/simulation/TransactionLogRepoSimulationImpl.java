package com.syncari.viper.simulation;

import com.syncari.core.model.TransactionLog;
import com.syncari.core.repositories.customer.TransactionLogRepo;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

public class TransactionLogRepoSimulationImpl extends BaseRepoSimulationImpl<TransactionLog> implements TransactionLogRepo {
    @Override
    public Page<TransactionLog> findByBatchId(String batchId, Pageable page) {
        return Page.empty();
    }

    @Override
    public List<TransactionLog> findByBatchIdAndSyncariIdIn(String batchId, List<String> syncariIds) {
        return Collections.emptyList();
    }

    @Override
    public Page<TransactionLog> findMergesByBatchId(String batchId, Pageable page) {
        return Page.empty();
    }

    @Override
    public Stream<TransactionLog> findAllStream(ObjectId pageMarker) {
        return Stream.empty();
    }

    @Override
    public Stream<TransactionLog> findAllStream() {
        return Stream.empty();
    }

    @Override
    public Stream<TransactionLog> findByObjectIdRange(ObjectId start, ObjectId end) {
        return Stream.empty();
    }

    @Override
    public Page<TransactionLog> findByRange(Pageable page, Date start, Date end) {
        return Page.empty();
    }

    @Override
    public Page<TransactionLog> findEntityOperationByRange(Pageable page, Date start, Date end, String entityName, String operation) {
        return Page.empty();
    }

    @Override
    public Page<TransactionLog> findByEntityAndOperation(Pageable page, String entityName, String operation) {
        return Page.empty();
    }

    @Override
    public Page<TransactionLog> findBySyncariId(Pageable page, String syncariId) {
        return Page.empty();
    }

    @Override
    public List<TransactionLog> findEntitySyncariIdsByDate(String entityName, List<String> syncariIds, Date date) {
        return List.of();
    }

    @Override
    public List<TransactionLog> findDestinationLogs(String entityName, List<String> sourceTxns, Date createdAt) {
        return List.of();
    }

    @Override
	public Long countByBatchId(String batchId) {
		return 0L;
	}

}
