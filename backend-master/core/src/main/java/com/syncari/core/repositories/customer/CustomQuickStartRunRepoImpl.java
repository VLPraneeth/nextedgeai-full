package com.syncari.core.repositories.customer;

import com.syncari.core.model.QuickStartRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
public class CustomQuickStartRunRepoImpl implements CustomQuickStartRunRepo {

    @Autowired
    private MongoTemplate customerMongoTemplate;
    @Override
    public List<QuickStartRun> getHistoryByQuickStartType(String qsType) {
        Query query = new Query().addCriteria(where("qsType").is(qsType))
                .with(Sort.by("executedAt").descending()).limit(50);

        return customerMongoTemplate.find(query, QuickStartRun.class);
    }
}
