package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Connector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class PrintSalesforceEndpoints {

    @ChangeSet(order = "001", id = "printsalesforceendpoints", author = "varsha")
    public void printsalesforceendpoints(MongoTemplate template) {

        List<Connector> connectors = MigrationContext.getConnectorService().getAllActive().stream()
                .filter(c -> c.getMetadata().getName().equalsIgnoreCase("salesforce")).collect(Collectors.toList());

        for (int i = 0; i < connectors.size(); i++) {
            log.info(connectors.get(i).getEndpoint());
        }
    }
}