package com.syncari.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.service.def.MetadataService;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.*;

@Component
public class TestHelper {

    private static final String BASE_RESOURCE_PATH = "src/test/resources/fixtures/salesforce/";

    public SyncRequest createSyncRequestForEntity(String entity, MetadataService service, ConnectorInfo connector) {
        try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + entity + ".json")) {
            ObjectMapper mapper = new ObjectMapper();
            SyncRequest request = mapper.readValue(fileStream, SyncRequest.class);

            EntitySchema schema = service.describe(new DescribeRequest(connector, entity)).get();
            schema.getAttributes().stream().forEach(x -> x.setStatus(Status.ACTIVE));
            request.setEntitySchema(schema);
            request.setEntitySchemaWithMappedFields(schema);

            Map<String, List<EntityData>> data = new HashMap<>();
            for (String key : request.getData().keySet()) {
                List<EntityData> value = request.getData().get(key);
                value.forEach(v -> {
                    if (v.has("Email")) {
                        v.addValue("Email", System.currentTimeMillis() + v.getValueAsString("Email"));
                    }
                    if(v.has("Datetime__c")) {
                		v.addValue("Datetime__c", ZonedDateTime.parse(request.getData().get(key).get(0).getValueAsString("Datetime__c")));
                	} else if (v.has("CloseDate")) {
                        DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
                        Calendar cal = GregorianCalendar.getInstance();
                        try {
                            cal.setTime(df.parse(request.getData().get(key).get(0).getValueAsString("CloseDate")));
                        } catch (Exception e) {

                        }
                	    v.addValue("CloseDate", cal);
                    }
                    if (StringUtils.isEmpty(v.getSyncariEntityId())) {
                        v.setSyncariEntityId("mocksyncariid_" + System.currentTimeMillis());
                    }
                });
				data.put(connector.getId(), value);
            }
            request.setData(data);
            request.setConnector(connector);
            return request;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    public static String getMock(final String mock) {
        return new String(Files.readAllBytes(Paths.get(TestHelper.class.getResource(mock).toURI())));
    }

    public static String getRandomString(){
        return UUID.randomUUID().toString().replace("-","");
    }
}
