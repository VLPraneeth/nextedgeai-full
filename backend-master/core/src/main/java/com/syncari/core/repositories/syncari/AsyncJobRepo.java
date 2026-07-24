package com.syncari.core.repositories.syncari;

import java.util.List;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.AsyncJob;
import com.syncari.core.repositories.SyncariRepo;

public interface AsyncJobRepo extends SyncariRepo<AsyncJob> {
	@Query("{'type' : {$eq : '?0'}}")
    List<AsyncJob> findByType(String type);

	@Query("{'type' : {$eq : '?0'}, 'status' : {$in:?1}}")
    List<AsyncJob> findByTypeAndStatus(String type, List<String> status);
}
