package com.syncari.core.repositories.syncari;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.syncari.core.model.Quota;

@Repository
public interface QuotaRepo extends MongoRepository<Quota, String>{
}
