package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.database.PostgresService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class CreateDatasetView {


    PostgresService service;

    @ChangeSet(order = "001", id = "createViewFromDataset", author = "rohit", runAlways = true)
    public void createViewFromDataset(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        // Find all datasets and rawQuery
        MongoCollection<Document> dataset = template.getCollection("dataset");
        var datasets = dataset.find().into(new ArrayList<Document>());
        List<String> rawQueries =  datasets.stream().map(ds -> ds.getString("rawQuery")).collect(Collectors.toList());
        log.info("Raw Queries {}", rawQueries);
        ConnectorInfo connectorInfo = createDbConnector(template);
        rawQueries.forEach(rq -> {
            log.info("View to be created with query is {} ",rq );
            log.info("Connector Info to be used is {}", connectorInfo);
            if (!dryRunMode && (null != connectorInfo)) {
                service = new PostgresService();
                log.info("PostgresService instance is {}", service);
                try{
                    service.executeDdlQuery(connectorInfo, rq);
                }catch (Exception exception){
                    log.info("Failed executing ddl script, eating exception and moving to next one {}", exception.getMessage());
                    exception.printStackTrace();
                }
            }
        });
    }

    private ConnectorInfo createDbConnector(MongoTemplate template) {
        MongoCollection<Document> connector = template.getCollection("connector");
        var datastoreConnector = connector.find(new Document().append("datastoreType", "postgresql")).into(new ArrayList<Document>());
        if (CollectionUtils.isEmpty(datastoreConnector)  || datastoreConnector.size() > 1){
            log.error("More than one datastore connectors found {}", datastoreConnector);
            return null;
        }
        ConnectorInfo connectorInfo = new ConnectorInfo();
        Document dc  = datastoreConnector.get(0);
        connectorInfo.setName(dc.getString("name"));
        log.info("name is {}", dc.getString("name"));
        connectorInfo.setId(dc.getObjectId("_id").toHexString());
        connectorInfo.setAuthConfig(new AuthConfig().setUserName(((Document)dc.get("authConfig")).getString("userName"))
                .setPassword(((Document)dc.get("authConfig")).getString("password")));
        log.info("userName is {}", ((Document)dc.get("authConfig")).getString("userName"));
        log.info("clusername is {}", ((Document)dc.get("metaConfig")).getString("clusterName"));
        connectorInfo.setMetaConfig(Map.of(Constants.CLUSTER_NAME, ((Document)dc.get("metaConfig")).getString("clusterName") + ":" + ((Document)dc.get("metaConfig")).getString("port"),
                Constants.DATABASE_NAME, ((Document)dc.get("metaConfig")).getString("dbName"),PostgresService.SCHEMA_NAME, ((Document)dc.get("metaConfig")).getString("schemaName") ));
        return connectorInfo;
    }
}
