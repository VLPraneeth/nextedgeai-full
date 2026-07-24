package com.syncari.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.service.SchemaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TestHelper {

    private static final String BASE_RESOURCE_PATH = "src/test/resources/fixtures/salesforce/";
    @Autowired
    DataTransformer transformer;
    @Autowired
    SchemaService schemaService;

    public SyncRequest createSyncRequestForEntity(String entity, Connector connector) {
        try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + entity + ".json")) {
            ObjectMapper mapper = new ObjectMapper();
            SyncRequest request = mapper.readValue(fileStream, SyncRequest.class);

            EntitySchema schema = new EntitySchema(entity, entity);
            EntityDefinition entityDefinition = schemaService.getEntity(connector.getId(), entity);
            List<AttributeDefinition> attributes = schemaService.getActiveAttributes(connector.getId(), entity);
            schema.setAttributes(
                    attributes.stream().map(a -> transformer.toAttrSchema(a, entityDefinition, connector))
                            .collect(Collectors.toList()));
            request.setEntitySchema(schema);

            Map<String, List<EntityData>> data = new HashMap<>();
            for (String key : request.getData().keySet()) {
                List<EntityData> value = request.getData().get(key);
                value.forEach(v -> {
                	if(v.has("Datetime__c")) {
                		v.addValue("Datetime__c", ZonedDateTime.parse(request.getData().get(key).get(0).getValueAsString("Datetime__c")));
                	}
                });
				data.put(connector.getId(), value);
            }
            request.setData(data);
            request.setConnector(transformer.toConnectorInfo(connector));
            return request;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
