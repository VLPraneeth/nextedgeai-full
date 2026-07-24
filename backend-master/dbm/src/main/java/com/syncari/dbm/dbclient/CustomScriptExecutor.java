package com.syncari.dbm.dbclient;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.exception.MongobeeException;
import com.mongodb.MongoClient;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.MongoConfig;

@Component
@Slf4j
public class CustomScriptExecutor {
	@Autowired
	MongoClient client;
	@Autowired
	MongoConfig mongoConfig;
	@Autowired MongoTemplate customerMongoTemplate;
	@Autowired MongoTemplate syncariMongoTemplate;
    
	public void migrateSyncari(String customScriptFileName) {
        Syncaribee runner = new Syncaribee(client);
        runner.setDbName("syncaridb");
        runner.setMongoTemplate(syncariMongoTemplate);
        runner.setCustomScriptsFileName("com.syncari.dbm.customscripts.syncari." + customScriptFileName);
        run(runner, syncariMongoTemplate);
    }

    public void migrateCustomers(String customScriptFileName) {
        Syncaribee runner = new Syncaribee(mongoConfig.retrieveMongoClient(false));
        runner.setMongoTemplate(customerMongoTemplate);
        runner.setDbName(SyncariContext.getDatabase());
        runner.setCustomScriptsFileName("com.syncari.dbm.customscripts.customer." + customScriptFileName);
        run(runner, customerMongoTemplate);
    }
    
    public void migrateCustomer(String dbName, String customScriptFileName) {
    	Syncaribee runner = new Syncaribee(mongoConfig.retrieveMongoClient(false));
    	runner.setMongoTemplate(customerMongoTemplate);
    	runner.setDbName(dbName);
        runner.setCustomScriptsFileName("com.syncari.dbm.customscripts.customer." + customScriptFileName);
    	run(runner, customerMongoTemplate);
    }
    
    private void run(Syncaribee syncaribee, MongoTemplate db) {
        try {
            syncaribee.execute();
        } catch (MongobeeException e) {
        	log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            // delete any unreleased syncaribeelock locks
            MongoCollection<Document> syncaribeelock = db.getCollection("syncaribeelock");
            syncaribeelock.deleteMany(new Document());
        }
    }
}

