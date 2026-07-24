package com.syncari.core.repositories.customer;

import com.syncari.core.model.DataQualityRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class CustomDataQualityRuleRepoImpl implements CustomDataQualityRuleRepo {

    @Autowired
    MongoTemplate customerMongoTemplate;

    @Override
    public void moveRulesToOtherCategory(String categoryId, String otherCategoryId) {
        Query query = new Query(Criteria.where("category").is(categoryId));
        Update update = new Update();
        update.set("category", otherCategoryId);
        customerMongoTemplate.updateMulti(query, update, DataQualityRule.class);
    }

}
