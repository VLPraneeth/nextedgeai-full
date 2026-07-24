package com.syncari.karibu.rest.controllers;

import com.syncari.connector.Constants;
import com.syncari.connector.datastore.PostgresqlDatastoreService;
import com.syncari.core.DataTransformer;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_CONNECTOR;
import static com.syncari.core.security.Permissions.READ_STUDIO;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/mcp")
public class MCPController {
    @Autowired
    AppConfig appConfig;
    @Autowired
    PostgresqlDatastoreService service;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    DataTransformer transformer;

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/allTables")
    public String getTables() {
        String dbName = "";
        try{
            dbName = getDbName()+".";
        } catch (Exception e) {
        }
        List<String> results = new ArrayList<>();
        List<EntityDefinition> syncariEntities = schemaService.getSyncariEntities();
        for (EntityDefinition e : syncariEntities) {
            String tableName = dbName+(StringUtils.isBlank(e.getDataStoreName()) ? e.getApiName() : e.getDataStoreName());
            results.add(String.format("%s,%s,%s,%s", e.getId(), tableName, e.getApiName(), e.getDisplayName()));
        }
        // The results are a list of string, each line containing id,tablename,apiname,displayname
        return results.stream().collect(Collectors.joining(System.lineSeparator()));

    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/allFields")
    public String getFields(@RequestParam(value = "entity") String entity) {
        List<String> results = new ArrayList<>();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName(entity).get();
        for (AttributeDefinition a : syncariEntity.getAttributes()) {
            String columnName = (StringUtils.isBlank(a.getDataStoreName()) ? a.getApiName() : a.getDataStoreName());
            // The results are a list of string, each line containing id,tablename,apiname,displayname
            results.add(String.format("%s,%s,%s,%s", a.getId(), columnName, a.getDisplayName(), a.getDataType().getName()));
        }
        return results.stream().collect(Collectors.joining(System.lineSeparator()));
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/executeQuery")
    public String executeQuery(@RequestParam(value = "query") String query) {
        log.info("Query : {}", query);
        return service.executeQuery(query, transformer.toConnectorInfo(getDatastore()));
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/synapses")
    public String getSynapses() {
        List<String> results = new ArrayList<>();

        List<Connector> persisted = connectorService.listPublished();
        for (Connector connector : persisted) {
            results.add(String.format("%s,%s,%s", connector.getId(), connector.getMetadata().getName(), connector.getName()));
        }
        // The results are a list of string, each line containing synapseId,type,name
        return results.stream().collect(Collectors.joining(System.lineSeparator()));
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/entities/{synapseId}")
    public String getSynapseEntity(@PathVariable String synapseId) {
        List<String> results = new ArrayList<>();

        List<EntityDefinition> persisted = schemaService.getEntities(synapseId);
        for (EntityDefinition entity : persisted) {
            results.add(String.format("%s,%s,%s", entity.getId(), entity.getApiName(), entity.getDisplayName()));
        }
        // The results are a list of string, each line containing entityId,apiName,displayName
        return results.stream().collect(Collectors.joining(System.lineSeparator()));
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/entities/fields/{entityId}")
    public String getSynapseEntityFields(@PathVariable String entityId) {
        List<String> results = new ArrayList<>();

        List<AttributeDefinition> fields = schemaService.getEntity(entityId).getAttributes();
        for (AttributeDefinition field : fields) {
            results.add(String.format("%s,%s,%s", field.getId(), field.getApiName(), field.getDisplayName()));
        }
        // The results are a list of string, each line containing fieldId,apiName,displayName
        return results.stream().collect(Collectors.joining(System.lineSeparator()));
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/services")
    public String services() {
        List<String> results = new ArrayList<>();

        List<Connector> connectors = connectorService.listService();
        for (Connector connector : connectors) {
            results.add(String.format("%s,%s,%s", connector.getId(), connector.getMetadata().getName(), connector.getName()));
        }
        // The results are a list of string, each line containing serviceId,type,name
        return results.stream().collect(Collectors.joining(System.lineSeparator()));
    }
    
    private Connector getDatastore() {
        return connectorService.getSyncariDatastore().orElseThrow(()->new NotFoundException("NO_DATASTORE"));
    }

    private String getDbName() {
        return getDatastore().getValue(Constants.DATABASE_NAME);
    }
 }