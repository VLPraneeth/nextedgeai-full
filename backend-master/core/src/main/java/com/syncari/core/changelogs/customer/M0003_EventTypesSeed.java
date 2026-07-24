package com.syncari.core.changelogs.customer;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.event.EventTypes;

@ChangeLog(order="0003")
public class M0003_EventTypesSeed {
	@ChangeSet(order = "001", id = "seedEventTypes", author = "varsha")
	public void addSuperAdminPrivileg(MongoTemplate db) {
		List<Document> eventTypeSeed = List.of(
				new Document("name", EventTypes.API_CALL).append("label", "API call"),
				new Document("name", EventTypes.LOGIN).append("label", "Login"),
				new Document("name", EventTypes.LOGOUT).append("label", "Logut"),
				new Document("name", EventTypes.ADD_CONNECTOR).append("label", "Add Connector"),
				new Document("name", EventTypes.ACTIVATE_CONNECTOR).append("label", "Activate Connector"),
				new Document("name", EventTypes.DEACTIVATE_CONNECTOR).append("label", "De Activate Connector"),
				new Document("name", EventTypes.ACTIVATE_ENTITY).append("label", "Activate Entity"),
				new Document("name", EventTypes.DEACTIVATE_ENTITY).append("label", "De Activate Entity"),
				new Document("name", EventTypes.EDIT_PIPELINE).append("label", "Edit Pipeline"),
				new Document("name", EventTypes.EDIT_CONNECTOR).append("label", "Edit Connector"),
				new Document("name", EventTypes.PIPELINE_STAGE).append("label", "Pipeline Stage"),
				new Document("name", EventTypes.ADD_USER).append("label", "Add User"),
				new Document("name", EventTypes.EDIT_USER).append("label", "Edit User"),
				new Document("name", EventTypes.ADD_ROLE).append("label", "Add Role"),
				new Document("name", EventTypes.ASSIGN_ROLE).append("label", "Assign Role"),
				new Document("name", EventTypes.UNASSIGN_ROLE).append("label", "UnAssign Role")
				);
		MongoCollection<Document> eventTypes = db.getCollection("eventType");
		eventTypes.insertMany(eventTypeSeed);
	}

}
