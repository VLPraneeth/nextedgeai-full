package com.syncari.dbm.customscripts.customer;

import static com.mongodb.client.model.Filters.regex;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.github.mongobee.changeset.ChangeSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_17410_FindEntityDefinition {

  @ChangeSet(order = "001", id = "findEntityDefinition", author = "sibin", runAlways = true)
  public void updateNewCountDataset(MongoTemplate template) {
    log.info("Searching for $ and _U0024_");
    var entityDefinition = template.getCollection("entityDefinition");
    var eDollars = entityDefinition.find(regex("apiName", ".*\\$.*"));
    for (var ed : eDollars) {
      log.info("Found entityDefinition with $: {}", ed.toJson());
    }

    var unicodeDollars = entityDefinition.find(regex("apiName", ".*_U0024_.*"));
    for (var ed : unicodeDollars) {
      log.info("Found entityDefinition with _U0024_: {}", ed.toJson());
    }
  }
}
