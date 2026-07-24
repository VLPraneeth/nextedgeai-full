package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;

@Slf4j
public class SYN3945_RemoveInActiveAvailableInstancesForUser {

    @ChangeSet(order = "001", id = "updateAvailableInstances", author = "rohit")
    public void updateAvailableInstances(MongoTemplate template) {
        MongoCollection<Document> userCollection = template.getCollection("user");
        MongoCollection<Document> org = template.getCollection("organization");
        // Find deleted or inactive orgs,Find instances of those orgs
        // Find if any user contains any of that intance, if yes then delete from user
        List<Document> deletedOrgInstancesDoc = org.find(or(eq("status", "CANCELLED"),
                eq("status", "DELETED"))
                ).projection(fields(excludeId(),
                include("status","instances"))).into(new ArrayList<Document>());
        List<String> deletedOrgInstances = new ArrayList<>();
        log.info("Deleted Org size is {}",deletedOrgInstancesDoc.size());
        deletedOrgInstancesDoc.forEach(doc -> {
            log.info("Status is {}",(String)doc.get("status"));
            if (null != doc.get("status")){
                List<Document> instances = (List<Document>)doc.get("instances");
                log.info("Instance size is {}",instances.size());
                instances.forEach(instance -> {
                    deletedOrgInstances.add((String)instance.get("syncariId"));
                });
            }
        });
        if ((null != deletedOrgInstances) && (deletedOrgInstances.size() > 0)) {
            log.info("Deleted Org instances are {}",deletedOrgInstances);
            // for each instance find user which contains that instance
            // and see if user default instance is same or not
            FindIterable<Document> deletedInstanceForUserDocs =  userCollection.find(and(in("availableInstances",deletedOrgInstances),eq("systemUser",false)));
            log.info("Deleted user docs size is  {}",deletedInstanceForUserDocs.into(new ArrayList<>()).size());

            Bson delete = Updates.pullAll("availableInstances",deletedOrgInstances);
            UpdateResult result = userCollection.updateMany(and(in("availableInstances",deletedOrgInstances),eq("systemUser",false)),delete);
            log.info("Update status of deleted org associated users is  {}",result);
            // for those users delete available instances for deleted orgs.
            // validate currentInstance and change that to one of available instances after fixing available instances
            FindIterable<Document> usersWithDeletedOrgCurrentInstanceId =  userCollection.find(and(in("currentInstanceId",deletedOrgInstances),eq("systemUser",false)));
            List<Document> listOfUsers = usersWithDeletedOrgCurrentInstanceId.into(new ArrayList<>());
            log.info("Users with current instance id  of deleted org instance numbers is  {}",listOfUsers.size());

            for (Document document : listOfUsers) {
                if ( (null != (List<String>)document.get("availableInstances")) && (((List<String>) document.get("availableInstances")).size() > 0)){
                    String instanceId = ((List<String>)document.get("availableInstances")).get(0);
                    log.info("First instance of user {} is {}",document.get("_id"),instanceId);
                    Bson updatedVal = Updates.set("currentInstanceId",instanceId);
                    UpdateResult userUpadteResult = userCollection.updateOne(eq("_id",document.get("_id")),updatedVal);
                    log.info("Update status of user {} is {}",document.get("_id"),userUpadteResult);
                }
            }

        }

    }

}
