package com.syncari.connector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.TestHelper;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.jira.JiraSeed;
import com.syncari.connector.jira.JiraService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class MockJiraServiceTest extends JiraService {

    @Autowired
    private ObjectMapper mapper;


    @Test
    public void shouldRemoveDuplicatedCreatedAt() throws JsonProcessingException {
        EntitySchema schema = JiraSeed.getSeedEntitySchema(JiraSeed.ISSUE);
        //This is property that Syncari defined
        assertTrue(schema.getField("created_at").get().isSystem());

        String isseSchemaResponse = TestHelper.getMock("/mocks/jira/jira_issue_schema.json");
        List row = mapper.readValue(isseSchemaResponse, List.class);
        super.extractRows(schema, row);

        assertFalse(schema.getAttributes().isEmpty());
        assertTrue(schema.hasField("created_at"));
        //Since was retrieved from Source, this is not longer defined by Syncari
        assertFalse(schema.getField("created_at").get().isSystem());
    }
}