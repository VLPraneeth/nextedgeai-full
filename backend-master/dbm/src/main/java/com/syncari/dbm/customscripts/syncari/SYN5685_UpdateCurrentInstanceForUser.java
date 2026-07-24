package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.*;

@Slf4j
public class SYN5685_UpdateCurrentInstanceForUser {

    @ChangeSet(order = "001", id = "updateCurrentInstanceForUsers", author = "rohit")
    public void updateCurrentInstanceForUsers(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        log.info("Running this tool in dryrun mode: {} ", dryRunMode);
        MongoCollection<Document> userCollection = template.getCollection("user");
        // Find user with deletedInstance for given instanceId
        //O3OYVH
        FindIterable<Document> usersWithDeletedInstance =  userCollection.find(eq("currentInstanceId","O3OYVH"));
        if ((null != usersWithDeletedInstance) ) {
            log.info("Deleted Instance users are {}",usersWithDeletedInstance);
            // For each user update currentInstance to first of availableInstance if it exists
            List<Document> listOfUsers = usersWithDeletedInstance.into(new ArrayList<>());
            log.info("Users with current instance id  of deleted instance numbers is  {}",listOfUsers.size());

            for (Document document : listOfUsers) {
                if ( (null != (List<String>)document.get("availableInstances")) && (((List<String>) document.get("availableInstances")).size() > 0)){
                    String instanceId = ((List<String>)document.get("availableInstances")).get(0);
                    log.info("First instance of user {} is {}",document.get("_id"),instanceId);
                    if (!dryRunMode){
                        Bson updatedVal = Updates.set("currentInstanceId",instanceId);
                        UpdateResult userUpadteResult = userCollection.updateOne(eq("_id",document.get("_id")),updatedVal);
                        log.info("Update status of user {} is {}",document.get("_id"),userUpadteResult);
                    }
                }
            }
        }
    }
}
