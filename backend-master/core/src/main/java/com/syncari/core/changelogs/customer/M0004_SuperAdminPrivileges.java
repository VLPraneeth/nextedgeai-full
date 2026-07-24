package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.MigrationContext;
import com.syncari.core.changelogs.syncari.M0004_InitialUsers;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.security.Permissions;

@ChangeLog(order="0004")
public class M0004_SuperAdminPrivileges {

	// This changelog will run everytime and updates the roleSeed in case any new permissions are added to new as well as old instances
	@ChangeSet(order = "001", id = "addRoleSeed", author = "varsha", runAlways = true)
	public void addRoleSeed(MongoTemplate template) {
		MongoCollection<Document> roles = template.getCollection("role");
		List<Document> permissions = Permissions.adminPermissions().stream().map(p -> createPrivilege(p))
				.collect(Collectors.toList());
		Document adminRole = roles.find(eq("name", RoleConstants.ORG_ADMIN)).first();

		if(adminRole == null) {
			roles.updateOne(eq("name","Admin"), new Document("$set", new Document("privileges", permissions)), new UpdateOptions().upsert(true));
			roles.updateOne(eq("name","Admin"), new Document("$set", new Document("system", true)), new UpdateOptions().upsert(true));
    		roles.updateOne(eq("name","Admin"), new Document("$set", new Document("active", true)), new UpdateOptions().upsert(true));
		}

		permissions = Permissions.syncManagerPermissions().stream().map(p -> createPrivilege(p))
				.collect(Collectors.toList());
		roles.updateOne(eq("name",RoleConstants.SYNC_MANAGER), new Document("$set", new Document("privileges", permissions)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.SYNC_MANAGER), new Document("$set", new Document("system", true)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.SYNC_MANAGER), new Document("$set", new Document("active", true)), new UpdateOptions().upsert(true));

		permissions = Permissions.viewerPermissions().stream().map(p -> createPrivilege(p))
				.collect(Collectors.toList());
		roles.updateOne(eq("name",RoleConstants.VIEWER), new Document("$set", new Document("privileges", permissions)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.VIEWER), new Document("$set", new Document("system", true)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.VIEWER), new Document("$set", new Document("active", true)), new UpdateOptions().upsert(true));

    	Document oldAdminRole = roles.find(eq("name", "Admin")).first();
    	if(adminRole == null && oldAdminRole != null) {
    		roles.updateOne(eq("name", "Admin"),
    				new Document("$set", new Document("name", RoleConstants.ORG_ADMIN)), new UpdateOptions().upsert(false));
    	}
    	
		permissions = Permissions.adminPermissions().stream().map(p -> createPrivilege(p))
				.collect(Collectors.toList());
		roles.updateOne(eq("name",RoleConstants.ORG_ADMIN), new Document("$set", new Document("privileges", permissions)), new UpdateOptions().upsert(false));
		roles.updateOne(eq("name",RoleConstants.ORG_ADMIN), new Document("$set", new Document("system", true)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.ORG_ADMIN), new Document("$set", new Document("active", true)), new UpdateOptions().upsert(true));

		permissions = Permissions.instanceAdminPermissions().stream().map(p -> createPrivilege(p))
				.collect(Collectors.toList());
		roles.updateOne(eq("name",RoleConstants.INSTANCE_ADMIN), new Document("$set", new Document("privileges", permissions)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.INSTANCE_ADMIN), new Document("$set", new Document("system", true)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.INSTANCE_ADMIN), new Document("$set", new Document("active", true)), new UpdateOptions().upsert(true));

		permissions = Permissions.ghostPermissions().stream().map(p -> createPrivilege(p))
				.collect(Collectors.toList());
		roles.updateOne(eq("name",RoleConstants.GHOST), new Document("$set", new Document("privileges", permissions)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.GHOST), new Document("$set", new Document("system", true)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.GHOST), new Document("$set", new Document("active", true)), new UpdateOptions().upsert(true));
		
		permissions = Permissions.dashboardAuthorPermissions().stream().map(p -> createPrivilege(p))
		.collect(Collectors.toList());
		roles.updateOne(eq("name",RoleConstants.DASHBOARD_AUTHOR), new Document("$set", new Document("privileges", permissions)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.DASHBOARD_AUTHOR), new Document("$set", new Document("system", true)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.DASHBOARD_AUTHOR), new Document("$set", new Document("active", true)), new UpdateOptions().upsert(true));

		permissions = Permissions.dashboardlightViewerPermissions().stream().map(p -> createPrivilege(p))
				.collect(Collectors.toList());
		roles.updateOne(eq("name",RoleConstants.DASHBOARD_LIGHT_VIEWER), new Document("$set", new Document("privileges", permissions)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.DASHBOARD_LIGHT_VIEWER), new Document("$set", new Document("system", true)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.DASHBOARD_LIGHT_VIEWER), new Document("$set", new Document("active", true)), new UpdateOptions().upsert(true));

		permissions = Permissions.synapseApproverPermissions().stream().map(p -> createPrivilege(p))
				.collect(Collectors.toList());
		roles.updateOne(eq("name",RoleConstants.SYNAPSE_APPROVER), new Document("$set", new Document("privileges", permissions)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.SYNAPSE_APPROVER), new Document("$set", new Document("system", true)), new UpdateOptions().upsert(true));
		roles.updateOne(eq("name",RoleConstants.SYNAPSE_APPROVER), new Document("$set", new Document("active", true)), new UpdateOptions().upsert(true));
	}
	
