package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0015")
public class M0015_FixIdFields {

	@ChangeSet(order = "001", id = "fixIdFields", author = "neelesh")
	public void fixIdFields(MongoTemplate template) {
	  //Noop
	}

	@ChangeSet(order = "002", id = "fixIdFieldsDataType", author = "abhinav")
	public void fixIdFieldsDataType(MongoTemplate template) {
	  //Noop
	}
}
