package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Connector;
import com.syncari.core.service.DatastoreService;
import com.syncari.dbm.customscripts.customer.util.DatastoreUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Slf4j
public class SYN_17253_CreateDatastoreIndex {
    DatastoreUtil datastoreUtil = new DatastoreUtil();

    @ChangeSet(order = "001", id = "createDatastoreIndex", author = "varsha", runAlways = true)
    public void createDatastoreIndex(MongoTemplate template) throws SQLException, ClassNotFoundException {
        String dbname = System.getProperty("dbname");
        String entityName = System.getProperty("entityName");
        String[] fields = System.getProperty("fields").toString().split(",");
        createdDatasetIndexes(dbname, entityName, List.of(fields));
    }


    private void createdDatasetIndexes(String dbName, String entityName, List<String> fields) throws SQLException, ClassNotFoundException {
        DatastoreService datastoreService = MigrationContext.getDatastoreService();
        Connector datastore = datastoreService.findActiveDatastore()
                .orElseThrow(() -> new RuntimeException("Datastore connector missing"));
        ConnectorInfo connector = datastoreUtil.toConnectorInfo(Optional.of(datastore));
        connector.getMetaConfig().put(Constants.CLUSTER_NAME, "35.197.29.117");

        try (Connection conn = datastoreUtil.getConnection(connector)) {
            String fieldsToIndex = StringUtils.join(fields, ",");
            String index =  String.format("create index if not exists %s on %s (%s)", fieldsToIndex.replace(",", "_")+"_"+entityName, dbName, fieldsToIndex);

            log.info("Index creation sql {}", index);
            try {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeQuery(index);
                }
            } catch (Exception exception){
                log.error("Exception occurred for index sql {} with message {}", index, ExceptionUtils.getStackTrace(exception));
            }
        }
    }

}
