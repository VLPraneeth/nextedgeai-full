package com.syncari.core.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.syncari.core.model.misc.Model;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface SyncariRepo<T extends Model> extends MongoRepository<T, String>{

    @Query(value="{'_id' : {$in: ?0}}", delete = true)
    void deleteAllById(List<String> ids);

    @Query(value = "{ 'seeded' :{'$ne': true}}", delete = true)
    void reset();

}
