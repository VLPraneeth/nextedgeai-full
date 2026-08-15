package com.syncari.core.changelogs.syncari;

import static com.mongodb.client.model.Filters.eq;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.model.util.Status;

@ChangeLog(order = "9991")
public class M9991_DemoTenantIsolation {
    public static final String TENANT_ID = "nextedge_tenant_b";
    public static final String TENANT_ORG = "NextEdge AI Isolation Demo";
    public static final String TENANT_EMAIL = System.getenv().getOrDefault(
            "NEXTEDGE_TENANT_ADMIN_EMAIL", "demo@nextedge.ai");

    @ChangeSet(order = "001", id = "nextEdgeIsolationDemoTenant", author = "nextedge")
    public void createIsolationDemoTenant(MongoTemplate template) {
        MongoCollection<Document> organizations = template.getCollection("organization");
        Document organization = organizations.find(eq("name", TENANT_ORG)).first();
        if (organization == null) {
            Document databaseResource = new Document("type", "DATABASE")
                    .append("configuration", Map.of("database", TENANT_ID));
            Document instance = new Document("type", "production")
                    .append("name", "NextEdge AI Tenant B")
                    .append("displayName", "NextEdge AI Tenant B")
                    .append("status", Status.ACTIVE.name())
                    .append("resources", Map.of("DATABASE", databaseResource))
                    .append("nextEdgeId", TENANT_ID)
                    .append("syncariId", TENANT_ID);
            organization = new Document("name", TENANT_ORG)
                    .append("status", Status.ACTIVE.name())
                    .append("orgType", "standard")
                    .append("instances", List.of(instance));
            organizations.insertOne(organization);
        }

        MongoCollection<Document> users = template.getCollection("user");
        Document tenantUserFields = new Document("email", TENANT_EMAIL)
                .append("password", new BCryptPasswordEncoder().encode(requiredTenantPassword()))
                .append("status", Status.ACTIVE.name())
                .append("isAdmin", false)
                .append("isSuperAdmin", false)
                .append("systemUser", false)
                .append("firstName", "Guided")
                .append("lastName", "Demo")
                .append("orgId", organization.getObjectId("_id").toHexString())
                .append("currentInstanceId", TENANT_ID)
                .append("availableInstances", Set.of(TENANT_ID))
                .append("restrictedFromLogin", false)
                .append("guidedDemo", true)
                .append("seeded", true);
        users.updateOne(eq("email", TENANT_EMAIL), new Document("$set", tenantUserFields), new UpdateOptions().upsert(true));
    }

    private static String requiredTenantPassword() {
        String password = System.getenv("NEXTEDGE_TENANT_ADMIN_PASSWORD");
        if (password == null || password.length() < 16) {
            throw new IllegalStateException("NEXTEDGE_TENANT_ADMIN_PASSWORD must contain at least 16 characters");
        }
        return password;
    }
}
