package com.syncari.core.repositories.customer;

import com.syncari.core.model.SinkLog;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface SinkLogRepo extends SyncariRepo<SinkLog> {

    Page<SinkLog> findByBatchId(String batchId, Pageable page);


}
