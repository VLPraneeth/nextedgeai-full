package com.syncari.core.repositories.customer;

import com.syncari.core.model.insights.dataset.Dataset;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Slf4j
@Repository
public class CustomDatasetRepoImpl implements CustomDatasetRepo{

    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Override
    public List<Dataset> findAllApprovedAndGreaterThanId(String datasetId, int limit) {
        Query query = new Query().addCriteria(where("draftStatus").is("APPROVED"))
                .addCriteria(where("_id").lt(new ObjectId(datasetId))).addCriteria(where("version").exists(true))
                .limit(limit).with(Sort.by(Sort.Direction.DESC,"_id"));

        return customerMongoTemplate.find(query,Dataset.class);
    }

    @Override
    public List<Dataset> findAllApprovedWithLimit(int limit) {
        Query query = new Query().addCriteria(where("draftStatus").is("APPROVED"))
                .addCriteria(where("version").exists(true))
                .limit(limit).with(Sort.by(Sort.Direction.DESC,"_id"));

        return customerMongoTemplate.find(query,Dataset.class);
    }
}
