package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.eq;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.repositories.customer.RoleRepo;
import com.syncari.core.security.Permissions;

@ChangeLog(order="0041")
public class M0041_AddRolePrivileges {
	
	@ChangeSet(order = "001", id = "addDataStudioPriv", author = "varsha")
	public void addRoleSeed(MongoTemplate template) {
		//No-op
	}

    private void addPriv(MongoCollection<Document> roles, String role, boolean write) {
        Document roleDoc = roles.find(eq("name", role)).first();
		((List)roleDoc.get("privileges")).add(new Document("resourceId", "global").append("privilegeId", Permissions.READ_DATA_STUDIO));
		if(write) {
		    ((List)roleDoc.get("privileges")).add(new Document("resourceId", "global").append("privilegeId", Permissions.WRITE_DATA_STUDIO));
		}
        Document update = new Document();
        update.append("$set",new Document("privileges", roleDoc.get("privileges")));
        roles.updateOne(eq("name", role), update, new UpdateOptions().upsert(false));
    }
	
}
