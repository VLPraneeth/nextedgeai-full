package com.syncari.dbm.customscripts.customer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Connector;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_18025_CountWebhookWithMutipleEntities {

  @ChangeSet(order = "001", id = "countWebhookWithMutipleEntities", author = "sibin",
      runAlways = true)
  public void countWebhookWithMutipleEntities(MongoTemplate template) {

    log.info("countWebhookWithMutipleEntities start");
    var connectorMetaRepo = MigrationContext.getConnectorMetadataRepo();
    var connectorRepo = MigrationContext.getConnectorRepo();
    var schemaServie = MigrationContext.getSchemaService();

    List<String> webhookMetaIdList = connectorMetaRepo.findWebhookReceivers().stream()
        .map(m -> m.getId()).collect(Collectors.toList());
    List<Connector> connectors = new ArrayList<>();
    webhookMetaIdList.forEach(mId -> {
      connectors.addAll(connectorRepo.findByMetadataId(mId));
    });
    connectors.forEach(c -> {
      var entities = schemaServie.getEntities(c.getId(), false);
      if (entities.size() > 1) {
        var activeEntities = entities.stream().filter(e -> e.isActive())
            .map(e -> e.getApiName() + "(" + e.getId() + ")").collect(Collectors.toList());
        var inActiveEntities = entities.stream().filter(e -> !e.isActive())
            .map(e -> e.getApiName() + "(" + e.getId() + ")").collect(Collectors.toList());
        log.info(
            "Found webhook connector {}({}) with mutiple entities; active entities {} inactive entities {}",
            c.getName(), c.getId(), activeEntities, inActiveEntities);
      } else {
        log.info("Found webhook connector {}({}) with single entity {} ", c.getName(), c.getId(),
            entities.stream().map(e -> e.getApiName() + "(" + e.getId() + ")")
                .collect(Collectors.toList()));
      }
    });
    log.info("countWebhookWithMutipleEntities end");
  }

}
