package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class SYN_12103_FixOldMetricTableDataCards {

    @ChangeSet(order = "001", id = "fixOldMetricTableDataCards", author = "abhinav", runAlways = true)
    public void fixOldMetricTableDataCards(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        var datacard = template.getCollection("datacard");

        // metric filter
        Bson metricFilter = and(eq("seeded" ,false), eq("draftStatus", "APPROVED"),
                eq("contents.0.config._class", "com.syncari.core.model.insights.MetricTableVizConfig"),
                eq("contents.0.type", "METRIC"));
        final List<Document> metricDCs = datacard.find(metricFilter).into(new ArrayList<>());

        if(!metricDCs.isEmpty()){
            // Following metric datacards need to be updated
            List<String> dcNames = metricDCs.stream().map(d -> d.getString("name")).collect(Collectors.toList());
            log.info("Metric Datacards to update: {}", dcNames.toString());

            if(!dryRunMode){
                datacard.updateMany(metricFilter,
                        new Document("$set", new Document("contents.0.config._class", "com.syncari.core.model.insights.MetricVizConfig"))
                );
            }
        }

        // table filter
        Bson tableFilter = and(eq("seeded" ,false), eq("draftStatus", "APPROVED"),
                eq("contents.0.config._class", "com.syncari.core.model.insights.MetricTableVizConfig"),
                eq("contents.0.type", "TABLE"));
        final List<Document> tableDCs = datacard.find(tableFilter).into(new ArrayList<>());

        if(!tableDCs.isEmpty()){
            // Following table datacards need to be updated
            List<String> dcNames = tableDCs.stream().map(d -> d.getString("name")).collect(Collectors.toList());
            log.info("Table Datacards to update: {}", dcNames.toString());

            if(!dryRunMode){
                datacard.updateMany(tableFilter,
                        new Document("$set", new Document("contents.0.config._class", "com.syncari.core.model.insights.TableVizConfig"))
                );
            }
        }
    }
}
