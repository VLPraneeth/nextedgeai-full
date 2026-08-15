package com.syncari.core.changelogs.syncari;

import static com.mongodb.client.model.Filters.eq;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.util.Status;

@ChangeLog(order = "9992")
public class M9992_GuidedDemoAccount {
    private static final String GUIDED_DEMO_EMAIL = System.getenv().getOrDefault(
            "NEXTEDGE_TENANT_ADMIN_EMAIL", "demo@nextedge.ai");
    private static final String LEGACY_TENANT_EMAIL = "tenant-admin@nextedge.ai";

    @ChangeSet(order = "001", id = "nextEdgeGuidedDemoAccount", author = "nextedge", runAlways = true)
    public void configureGuidedDemoAccount(MongoTemplate template) {
        MongoCollection<Document> users = template.getCollection("user");
        Document user = users.find(eq("email", GUIDED_DEMO_EMAIL)).first();
        if (user == null) {
            user = users.find(eq("email", LEGACY_TENANT_EMAIL)).first();
        }
        if (user == null) {
            throw new IllegalStateException("The isolated demo user was not migrated");
        }

        Document guidedDemoFields = new Document("email", GUIDED_DEMO_EMAIL)
                .append("password", new BCryptPasswordEncoder().encode(requiredTenantPassword()))
                .append("status", Status.ACTIVE.name())
                .append("isAdmin", false)
                .append("isSuperAdmin", false)
                .append("syncariDev", false)
                .append("systemUser", false)
                .append("firstName", "Guided")
                .append("lastName", "Demo")
                .append("restrictedFromLogin", false)
                .append("guidedDemo", true)
                .append("seeded", true);
        users.updateOne(eq("_id", user.getObjectId("_id")), new Document("$set", guidedDemoFields));
    }

    private static String requiredTenantPassword() {
        String password = System.getenv("NEXTEDGE_TENANT_ADMIN_PASSWORD");
        if (password == null || password.length() < 16) {
            throw new IllegalStateException("NEXTEDGE_TENANT_ADMIN_PASSWORD must contain at least 16 characters");
        }
        return password;
    }
}
