package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.util.Status;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Slf4j
public class SYN_6779_AddPLGSystemAPIUser {

    private final String INSTANCE_ASSOCIATED_WITH = "NSFI14";

    @ChangeSet(order = "001", id = "addPLGSystemUser", author = "rohit")
    public void addPLGSystemUser(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        var syncariId = System.getProperty("syncariId");

        MongoCollection<Document> orgCollection = template.getCollection("organization");
        MongoCollection<Document> userCollection = template.getCollection("user");
        // Find Syncari Org Id
        log.info("SyncariId to be used is {}", syncariId);
        Bson orgFilter = Filters.eq("instances.syncariId", syncariId);
        var orgs = orgCollection.find(orgFilter).into(new ArrayList<Document>());
        String orgId = orgs.get(0).get("_id").toString();
        log.info("Org id is {}", orgId);

        MongoCollection<Document> users = template.getCollection("user");
        String clientId = this.generateRandomString(20);
        String clientSecret = this.generateRandomString(32);

        for (int i = 0; i <= 3; i++) {
            try {
                Bson userFilter = Filters.eq("clientId", clientId);
                List<Document> clientIdUser = userCollection.find(userFilter).into(new ArrayList<Document>());
                log.info("Client Id user is {}", clientIdUser);
                if ((null == clientIdUser) || (CollectionUtils.isEmpty(clientIdUser))){
                    break;
                }
                clientId = this.generateRandomString(20);
                if (i == 3 ) throw new RuntimeException("Issue finding right client id");
            } catch (NotFoundException nfe) {
                break;
            }
        }

        log.info("Generated clientId is {}", clientId);
        log.info("Generated clientSecret is {}", clientSecret);

        // Create plg system user
        String systemUser = "system_plg@syncari.com";
        SecureRandom random = new SecureRandom();
        byte[] passwordBytes = new byte[16];
        random.nextBytes(passwordBytes);
        String password = Hex.encodeHexString(passwordBytes);
        log.info("Password generated is " + password);
        if(!dryRunMode){
            users.insertOne(new Document("email", systemUser).append("password", new BCryptPasswordEncoder().encode(password))
                    .append("status", Status.ACTIVE.name())
                    .append("systemUser", true)
                    .append("firstName", "Syncari PLG")
                    .append("lastName", "System User")
                    .append("isApiUser", true)
                    .append("isAdmin", true)
                    .append("orgId", orgId)
                    .append("availableInstances", Set.of(syncariId))
                    .append("currentInstanceId", syncariId)
                    .append("seeded", true)
                    .append("clientId", clientId)
                    .append("clientSecret", new BCryptPasswordEncoder().encode(clientSecret)));
        }
    }

    public String generateRandomString(int stringSize) {
        SecureRandom random = new SecureRandom();
        byte bytes[] = new byte[stringSize];
        random.nextBytes(bytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String randomeString = encoder.encodeToString(bytes);
        return randomeString;
    }
}
