package com.syncari.core.http.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.HttpSourceConfigInfo;
import com.syncari.connector.data.*;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.springframework.http.HttpHeaders;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HttpSourceNoPaginationGeneratorTest {
    private String response = StringUtils.join(
            "{\n",
            "    \"custom_field_options\": [\n",
            "        {\n",
            "            \"url\": \"https://something.zendesk.com/api/v2/ticket_fields/123/options/1.json\",\n",
            "            \"id\": 1,\n",
            "            \"name\": \"Company 1\",\n",
            "            \"raw_name\": \"Company 1\",\n",
            "            \"position\": 0,\n",
            "            \"value\": \"1\"\n",
            "        },\n",
            "        {\n",
            "            \"url\": \"https://something.zendesk.com/api/v2/ticket_fields/123/options/2.json\",\n",
            "            \"id\": 2,\n",
            "            \"name\": \"Company 2\",\n",
            "            \"raw_name\": \"Company 2\",\n",
            "            \"position\": 1,\n",
            "            \"value\": \"2\"\n",
            "        },\n",
            "        {\n",
            "            \"url\": \"https://something.zendesk.com/api/v2/ticket_fields/123/options/3.json\",\n",
            "            \"id\": 3,\n",
            "            \"name\": \"Company 3\",\n",
            "            \"raw_name\": \"Company 3\",\n",
            "            \"position\": 2,\n",
            "            \"value\": \"3\"\n",
            "        }\n",
            "    ],\n",
            "    \"next_page\": null,\n",
            "    \"previous_page\": null,\n",
            "    \"count\": 3\n",
            "}");

    @Test
    public void testDeser() throws JsonProcessingException {
        final HttpSourceNoPaginationGenerator generator = new HttpSourceNoPaginationGenerator();
        final HTTPSourceResult result = new HTTPSourceResult();
        final HttpSourceConfigInfo httpSource = new HttpSourceConfigInfo();
        httpSource.setRecordSelector("/custom_field_options");
        httpSource.setIdSelector("/id");
        result.setBodyString(response);
        result.setBody(new ObjectMapper().readTree(response));
        result.setHeaders(HttpHeaders.EMPTY);
        final ZonedDateTime callTime = ZonedDateTime.now();
        result.setCalledAt(callTime);
        final SyncRequest request = new SyncRequest();
        final EntitySchema entitySchema = new EntitySchema("options", "options");
        entitySchema.addField(new AttributeSchema("id", "string"));
        entitySchema.addField(new AttributeSchema("value", "string"));
        entitySchema.addField(new AttributeSchema("url", "string"));
        entitySchema.addField(new AttributeSchema("raw_name", "string"));
        entitySchema.addField(new AttributeSchema("name", "string"));
        entitySchema.addField(new AttributeSchema("position", "integer"));
        request.setEntitySchema(entitySchema);
        request.setWatermark(new WatermarkInfo()
                .setStart(callTime.toInstant().toEpochMilli() - 100000)
                //end wm less than call time
                .setEnd(callTime.toInstant().toEpochMilli() - 10)
        );
        final ConnectorInfo connector = new ConnectorInfo();
        connector.setId("123");
        request.setConnector(connector);
        final List<EntityData> records = generator.getRecords(request,
                result, httpSource);
        assertEquals(3, records.size());
        records.forEach(r -> assertEquals(request.getWatermark().getEnd(), r.getLastModified()));
    }
}