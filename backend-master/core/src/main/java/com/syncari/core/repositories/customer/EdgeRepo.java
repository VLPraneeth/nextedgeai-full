package com.syncari.core.repositories.customer;

import java.util.List;

import com.syncari.core.model.Edge;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.mongodb.repository.Query;

public interface EdgeRepo extends SyncariRepo<Edge> {

    List<Edge> findByGraphId(String graphId);

    @Query("{ 'graphId' : {$in:?0}}")
    List<Edge> findByGraphIds(List<String> graphIds);

    @Query(value="{'graphId' : ?0}", delete = true)
    void deleteByGraphId(String graphId);

    @Query(value="{'graphId' : {'$in':?0}}", delete = true)
    void deleteByGraphIdIn(List<String> graphIds);

}
