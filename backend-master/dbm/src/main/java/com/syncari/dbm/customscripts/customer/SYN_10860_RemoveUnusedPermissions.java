package com.syncari.dbm.customscripts.customer;

import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.security.Permissions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_10860_RemoveUnusedPermissions {

	@ChangeSet(order = "001", id = "removeUnusedPermissions", author = "sibin", runAlways = true)
	public void updateLastModified(MongoTemplate template) {
		boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
		MongoCollection<Document> roles = template.getCollection("role");
		List<String> allPermissions = Permissions.allPermissions();
		for (Document role : roles.find()) {
			var roleName = role.getString("name");
			List<Document> permissionsInDB = (List<Document>) role.get("privileges");
			log.info("{} role is having {} permissions before update", roleName, permissionsInDB.size());
			List<String> permissionsToBeRemoved = new ArrayList<>(
					permissionsInDB.stream().map(p -> p.getString("privilegeId")).collect(Collectors.toList()));
			permissionsToBeRemoved.removeAll(allPermissions);
			log.info("Permissions {}  will be removed from role {}", permissionsToBeRemoved, roleName);
			for (String p : permissionsToBeRemoved) {
				permissionsInDB = permissionsInDB.stream().filter(pInDB -> !p.equals(pInDB.getString("privilegeId")))
						.collect(Collectors.toList());

			}
			log.info("{} role is will be having {} permissions after update", roleName, permissionsInDB.size());
			if (!dryRun) {
				roles.updateOne(eq("_id", role.getObjectId("_id")),
						new Document("$set", new Document("privileges", permissionsInDB)),
						new UpdateOptions().upsert(true));
				log.info("{} role is having {} permissions after update", roleName, permissionsInDB.size());
			}
		}
	}

}
