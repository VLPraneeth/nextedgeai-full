package com.syncari.dbm.customscripts.customer;

import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_17421_MigrateHttpSourcesToConnector {

    @ChangeSet(order = "001", id = "migrateHttpSourcesToConnector", author = "sibin", runAlways = true)
    public void migrateHttpSourcesToConnector(MongoTemplate template) {
    	boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
    	log.info("Running this tool in dryrun mode: {} ", dryRunMode);
    	String metaId = (String) System.getProperty("metaId");
    	var metaRepo = MigrationContext.getConnectorMetadataRepo();
    	var connectorRepo = MigrationContext.getConnectorRepo();
    	var meta = metaRepo.findById(metaId);
    	if(meta.isPresent()) {
    	  log.info("Found {} conenctor meta", meta.get().getName());
    	  if(meta.get().getHttpSources() != null) {
    	    log.info("Found {} conenctor meta http sources {}", meta.get().getHttpSources().stream().map(c -> c.getApiName()).collect(Collectors.toList()));
    	  }
    	  var connectors = connectorRepo.findByMetadataId(metaId);
    	  log.info("Found {} conenctors", connectors.stream().map(c -> c.getId()).collect(Collectors.toList()));
    	  for(var con : connectors) {
    	    log.info("Existing http sources {} connector {}", con.getMetadata().getHttpSources().stream().map(c -> c.getApiName()).collect(Collectors.toList()), con.getId());
    	  }
    	  if(!dryRunMode) {
    	    for(var con : connectors) {
    	      con.getMetadata().setHttpSources(meta.get().getHttpSources());
            }
    	    connectorRepo.saveAll(connectors);
    	  }
    	}
    	
    }
}
