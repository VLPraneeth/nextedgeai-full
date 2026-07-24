package com.syncari.dbm.dbclient;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.Resource;
import com.syncari.core.model.ResourceType;
import com.syncari.core.repositories.syncari.OrganizationRepo;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SyncariMongoDBClient {

    @Autowired
    OrganizationRepo organizationRepo;

    @Value("${spring.data.mongodb.uri}")
    private String uri;

    @Value("${spring.data.mongodb.readOnlyUri}")
    private String readUri;

    private boolean writeMode = false;

    ObjectMapper mapper = new ObjectMapper();
    
    private final static String SYNCARI_DB = "syncaridb";

    private MongoClient mongoClient;

    public ObjectMapper getMapper() {
        return mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
    
    public void setWriteMode(boolean writeMode) {
        this.writeMode = writeMode;
    }

    public ConnectionString getConnectionString() {
        log.info("Writemode: {}, URI: {} for connection.", writeMode, writeMode ? uri : readUri);
        return (writeMode) ? new ConnectionString(uri) : new ConnectionString(readUri);
    }

    private MongoClient getMongoClient() {
        if (mongoClient == null) {
            MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(getConnectionString())
                .retryWrites(true)
                .build();
            mongoClient = MongoClients.create(settings);
        }
        return mongoClient;
    }

    public void testConnection(String dbName) {
        MongoDatabase db = getDatabase(dbName);
    }

    public MongoDatabase getSyncariDb() {
        return getDatabase(SYNCARI_DB);
    }

    public MongoDatabase getCustomerDb(String dbName) {
        return getDatabase(dbName);
    }

    private MongoDatabase getDatabase(String dbName) {
        return getMongoClient().getDatabase(dbName);
    }

    public List<String> getCustomerDatabaseNames() {
        List<String> customerDBs = new ArrayList<>();
        List<Organization> all = organizationRepo.findAllActiveCustomers();
        for(Organization organization : all) {
            List<Instance> instances = organization.getInstances();
            for(Instance instance : instances) {
                Resource custdb = instance.getResource(ResourceType.DATABASE).get();
                customerDBs.add(custdb.getConfiguration().get("database"));
            }
        }
        return customerDBs;
    }

    public List<String> executeCommandForEachCustomer(String query) {
        log.info("\nExecuting command: {} \n", query);
        List<String> results = new ArrayList<>();
        getCustomerDatabaseNames().forEach(x -> results.add(executeCommand(getCustomerDb(x), query)));
        return results;
    }

    public List<String> executeForCustomers(String query, List<String> customers) {
        log.info("\nExecuting command: {} \n", query);
        List<String> results = new ArrayList<>();
        if (customers.size() == 1 && "all".equalsIgnoreCase(customers.get(0))) {
            results.addAll(executeCommandForEachCustomer(query));
        } else {
            customers.forEach(x -> results.add(executeCommand(getCustomerDb(x), query)));
        }
        return results;
    }

    public String executeCommand(MongoDatabase db, String query) {
        return executeCommandWithoutSessions(db, query);
        // TODO: when we support transactions (sessions) in the prod cluster, we need to enable this path.
        // return executeCommandWithSessions(db, query);
    }

    public String executeCommandWithoutSessions(MongoDatabase db, String query) {
        Document results = db.runCommand(Document.parse(query));
        log.info("\nResults for db {} are:\n ", db.getName());
        String resultJson = results.toJson();
        log.info("\n=================\n");
        log.info(resultJson);
        log.info("\n=================\n");
        return resultJson;
    }

    public String executeCommandWithSessions(MongoDatabase db, String query) {
        ClientSession sess = getMongoClient().startSession();
        String resultJson = "";
        try (sess) {
            Document results = db.runCommand(Document.parse(query));
            log.info("\nResults for db {} are:\n ", db.getName());
            resultJson = results.toJson();
            log.info("\n=================\n");
            log.info(resultJson);
            log.info("\n=================\n");
            if (writeMode) {
                sess.commitTransaction();
            } else {
                sess.abortTransaction();
            }
        } catch (Exception e) {
            log.info("Encountered exception: {} ", e.getMessage());
            throw e;
        }
        return resultJson;
    }
}
