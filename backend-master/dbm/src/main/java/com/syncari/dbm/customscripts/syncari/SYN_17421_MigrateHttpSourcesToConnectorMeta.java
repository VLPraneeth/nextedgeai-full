package com.syncari.dbm.customscripts.syncari;

import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_17421_MigrateHttpSourcesToConnectorMeta {

    @ChangeSet(order = "001", id = "migrateHttpSourcesToConnectorMeta", author = "sibin", runAlways = true)
    public void migrateHttpSourcesToConnector(MongoTemplate template) {
    	boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
    	log.info("Running this tool in dryrun mode: {} ", dryRunMode);
    	String metaId = (String) System.getProperty("metaId");
    	MongoCollection<Document> httpSourceConfigDoc = template.getCollection("httpSourceConfig");
    	MongoCollection<Document> connectorMetadataDoc = template.getCollection("connectorMetadata");
    	log.info("Finding  httpSourceConfig for this tool in connector: {} ", metaId);
    	Bson query = new Document("_id", new ObjectId(metaId));
    	for(Document meta : connectorMetadataDoc.find(query)) {
    	  List<Document> httpSourceList = (List<Document>) meta.get("httpSources");
    	  if(httpSourceList != null) {
    	    log.info("Found  {} httpSourceConfig for this tool in connector meta: {} ", httpSourceList.size(), metaId);
    	    for(Document http : httpSourceList) {
    	      log.info("httpSourceConfig {}", http.toJson());
    	    }
    	  }
    	  query = new Document("metaId", metaId);
    	  var httpFromCollection = httpSourceConfigDoc.find(query).into(new ArrayList<>());
    	  log.info("Found {} httpSourceConfig from collection", httpFromCollection.size());
    	  for(Document httpSrc : httpFromCollection) {
    	    log.info("Found {}", httpSrc.toJson());
    	  }
    	  if(!dryRunMode) {
    	    Bson updatedVal = Updates.set("httpSources", httpFromCollection);
    	    connectorMetadataDoc.findOneAndUpdate(eq("_id",new ObjectId(metaId)), updatedVal);
    	  }
    	}
    }
}
