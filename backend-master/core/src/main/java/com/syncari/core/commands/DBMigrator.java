package com.syncari.core.commands;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.github.mongobee.Mongobee;
import com.github.mongobee.exception.MongobeeException;
import com.mongodb.MongoClient;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.MongoConfig;

@Component
public class DBMigrator {
	@Autowired
	MongoClient client;
	@Autowired
	MongoConfig mongoConfig;
	@Autowired MongoTemplate customerMongoTemplate;
	@Autowired MongoTemplate syncariMongoTemplate;
	@Autowired MongoDbFactory customerDBFactory;
    
	public void migrateSyncari(){
        Mongobee runner = new Mongobee(client);
        runner.setDbName("syncaridb");
        runner.setMongoTemplate(syncariMongoTemplate);
        runner.setChangeLogsScanPackage(
                "com.syncari.core.changelogs.syncari");
        run(runner, syncariMongoTemplate);
    }

    public void migrateCustomers(){
        Mongobee runner = new Mongobee(mongoConfig.retrieveMongoClient(false));
        runner.setMongoTemplate(customerMongoTemplate);
        runner.setDbName(SyncariContext.getDatabase());
        runner.setChangeLogsScanPackage(
                "com.syncari.core.changelogs.customer");
        run(runner, customerMongoTemplate);
    }
    
    public void migrateCustomer(String dbName){
    	Mongobee runner = new Mongobee(mongoConfig.retrieveMongoClient(false));
    	runner.setMongoTemplate(customerMongoTemplate);
    	runner.setDbName(dbName);
    	runner.setChangeLogsScanPackage(
    			"com.syncari.core.changelogs.customer");
    	run(runner, customerMongoTemplate);
    }
    
    private void run(Mongobee mongobee, MongoTemplate db){
        try {
            mongobee.execute();
        } catch (MongobeeException e) {
        	e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            // delete any unreleased mongobee locks
            MongoCollection<Document> mongobeelock = db.getCollection("mongobeelock");
            mongobeelock.deleteMany(new Document());
        }
    }
}
