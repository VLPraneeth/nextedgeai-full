package com.syncari.core.changelogs.customer;

import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

@ChangeLog(order = "0024")
public class M0024_LookUpFunctionConfiguration {

	@ChangeSet(order = "001", id = "lookUpFunctionConfiguration", author = "varsha")
	public void lookUpFunctionConfiguration(MongoTemplate db) {
		// No-op
	}
}
