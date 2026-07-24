package com.syncari.core.repositories.customer;

import com.syncari.core.model.FieldDataScoreSnapshot;
import com.syncari.core.model.SimulationRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
public class CustomSimulationRunRepoImpl implements CustomSimulationRunRepo {

    private static final String CREATED_AT = "createdAt";

    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Override
    public SimulationRun findLatest(String targetId) {

        Query query = new Query().addCriteria(where("targetId").is(targetId))
                .with(Sort.by(CREATED_AT).descending()).limit(1);

        List<SimulationRun> runs = customerMongoTemplate.find(query, SimulationRun.class);
        return runs.isEmpty() ? null : runs.get(0);
    }
}