	@ChangeSet(order = "002", id = "addInbox", author = "varsha")
	public void addInbox(MongoTemplate template) {
		MongoDatabase syncariDb = MigrationContext.getSyncariDB();
    	MongoCollection<Document> users = syncariDb.getCollection("user");
    	Document admin = users.find(eq("email", M0004_InitialUsers.SUPER_ADMIN_EMAIL)).first();
    	
		MongoCollection<Document> inboxes = template.getCollection("inbox");
		inboxes.insertOne(new Document("userId", admin.get("_id").toString()).append("notifications", new ArrayList<>()));
	}

	@ChangeSet(order = "003", id = "addAdminUserRoleSeed", author = "varsha")
	public void addAdminUserRoleSeed(MongoTemplate template) {
		MongoDatabase syncariDb = MigrationContext.getSyncariDB();
    	MongoCollection<Document> users = syncariDb.getCollection("user");
    	Document admin = users.find(eq("email", M0004_InitialUsers.SUPER_ADMIN_EMAIL)).first();
    	
    	MongoCollection<Document> roles = template.getCollection("role");
    	Document adminRole = roles.find(eq("name", RoleConstants.ORG_ADMIN)).first();
    	
    	MongoCollection<Document> userRoles = template.getCollection("userRole");
		userRoles.insertOne(new Document("userId", admin.get("_id").toString()).append("roleIds", Set.of(adminRole.get("_id").toString()))
		.append("seeded", true));
	}
	
	@ChangeSet(order = "004", id = "addGhostRoleSeed", author = "varsha")
	public void addGhostRoleSeed(MongoTemplate template) {
		MongoCollection<Document> roles = template.getCollection("role");
		List<Document> permissions = Permissions.ghostPermissions().stream().map(p -> createPrivilege(p))
				.collect(Collectors.toList());
		roles.updateOne(eq("name", RoleConstants.GHOST), new Document("$set", new Document("privileges", permissions)),
				new UpdateOptions().upsert(true));
	}
	
	@ChangeSet(order = "005", id = "addInstanceAdminRoleSeed", author = "varsha")
	public void addInstanceAdminRoleSeed(MongoTemplate template) {
		MongoCollection<Document> roles = template.getCollection("role");
		
		roles.updateOne(eq("name", "Admin"),
				new Document("$set", new Document("name", RoleConstants.ORG_ADMIN)), new UpdateOptions().upsert(false));
	}

	private Document createPrivilege(String privilegeId) {
		return new Document("resourceId", "global").append("privilegeId", privilegeId);
	}
}
