package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.DatastoreService;
import com.syncari.core.service.SchemaService;
import com.syncari.dbm.customscripts.customer.util.DatastoreUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.sql.*;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j
public class SYN_15130_DeleteDataFromDataStore {
    public static final String SCHEMA_NAME = "schemaName";
    private final int QUERY_TIMEOUT = 30;
    // This is a postgres specific limit, but might make sense to limit all variables to less than this

    public static String COUNT_QUERY = "Select count(syncariid) as totalCount from %s";
    public static String ID_QUERY = "SELECT syncariid AS syncariid FROM %s OFFSET %s LIMIT %s";
    public static String DELETE_QUERY = "DELETE FROM %s where syncariid in (%s)";
    private static final int PAGE_SIZE = 1000;
    DatastoreUtil datastoreUtil = new DatastoreUtil();


    @ChangeSet(order = "001", id = "deleteDataFromDataStore", author = "sibin", runAlways = true)
    public void deleteDataFromDataStore(MongoTemplate template) {
    	SchemaService schemaService = MigrationContext.getSchemaService();
    	DatastoreService datastoreService = MigrationContext.getDatastoreService();
    	Connector datastore = datastoreService.findActiveDatastore()
                .orElseThrow(() -> new RuntimeException("Datastore connector missing"));
    	ConnectorInfo info = datastoreUtil.toConnectorInfo(Optional.of(datastore));
    	EntityRepo entityRepo = MigrationContext.getEntityRepo();
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        List<String> entityNames = new ArrayList<>();
        String en = System.getProperty("entityName");
        if(StringUtils.isBlank(en)) {
        	log.info("No entity name provided. Loading all entities of the instance");
        	Schema schema = schemaService.getSyncariSchema(true);
        	entityNames = schema.getEntities().stream().map(e -> e.getApiName()).collect(Collectors.toList());
        } else {
        	entityNames.add(en);
        }
        log.info("Entities to be processed {}", entityNames);
        for(String entityName: entityNames) {
        	EntityDefinition syncariEntity = schemaService.getSyncariEntityByName(entityName).orElse(null);
        	if(syncariEntity == null) {
        		log.error("Syncari entity for apiName {} not found", entityName);
        		continue;
        	}
        	var featureService = MigrationContext.getFeatureService();
        	if(featureService.isEnabled(Features.Datastore)) {
        		log.info("Datastore enabled");
        		log.info("Finding stale records from datastore");
        		long count = count(info, syncariEntity.getResolvedDataStoreName());
				log.info("Total record count for {} in datastore is {}", entityName, count);
				long offset = 0L;
				List<String> idsToBeDeleted = new ArrayList<>();
				while (offset < count) {
					List<String> idsFromDS = fetch(info, syncariEntity.getResolvedDataStoreName(), offset, PAGE_SIZE);
					idsFromDS.forEach(id -> {
                        Optional<EntityData> data = entityRepo.findById(syncariEntity, id);
                        data.ifPresentOrElse(d -> {
                            if (d.isDeleted()){
                                log.info("Id is deleted in Mongo db {}", d.getId());
                                idsToBeDeleted.add(id);
                            }
                        },()-> idsToBeDeleted.add(id));
						
					});
					offset+=PAGE_SIZE;
				}
				log.info("Number of Ids to be deleted for entity {} is {}", entityName, idsToBeDeleted.size());
				log.info("Ids to be deleted for entity {} are {}", entityName, idsToBeDeleted);
				if(!dryRun) {
					if(!idsToBeDeleted.isEmpty()) {
						ListUtils.partition(idsToBeDeleted, 100).forEach(idsBatch -> {
							log.info("Deleting for entity {} Ids {} in this batch", entityName, idsBatch);
							delete(info, syncariEntity.getResolvedDataStoreName(), idsBatch);
						});
					}
				}
        	}
        }
    }

