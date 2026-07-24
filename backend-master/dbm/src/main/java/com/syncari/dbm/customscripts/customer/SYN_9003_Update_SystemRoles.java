package com.syncari.dbm.customscripts.customer;

import static com.mongodb.client.model.Filters.eq;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.model.misc.RoleConstants;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_9003_Update_SystemRoles {

    @ChangeSet(order = "001", id = "updateRolesSystemAndActive", author = "sibin")
    public void updateRolesSystemAndActive(MongoTemplate template) {
    	updateRole(template, "Admin");
        updateRole(template, RoleConstants.ORG_ADMIN);
        updateRole(template, RoleConstants.SYNC_MANAGER);
        updateRole(template, RoleConstants.VIEWER);
        updateRole(template, RoleConstants.ORG_ADMIN);
        updateRole(template, RoleConstants.INSTANCE_ADMIN);
        updateRole(template, RoleConstants.GHOST);
        updateRole(template, RoleConstants.DASHBOARD_AUTHOR);
        updateRole(template, RoleConstants.DASHBOARD_LIGHT_VIEWER);
        
    }

    private void updateRole(MongoTemplate template, String roleStr) {
    	boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MongoCollection<Document> roles = template.getCollection("role");
        
        Document role = roles.find(eq("name", roleStr)).first();
        if(role != null) {
        	log.info("Role id is " + role.get("_id") + " for " + roleStr);
        	if(!dryRunMode) {
        		roles.updateOne(eq("name",roleStr), new Document("$set", new Document("system", true)), new UpdateOptions().upsert(true));
        		roles.updateOne(eq("name",roleStr), new Document("$set", new Document("active", true)), new UpdateOptions().upsert(true));
        	}
        	
        } else {
        	log.info("No role " + roleStr + " present");
        }
    }
}
