package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.Set;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.changelogs.syncari.M0004_InitialUsers;
import com.syncari.core.model.misc.RoleConstants;

@ChangeLog(order = "9991")
public class M9991_DemoTenantIsolation {
    private static final String TENANT_ID = "nextedge_tenant_b";
    private static final String TENANT_EMAIL = System.getenv().getOrDefault(
            "NEXTEDGE_TENANT_ADMIN_EMAIL", "tenant-admin@nextedge.ai");

    @ChangeSet(order = "001", id = "nextEdgeIsolationDemoTenantAdminV2", author = "nextedge", runAlways = true)
    public void seedIsolationDemoTenantAdmin(MongoTemplate template) {
        if (!TENANT_ID.equals(SyncariContext.getSyncariId())) {
            return;
        }

        MongoCollection<Document> systemUsers = MigrationContext.getSyncariDB().getCollection("user");
        Document tenantAdmin = requiredUser(systemUsers, TENANT_EMAIL);
        Document bootstrapAdmin = requiredUser(systemUsers, M0004_InitialUsers.SUPER_ADMIN_EMAIL);

        MongoCollection<Document> roles = template.getCollection("role");
        Document adminRole = roles.find(eq("name", RoleConstants.ORG_ADMIN)).first();
        if (adminRole == null) {
            throw new IllegalStateException("The tenant Org Admin role was not migrated");
        }

        String tenantUserId = tenantAdmin.getObjectId("_id").toHexString();
        String bootstrapUserId = bootstrapAdmin.getObjectId("_id").toHexString();
        MongoCollection<Document> userRoles = template.getCollection("userRole");
        userRoles.deleteOne(eq("userId", bootstrapUserId));
        userRoles.updateOne(eq("userId", tenantUserId), new Document("$set", new Document(
                "roleIds", Set.of(adminRole.getObjectId("_id").toHexString())).append("seeded", true)),
                new UpdateOptions().upsert(true));

        MongoCollection<Document> inboxes = template.getCollection("inbox");
        inboxes.deleteMany(eq("userId", bootstrapUserId));
        inboxes.updateOne(eq("userId", tenantUserId), new Document("$setOnInsert", new Document(
                "userId", tenantUserId).append("notifications", new ArrayList<>())), new UpdateOptions().upsert(true));

        MongoCollection<Document> preferences = template.getCollection("userPreference");
        preferences.updateOne(eq("userId", tenantUserId), new Document("$setOnInsert", new Document(
                "userId", tenantUserId)), new UpdateOptions().upsert(true));
    }

    private Document requiredUser(MongoCollection<Document> users, String email) {
        Document user = users.find(eq("email", email)).first();
        if (user == null) {
            throw new IllegalStateException("Required seeded user is missing");
        }
        return user;
    }
}