    protected List<Map<String, Object>> executeDmlQuery(ConnectorInfo connector, String sql, Map<String, String> fieldNames){
        List<Map<String, Object>> extractedVals = new ArrayList<>();
        try (Connection conn = datastoreUtil.getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                log.info("SQL to be executed is {} and timeout is {}" , sql, QUERY_TIMEOUT);
                stmt.setQueryTimeout(QUERY_TIMEOUT);
                try(ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        Map<String, Object> extractedVal = new HashMap<>();
                        Set<Entry<String, String>> fielNameAndDataType = fieldNames.entrySet();
                        fielNameAndDataType.forEach(x -> {
                            String fieldName = x.getKey();
                            String  dataType = x.getValue();
                            Object val = extractVal(rs, dataType, fieldName);
                            if (null != val){
                                extractedVal.put(fieldName, val);
                            }
                        });
                        extractedVals.add(extractedVal);
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return extractedVals;
    }
    
    protected Object extractVal(ResultSet rs, String dataType, String fieldName) {
        try{
            switch (dataType) {
                case "date":
                    return rs.getDate(fieldName);
                case "datetime":{
                    final Timestamp timestamp = rs.getTimestamp(fieldName);
                    return timestamp==null? null :ZonedDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
                }
                case "timestamp": {
                    final Timestamp timestamp = rs.getTimestamp(fieldName);
                    return timestamp==null? null :timestamp.toInstant();
                }
                case "string": {
                    return rs.getString(fieldName);
                }
                default:
                    return rs.getObject(fieldName);
            }
        }catch (SQLException e){
            log.error("Exception occurred {} and cause is {}", e.getMessage(), e.getCause());
        }
        return null;
    }
    
    public long count(ConnectorInfo connectorInfo , String datastoreName){
        String query = String.format(COUNT_QUERY, getTableName(datastoreName,connectorInfo));
        // totalCount is an alias in Count query
        List<Map<String, Object>> result =  executeDmlQuery(connectorInfo, query, Map.of("totalCount","long"));
        if (CollectionUtils.isNotEmpty(result)){
            return (Long)result.stream().findFirst().get().get("totalCount");
        }
        return 0;
    }
    
    public List<String> fetch(ConnectorInfo connectorInfo , String datastoreName, long offset, long limit){
        String query = String.format(ID_QUERY, getTableName(datastoreName,connectorInfo), offset, limit);
        List<Map<String, Object>> result =  executeDmlQuery(connectorInfo, query, Map.of("syncariid","string"));
        List<String> ids = List.of();
        if (CollectionUtils.isNotEmpty(result)){
            ids = result.stream().map(entry -> (String) entry.get("syncariid")).collect(Collectors.toList());
        }
        return ids;
    }
    
    public void delete(ConnectorInfo connectorInfo , String datastoreName, List<String> ids){
    	if(CollectionUtils.isEmpty(ids)) {
    		return;
    	}
		String query = String.format(DELETE_QUERY, getTableName(datastoreName, connectorInfo), ids.stream().map(s -> {
			return "'" + s + "'";
		}).collect(Collectors.joining(", ")));
        try (Connection conn = datastoreUtil.getConnection(connectorInfo)) {
			try (Statement stmt = conn.createStatement()) {
				log.info("SQL to be executed is {} and timeout is {}", query, QUERY_TIMEOUT);
				stmt.setQueryTimeout(QUERY_TIMEOUT);
				int count = stmt.executeUpdate(query);
				log.info("{} records removed from datastore for ", count);
			}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    protected String getTableName(String entity, ConnectorInfo connector) {
        StringBuilder table = new StringBuilder(getEscapeChar());
        table.append(getCased(entity)).append(getEscapeChar());
        return !StringUtils.isBlank(getSchemaName(connector)) ?  getSchemaName(connector) + "." + table :  table.toString();
    }

    protected static String getValue(ConnectorInfo connector, String key) {
        Object schema = connector.getMetaConfig().get(key);
        return schema == null ? "" : schema.toString();
    }
    
    protected String getEscapeChar() {
        return "\"";
    }
    
    protected String getCased(String name) {
        return StringUtils.isBlank(name) ? name : name.toLowerCase();
    }
    
    protected String getSchemaName(ConnectorInfo connector) {
        return getValue(connector, SCHEMA_NAME);
    }
    
}
