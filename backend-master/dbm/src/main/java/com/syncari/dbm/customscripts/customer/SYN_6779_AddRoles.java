package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.changelogs.syncari.M0004_InitialUsers;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.model.util.Status;
import com.syncari.core.security.Permissions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class SYN_6779_AddRoles {

    @ChangeSet(order = "001", id = "addPermissionToRole", author = "rohit")
    public void addPermissionToRole(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MongoCollection<Document> role = template.getCollection("role");
        // Find Syncari Org Id
        Bson roleFilter = Filters.eq("name", RoleConstants.ORG_ADMIN.toString());
        var roles = role.find(roleFilter).into(new ArrayList<Document>());
        assert roles.size() == 1;
        log.info("Role id is " + roles.get(0).get("_id"));

        List<Document> permissions = Permissions.adminPermissions().stream().map(p -> createPrivilege(p))
                .collect(Collectors.toList());
        log.info("Permissions list size is " + permissions.size());
        Document adminRole = role.find(eq("name", RoleConstants.ORG_ADMIN)).first();

        if(!dryRunMode) {
            role.updateOne(eq("name",RoleConstants.ORG_ADMIN.toString()), new Document("$set", new Document("privileges", permissions)), new UpdateOptions().upsert(true));
        }
    }

    @ChangeSet(order = "002", id = "assignRoleToUser", author = "rohit")
    public void assignRoleToUser(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MongoDatabase syncariDb = template.getMongoDbFactory().getDb("syncaridb");
        MongoCollection<Document> users = syncariDb.getCollection("user");
        Document systemUser = users.find(eq("email", "system_plg@syncari.com")).first();
        log.info("PLG System User Id is {}", systemUser.get("_id"));
        log.info("Db is {}", template.getDb().getName());
        MongoCollection<Document> roles = template.getCollection("role");
        Bson roleFilter = Filters.eq("name", RoleConstants.ORG_ADMIN.toString());
        Document adminRole = roles.find(roleFilter).first();
        String roleId = adminRole.get("_id").toString();
        log.info("Existin adminRole id is {}", roleId);
        MongoCollection<Document> userRoles = template.getCollection("userRole");
        if (!dryRunMode){
            userRoles.insertOne(new Document("userId", systemUser.get("_id").toString()).append("roleIds", Set.of(roleId))
                    .append("seeded", true));
        }
    }

    private Document createPrivilege(String privilegeId) {
        return new Document("resourceId", "global").append("privilegeId", privilegeId);
    }
}
