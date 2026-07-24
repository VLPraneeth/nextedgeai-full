package com.syncari.core.changelogs.syncari;

import static com.mongodb.client.model.Filters.eq;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.User;
import org.apache.commons.codec.binary.Hex;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Status;

@ChangeLog(order="0004")
public class M0004_InitialUsers {
	private static final String DEFAULT_DB = "nextedge_admin";
	public static final String SUPER_ADMIN_EMAIL = System.getenv().getOrDefault("NEXTEDGE_ADMIN_EMAIL", "admin@nextedge.ai");
	public static final String DEFAULT_ORG_NAME = "NextEdge AI";

	@ChangeSet(order = "001", id = "superAdmin", author = "neelesh")
	public void addSuperAdmin(MongoTemplate template) {
		MongoCollection<Document> users = template.getCollection("user");
		Document admin = users.find(eq("email", SUPER_ADMIN_EMAIL)).first();
		
		if (admin == null) {
			users.insertOne(new Document("email", SUPER_ADMIN_EMAIL).append("password", new BCryptPasswordEncoder().encode(requiredAdminPassword()))
					.append("status", Status.ACTIVE.name())
					.append("isSuperAdmin", true).append("firstName", "NextEdge").append("lastName", "Admin")
					.append("seeded", true));
		}
		admin = users.find(eq("email", SUPER_ADMIN_EMAIL)).first();
		MongoCollection<Document> inboxes = template.getMongoDbFactory().getDb(DEFAULT_DB).getCollection("inbox");
		inboxes.insertOne(new Document("userId", admin.get("_id")).append("notifications", new ArrayList<>()));
		MongoCollection<Document> userPref = template.getMongoDbFactory().getDb(DEFAULT_DB).getCollection("userPreference");
		userPref.insertOne(new Document("userId", admin.get("_id").toString()));
	}

	@ChangeSet(order = "002", id = "OrgInstance", author = "neelesh")
	public void addSuperAdminOrg(MongoTemplate db) {
		MongoCollection<Document> users = db.getCollection("user");
		MongoCollection<Document> orgs = db.getCollection("organization");
		Document org = new Document("name", DEFAULT_ORG_NAME);
		
		Document resource = new Document("type","DATABASE").append("configuration", Map.of("database",DEFAULT_DB));
		Document instance = new Document("type","production")
		        .append("name", "NextEdge AI Demo")
				.append("resources", Map.of("DATABASE",resource))
				.append("nextEdgeId", DEFAULT_DB)
				// Transitional read compatibility; removed after all existing documents are migrated.
				.append("syncariId", DEFAULT_DB);
		org.append("instances", List.of(instance));
		orgs.insertOne(org);
		Object id = orgs.find(new BasicDBObject("name", DEFAULT_ORG_NAME)).first().get("_id");
		Bson query = eq("email", SUPER_ADMIN_EMAIL);
		
		Document updated = users.findOneAndUpdate(query, new Document("$set",new Document("orgId", id.toString())));
		assert updated != null;
	}

	@ChangeSet(order = "003", id = "systemUser", author = "abhinav")
	public void addSystemUser(MongoTemplate db) {
		MongoCollection<Document> users = db.getCollection("user");
		//Create one system user per org
		SecureRandom random = new SecureRandom();
		byte[] passwordBytes = new byte[16];
		random.nextBytes(passwordBytes);
		String password = Hex.encodeHexString(passwordBytes);
		users.insertOne(new Document("email", User.SYSTEM_USER_PREFIX).append("password", new BCryptPasswordEncoder().encode(password))
				.append("status", Status.ACTIVE.name())
				.append("systemUser", true)
				.append("firstName", "NextEdge")
				.append("lastName", "System User")
				.append("status", Status.ACTIVE.name())
				.append("seeded", true));
	}

	private static String requiredAdminPassword() {
		String password = System.getenv("NEXTEDGE_ADMIN_PASSWORD");
		if (password == null || password.length() < 16) {
			throw new IllegalStateException("NEXTEDGE_ADMIN_PASSWORD must be supplied and contain at least 16 characters");
		}
		return password;
	}
	
}
