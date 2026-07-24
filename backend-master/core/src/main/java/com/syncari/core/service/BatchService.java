package com.syncari.core.service;

import java.util.List;
import java.util.Optional;

import com.syncari.core.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.connector.Operation;
import com.syncari.core.model.Batch;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.BatchRepo;

@Component
public class BatchService {
    @Autowired
    BatchRepo batchRepo;

    public Batch save(Batch batch) {
        return batchRepo.save(batch);
    }
    
    public Optional<Batch> findById(String batchId) {
        return batchRepo.findById(batchId);
    }
    
    public List<Batch> findByEntityId(String entityId) {
        return batchRepo.findByEntityId(entityId);
    }
    
    public List<Batch> findBy(String entityId, Operation operation) {
    	return batchRepo.findBatch(entityId, operation);
    }
    
    public Batch cancel(String batchId) {
        Batch batch = batchRepo.findById(batchId).orElseThrow();
        batch.setStatus(Status.CANCELLED);
        return batchRepo.save(batch);
    }

    public Batch updateRowsAffected(String batchId, long rowsAffected, long rowsTotal) {
        Batch b = findById(batchId).orElseThrow(() -> new NotFoundException(Batch.class, "id", batchId));
        b.setRowsAffected(rowsAffected);
        if(b.getRowsTotal() < rowsTotal) {
        	b.setRowsTotal(rowsTotal);
        }
        return save(b);
    }

}
