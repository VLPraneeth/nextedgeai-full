package com.syncari.core.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.AsyncJob;
import com.syncari.core.repositories.syncari.AsyncJobRepo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AsyncJobService {
    @Autowired
    AsyncJobRepo jobRepo;

    public Optional<AsyncJob> findById(String id) {
        return jobRepo.findById(id);
    }
    
    public List<AsyncJob> findByTypeAndStatus(String type, List<String> statuses) {
    	return jobRepo.findByTypeAndStatus(type, statuses);
    }
    
    public List<AsyncJob> findByType(String type) {
        return jobRepo.findByType(type);
    }
    
    public AsyncJob save(AsyncJob job) {
    	return jobRepo.save(job);
    }
}
