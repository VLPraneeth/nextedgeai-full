package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.syncari.connector.Constants;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.CreateFieldRequest;
import com.syncari.connector.data.DeleteFieldRequest;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.service.HubspotService;
import com.syncari.connector.service.SalesforceService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.IntegrationTest;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Category(IntegrationTest.class)
public class SchemaSyncTest extends AbstractSyncariTest {
    private Connector sfdcConnector;
    private Connector hubspotConnector;
    @Autowired
    ConnectorService synapseService;
    @Autowired
    EndSystemConfig config;
    @Autowired
    SchemaService schemaServiceBean;
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    SalesforceService salesforceService;
    @Autowired
    HubspotService hubspotService;
    @Autowired
    DataTransformer transformer;
    @Autowired
    ConnectorService connectorService;

    @Before
    public void setUp() {
        super.setUp();
        sfdcConnector = new Connector("sfdc1", synapseService.describe("salesforce").getId(), config.getSalesforceUrl(),
                config.getUser(), config.getPassword());
        sfdcConnector.getAuthConfig().setToken(config.getToken());
        sfdcConnector = synapseService.save(sfdcConnector);
        synapseService.authenticated(sfdcConnector.getId());

        hubspotConnector = new Connector("hubspot1", synapseService.describe("hubspot").getId(),
                "https://api.hubapi.com");
        hubspotConnector.getAuthConfig().setClientId(config.getHubspotTestClientId()).setClientSecret(config.getHubspotTestClientSecret()).setRefreshToken(config.getHubspotTestClientRefreshToken()).setExpiresIn("0");
        hubspotConnector = synapseService.save(hubspotConnector);
        hubspotConnector = synapseService.refreshAuthentication(hubspotConnector);
        hubspotConnector = synapseService.save(hubspotConnector);
        synapseService.authenticated(hubspotConnector.getId());
        synapseService.activate(hubspotConnector.getId());
    }

    @Ignore
    @Test
    public void createInSfdcSyncsToHubspot() throws Exception {
        Map<String, AttributeSchema> fieldMap = new HashMap<String, AttributeSchema>();
        Exception ex = null;
        try {
            int sizeInSfdc = getSizeInSfdc();

            fieldMap.put("auto_number_test__c", createFieldInSfdc("auto_number_test__c", "AutoNumber"));
//          AttributeSchema lookup_test__c = createFieldInSfdc("lookup_test__c", "Lookup Test", "Lookup");
            fieldMap.put("checkbox_test__c", createFieldInSfdc("checkbox_test__c", "Checkbox"));
            fieldMap.put("currency_test__c", createFieldInSfdc("currency_test__c", "Currency"));
            fieldMap.put("date_test__c", createFieldInSfdc("date_test__c", "Date"));
            fieldMap.put("date_time_test__c", createFieldInSfdc("date_time_test__c", "DateTime"));
            fieldMap.put("email_test__c", createFieldInSfdc("email_test__c", "Email"));
            fieldMap.put("number_test__c", createFieldInSfdc("number_test__c", "Number"));
            fieldMap.put("percent_test__c", createFieldInSfdc("percent_test__c", "Percent"));
            fieldMap.put("phone_test__c", createFieldInSfdc("phone_test__c", "Phone"));
            fieldMap.put("picklist_test__c", createFieldInSfdc("picklist_test__c", "Picklist"));
            fieldMap.put("picklist_multiselect_test__c", createFieldInSfdc("picklist_multiselect_test__c", "MultiselectPicklist"));
            fieldMap.put("text_test__c", createFieldInSfdc("text_test__c", "Text"));
            fieldMap.put("text_area_test__c", createFieldInSfdc("text_area_test__c", "TextArea"));
            fieldMap.put("text_area_long_test__c", createFieldInSfdc("text_area_long_test__c", "LongTextArea"));
            fieldMap.put("text_area_encrypted_test__c",
                    createFieldInSfdc("text_area_encrypted_test__c", "EncryptedText"));
            fieldMap.put("url_test__c", createFieldInSfdc("url_test__c", "Url"));

            // ====== Not supported in sfdc / or java client but document has it
            // fieldMap.put("time_test__c", createFieldInSfdc("time_test__c", "Time"));
            // fieldMap.put("text_area_rich_test__c", createFieldInSfdc("text_area_rich_test__c", "RichTextArea"));
            // fieldMap.put("html_test__c", createFieldInSfdc("html_test__c", "Html"));
            // fieldMap.put("geolocation_test__c", createFieldInSfdc("geolocation_test__c", "Geolocation"));
            // fieldMap.put("formula_test__c", createFieldInSfdc("formula_test__c", "Formula"));
            // fieldMap.put("summary_test__c", createFieldInSfdc("summary_test__c", "Summary")); // needs summaryForeignKey to be set
            
            assertEquals(sizeInSfdc + fieldMap.size(), getSizeInSfdc());
            int sizeInHubspot = getSizeInHubspot();

            schemaServiceBean.refreshSynapseSchema(sfdcConnector.getId());
            EntityDefinition contact = schemaServiceBean.getEntity(sfdcConnector.getId(), "Contact");
            for (AttributeDefinition attr : contact.getAttributes()) {
                if (fieldMap.containsKey(attr.getApiName())) {
                    EntityDefinition hubContact = schemaServiceBean.getEntity(hubspotConnector.getId(), "contact");
                    schemaServiceBean.createAttributeInSynapse(hubspotConnector.getId(), hubContact, attr);
                }
            }

            assertEquals(sizeInSfdc + fieldMap.size(), getSizeInSfdc());
            assertEquals(sizeInHubspot + fieldMap.size(), getSizeInHubspot());
        } catch(Exception e) {
            ex = e;
            log.error(e.getMessage(), e);
        } finally {
            Thread.sleep(5000);
            fieldMap.forEach((k, v) -> {
                if (v != null) {
                    log.info("deleting {}", k);
                    try {
                        deleteFieldInSfdc(k, fieldMap.get(k).getExternalId());
                    } catch (Exception e) {
                    }
                    try {
                        deleteFieldInHubspot(k);
                    } catch (Exception e) {
                    }
                }
            });
        }
        if(ex != null) throw ex;
    }

