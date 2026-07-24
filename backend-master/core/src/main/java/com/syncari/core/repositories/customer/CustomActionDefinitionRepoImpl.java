package com.syncari.core.repositories.customer;

import com.syncari.core.actions.ActionsSeed;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.FunctionConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@Slf4j
public class CustomActionDefinitionRepoImpl implements CustomActionDefinitionRepo{
	@Autowired
	private MongoTemplate customerMongoTemplate;
	@Override
	public Optional<ActionDefinition> findByName(String name) {
		ActionDefinition action = customerMongoTemplate.findOne(new Query().addCriteria(where("name").is(name)), ActionDefinition.class);
		return Optional.ofNullable(action).map(a->ActionsSeed.populateAction(a));
	}

	@Override
	public Optional<ActionDefinition> findByObjectId(String name) {
		Query query = new Query();
		query.addCriteria(where("_id").is(new ObjectId(name)));
		ActionDefinition actionDefinition = customerMongoTemplate.findOne(query , ActionDefinition.class);
		return Optional.ofNullable(actionDefinition);
	}
}
