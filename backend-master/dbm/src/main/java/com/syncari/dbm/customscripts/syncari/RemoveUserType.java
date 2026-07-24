package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.User;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;

@Slf4j
public class RemoveUserType {

    @ChangeSet(order = "001", id = "removeUserType", author = "rohit", runAlways = true)
    public void removeUserType(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MongoCollection<Document> userCollection = template.getCollection("user");
        FindIterable<Document> usersToIterate =   userCollection.find(exists("userType", true));
        if (null != usersToIterate){
            List<Document> listOfUsers = usersToIterate.into(new ArrayList<>());
            listOfUsers.forEach(u -> {
                if (!dryRunMode){
                    UpdateResult result = userCollection.updateOne(eq("_id", u.get("_id")), Updates.unset("userType"));
                    log.info("Result is {}",result);
                }else{
                    log.info("Running in dry mode user is  {}",u);
                }
            });


        }






    }
}