    // TODO duplicate fields created
    // TODO field created, deleted and recreated
    // TODO field created without metadata
    
    public AttributeSchema createFieldInSfdc(String apiName, String datatype) throws InterruptedException {
        AttributeSchema schema = new AttributeSchema();
        schema.setApiName(apiName);
        schema.setDataType(datatype);
        schema.setDisplayName(datatype + " Test");
        if("picklist".equalsIgnoreCase(datatype) || "MultiselectPicklist".equalsIgnoreCase(datatype)) {
            schema.getPicklistValues().add("test1");
            schema.getPicklistValues().add("test2");
        }
        return salesforceService
                .createField(new CreateFieldRequest(Constants.CONTACT, transformer.toConnectorInfo(sfdcConnector), schema));
    }

    public void deleteFieldInSfdc(String fieldName, String id) {
        DeleteFieldRequest deleteFieldRequest = new DeleteFieldRequest(transformer.toConnectorInfo(sfdcConnector),
                Constants.CONTACT, fieldName);
        deleteFieldRequest.setExternalFieldId(id);
        salesforceService.deleteField(deleteFieldRequest);
    }

    public void deleteFieldInHubspot(String fieldName) {
        hubspotService.deleteField(
                new DeleteFieldRequest(transformer.toConnectorInfo(hubspotConnector), "contact", fieldName));
    }

    public int getSizeInSfdc() throws InterruptedException {
        DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(sfdcConnector),
                List.of(Constants.CONTACT));
        Thread.sleep(5000);
        List<EntitySchema> entities = salesforceService.describeAll(request);
        assertTrue(entities.size() > 100);
        for (EntitySchema entitySchema : entities) {
            if(entitySchema.getApiName().equalsIgnoreCase(Constants.CONTACT)) {
                return entitySchema.getAttributes().size();
            }
        }
        return 0;
    }

    public int getSizeInHubspot() throws InterruptedException {
        DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(hubspotConnector),
                List.of("contact"));
        Thread.sleep(5000);
        List<EntitySchema> entities = hubspotService.describeAll(request);
        return entities.get(0).getAttributes().size();
    }

}
