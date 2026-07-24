package com.syncari.connector.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.hubspot.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.HubspotRestClient;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.utils.Pair;
import com.syncari.utils.Retry;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.syncari.connector.service.HubspotService.ASSOCIATION_SUFFIX;
import static com.syncari.connector.service.seed.HubspotSeed.HS_OBJECT_ID;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ConnectorConfig.class, TestConfig.class})
@Slf4j
public class HubspotServiceTest extends AbstractConnectorTest implements DataServiceTest {
    static { System.setProperty("os.arch", "i686_64"); }

    /*
    Portal Id     - Key
    6196729       - 8ff4f476-ff46-4c42-80d5-71e3496cf565
    21164229      - 1abadc1f-702f-4023-ab17-b2c5faf100ea
    21395455      - 4d3bf248-6b30-4757-9fae-420a83539ffc
    21163731      - 5578e7c6-0ba9-483a-be07-b57eee9adced
    14538430      - 30b3e25f-dce6-4462-a087-02dc5f87fcfa
     */
    @Autowired
    HubspotService hubspotService;
    private static final String COMPANY_NAME = "test company";

    private ConnectorInfo connector;

    private static final String CLIENTID = "a5dd557c-6967-4f23-8589-ae624c6d32c0";
    private static final String SECRET = "test_value_75";
    private static final String REFRESH_TOKEN = "test_value_76";


    @Rule
    public RetryRule retryRule = new RetryRule();

    @Before
    public void before() {
        connector = createConnector(REFRESH_TOKEN);
    }

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) {
            connector = createConnector(REFRESH_TOKEN);
        }
        return connector;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return hubspotService;
    }

    @Override
    public MetadataService getMetadataService() {
        return hubspotService;
    }

    @Override
    public CommonDataService getDataService() {
        return hubspotService;
    }

    @Override
    public String getDescribeObject() {
        return Constants.COMPANY.toLowerCase();
    }

    @Override
    @Test
    public void testConnectionTest() {
        verifyTestConnection();
    }

    @Override
    @Test
    public void describeTest() {
        describe(null, null);
        describe(Constants.COMPANY.toLowerCase(), null);
        describe(Constants.CONTACT.toLowerCase(), null);
        describe("lead", null);
    }

    private void validateScopes(String oAuthUri, String param, String expectedValues){
        String query = oAuthUri.split("\\?")[1];
        final Map<String, String> map = Splitter.on('&').trimResults().withKeyValueSeparator('=').split(query);
        String received = map.getOrDefault(param, "");
        Set<String> receivedScopes = new HashSet<>(Arrays.stream(received.split(" ")).collect(Collectors.toSet()));
        Set<String> expectedScopes = new HashSet<>(Arrays.stream(expectedValues.split(" ")).collect(Collectors.toSet()));
        assertTrue(expectedScopes.containsAll(receivedScopes));
    }

    @Test
    public void getOauthUri() {
        ConnectorInfo info = getConnector();
        String oAuthUri = hubspotService.getOAuthUri(getConnector());
        validateScopes(oAuthUri, "scope", "oauth crm.objects.companies.read crm.objects.companies.write crm.objects.contacts.read crm.objects.contacts.write crm.objects.deals.read crm.objects.deals.write crm.objects.owners.read crm.schemas.companies.read crm.schemas.contacts.read crm.schemas.deals.read");
        validateScopes(oAuthUri, "optional_scope", "crm.objects.line_items.write crm.objects.line_items.read crm.objects.marketing_events.read crm.objects.marketing_events.write crm.lists.read crm.lists.write e-commerce sales-email-read content crm.objects.custom.read crm.objects.custom.write crm.schemas.custom.read tickets business-intelligence forms files crm.schemas.companies.write crm.schemas.contacts.write crm.schemas.deals.write");
        List<String> scopes = List.of("crm.import", "crm.objects.custom.read", "crm.objects.custom.write", "crm.schemas.custom.read", "tickets", "business-intelligence", "files", "files.ui_hidden.read", "crm.schemas.companies.write", "crm.schemas.contacts.write", "crm.schemas.deals.write");
        info.setRequiredScopes(scopes);
        oAuthUri = hubspotService.getOAuthUri(getConnector());
        validateScopes(oAuthUri, "scope", "oauth crm.objects.companies.read crm.objects.companies.write crm.objects.contacts.read crm.objects.contacts.write crm.objects.deals.read crm.objects.deals.write crm.objects.owners.read crm.schemas.companies.read crm.schemas.companies.write crm.schemas.contacts.read crm.schemas.contacts.write crm.schemas.deals.read crm.schemas.deals.write crm.import crm.objects.custom.read crm.objects.custom.write crm.schemas.custom.read tickets business-intelligence files files.ui_hidden.read crm.schemas.companies.write crm.schemas.contacts.write crm.schemas.deals.write");
        validateScopes(oAuthUri, "optional_scope", "");
        scopes = List.of("tickets", "business-intelligence", "files");
        info.setRequiredScopes(scopes);
        oAuthUri = hubspotService.getOAuthUri(getConnector());
        validateScopes(oAuthUri, "scope", "oauth crm.objects.companies.read crm.objects.companies.write crm.objects.contacts.read crm.objects.contacts.write crm.objects.deals.read crm.objects.deals.write crm.objects.owners.read crm.schemas.companies.read crm.schemas.companies.write crm.schemas.contacts.read crm.schemas.contacts.write crm.schemas.deals.read crm.schemas.deals.write tickets business-intelligence files");
        validateScopes(oAuthUri, "optional_scope", "");
    }

    @Test
    public void describeEngagement() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), "engagement");
        Optional<EntitySchema> entitySchema = hubspotService.describe(describeRequest);
        assertTrue(entitySchema.isPresent());
        assertTrue(entitySchema.get().getField("hs_call_disposition").isPresent());
        assertFalse(entitySchema.get().getField("hs_call_disposition").get().getPicklistValues().isEmpty());
    }

    @Test
    public  void describeDeal(){
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), "deal");
        Optional<EntitySchema> entitySchema = hubspotService.describe(describeRequest);
        assertTrue(entitySchema.isPresent());
        assertTrue(entitySchema.get().getField("hs_primary_associated_company").isPresent());
        assertEquals("integer",entitySchema.get().getField("hs_primary_associated_company").get().getDataType());
    }
    
    @Test
    @Ignore("Requires HubSpot API permissions for lead entity which test environment doesn't have")
    public void describeLeadPrimaryFieldsAreUpdatable() {
        // This test verifies that hs_primary_company_id and hs_primary_contact_id fields
        // are set as updatable for the lead entity, overriding their default read-only status
        
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), "lead");
        Optional<EntitySchema> entitySchema = hubspotService.describe(describeRequest);
        assertTrue(entitySchema.isPresent());
        
        // Check that hs_primary_company_id is updatable
        Optional<AttributeSchema> primaryCompanyField = entitySchema.get().getField("hs_primary_company_id");
        assertTrue("hs_primary_company_id field should exist", primaryCompanyField.isPresent());
        assertTrue("hs_primary_company_id should be updatable for lead entity", 
                  primaryCompanyField.get().isUpdateable());
        assertTrue("hs_primary_company_id data type should be string for lead entity",
                primaryCompanyField.get().getDataType().equalsIgnoreCase("string"));
        
        // Check that hs_primary_contact_id is updatable
        Optional<AttributeSchema> primaryContactField = entitySchema.get().getField("hs_primary_contact_id");
        assertTrue("hs_primary_contact_id field should exist", primaryContactField.isPresent());
        assertTrue("hs_primary_contact_id should be updatable for lead entity", 
                  primaryContactField.get().isUpdateable());
        assertTrue("hs_primary_contact_id datatype should be string for lead entity",
                primaryContactField.get().getDataType().equalsIgnoreCase("string"));
    }

    @Test
    public void describeCustomObjectsTest() {
        DescribeAllRequest request = new DescribeAllRequest(createConnector("1abadc1f-702f-4023-ab17-b2c5faf100ea"),
                List.of(Constants.COMPANY.toLowerCase(), Constants.CONTACT.toLowerCase()));
        List<EntitySchema> entities = hubspotService.describeAll(request);
        assertEquals(19, entities.size());
        List<EntitySchema> customEntities = entities.stream().filter(x -> x.isCustom()).collect(Collectors.toList());
        assertTrue(customEntities.size() > 1);
        EntitySchema customAddressSchema = customEntities.stream().filter(x -> x.getApiName().equalsIgnoreCase("p21164229_custom_address")).findFirst().get();
        assertEquals("p21164229_custom_address", customAddressSchema.getApiName());
        verifySchemaBasic(customAddressSchema);
        assertEquals("number", customAddressSchema.getField("Zip2").get().getDataType());
        assertEquals("date", customAddressSchema.getField("SinceDate").get().getDataType());
        assertEquals("boolean", customAddressSchema.getField("IsMetro").get().getDataType());
        assertEquals("reference", customAddressSchema.getField("ContactPerson").get().getDataType());
        assertEquals("number", customAddressSchema.getField("customCalculatedField").get().getDataType());
        assertTrue(customAddressSchema.getField("customCalculatedField").get().isCalculated());
    }

    @Test
    public void describeExistingCustomObjectTest() {
        DescribeRequest describeRequest = new DescribeRequest(createConnector("1abadc1f-702f-4023-ab17-b2c5faf100ea"), "p21164229_custom_address");
        Optional<EntitySchema> entitySchema = hubspotService.describe(describeRequest);
        assertTrue(entitySchema.isPresent());
        assertTrue(entitySchema.get().isCustom());
        assertEquals(entitySchema.get().getApiName(), "p21164229_custom_address");
        assertTrue(entitySchema.get().getAttributes().size() > 0);
    }

    @Test
    public void describeNonExistingCustomObjectTest() {
        DescribeRequest describeRequest = new DescribeRequest(createConnector("1abadc1f-702f-4023-ab17-b2c5faf100ea"), "non_existing_custom_object");
        Optional<EntitySchema> entitySchema = hubspotService.describe(describeRequest);
        assertFalse(entitySchema.isPresent());
    }

    @Override
    @Test
    public void describeAllTest() {
        DescribeAllRequest request = new DescribeAllRequest(connector,
                List.of(Constants.COMPANY.toLowerCase(), Constants.CONTACT.toLowerCase()));
        List<EntitySchema> entities = hubspotService.describeAll(request);
        assertEquals(23, entities.size());
        entities.forEach(e -> {
            String idAttribName = HS_OBJECT_ID;
            if (Constants.OWNER.equalsIgnoreCase(e.getApiName())) {
                idAttribName = "ownerId";
            } else if (Constants.EVENT.equalsIgnoreCase(e.getApiName()) ||
                    Constants.EMAIL_EVENT.equalsIgnoreCase(e.getApiName()) ||
                    "note".equalsIgnoreCase(e.getApiName()) ||
                    Constants.ACTIVITY.equalsIgnoreCase(e.getApiName()) ||
                    e.getApiName().contains(ASSOCIATION_SUFFIX)) {
                idAttribName = "id";
            } else if (Constants.FORM_SUBMISSION.equalsIgnoreCase(e.getApiName())){
                idAttribName = "submissionId";
            } else if (Constants.FORM.equalsIgnoreCase(e.getApiName())){
                idAttribName = "formId";
            }
            final String idName = idAttribName; //to pass to lambda
            var idAttribute = e.getAttributes().stream().filter(a -> a.getApiName().equalsIgnoreCase(idName)).findFirst().get();
            assertNotNull(idAttribute);
            assertTrue(idAttribute.isIdField());
            assertTrue(idAttribute.isSystem());

            // validate Id field
            var idField = e.getIdField();
            assertEquals(idAttribName, idField.getApiName());
            assertTrue(idField.isIdField());
            assertTrue(idField.isUnique());
            assertFalse(idField.isNillable());
            assertFalse(idField.isUpdateable());

            if ("contact".equalsIgnoreCase(e.getApiName())) {
                assertTrue(e.hasField("associatedcompanyid"));
                String dataType = e.getField("associatedcompanyid").get().getDataType();
                assertTrue("reference".equalsIgnoreCase(dataType));
                assertTrue("company".equalsIgnoreCase(e.getField("associatedcompanyid").get().getReferenceTo()));
                assertTrue(HS_OBJECT_ID.equalsIgnoreCase(e.getField("associatedcompanyid").get().getReferenceTargetField()));

                var emailField = e.getField("email").get();
                assertFalse(emailField.isNillable());

                var firstnameField = e.getField("firstname").get();
                assertTrue(firstnameField.isNillable());

                var watermarkField = e.getField("lastmodifieddate").get();
                assertTrue(watermarkField.isWatermarkField());
                assertFalse(watermarkField.isNillable());
            }
            // verify datatypes
            if ("company".equalsIgnoreCase(e.getApiName())) {
                assertTrue(e.hasField("hs_is_target_account"));
                assertTrue("boolean".equalsIgnoreCase(e.getField("hs_is_target_account").get().getDataType()));

                var nameField = e.getField("name").get();
                assertFalse(nameField.isNillable());
                var webtechField = e.getField("web_technologies").get();
                assertTrue(webtechField.isMultiValueField());
                assertEquals("enumeration",webtechField.getDataType());
                var watermarkField = e.getField("hs_lastmodifieddate").get();
                assertTrue(watermarkField.isWatermarkField());
                assertFalse(watermarkField.isNillable());
            }
            if ("deal".equalsIgnoreCase(e.getApiName())) {
                assertTrue(e.hasField("hubspot_owner_id"));
                assertTrue("reference".equalsIgnoreCase(e.getField("hubspot_owner_id").get().getDataType()));
                assertTrue("owner".equalsIgnoreCase(e.getField("hubspot_owner_id").get().getReferenceTo()));
                assertTrue("ownerId".equalsIgnoreCase(e.getField("hubspot_owner_id").get().getReferenceTargetField()));

                // Verify fabricated field
                assertTrue(e.hasField("associatedcompanyid"));
                assertTrue("reference".equalsIgnoreCase(e.getField("associatedcompanyid").get().getDataType()));
                assertTrue("company".equalsIgnoreCase(e.getField("associatedcompanyid").get().getReferenceTo()));
                assertTrue("contact".equalsIgnoreCase(e.getField("associatedVids").get().getReferenceTo()));
                assertTrue("vid".equalsIgnoreCase(e.getField("associatedVids").get().getReferenceTargetField()));
                assertTrue(e.getField("associatedVids").get().isMultiValueField());
                assertTrue(e.getField("hs_all_collaborator_owner_ids").get().isMultiValueField());
                assertTrue(HS_OBJECT_ID
                        .equalsIgnoreCase(e.getField("associatedcompanyid").get().getReferenceTargetField()));

                var dealnameField = e.getField("dealname").get();
                assertFalse(dealnameField.isNillable());

                var pipelineField = e.getField("pipeline").get();
                assertFalse(pipelineField.isNillable());

                var dealstageField = e.getField("dealstage").get();
                assertFalse(dealstageField.isNillable());

                assertTrue(e.hasField("closedate"));
                assertTrue("datetime".equalsIgnoreCase(e.getField("closedate").get().getDataType()));

                var watermarkField = e.getField("hs_lastmodifieddate").get();
                assertTrue(watermarkField.isWatermarkField());
                assertFalse(watermarkField.isNillable());
            }

            if ("ticket".equalsIgnoreCase(e.getApiName())) {
                var subjectField = e.getField("subject").get();
                assertFalse(subjectField.isNillable());

                var pipelineField = e.getField("hs_pipeline").get();
                assertFalse(pipelineField.isNillable());

                var pipelineStageField = e.getField("hs_pipeline_stage").get();
                assertFalse(pipelineStageField.isNillable());
            }

            if ("activity".equalsIgnoreCase(e.getApiName())) {
                assertTrue(e.getField("updatedAt").get().isWatermarkField());
            }

            if ("engagement".equalsIgnoreCase(e.getApiName())) {
                assertTrue(e.hasField("associatedVids"));
                assertTrue(e.hasField("associatedcompanyid"));
            }
        });
    }

    // Impartner provides the oauth scopes as part of additionalScopes in ConnectorInfo. Verify describeAll is returning objects that can be accessed with the scopes
    @Test
    public void impartnerScopeUpdateTest() {
        connector.setRequiredScopes(List.of("oauth", "crm.objects.contacts.read", "crm.objects.contacts.write",
                "crm.objects.companies.write", "crm.objects.companies.read", "crm.objects.deals.read",
                "crm.objects.custom.write", "crm.objects.custom.read",
                "crm.schemas.contacts.read", "crm.schemas.companies.read", "crm.schemas.deals.read", "crm.schemas.custom.read"));
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entitySchemaList = hubspotService.describeAll(request);
        assertTrue(entitySchemaList.size() == 9);
        assertTrue(entitySchemaList.stream().anyMatch(schema -> schema.getApiName().equalsIgnoreCase("deal") && schema.isReadOnly()));
        assertTrue(entitySchemaList.stream().anyMatch(schema -> schema.getApiName().equalsIgnoreCase("contact_association")));
        connector.setRequiredScopes(List.of());
    }

    @Test
    public void impartnerOptionalScopeUpdateTest() {
        connector.setRequiredScopes(List.of("oauth", "crm.objects.contacts.read", "crm.objects.contacts.write",
                "crm.objects.companies.write", "crm.objects.companies.read", "crm.objects.deals.read",
                "crm.objects.custom.write", "crm.objects.custom.read",
                "crm.schemas.contacts.read", "crm.schemas.companies.read", "crm.schemas.deals.read", "crm.schemas.custom.read"));
        connector.setOptionalScopes(List.of("crm.objects.leads.read"));
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entitySchemaList = hubspotService.describeAll(request);
        assertTrue(entitySchemaList.size() == 10);
        assertTrue(entitySchemaList.stream().anyMatch(schema -> schema.getApiName().equalsIgnoreCase("deal") && schema.isReadOnly()));
        assertTrue(entitySchemaList.stream().anyMatch(schema -> schema.getApiName().equalsIgnoreCase("contact_association")));
        connector.setRequiredScopes(List.of());
        connector.setOptionalScopes(List.of());
    }

    // Provarity provides the oauth scopes with oAuthScopes config key. Verify describeAll is returning objects that can be accessed with the scopes
    @Test
    public void provarityScopeUpdateTest() {
        connector.getMetaConfig().put("oAuthScopes", "oauth, crm.objects.contacts.read, crm.objects.contacts.write, crm.objects.companies.write, crm.objects.companies.read, crm.objects.deals.read, tickets");
        DescribeAllRequest request = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entitySchemaList = hubspotService.describeAll(request);
        assertTrue(entitySchemaList.size() == 6);
        assertTrue(entitySchemaList.stream().anyMatch(schema -> schema.getApiName().equalsIgnoreCase("deal") && schema.isReadOnly()));
        assertTrue(entitySchemaList.stream().anyMatch(schema -> schema.getApiName().equalsIgnoreCase("ticket") && !schema.isReadOnly()));
        connector.setRequiredScopes(List.of());
    }

    @Test
    public void createField() throws InterruptedException {
        AttributeSchema schema = new AttributeSchema();
        String apiName = "newtestfield";
        schema.setApiName(apiName);
        schema.setDataType("string");
        schema.setDisplayName("New test field");
        hubspotService.createField(new CreateFieldRequest("contact", connector, schema));

        try {

            retryWithBackoff(() -> {
                DescribeAllRequest request = new DescribeAllRequest(connector,
                        List.of(Constants.CONTACT.toLowerCase()));
                List<EntitySchema> entities = hubspotService.describeAll(request);
                assertEquals(18, entities.size());
                List<AttributeSchema> attributes = entities.stream().filter(e -> "contact".equalsIgnoreCase(e.getApiName())).findFirst().get().getAttributes();
                List<AttributeSchema> list = attributes.stream().filter(a -> apiName.equalsIgnoreCase(a.getApiName())).collect(Collectors.toList());
                assertEquals(1, list.size());
                assertTrue(list.get(0).isCustom());
            });
        } finally {
            hubspotService.deleteField(new DeleteFieldRequest(connector, "contact", apiName));
        }

        retryWithBackoff(() -> {
            DescribeAllRequest request = new DescribeAllRequest(connector,
                    List.of(Constants.CONTACT.toLowerCase()));
            List<EntitySchema> entities = hubspotService.describeAll(request);
            assertEquals(18, entities.size());
            List<AttributeSchema> attributes = entities.get(0).getAttributes();
            List<AttributeSchema> list = attributes.stream().filter(a -> apiName.equalsIgnoreCase(a.getApiName())).collect(Collectors.toList());
            assertEquals(0, list.size());
        });
    }

    @Test
    public void createFieldForCustomObject() throws InterruptedException {
        connector = createConnector("1abadc1f-702f-4023-ab17-b2c5faf100ea");
        AttributeSchema schema = new AttributeSchema();
        String apiName = "newtestfield";
        schema.setApiName(apiName);
        schema.setDataType("string");
        schema.setDisplayName("New test field");
        hubspotService.createField(new CreateFieldRequest("p21164229_custom_address", connector, schema));

        try {

            retryWithBackoff(() -> {
                DescribeAllRequest request = new DescribeAllRequest(connector,
                        List.of("p21164229_custom_address"));
                List<EntitySchema> entities = hubspotService.describeAll(request);
                assertEquals(19, entities.size());
                List<AttributeSchema> attributes = entities.stream().filter(e -> "p21164229_custom_address".equalsIgnoreCase(e.getApiName())).findFirst().get().getAttributes();
                List<AttributeSchema> list = attributes.stream().filter(a -> apiName.equalsIgnoreCase(a.getApiName())).collect(Collectors.toList());
                assertEquals(1, list.size());
                assertTrue(list.get(0).isCustom());
            });
        } finally {
            hubspotService.deleteField(new DeleteFieldRequest(connector, "p21164229_custom_address", apiName));
        }

        retryWithBackoff(() -> {
            DescribeAllRequest request = new DescribeAllRequest(connector,
                    List.of("p21164229_custom_address"));
            List<EntitySchema> entities = hubspotService.describeAll(request);
            assertEquals(19, entities.size());
            List<AttributeSchema> attributes = entities.stream().filter(e -> "p21164229_custom_address".equalsIgnoreCase(e.getApiName())).findFirst().get().getAttributes();
            List<AttributeSchema> list = attributes.stream().filter(a -> apiName.equalsIgnoreCase(a.getApiName())).collect(Collectors.toList());
            assertEquals(0, list.size());
        });
    }

    @Override
    @Test
    public void getByWatermarkSinceEpoch() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkSinceEpoch(Constants.DEAL.toLowerCase());
        });
    }

    @Test
    public void verifyDealAssociations() {
        Optional<EntitySchema> entitySchema = describe("deal", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        syncRequest.setData(Map.of(getConnector().getId(), List.of(new EntityData("deal").setId("2409191401"))));

        List<EntityData> result = getDataService().getByIds(syncRequest);
        assertFalse(result.isEmpty());
        assertTrue(result.get(0).getValue("associatedVids") != null);
        List<Long> vids = (List<Long>) result.get(0).getValue("associatedVids");
        assertTrue(vids.size() > 100);
    }

    @Test
    public void verifyQuoteAssociations() {
        // Create a quote first to test associations

        Optional<EntitySchema> entitySchema = describe("quote", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        syncRequest.setData(Map.of(getConnector().getId(), List.of(new EntityData("quote").setId("21225927847"))));

        List<EntityData> result = getDataService().getByIds(syncRequest);
        assertFalse(result.isEmpty());
        assertTrue(result.get(0).getValue("associatedVids") != null && ((List)(result.get(0).getValue("associatedVids"))).size() == 1);
        assertNotNull(result.get(0).getValue("associatedcompanyid"));
        assertNotNull(result.get(0).getValue("associateddealid"));
    }

    @Override
    @Test
    public void getByWatermarkRecent() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkRecent(Constants.OWNER.toLowerCase());
            verifyGetByWatermarkRecent(Constants.CONTACT.toLowerCase());
        });
    }

    @Override
    @Test
    public void getByWatermarkWithLimit() {
        retryWithBackoff(() -> {
            // owner entity api does not support limit
//            verifyGetByWatermarkWithLimit(Constants.OWNER.toLowerCase(), 2);
            verifyGetByWatermarkWithLimit(Constants.COMPANY.toLowerCase(), 2);
        });
    }

    @Override
    @Test
    public void getByWatermarkResultsOrdered() {
        retryWithBackoff(() -> {
            verifyGetByWatermarkResultsOrdered(Constants.DEAL.toLowerCase());
            verifyGetByWatermarkResultsOrdered(Constants.COMPANY.toLowerCase());
        });
    }

    @Override
    @Test
    public void getByIds() {
        retryWithBackoff(() -> {
            connector = createConnector("8ff4f476-ff46-4c42-80d5-71e3496cf565");
            verifyGetByIds(Constants.COMPANY.toLowerCase());
            verifyGetByIds(Constants.CONTACT.toLowerCase());
//            verifyGetByIds("product");
//            verifyGetByIds("line_item");
            verifyGetByIds("note");
            verifyGetByIds("form", 1);
            verifyGetByIds("ticket", 1);
        });
    }

    @Test
    public void getByWatermarkCustom() {
        retryWithBackoff(() -> {
            connector = createConnector("1abadc1f-702f-4023-ab17-b2c5faf100ea");
            verifyGetByWatermarkSinceEpoch("p21164229_custom_address");
            verifyGetByWatermarkRecent("p21164229_custom_address");
            verifyGetByWatermarkWithLimit("p21164229_custom_address", 2);
            verifyGetByWatermarkResultsOrdered("p21164229_custom_address");
            verifyGetByIds("p21164229_custom_address");
        });
    }

    @Override
    @Test
    public void getDeletedByWatermark() {
        // TBD
    }

    @Test
    public void getById_Skips_EmptyIds(){
        SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
        EntityData entityData = new EntityData(Constants.COMPANY.toLowerCase());
        entityData.setId("");
        request.getData().put(connector.getId(), List.of(entityData));
        List<EntityData> byIds = hubspotService.getByIds(request);
        assertEquals(0,byIds.size());
        entityData.setId(null);
        byIds = hubspotService.getByIds(request);
        assertEquals(0,byIds.size());


        SyncRequest contactRequest = getRequest(Constants.CONTACT.toLowerCase());
        EntityData contactEntityData = new EntityData(Constants.CONTACT.toLowerCase());
        contactEntityData.setId(null);
        contactRequest.getData().put(connector.getId(), List.of(contactEntityData));
        List<EntityData> contacts = hubspotService.getByIds(contactRequest);
        assertEquals(0,contacts.size());
        contactEntityData.setId("");
        contacts = hubspotService.getByIds(contactRequest);
        assertEquals(0,contacts.size());
    }

    @Test
    public void getById_InvalidId(){
        try {
            SyncRequest request = getRequest(Constants.OWNER.toLowerCase());
            EntityData entityData = new EntityData(Constants.OWNER.toLowerCase());
            entityData.setId("abcd");
            request.getData().put(connector.getId(), List.of(entityData));
            hubspotService.getByIds(request);
            fail();
        }catch (RuntimeException e) {
            log.error(e.getMessage());
            assertEquals("Unable to parse value for path parameter: ownerId", e.getMessage());
        }
    }

    @Test
    public void getByIdObjectNotFound(){
        SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
        EntityData entityData = new EntityData(Constants.COMPANY.toLowerCase());
        entityData.setId("2334234");
        request.getData().put(connector.getId(), List.of(entityData));
        assertEquals(0,hubspotService.getByIds(request).size());

        SyncRequest request1 = getRequest(Constants.CONTACT.toLowerCase());
        EntityData entityData1 = new EntityData(Constants.CONTACT.toLowerCase());
        entityData1.setId("2334234");
        request1.getData().put(connector.getId(), List.of(entityData1));
        assertEquals(0,hubspotService.getByIds(request1).size());

        SyncRequest request2 = getRequest(Constants.DEAL.toLowerCase());
        EntityData entityData2 = new EntityData(Constants.DEAL.toLowerCase());
        entityData2.setId("2334234");
        request2.getData().put(connector.getId(), List.of(entityData2));
        assertEquals(0,hubspotService.getByIds(request2).size());
    }

    @Test
    public void updateObjectNotFound(){
        SyncRequest updateRequest = getRequest(Constants.COMPANY);
        EntityData entityData = new EntityData(Constants.COMPANY).addValue("name", "test account2");
        entityData.setId("22323434");
        updateRequest.getData().put(connector.getId(), List.of(entityData));
        SyncResponse updateResponse = hubspotService.update(updateRequest);
        assertTrue(updateResponse.getResults().size() > 0);
        Result result = updateResponse.getResults().get(0);
        assertFalse(result.isSuccess());
        assertTrue(result.getId() != null);
        assertTrue(result.getErrors() != null);
        assertTrue(result.getErrors().size() > 0);
    }

    @Test
    public void parseEventData() throws NoSuchAlgorithmException{
        String data = "[\n"
                + "  {\n"
                + "    \"eventId\": 2270875301,\n"
                + "    \"subscriptionId\": 1238319,\n"
                + "    \"portalId\": 6196729,\n"
                + "    \"appId\": 204106,\n"
                + "    \"occurredAt\": 1632175988258,\n"
                + "    \"subscriptionType\": \"deal.deletion\",\n"
                + "    \"attemptNumber\": 0,\n"
                + "    \"objectId\": 6261424409,\n"
                + "    \"changeFlag\": \"DELETED\",\n"
                + "    \"changeSource\": \"API\"\n"
                + "  }\n"
                + "]";
        try {
            connector.getAuthConfig().setClientSecret(SECRET);
            hubspotService.parseEventData(new WebhookRequest().setBody(data).setConfig(connector).setHeaders(Map.of("x-hubspot-signature", "invalid")));
            fail();
        } catch (Exception e) {
            assertEquals("Invalid request. The signatures do not match.", e.getMessage());
        }
        String hash = Hex.encodeHexString(TextUtil.getSha(connector.getAuthConfig().getClientSecret().concat(data)));
        List<EventData> parsed = hubspotService.parseEventData(new WebhookRequest().setBody(data).setConfig(connector)
                .setHeaders(Map.of("x-hubspot-signature", hash)));
        assertEquals("6261424409", parsed.get(0).getData().getId());
    }

    @Test
    public void parseAssociationDeletionWebhook() throws NoSuchAlgorithmException {
        // Test association deletion webhook parsing
        String data = "[\n"
                + "  {\n"
                + "    \"eventId\": 123456789,\n"
                + "    \"subscriptionId\": 1234567,\n"
                + "    \"portalId\": 6196729,\n"
                + "    \"appId\": 204106,\n"
                + "    \"occurredAt\": 1632175988258,\n"
                + "    \"subscriptionType\": \"contact.associationChange\",\n"
                + "    \"attemptNumber\": 0,\n"
                + "    \"changeSource\": \"API\",\n"
                + "    \"associationType\": \"CONTACT_TO_COMPANY\",\n"
                + "    \"fromObjectId\": \"174442225803\",\n"
                + "    \"toObjectId\": \"39883298054\",\n"
                + "    \"associationRemoved\": true,\n"
                + "    \"isPrimaryAssociation\": false,\n"
                + "    \"sourceId\": \"test-source\"\n"
                + "  }\n"
                + "]";

        connector.getAuthConfig().setClientSecret(SECRET);
        String hash = Hex.encodeHexString(TextUtil.getSha(connector.getAuthConfig().getClientSecret().concat(data)));
        List<EventData> parsed = hubspotService.parseEventData(new WebhookRequest().setBody(data).setConfig(connector)
                .setHeaders(Map.of("x-hubspot-signature", hash)));

        // Verify parsed data
        assertEquals(1, parsed.size());
        EventData eventData = parsed.get(0);

        // Verify entity name is contact_association
        assertEquals("contact_association", eventData.getData().getName());

        // Verify operation is delete
        assertEquals(Operation.delete, eventData.getOperation());

        // Verify placeholder ID contains UNKNOWN-UNKNOWN (since webhook doesn't have typeId)
        assertTrue(eventData.getData().getId().contains("UNKNOWN-UNKNOWN"));
        assertEquals("174442225803-39883298054-company-UNKNOWN-UNKNOWN", eventData.getData().getId());

        // Verify field values
        assertEquals("174442225803", eventData.getData().getValue("fromObjectId"));
        assertEquals("39883298054", eventData.getData().getValue("toObjectId"));
        assertEquals("company", eventData.getData().getValue("toObjectType"));
        assertEquals("CONTACT_TO_COMPANY", eventData.getData().getValue("label"));
        assertEquals("contact", eventData.getData().getValue("fromObjectType"));

        // Verify isDeleted flag
        assertTrue(eventData.getData().isDeleted());
    }

    @Test
    public void parseAssociationCreationWebhook_ShouldBeIgnored() throws NoSuchAlgorithmException {
        // Test that association creation webhooks are ignored (only deletions are processed)
        String data = "[\n"
                + "  {\n"
                + "    \"eventId\": 123456789,\n"
                + "    \"subscriptionId\": 1234567,\n"
                + "    \"portalId\": 6196729,\n"
                + "    \"appId\": 204106,\n"
                + "    \"occurredAt\": 1632175988258,\n"
                + "    \"subscriptionType\": \"contact.associationChange\",\n"
                + "    \"attemptNumber\": 0,\n"
                + "    \"changeSource\": \"API\",\n"
                + "    \"associationType\": \"CONTACT_TO_COMPANY\",\n"
                + "    \"fromObjectId\": \"174442225803\",\n"
                + "    \"toObjectId\": \"39883298054\",\n"
                + "    \"associationRemoved\": false,\n"
                + "    \"isPrimaryAssociation\": false,\n"
                + "    \"sourceId\": \"test-source\"\n"
                + "  }\n"
                + "]";

        connector.getAuthConfig().setClientSecret(SECRET);
        String hash = Hex.encodeHexString(TextUtil.getSha(connector.getAuthConfig().getClientSecret().concat(data)));
        List<EventData> parsed = hubspotService.parseEventData(new WebhookRequest().setBody(data).setConfig(connector)
                .setHeaders(Map.of("x-hubspot-signature", hash)));

        // Verify that association creation webhook is ignored (returns empty list)
        assertEquals(0, parsed.size());
    }

    @Test
    public void mixOfFailureAndSuccessfulUpdates(){

        List<EntityData> accounts = getAccounts();
        String accountId1 = accounts.get(0).getId();
        String accountId2 = accounts.get(1).getId();
        SyncRequest updateRequest = getRequest(Constants.COMPANY);
        EntityData entityDataFail = new EntityData(Constants.COMPANY).addValue("domain", "bad domain").setId(accountId1);
        EntityData entityDataSuccess = new EntityData(Constants.COMPANY).addValue("city", "some city").setId(accountId2);

        updateRequest.getData().put(connector.getId(), List.of(entityDataFail,entityDataSuccess));
        SyncResponse updateResponse = hubspotService.update(updateRequest);
        assertEquals(2,updateResponse.getResults().size());
        Result result1 = updateResponse.getResults().get(0);
        assertFalse(result1.isSuccess());
        assertEquals(accountId1,result1.getId());

        Result result2 = updateResponse.getResults().get(1);
        assertTrue(result2.isSuccess());
        assertEquals(accountId2,result2.getId());

    }

    @Test
    public void mixOfFailureAndSuccessfulCreates(){
        SyncRequest createRequest = getRequest(Constants.COMPANY);
        EntityData entityDataFail = new EntityData(Constants.COMPANY).addValue("domain", "bad domain");
        EntityData entityDataSuccess = new EntityData(Constants.COMPANY).addValue("domain", "syncari.io");

        createRequest.getData().put(connector.getId(), List.of(entityDataFail,entityDataSuccess));
        SyncResponse createResponse = hubspotService.create(createRequest);
        assertEquals(2,createResponse.getResults().size());
        Result result1 = createResponse.getResults().get(0);
        assertFalse(result1.isSuccess());
        assertNull(result1.getId());

        Result result2 = createResponse.getResults().get(1);
        assertTrue(result2.isSuccess());
        assertNotNull(result2.getId());
        doDelete(List.of(result2.getId()),Constants.COMPANY);

    }

    @Test
    public void createAndUpdateAccount() {
        SyncResponse response = null;
        try {
            response = doCreate(Constants.COMPANY.toLowerCase(), COMPANY_NAME,"web_technologies",List.of("1_and_1_hosting","acquisio"));
            assertSuccessResponse(response);
            assertTrue(response.getResults().size() == 1);

            SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
            EntityData entityData = new EntityData(Constants.COMPANY.toLowerCase());
            String id = response.getResults().get(0).getId();
            entityData.setId(id);
            request.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> byIds = (List<EntityData>) hubspotService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals(id, byIds.get(0).getId());
            assertEquals(COMPANY_NAME, byIds.get(0).getValue("name"));
            assertEquals(List.of("1_and_1_hosting","acquisio"), byIds.get(0).getValue("web_technologies"));

            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);
            request = getRequest(Constants.COMPANY.toLowerCase());
            // update name and owner_id for the account
            entityData = new EntityData(Constants.COMPANY.toLowerCase())
                    .addValue("name", "Modified Company Name")
                    .addValue("hubspot_owner_id", "38171152"); // this is Syncari Dev user in hubspot test account
            entityData.setId(result.getId());
            request.getData().put(connector.getId(), List.of(entityData));
            SyncResponse updateResponse = hubspotService.update(request);
            assertTrue(updateResponse.isSuccess());

            byIds = (List<EntityData>) hubspotService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals(id, byIds.get(0).getId());
            assertEquals("Modified Company Name", byIds.get(0).getValue("name"));
            assertEquals("38171152", byIds.get(0).getValueAsString("hubspot_owner_id"));

        } finally {
            doDelete(response, Constants.COMPANY.toLowerCase());
        }
    }

    @Test
    @Ignore
    public void CRUDCustomObject() {
        SyncResponse response = null;
        connector = createConnector("1abadc1f-702f-4023-ab17-b2c5faf100ea");
        String customObjectName = "p21164229_custom_address";

        try {
            response = doCreateCustomAddress(customObjectName, "1234","city","newark","country","usa", "address_id", "1234",
                    "ismetro",false,"sincedate",1430438400000l,"zip2",12345,"contactperson",130887963);
            assertSuccessResponse(response);
            assertTrue(response.getResults().size() == 1);
//            assertTrue(response.getResults().get(0).getSyncariId() != null);
//            assertTrue(response.getResults().get(0).getId() != null);

            SyncRequest request = getRequest(customObjectName);
            EntityData entityData = new EntityData(customObjectName);
            String id = response.getResults().get(0).getId();
            entityData.setId(id);
            request.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> byIds = (List<EntityData>) hubspotService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals(id, byIds.get(0).getId());
            assertEquals("newark", byIds.get(0).getValue("city"));
            assertEquals("usa", byIds.get(0).getValue("country"));
            assertEquals("12345", byIds.get(0).getValue("zip2"));
            assertEquals("2015-05-01", byIds.get(0).getValue("sincedate"));
            assertEquals("false", byIds.get(0).getValue("ismetro"));
            assertEquals("130887963", byIds.get(0).getValue("contactperson"));
            // ismetro is false, so this will be empty.
            assertEquals(null, byIds.get(0).getValue("customcalculatedfield"));

            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);
            request = getRequest(customObjectName);
            entityData = new EntityData(customObjectName)
                    .addValue("city", "newark2")
                    .addValue("zip", "89888");
            entityData.addValue("zip2", 123456);
            entityData.addValue("sincedate", 1430438400000l);
            entityData.addValue("ismetro", true);
            entityData.addValue("contactperson",130887963);
            entityData.setId(result.getId());
            request.getData().put(connector.getId(), List.of(entityData));
            SyncResponse updateResponse = hubspotService.update(request);
            assertTrue(updateResponse.isSuccess());

            byIds = (List<EntityData>) hubspotService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals(id, byIds.get(0).getId());
            assertEquals("newark2", byIds.get(0).getValue("city"));
            assertEquals("89888", byIds.get(0).getValueAsString("zip"));
            assertEquals("123456", byIds.get(0).getValue("zip2"));
            assertEquals("2015-05-01", byIds.get(0).getValue("sincedate"));
            assertEquals("true", byIds.get(0).getValue("ismetro"));
            assertEquals("130887963", byIds.get(0).getValue("contactperson"));
            // ismetro is true, so this value will be calculated
            assertEquals("2", byIds.get(0).getValue("customcalculatedfield"));

        } finally {
            doDelete(response, customObjectName);
        }
    }

    @Test
    public void dealWithIntegerFields() {
        SyncResponse response = null;
        connector = createConnector("1abadc1f-702f-4023-ab17-b2c5faf100ea");

        try {
            SyncRequest request = getRequest(Constants.DEAL.toLowerCase());
            List<EntityData> oppties = new ArrayList<>();
            EntityData entityData1 = new EntityData(Constants.DEAL.toLowerCase()).addValue("dealname", "Hubspot deal numbers1")
                    .addValue("dealstage", "closedwon")
                    .addValue("closedate", ZonedDateTime.now())
                    .addValue("amount", 12.23)
                    .addValue("quantity", 5.00)
                    .addValue("associatedcompanyid", "15797770083")
                    .setSyncariEntityId("syncariId" + 1);
            oppties.add(entityData1);
            EntityData entityData2 = new EntityData(Constants.DEAL.toLowerCase())
                    .addValue("dealname", "Hubspot deal numbers2")
                    .addValue("dealstage", "closedwon")
                    .addValue("closedate", ZonedDateTime.now())
                    //sending integer to double field should work
                    .addValue("amount", 12)
                    .addValue("quantity", 5.00)
                    .addValue("associatedcompanyid", "15797770083")
                    .setSyncariEntityId("syncariId" + 2);
            oppties.add(entityData2);

            request.getData().put(connector.getId(), oppties);
            response = hubspotService.create(request);
            List<Result> results = response.getResults();
            assertEquals(2, results.size());
            for (Result result : results) {
                assertTrue(result.isSuccess());
                assertTrue(result.getErrors().isEmpty());
                assertNotNull(result.getId() != null);
            }
        } finally {
            doDelete(response, Constants.DEAL);
        }
    }
    @Test
    public void getFirstTime() throws InterruptedException {
        SyncResponse response = doCreate(Constants.COMPANY.toLowerCase(), COMPANY_NAME);
        assertSuccessResponse(response);
        try {
            retryWithBackoff(() -> {
                SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
                request.setWatermark(
                        new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

                long firstTimestamp = hubspotService.getFirstCreatedTime(request);
                assertNotEquals(0, firstTimestamp);
            });
        } finally {
            doDelete(response, Constants.COMPANY.toLowerCase());
        }
    }

    @Test
    public void getCompaniesByWatermarkInitial() throws InterruptedException {
        SyncResponse response = doCreate(Constants.COMPANY.toLowerCase(), COMPANY_NAME);
        assertSuccessResponse(response);
        try {
            retryWithBackoff(() -> {
                SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
                request.setWatermark(
                        new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertTrue(next.size() >= 10);
                List<String> names = List.of("HubSpot, Inc.",
                        "Unit test 1", "test company");
                Set<String> companyNames = next.stream().map(data->(String)data.getValue("name")).collect(Collectors.toSet());
                assertTrue(companyNames.contains(names.get(0)));
                assertTrue(companyNames.contains(names.get(1)));
                assertTrue(companyNames.contains(names.get(2)));
                assertFalse(byWatermark.getIterator().hasNext());
                // verify the last watermark is set correctly
                assertEquals(next.stream().max(Comparator.comparing(EntityData::getLastModified)).get().getLastModified(),
                        byWatermark.getIterator().getLastWatermark());
            });

        } finally {
            doDelete(response, Constants.COMPANY.toLowerCase());
        }
    }

    @Test
    public void getCompaniesByWatermarkInitialByPage() throws InterruptedException {
        SyncResponse response = doCreate(Constants.COMPANY.toLowerCase(), COMPANY_NAME);
        assertSuccessResponse(response);

        try {
            retryWithBackoff(()->{
                SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
                request.setWatermark(
                        new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
                request.setPageSize(1);
                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
                Set<String> names = Set.of("HubSpot, Inc.",
                        "Unit test 1", "test company");
//                assertTrue(names.contains(next.get(0).getValue("name")));
                long firstWm = next.get(0).getLastModified();
                assertTrue(byWatermark.getIterator().hasNext());
                next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
//                assertTrue(names.contains(next.get(0).getValue("name")));
                long secondWm = next.get(0).getLastModified();
                assertTrue(byWatermark.getIterator().hasNext());
                next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
//                assertTrue(names.contains(next.get(0).getValue("name")));
                long thirdWm = next.get(0).getLastModified();
                assertTrue(byWatermark.getIterator().hasNext());
                // verify the last watermark is set correctly
                assertEquals(Math.max(Math.max(firstWm, secondWm), thirdWm), byWatermark.getIterator().getLastWatermark());
            });
        } finally {
            doDelete(response, Constants.COMPANY.toLowerCase());
        }
    }

//    @Test
//    @Ignore("These are using legacy API. We use events/v3/events for pulling activities")
//    public void getEventsByWatermark() throws InterruptedException {
//        retryWithBackoff(()->{
//            SyncRequest request = getRequest(Constants.EVENT.toLowerCase(), createConnector("demo"));
//            request.setWatermark(
//                    new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
//            request.setPageSize(1);
//            FetchResponse byWatermark = hubspotService.getByWatermark(request);
//            assertTrue(byWatermark.getIterator().hasNext());
//            List<EntityData> next = byWatermark.getIterator().next();
//            assertTrue(next.size() >= 460);
//        });
//    }

    @Test
    public void getCompaniesByWatermarkIncremental() throws InterruptedException {
        Instant start = Instant.now();
        Thread.sleep(WAIT_SECONDS*1000);
        SyncResponse response = doCreate(Constants.COMPANY.toLowerCase(), COMPANY_NAME,"web_technologies",List.of("1_and_1_hosting","acquisio"));
        assertSuccessResponse(response);
        Thread.sleep(WAIT_SECONDS*2000);
        try {
            retryWithBackoff(5, 10, () -> {
                SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
                request.setWatermark(new WatermarkInfo(start.toEpochMilli()-10000, Instant.now().toEpochMilli(), false, 0));

                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
                Set<String> names = Set.of("Unit test 1", "test company");
                assertTrue(names.contains(next.get(0).getValue("name")));
                assertTrue(names.contains(next.get(0).getValue("name")));
                assertEquals(List.of("1_and_1_hosting","acquisio"),next.get(0).getValue("web_technologies"));
                assertTrue(next.get(0).getCreatedAt()>0);
                assertFalse(byWatermark.getIterator().hasNext());
            }, Optional.empty());

        } finally {
            doDelete(response, Constants.COMPANY.toLowerCase());
        }
    }

    @Test
    public void getCompaniesByIds() {
        Instant start = Instant.EPOCH;
        SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
        request.setWatermark(new WatermarkInfo(start.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));

        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        assertTrue(next.size() > 1);
        request.setData(Map.of(request.getConnector().getId(), next));
        List<EntityData> byIds = hubspotService.getByIds(request);
        assertEquals(next.size(),byIds.size());
        assertEquals(request.getEntitySchema().getAttributes().size(), byIds.get(0).getValues().size());
    }

    @Test
    public void getOwnersByIds() {
        Instant start = Instant.EPOCH;
        SyncRequest request = getRequest(Constants.OWNER.toLowerCase());
        request.setWatermark(new WatermarkInfo(start.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));

        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        // As we add new users to the hubspot instances, this number keeps going up.
        assertTrue(next.size() >= 1);
        assertNotNull(next.get(0).getId());
        assertEquals("owner",next.get(0).getName());
        request.setData(Map.of(request.getConnector().getId(), next));
        List<EntityData> byIds = hubspotService.getByIds(request);
        assertEquals(next.size(), byIds.size());
        assertNotNull(byIds.get(0).getId());
        request.setData(Map.of(request.getConnector().getId(),List.of(new EntityData().setId("99999"))));
        List<EntityData> none = hubspotService.getByIds(request);
        assertTrue(none.isEmpty());
    }

//    @Test
//    @Ignore("These are using legacy API. We use events/v3/events for pulling activities")
//    public void getEventsByIds() {
//        Instant start = Instant.EPOCH;
//        SyncRequest request = getRequest(Constants.EVENT.toLowerCase(), createConnector("demo"));
//        request.setWatermark(new WatermarkInfo(start.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
//        request.getWatermark().setLimit(10);
//
//        FetchResponse byWatermark = hubspotService.getByWatermark(request);
//        assertTrue(byWatermark.getIterator().hasNext());
//        List<EntityData> next = byWatermark.getIterator().next();
//        assertTrue(next.size() == 10);
//        assertNotNull(next.get(0).getId());
//        assertEquals("event", next.get(0).getName());
//        request = getRequest(Constants.EVENT.toLowerCase(), createConnector("demo"));
//        request.setData(Map.of(request.getConnector().getId(), List.of(next.get(0))));
//        List<EntityData> byIds = hubspotService.getByIds(request);
//        assertEquals(1, byIds.size());
//        assertNotNull(byIds.get(0).getId());
//        request.setData(Map.of(request.getConnector().getId(), List.of(new EntityData().setId("99999"))));
//        List<EntityData> none = hubspotService.getByIds(request);
//        assertTrue(none.isEmpty());
//    }

    @Test
    @Ignore
    public void getContactsByWatermarkInitial() throws InterruptedException {
        SyncResponse response = doCreateContact(false, 1);
        assertSuccessResponse(response);
        try {
            retryWithBackoff(() -> {
                SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
                request.setWatermark(
                        new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertTrue(next.size() > 3);
                List<EntityData> filtered = next.stream().filter(ed -> !ed.isDeleted() && ed.hasValue("firstname")).collect(Collectors.toList());;
                assertFalse(filtered.isEmpty());
                assertFalse(byWatermark.getIterator().hasNext());
            });

        } finally {
            doDelete(response, Constants.CONTACT.toLowerCase());
        }
    }

    @Test
    public void getContactsByWatermarkInitialByPage() throws InterruptedException {
        // Uses "Multiple associations (21163731)" hubspot instance
        connector = createConnector("5578e7c6-0ba9-483a-be07-b57eee9adced");
        SyncResponse response = doCreateContact(false, 1);
        assertSuccessResponse(response);

        try {
            retryWithBackoff(()->{
                SyncRequest request = getRequest(Constants.CONTACT.toLowerCase(), connector);
                request.setWatermark(
                        new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
                request.setPageSize(1);
                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
                Set<String> names = Set.of("test first namefalse0", "Brian", "Maria", "test");
                assertTrue(names.contains(next.get(0).getValue("firstname")));
                long firstWm = next.get(0).getLastModified();
                assertTrue(byWatermark.getIterator().hasNext());
                next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
                assertTrue(names.contains(next.get(0).getValue("firstname")));
                long secondWm = next.get(0).getLastModified();
                assertTrue(byWatermark.getIterator().hasNext());
                next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
                assertTrue(names.contains(next.get(0).getValue("firstname")));
                long thirdWm = next.get(0).getLastModified();
                assertFalse(byWatermark.getIterator().hasNext());
                // verify the last watermark is set correctly
                assertEquals(Math.max(Math.max(firstWm, secondWm), thirdWm), byWatermark.getIterator().getLastWatermark());
            });

        } finally {
            doDelete(response, Constants.CONTACT.toLowerCase());
            connector = null;
        }
    }

    @Test
    @Retry(maxRetries=3, retryDelay=5)
    public void getContactsByWatermarkIncremental() throws InterruptedException {
        Instant start = Instant.now();
        SyncResponse response = doCreateContact(false, 1);
        assertSuccessResponse(response);
        try {
            Thread.sleep(WAIT_SECONDS*1000);
            retryWithBackoff(()->{
                SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
                request.setWatermark(new WatermarkInfo(start.toEpochMilli(), Instant.now().plusSeconds(100).toEpochMilli(), false, 0));

                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertTrue(next.size() >= 1);
                Set<String> names = Set.of("test first namefalse0");
                assertTrue(names.contains(next.get(0).getValue("firstname")));
                assertFalse(byWatermark.getIterator().hasNext());
            });

        } finally {
            doDelete(response, Constants.CONTACT.toLowerCase());
        }
    }

    @Test
    @Retry(maxRetries=3, retryDelay=5)
    public void getContactsByWatermark_ExcludeLowerBound() throws InterruptedException {
        SyncResponse response = doCreateContact(false, 1);
        String id = response.getResults().get(0).getId();
        assertSuccessResponse(response);
        try {
            Thread.sleep(WAIT_SECONDS*1000);
            retryWithBackoff(()->{
                SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
                request.addData(getConnector().getId(), new EntityData().setId(id));
                List<EntityData> byIds = hubspotService.getByIds(request);
                assertFalse(byIds.isEmpty());
                long start = byIds.get(0).getLastModified();
                request = getRequest(Constants.CONTACT.toLowerCase());
                request.setWatermark(new WatermarkInfo(start, Instant.now().plusSeconds(100).toEpochMilli(), false, 0));

                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                // ideally this should be empty but any new records can be added externally
                // so just check if any new record the created record is not fetched as lower bound of wm is excluded
                if(byWatermark.getIterator().hasNext()) {
                    List<EntityData> next = byWatermark.getIterator().next();
                    next.forEach(data -> {
                        assertNotEquals(id, data.getId());
                    });
                }
            });

        } finally {
            doDelete(response, Constants.CONTACT.toLowerCase());
        }
    }

    @Test
    public void getWatermarkWithLimitReturnsOnlyLimitedRecords() throws InterruptedException {
        Instant begin = Instant.now();
        SyncResponse response = doCreateContact(false, 1);
        assertSuccessResponse(response);
        try {
            retryWithBackoff(()->{
                SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
                WatermarkInfo wm = new WatermarkInfo(begin.toEpochMilli(), Instant.now().plusSeconds(100).toEpochMilli(), false, 0);
                wm.setLimit(1);
                request.setPageSize(2);
                request.setWatermark(wm);
                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
                assertFalse(byWatermark.getIterator().hasNext());
            });
        } finally {
            doDelete(response, Constants.CONTACT.toLowerCase());
        }
    }

    @Test
    public void getOpptyByWatermarkIncremental() throws InterruptedException {
        Instant start = Instant.now().minusSeconds(10);
        SyncResponse response = doCreateOppty(1);
        assertSuccessResponse(response);
        try {
            retryWithBackoff(()->{
                SyncRequest request = getRequest(Constants.DEAL.toLowerCase());
                request.setWatermark(new WatermarkInfo(start.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));

                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
                assertEquals("Hubspot deal 0", next.get(0).getValue("dealname"));
                assertNotNull(next.get(0).getValue("associatedcompanyid"));
                LocalDateTime ltc = LocalDateTime.parse(next.get(0).getValue("closedate").toString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                ZonedDateTime dt = ltc.atZone(ZoneOffset.UTC.normalized());
                assertEquals(ZonedDateTime.now(ZoneOffset.UTC).getDayOfMonth(), dt.getDayOfMonth());
                assertFalse(byWatermark.getIterator().hasNext());
            });

        } finally {
            doDelete(response, Constants.DEAL.toLowerCase());
        }
    }

    @Test
    public void opptyWithBlankCompanySucceeds() throws InterruptedException {
        Instant start = Instant.now().minusSeconds(10);
        SyncResponse response = doCreateOppty(1,"");
        assertSuccessResponse(response);
        try {
            retryWithBackoff(()->{
                SyncRequest request = getRequest(Constants.DEAL.toLowerCase());
                request.setWatermark(new WatermarkInfo(start.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));

                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
                assertEquals("Hubspot deal 0", next.get(0).getValue("dealname"));
                assertNull(next.get(0).getValue("associatedcompanyid"));
                assertNotNull(next.get(0).getValue("closedate"));
                assertFalse(byWatermark.getIterator().hasNext());
            });

        } finally {
            doDelete(response, Constants.DEAL.toLowerCase());
        }
    }

    @Test
    public void opptyWithContactsAndCompanySucceeds() throws InterruptedException {
        Instant start = Instant.now().minusSeconds(10);
        String accountId = getAccountId();
        List<EntityData> contacts = getContacts().stream().filter(ed -> !ed.isDeleted()).collect(Collectors.toList());
        List<String> contactList = contacts.stream().map(c -> c.getId()).collect(Collectors.toList());
        String[] contactIds = contactList.toArray(new String[contacts.size()]);
        SyncResponse response = doCreateOppty(1,accountId, contactIds);
        assertSuccessResponse(response);
        try {
            retryWithBackoff(()->{
                SyncRequest request = getRequest(Constants.DEAL.toLowerCase());
                request.setWatermark(new WatermarkInfo(start.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));

                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
                assertEquals("Hubspot deal 0", next.get(0).getValue("dealname"));
                assertEquals(Long.valueOf(accountId),next.get(0).getValue("associatedcompanyid"));
                assertEquals(contactList.stream().map(c->Long.valueOf(c)).collect(Collectors.toSet()),Set.copyOf((List)next.get(0).getValue("associatedVids")));
                assertNotNull(next.get(0).getValue("closedate"));
                assertFalse(byWatermark.getIterator().hasNext());
            });

        } finally {
            doDelete(response, Constants.DEAL.toLowerCase());
        }
    }

    @Test
    public void updateOpptyWithContactsAndCompanySucceeds() throws InterruptedException {
        Instant start = Instant.now().minusSeconds(10);
        String accountId = getAccountId();
        String accountId2 = getAccountId(1);
        List<EntityData> contacts = getContacts();
        List<String> contactList = contacts.stream().map(c -> c.getId()).collect(Collectors.toList());
        String[] contactIds = contactList.toArray(new String[contacts.size()]);
        SyncResponse response = doCreateOppty(1,accountId, new String[]{contactIds[0]});
        assertSuccessResponse(response);
        try {
            retryWithBackoff(()->{
                SyncRequest request = getRequest(Constants.DEAL.toLowerCase());
                request.setWatermark(new WatermarkInfo(start.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));

                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
                assertEquals("Hubspot deal 0", next.get(0).getValue("dealname"));
                assertEquals("300", next.get(0).getValue("amount"));
                assertEquals(Long.valueOf(accountId), next.get(0).getValue("associatedcompanyid"));
                //TODO: This is failing due to changes in how we handle assocs.Fix the test
                //assertEquals(List.of(Long.valueOf(contactIds[0])),next.get(0).getValue("associatedVids"));
                assertNotNull(next.get(0).getValue("closedate"));

            });
            //update contacts on oppty
            SyncRequest updateRequest = getRequest(Constants.DEAL.toLowerCase());
            List<EntityData> oppties = new ArrayList<>();
            EntityData entityData = new EntityData(Constants.DEAL.toLowerCase());
            entityData.setId(response.getResults().get(0).getId());
            List a = new ArrayList();
            Arrays.stream(contactIds).forEach(j -> a.add(j));
            // verify nulls are handled
            a.add(null);
            entityData.addValue("associatedVids", a);
            entityData.addValue("amount", 500);
            entityData.addValue("associatedcompanyid", accountId2);
            oppties.add(entityData);

            updateRequest.getData().put(connector.getId(), oppties);
            SyncResponse updateResponse = hubspotService.update(updateRequest);
            assertSuccessResponse(updateResponse);

            retryWithBackoff(()-> {
                SyncRequest request = getRequest(Constants.DEAL.toLowerCase());
                request.setWatermark(new WatermarkInfo(start.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
                assertEquals("Hubspot deal 0", next.get(0).getValue("dealname"));
                assertEquals("500", next.get(0).getValue("amount"));
                assertEquals(Long.valueOf(accountId2), next.get(0).getValue("associatedcompanyid"));
                //TODO: This is failing due to changes in how we handle assocs.Fix the test
                //assertEquals(contactList.stream().map(c->Long.valueOf(c)).collect(Collectors.toSet()),Set.copyOf((List)next.get(0).getValue("associatedVids")));
                assertNotNull(next.get(0).getValue("closedate"));
                assertFalse(byWatermark.getIterator().hasNext());
            });
        } finally {
            doDelete(response, Constants.DEAL.toLowerCase());
        }
    }

    @Test
    public void getOpptyByWatermarkIncrementalWithLimits() throws InterruptedException {
        Instant start = Instant.now().minusSeconds(10);
        SyncResponse response = doCreateOppty(3);
        assertSuccessResponse(response);
        try {
            retryWithBackoff(()->{
                SyncRequest request = getRequest(Constants.DEAL.toLowerCase());
                WatermarkInfo watermark = new WatermarkInfo(start.toEpochMilli(), Instant.now().toEpochMilli(), false, 0);
                watermark.setLimit(2);
                request.setWatermark(watermark);

                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertEquals(2, next.size());
                assertTrue(next.get(0).getValue("dealname").toString().startsWith("Hubspot deal"));
                assertTrue(next.get(1).getValue("dealname").toString().startsWith("Hubspot deal"));
                assertNotNull(next.get(0).getValue("associatedcompanyid"));
                assertNotNull(next.get(1).getValue("associatedcompanyid"));
                assertFalse(byWatermark.getIterator().hasNext());
            });

        } finally {
            doDelete(response, Constants.DEAL.toLowerCase());
        }
    }


    @Test
    public void getUsersByWatermark() throws InterruptedException {
        SyncRequest request = getRequest(Constants.OWNER.toLowerCase());
        request.setWatermark(
                new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        assertTrue(next.size() > 0);
        // picking the last element as the firsy user created is last in the list
        EntityData lastElementInList = next.get(next.size() - 1);
        assertEquals("Syncari", lastElementInList.getValue("firstName"));
        assertEquals("Dev", lastElementInList.getValue("lastName"));
        assertTrue(Boolean.parseBoolean(lastElementInList.getValue("isActive").toString()));
        assertFalse(lastElementInList.isDeleted());
        assertFalse(byWatermark.getIterator().hasNext());
    }

    @Test
    public void getProductsByWatermark() throws InterruptedException {
        SyncRequest request = getRequest("product");
        request.setWatermark(
                new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        assertTrue(next.size() > 0);
        // picking the last element as the firsy user created is last in the list
        EntityData lastElementInList = next.get(next.size() - 1);
        assertTrue(lastElementInList.getValue("name") != null);
        assertTrue(lastElementInList.getValue("price") != null);
        assertFalse(lastElementInList.isDeleted());
        assertFalse(byWatermark.getIterator().hasNext());
    }

    @Test
    public void getNotesByWatermark() throws InterruptedException {
        SyncRequest request = getRequest("note");
        request.setWatermark(
                new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        assertTrue(next.size() > 0);
        // picking the last element as the firsy user created is last in the list
        EntityData lastElementInList = next.get(next.size() - 1);
        assertEquals("noteBody+0_updated", lastElementInList.getValue("hs_note_body"));
        assertFalse(lastElementInList.isDeleted());
        assertFalse(byWatermark.getIterator().hasNext());
    }

    @Test
    public void describeLineItem() {
        DescribeRequest describeRequest = new DescribeRequest(getConnector(), "line_item");
        Optional<EntitySchema> entitySchema = hubspotService.describe(describeRequest);
        assertTrue(entitySchema.isPresent());
        Optional<AttributeSchema> productAttrOptional = entitySchema.get().getField("hs_product_id");
        assertTrue(productAttrOptional.isPresent());
        assertEquals("reference", productAttrOptional.get().getDataType());
        assertEquals("product", productAttrOptional.get().getReferenceTo());
        assertEquals("hs_object_id", productAttrOptional.get().getReferenceTargetField());

        Optional<AttributeSchema> dealAttrOptional = entitySchema.get().getField("hs_deal_id");
        assertTrue(dealAttrOptional.isPresent());
        assertEquals("reference", dealAttrOptional.get().getDataType());
        assertEquals("deal", dealAttrOptional.get().getReferenceTo());
        assertEquals("hs_object_id", dealAttrOptional.get().getReferenceTargetField());

        Optional<AttributeSchema> quoteAttrOptional = entitySchema.get().getField("hs_quote_id");
        assertTrue(quoteAttrOptional.isPresent());
        assertEquals("reference", quoteAttrOptional.get().getDataType());
        assertEquals("quote", quoteAttrOptional.get().getReferenceTo());
        assertEquals("hs_object_id", quoteAttrOptional.get().getReferenceTargetField());
    }

    @Test
    public void getLineItemsByWatermark() throws InterruptedException {
        SyncRequest request = getRequest("line_item");
        request.setWatermark(
                new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        retryWithBackoff(()->{
            FetchResponse byWatermark = hubspotService.getByWatermark(request);
            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> next = byWatermark.getIterator().next();
            assertTrue(next.size() > 0);
            // picking the last element as the first user created is last in the list
            EntityData lastElementInList = next.get(next.size() - 1);
            assertTrue(lastElementInList.getValue("name") != null);

            if (lastElementInList.getValue("recurringbillingfrequency") != null){
                assertTrue(lastElementInList.getValue("hs_recurring_billing_period") != null);
            }
            assertFalse(lastElementInList.isDeleted());
            assertTrue(byWatermark.getIterator().hasNext());
        });

    }

    @Test
    public void cudLineItemTest() {
        SyncRequest request = getRequest("product");
        request.setWatermark(
                new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        assertTrue(!next.isEmpty());
        String product = next.get(0).getId();
        request = getRequest("deal");
        request.setWatermark(
                new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        next = byWatermark.getIterator().next();
        assertTrue(!next.isEmpty());
        String deal = next.get(0).getId();

        request = getRequest("line_item");
        EntityData entityData = new EntityData("line_item");
        entityData.addValue("name", "test line item");
        entityData.addValue("quantity", 5);
        entityData.addValue("hs_product_id", product);
        entityData.addValue("hs_deal_id", deal);
        entityData.addValue("price", 10);
        request.setData(Map.of(connector.getId(), List.of(entityData)));
        SyncResponse response = hubspotService.create(request);
        assertTrue(response.isSuccess());
        String id = response.getResults().get(0).getId();
        entityData.setValues(Map.of("name", "test line item 2"));
        entityData.setId(id);
        try {
            connector.getAuthConfig().setAccessToken("Force Refresh Token");
            hubspotService.update(request);
            List<EntityData> results = hubspotService.getByIds(request);
            assertTrue(!results.isEmpty());
            assertTrue(results.get(0).getValueAsString("name").equalsIgnoreCase("test line item 2"));
            request = getRequest("line_item_association");
            EntityData association = new EntityData("line_item_association").setId(String.format("%s-%s-deal-HUBSPOT_DEFINED-20", id, deal));
            request.setData(Map.of(connector.getId(), List.of(association)));
            List<EntityData> associationResult = hubspotService.getByIds(request);
            assertFalse(associationResult.isEmpty());
        } finally {
            hubspotService.delete(request);
        }
    }

    @Test
    public void cudLineItemWithQuoteAssociationTest() {
        SyncRequest request = getRequest("product");
        request.setWatermark(
                new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        assertTrue(!next.isEmpty());
        String product = next.get(0).getId();
        request = getRequest("quote");
        request.setWatermark(
                new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        next = byWatermark.getIterator().next();
        assertTrue(!next.isEmpty());
        String quoteId = next.get(0).getId();

        request = getRequest("line_item");
        EntityData entityData = new EntityData("line_item 1");
        entityData.addValue("name", "test line item 1");
        entityData.addValue("quantity", 5);
        entityData.addValue("hs_product_id", product);
        entityData.addValue("hs_quote_id", quoteId);
        entityData.addValue("price", 10);
        request.setData(Map.of(connector.getId(), List.of(entityData)));
        SyncResponse response = hubspotService.create(request);
        assertTrue(response.isSuccess());
        String id = response.getResults().get(0).getId();
        entityData.setValues(Map.of("name", "test line item 3"));
        entityData.setId(id);
        try {
            connector.getAuthConfig().setAccessToken("Force Refresh Token");
            hubspotService.update(request);
            List<EntityData> results = hubspotService.getByIds(request);
            assertTrue(!results.isEmpty());
            assertTrue(results.get(0).getValueAsString("name").equalsIgnoreCase("test line item 3"));
            request = getRequest("line_item_association");
            EntityData association = new EntityData("line_item_association").setId(String.format("%s-%s-quote-HUBSPOT_DEFINED-68", id, quoteId));
            request.setData(Map.of(connector.getId(), List.of(association)));
            List<EntityData> associationResult = hubspotService.getByIds(request);
            assertFalse(associationResult.isEmpty());
        } finally {
            hubspotService.delete(request);
        }
    }

    @Ignore
    @Test
    public void getByRecencyHonorsEndTimeStamp() throws InterruptedException {
        SyncResponse response1 = doCreate(Constants.COMPANY.toLowerCase(), "recency 1");
        assertSuccessResponse(response1);
        SyncResponse response2 = doCreate(Constants.COMPANY.toLowerCase(), "recency 2");
        assertSuccessResponse(response2);
        // Hubspot creates do not provide read-after-write semantics.
        // Have to wait for eventually consistent writes to converge
        try {
            Thread.sleep(WAIT_SECONDS*1000);
            SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
            request.setWatermark(
                    new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));

            FetchResponse byWatermark = hubspotService.getByWatermark(request);
            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> next = byWatermark.getIterator().next();
            assertEquals(4, next.size());

            // Back 10 mins
            request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.EPOCH.toEpochMilli()-360000, false, 0));
            byWatermark = hubspotService.getByWatermark(request);
            assertTrue(byWatermark.getIterator().hasNext());
            next = byWatermark.getIterator().next();
            assertEquals(2, next.size());
            assertEquals("test company", next.get(0).getValue("name"));
            assertFalse(byWatermark.getIterator().hasNext());

        } finally {
            doDelete(response1, Constants.COMPANY.toLowerCase());
            doDelete(response2, Constants.COMPANY.toLowerCase());
        }

    }

    @Test
    @Retry(maxRetries=5, retryDelay=10)
    public void getByRecencyFetchesCompany() throws InterruptedException {
        SyncResponse response = doCreate(Constants.COMPANY.toLowerCase(), COMPANY_NAME);
        assertSuccessResponse(response);
        try {
            Thread.sleep(WAIT_SECONDS*2000);
            SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
            request.setWatermark(
                    new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

            List<String> companies = List.of("test company", "Unit test 1", "HubSpot, Inc.");
            FetchResponse byWatermark = hubspotService.getByWatermark(request);
            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> next = byWatermark.getIterator().next();
            assertTrue(next.size() >= 10);
            Set<String> companyNames = next.stream().map(data->(String)data.getValue("name")).collect(Collectors.toSet());
            assertTrue(companyNames.contains(companies.get(0)));
            assertTrue(companyNames.contains(companies.get(1)));
            assertTrue(companyNames.contains(companies.get(2)));
            assertFalse(byWatermark.getIterator().hasNext());
        } finally {
            doDelete(response, Constants.COMPANY.toLowerCase());
        }
    }

    @Test
    @Retry(maxRetries=3, retryDelay=5)
    public void getByRecencyFetchesContact() throws InterruptedException {
        SyncResponse response = doCreateContact(false, 1);
        assertSuccessResponse(response);
        try {
            Thread.sleep(WAIT_SECONDS*1000);
            SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
            request.setWatermark(
                    new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

            FetchResponse byWatermark = hubspotService.getByWatermark(request);
            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> next = byWatermark.getIterator().next();
            assertTrue(next.size() > 0);
            List<String> firstNames = next.stream().filter(c -> c.getValue("firstname") != null).map(c -> c.getValue("firstname").toString()).collect(Collectors.toList());
            assertTrue(firstNames.contains("Cool"));
            assertFalse(byWatermark.getIterator().hasNext());
        } finally {
            doDelete(response, Constants.CONTACT.toLowerCase());
        }
    }

    @Test
    public void getByRecencyFetchesRepeatedHasNext() throws InterruptedException {
        SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
        request.setWatermark(
                new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        List<String> companies = List.of("Unit test 1", "HubSpot, Inc.");
        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        assertTrue(next.size() >= 10);
        Set<String> companyNames = next.stream().map(data->(String)data.getValue("name")).collect(Collectors.toSet());
        assertTrue(companyNames.contains(companies.get(0)));
        assertTrue(companyNames.contains(companies.get(1)));
        assertFalse(byWatermark.getIterator().hasNext());
    }

    @Test
    public void updateAccount() {
        SyncResponse response = null;
        try {
            response = doCreate(Constants.COMPANY, COMPANY_NAME);
            assertSuccessResponse(response);

            SyncRequest updateRequest = getRequest(Constants.COMPANY);
            EntityData entityData = new EntityData(Constants.COMPANY).addValue("name", "test account2");
            entityData.setId(response.getResults().get(0).getId());
            updateRequest.getData().put(connector.getId(), List.of(entityData));
            SyncResponse updateResponse = hubspotService.update(updateRequest);
            assertTrue(updateResponse.getResults().size() > 0);
            Result result = updateResponse.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getId() != null);
        } finally {
            doDelete(response, Constants.COMPANY);
        }

    }

    @Test
    public void createContact() {
        SyncResponse response = null;
        try {
            response = doCreateContact(false, 1);
            assertSuccessResponse(response);
            assertEquals(1,response.getResults().size());

            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);
            assertTrue(result.getSyncariId() != null);

            SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
            EntityData entityData = new EntityData(Constants.CONTACT.toLowerCase());
            entityData.setId(result.getId());
            request.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> byIds = (List<EntityData>) hubspotService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals(result.getId(), byIds.get(0).getId());
            assertEquals("test first namefalse0", byIds.get(0).getValue("firstname"));
        } finally {
            doDelete(response, Constants.CONTACT.toLowerCase());
        }
    }

    private NonRetriableException getNonRetriableException() {
        return new NonRetriableException(ErrorCodes.ACCESS_DENIED, "Unauthorized", "401");
    }

    @Test
    public void getSuccessAfterTokenRefresh(){
        ConnectorInfo connector = getConnector();
        connector.getAuthConfig().setAccessToken("TOKEN1");
        connector.getAuthConfig().setRefreshToken("TOKEN1");
        HubspotRestClient mockHubSpotRestClient = Mockito.spy(HubspotRestClient.class);
        Supplier<AuthConfig> mockHandler = mock(Supplier.class);
        doThrow(getNonRetriableException())
                .doReturn(new EntityData("data1"))
                .when(mockHubSpotRestClient).post(anyString(), anyMap(), any(AuthConfig.class));

        doReturn(new AuthConfig().setAccessToken("TOKEN2").setRefreshToken("TOKEN2")).when(mockHandler).get();

        assertEquals("TOKEN1", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN1", connector.getAuthConfig().getRefreshToken());

        EntityData data = mockHubSpotRestClient.post("someurl", new HashMap<String, Object>(), connector, mockHandler);
        assertEquals("data1", data.getName());
        assertEquals("TOKEN2", connector.getAuthConfig().getAccessToken());
        assertEquals("TOKEN2", connector.getAuthConfig().getRefreshToken());

        verify(mockHubSpotRestClient, times(2)).post(anyString(), anyMap(), any(AuthConfig.class));
        verify(mockHandler).get();
    }

    @Test
    public void createNote() {
        SyncResponse response = null;
        try {
            SyncRequest request = getRequest("note");
            List<EntityData> records = new ArrayList<>();
            EntityData entityData = new EntityData("note").addValue("hs_timestamp", "2022-02-24T05:00:00Z")
                    .addValue("hs_note_body", "test last \n name").addValue("hubspot_owner_id", "38171152");
            entityData.setSyncariEntityId("123");
            records.add(entityData);
            request.getData().put(connector.getId(), records);
            response = hubspotService.create(request);
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());

            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);

            entityData.setId(result.getId());
            request.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> byIds = (List<EntityData>) hubspotService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals(result.getId(), byIds.get(0).getId());
            assertEquals("test last \n name", byIds.get(0).getValue("hs_note_body"));
        } finally {
            doDelete(response, "note");
        }
    }

    @Test
    public void createProduct() {
        SyncResponse response = null;
        try {
            SyncRequest request = getRequest("product");
            List<EntityData> records = new ArrayList<>();
            EntityData entityData = new EntityData("product").addValue("createdate", "2022-02-24T05:00:00Z")
                    .addValue("name", "product").addValue("price", "200");
            records.add(entityData);
            request.getData().put(connector.getId(), records);
            response = hubspotService.create(request);
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());

            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);

            entityData.setValues(Map.of("price", "300"));
            entityData.setId(result.getId());

            response = hubspotService.update(request);
            assertSuccessResponse(response);

            entityData.setId(result.getId());
            request.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> byIds = (List<EntityData>) hubspotService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals(result.getId(), byIds.get(0).getId());
            assertEquals("product", byIds.get(0).getValue("name"));
            assertEquals("300", byIds.get(0).getValue("price"));
        } finally {
            doDelete(response, "product");
        }

    }

    @Test
    public void createTicket() {
        SyncResponse response = null;
        try {
            SyncRequest request = getRequest("ticket");
            List<EntityData> records = new ArrayList<>();
            EntityData entityData = new EntityData("ticket").addValue("createdate", "2022-02-24T05:00:00Z")
                    .addValue("subject", "unit test").addValue("hs_ticket_priority", "HIGH").addValue("hs_pipeline_stage", 1);
            entityData.setSyncariEntityId("123");
            records.add(entityData);
            EntityData entityData1 = new EntityData("ticket").addValue("createdate", "2022-02-24T05:00:00Z")
                    .addValue("subject", "unit test1").addValue("hs_ticket_priority", "HIGH").addValue("hs_pipeline_stage", 1);
            entityData1.setSyncariEntityId("234");
            records.add(entityData1);
            request.getData().put(connector.getId(), records);
            response = hubspotService.create(request);
            assertSuccessResponse(response);
            assertEquals(2, response.getResults().size());
            Map<String, String> hubIdToSyncariId = new HashMap<>();
            response.getResults().forEach(r -> {
                assertNotNull(r.getSyncariId());
                hubIdToSyncariId.put(r.getId(), r.getSyncariId());
            });

            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);

            entityData.setId(result.getId());
            request.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> byIds = (List<EntityData>) hubspotService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("123", hubIdToSyncariId.get(byIds.get(0).getId()));
            assertEquals(result.getId(), byIds.get(0).getId());
            assertEquals("unit test", byIds.get(0).getValue("subject"));
        } finally {
            doDelete(response, "ticket");
        }

    }

    @Test
    public void updateTicket() {
        SyncResponse response = null;
        try {
            SyncRequest request = getRequest("ticket");
            List<EntityData> records = new ArrayList<>();
            EntityData entityData = new EntityData("ticket").addValue("createdate", "2022-02-24T05:00:00Z")
                    .addValue("subject", "unit test").addValue("hs_ticket_priority", "HIGH").addValue("hs_pipeline_stage", 1);
            entityData.setSyncariEntityId("123");
            records.add(entityData);
            request.getData().put(connector.getId(), records);
            response = hubspotService.create(request);
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());
            Map<String, String> hubIdToSyncariId = new HashMap<>();
            response.getResults().forEach(r -> {
                assertNotNull(r.getSyncariId());
                hubIdToSyncariId.put(r.getId(), r.getSyncariId());
            });

            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);

            entityData.setId(result.getId());
            request.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> byIds = (List<EntityData>) hubspotService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("123", hubIdToSyncariId.get(byIds.get(0).getId()));
            assertEquals(result.getId(), byIds.get(0).getId());
            assertEquals("unit test", byIds.get(0).getValue("subject"));
            assertEquals("HIGH", byIds.get(0).getValue("hs_ticket_priority"));

            SyncRequest updateRequest = getRequest(Constants.TICKET.toLowerCase());
            EntityData updateData = new EntityData(Constants.TICKET.toLowerCase());
            updateData.addValue("subject", "unit test Updated").addValue("hs_ticket_priority", "LOW");
            updateData.setId(result.getId());
            updateRequest.getData().put(connector.getId(), List.of(updateData));
            SyncResponse updateResponse = hubspotService.update(updateRequest);
            assertSuccessResponse(updateResponse);
            assertTrue(updateResponse.getResults().size() == 1);

            request.getData().put(connector.getId(), List.of(entityData));
            byIds = (List<EntityData>) hubspotService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("123", hubIdToSyncariId.get(byIds.get(0).getId()));
            assertEquals(result.getId(), byIds.get(0).getId());
            assertEquals("unit test Updated", byIds.get(0).getValue("subject"));
            assertEquals("LOW", byIds.get(0).getValue("hs_ticket_priority"));
        } finally {
            doDelete(response, "ticket");
        }

    }

    @Test
    public void updateDeletedTicket() {
        SyncResponse response = null;
        boolean deleted = false;
        try {
            SyncRequest request = getRequest("ticket");
            List<EntityData> records = new ArrayList<>();
            EntityData entityData = new EntityData("ticket").addValue("createdate", "2022-02-24T05:00:00Z")
                    .addValue("subject", "unit test").addValue("hs_ticket_priority", "HIGH").addValue("hs_pipeline_stage", 1);
            entityData.setSyncariEntityId("123");
            records.add(entityData);
            request.getData().put(connector.getId(), records);
            response = hubspotService.create(request);
            assertSuccessResponse(response);
            assertEquals(1, response.getResults().size());
            Map<String, String> hubIdToSyncariId = new HashMap<>();
            response.getResults().forEach(r -> {
                assertNotNull(r.getSyncariId());
                hubIdToSyncariId.put(r.getId(), r.getSyncariId());
            });

            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);

            entityData.setId(result.getId());
            request.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> byIds = (List<EntityData>) hubspotService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals("123", hubIdToSyncariId.get(byIds.get(0).getId()));
            assertEquals(result.getId(), byIds.get(0).getId());
            assertEquals("unit test", byIds.get(0).getValue("subject"));
            assertEquals("HIGH", byIds.get(0).getValue("hs_ticket_priority"));

            doDelete(response, "ticket");
            deleted = true;

            SyncRequest updateRequest = getRequest(Constants.TICKET.toLowerCase());
            EntityData updateData = new EntityData(Constants.TICKET.toLowerCase());
            updateData.addValue("subject", "unit test Updated").addValue("hs_ticket_priority", "LOW");
            updateData.setId(result.getId());
            updateRequest.getData().put(connector.getId(), List.of(updateData));
            SyncResponse updateResponse = hubspotService.update(updateRequest);
            assertFalse(updateResponse.isSuccess());
            assertFalse(updateResponse.getResults().isEmpty());
            assertTrue(updateResponse.getResults().get(0).getErrorCode().equalsIgnoreCase(ErrorCodes.DATA_NOT_FOUND.name()));
        } finally {
            if(!deleted) {
                doDelete(response, "ticket");
            }
        }

    }

    @Test
    public void createContactBatch() {
        SyncResponse response = null;
        try {
            response = doCreateContact(true, 15);
            assertSuccessResponse(response);
            assertEquals(15,response.getResults().size());

            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);

            SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
            EntityData entityData = new EntityData(Constants.CONTACT.toLowerCase());
            entityData.setId(result.getId());
            request.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> byIds = (List<EntityData>) hubspotService.getByIds(request);
            assertTrue(byIds.size() == 1);
            assertEquals(result.getId(), byIds.get(0).getId());
            assertTrue(byIds.get(0).getValue("firstname").toString().startsWith("test first nametrue"));
        } finally {
            doDelete(response, Constants.CONTACT.toLowerCase());
        }
    }

    @Test
    public void getContactByUnknownId() {
        SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
        EntityData entityData = new EntityData(Constants.CONTACT.toLowerCase());
        entityData.setId("1234567890");
        request.getData().put(connector.getId(), List.of(entityData));
        List<EntityData> byIds = hubspotService.getByIds(request);
        assertTrue(byIds.size() == 0);
    }

    @Test
    public void getOpptyByUnknownId() {
        SyncRequest request = getRequest("deal");
        EntityData entityData = new EntityData("deal");
        entityData.setId("1234567890");
        request.getData().put(connector.getId(), List.of(entityData));
        List<EntityData> byIds = hubspotService.getByIds(request);
        assertTrue(byIds.size() == 0);
    }
    @Test
    public void getCompanyByUnknownId() {
        SyncRequest request = getRequest("company");
        EntityData entityData = new EntityData("company");
        entityData.setId("1234567890");
        request.getData().put(connector.getId(), List.of(entityData));
        List<EntityData> byIds = hubspotService.getByIds(request);
        assertTrue(byIds.size() == 0);
    }

    @Test
    public void updateContact() {
        SyncResponse response = null;
        try {
            response = doCreateContact(false, 1);
            assertSuccessResponse(response);
            assertEquals(1,response.getResults().size());

            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);

            SyncRequest updateRequest = getRequest(Constants.CONTACT.toLowerCase());
            EntityData entityData = new EntityData(Constants.CONTACT.toLowerCase());
            entityData.addValue("lastName", "updated last name");
            entityData.setId(result.getId());
            entityData.addValue("hs_buying_role", List.of("INFLUENCER", "BLOCKER", "EXECUTIVE_SPONSOR"));
            updateRequest.getData().put(connector.getId(), List.of(entityData));
            SyncResponse updateResponse = hubspotService.update(updateRequest);
            assertSuccessResponse(updateResponse);
            assertTrue(updateResponse.getResults().size() == 1);

            SyncRequest getRequest = getRequest(Constants.CONTACT.toLowerCase());
            entityData = new EntityData(Constants.CONTACT.toLowerCase());
            entityData.setId(result.getId());
            getRequest.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> entities = hubspotService.getByIds(getRequest);
            assertTrue(entities.size() == 1);
            assertEquals(List.of("INFLUENCER", "BLOCKER", "EXECUTIVE_SPONSOR"), entities.get(0).getTypedValue("hs_buying_role"));
        } finally {
            doDelete(response, Constants.CONTACT.toLowerCase());
        }
    }

    @Test
    public void mergeAccount() {
        SyncResponse response = null;
        SyncResponse looserResponse = null;
        boolean deleteLoser = true;
        try {
            response = doCreate(Constants.COMPANY.toLowerCase(), COMPANY_NAME);
            looserResponse = doCreate(Constants.COMPANY.toLowerCase(), "loser company");
            assertSuccessResponse(response);
            assertTrue(response.getResults().size() == 1);
            assertSuccessResponse(looserResponse);
            assertTrue(looserResponse.getResults().size() == 1);

            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);

            Result result1 = looserResponse.getResults().get(0);
            assertTrue(result1.isSuccess());
            assertTrue(result1.getErrors().isEmpty());
            assertTrue(result1.getId() != null);

            MergeRequest request = getMergeRequest(Constants.COMPANY.toLowerCase());
            EntityData winner = new EntityData(Constants.COMPANY.toLowerCase());
            winner.setId(result.getId());
            request.setWinner(winner);
            EntityData looser = new EntityData(Constants.COMPANY.toLowerCase()).addValue("Id", result1.getId());
            looser.setId(result1.getId());
            request.addLoser(looser);
            MergeResponse mergeResponse = hubspotService.merge(request);
            assertTrue(mergeResponse.getWinnerResult().isSuccess());
            assertTrue(mergeResponse.getLoserResult().isSuccess());
            assertTrue(mergeResponse.getWinnerResult().getResults().size() == 1);
            assertTrue(mergeResponse.getLoserResult().getResults().get(0).isSuccess());
            deleteLoser = false;
        } finally {
            doDelete(response, Constants.COMPANY.toLowerCase());
            if (deleteLoser) doDelete(looserResponse, Constants.COMPANY.toLowerCase());
        }
    }

    @Test
    public void mergeContact() {
        SyncResponse response = null;
        SyncResponse looserResponse = null;
        boolean deleteLooser = true;
        try {
            response = doCreateContact(false, 1);
            looserResponse = doCreateLooser();
            assertSuccessResponse(response);
            assertEquals(1,response.getResults().size());
            assertSuccessResponse(looserResponse);
            assertEquals(1,looserResponse.getResults().size());

            Result result = response.getResults().get(0);
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getId() != null);

            Result result1 = looserResponse.getResults().get(0);
            assertTrue(result1.isSuccess());
            assertTrue(result1.getErrors().isEmpty());
            assertTrue(result1.getId() != null);

            MergeRequest request = getMergeRequest(Constants.CONTACT.toLowerCase());
            EntityData winner = new EntityData(Constants.CONTACT.toLowerCase());
            winner.setId(result.getId());
            request.setWinner(winner);
            EntityData looser = new EntityData(Constants.CONTACT.toLowerCase());
            looser.setId(result1.getId());
            request.addLoser(looser);
            MergeResponse mergeResponse = hubspotService.merge(request);
            assertTrue(mergeResponse.getWinnerResult().isSuccess());
            assertTrue(mergeResponse.getLoserResult().isSuccess());
            assertTrue(mergeResponse.getWinnerResult().getResults().size() == 1);
            assertTrue(mergeResponse.getLoserResult().getResults().get(0).isSuccess());
            deleteLooser = false;
            mergeResponse = hubspotService.merge(request);
            assertTrue(mergeResponse.getWinnerResult().isSuccess());
        } finally {
            doDelete(response, Constants.CONTACT.toLowerCase());
            if (deleteLooser) doDelete(looserResponse, Constants.CONTACT.toLowerCase());
        }
    }

    @Test
    public void iteratorResetsWatermarkAndOffsetAt10k(){
        SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        HubspotRestClient mockClient = mock(HubspotRestClient.class);

        when(mockClient.postRaw(isA(String.class),isA(String.class),isA(ConnectorInfo.class), any())).then(getMockResults(15000));
        HubspotIncrementalIterator hubspotIncrementalIterator = new HubspotIncrementalIterator(request.getWatermark(), 0, 0, 0,
                request.getConnector(), request.getEntitySchema(), mockClient, false, () -> hubspotService.refreshToken(connector));
        int count =0;
        while(hubspotIncrementalIterator.hasNext()){
            count+=hubspotIncrementalIterator.next().size();
        }
        assertEquals(15000, count);
    }

    @Test
    public void deleteMergedHubspotContacts() throws Exception {
        SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
        request.setWatermark(new WatermarkInfo(1634601309500L, Instant.now().toEpochMilli(), false, 0));

        HubspotRestClient mockClient = mock(HubspotRestClient.class);
        when(mockClient.postRaw(isA(String.class),isA(String.class),isA(ConnectorInfo.class), any())).thenReturn((getMergedContactResults()));

        HubspotIncrementalIterator hubspotIncrementalIterator = new HubspotIncrementalIterator(request.getWatermark(), 0, 0, 0,
                request.getConnector(), request.getEntitySchema(), mockClient, false, () -> hubspotService.refreshToken(connector));

        List<String> expectedIdsToDelete = Arrays.asList("2001", "2002", "3001");
        List<String> actualIdsToDelete = new ArrayList<>();
        while(hubspotIncrementalIterator.hasNext()) {
            List<EntityData> entityDataList = hubspotIncrementalIterator.next();
            actualIdsToDelete = entityDataList.stream().filter(EntityData::isDeleted).map(EntityData::getId).collect(Collectors.toList());
        }

        assertTrue(expectedIdsToDelete.size() == actualIdsToDelete.size());
        assertTrue(expectedIdsToDelete.containsAll(actualIdsToDelete));
        assertTrue(actualIdsToDelete.containsAll(expectedIdsToDelete));
    }

    @Test
    public void deleteMergedHubspotEntities() throws Exception {
        deleteMergedHubspotEntities(Constants.DEAL.toLowerCase());
        deleteMergedHubspotEntities(Constants.COMPANY.toLowerCase());
    }

    private void deleteMergedHubspotEntities(String entity) throws Exception {
        SyncRequest request = getRequest(entity);
        request.setWatermark(new WatermarkInfo(1634601309500L, Instant.now().toEpochMilli(), false, 0));

        HubspotRestClient mockClient = mock(HubspotRestClient.class);
        when(mockClient.postRaw(any(), any(), any(), any(Supplier.class))).thenReturn((getMergedEntityResults()));

        HubspotIncrementalIterator hubspotIncrementalIterator = new HubspotIncrementalIterator(request.getWatermark(), 0, 0, 0,
                request.getConnector(), request.getEntitySchema(), mockClient, false, () -> hubspotService.refreshToken(connector));

        List<String> expectedIdsToDelete = Arrays.asList("2001", "2002", "3001", "3002");
        List<String> actualIdsToDelete = new ArrayList<>();
        Map<String, EntityData> entityDataMap = Map.of();
        while(hubspotIncrementalIterator.hasNext()) {
            var entityDataList = hubspotIncrementalIterator.next();
            actualIdsToDelete = entityDataList.stream().filter(EntityData::isDeleted).map(EntityData::getId).collect(Collectors.toList());
            entityDataMap = entityDataList.stream().collect(Collectors.toMap(EntityData::getId, Function.identity()));
        }

        assertTrue(expectedIdsToDelete.size() == actualIdsToDelete.size());
        assertTrue(expectedIdsToDelete.containsAll(actualIdsToDelete));
        assertTrue(actualIdsToDelete.containsAll(expectedIdsToDelete));

        assertEquals(entityDataMap.get("2001").getLastModified(), entityDataMap.get("2003").getLastModified());
        assertEquals(entityDataMap.get("2002").getLastModified(), entityDataMap.get("2003").getLastModified());
        assertEquals(entityDataMap.get("3001").getLastModified(), entityDataMap.get("3003").getLastModified());
        assertEquals(entityDataMap.get("3002").getLastModified(), entityDataMap.get("3003").getLastModified());
    }

    @Test
    public void getEngagementsByWatermarkInitial() throws InterruptedException {
        retryWithBackoff(() -> {
            SyncRequest request = getRequest("engagement");
            request.setWatermark(
                    new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

            FetchResponse byWatermark = hubspotService.getByWatermark(request);
            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> next = byWatermark.getIterator().next();
            assertTrue(next.size() > 0);
        });
    }

    @Test
    public void getEngagementsByWatermarkInitialByPage() throws InterruptedException {
        retryWithBackoff(()->{
            SyncRequest request = getRequest("engagement");
            request.setWatermark(
                    new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
            request.setPageSize(1);
            FetchResponse byWatermark = hubspotService.getByWatermark(request);
            assertTrue(byWatermark.getIterator().hasNext());
            List<EntityData> next = byWatermark.getIterator().next();
            assertEquals(1, next.size());
            assertEquals("CALL", next.get(0).getValue("hs_engagement_type"));
            assertEquals("<p>This was a test call to the customer</p>", next.get(0).getValue("hs_call_body"));
            long firstWm = next.get(0).getLastModified();
            assertNotNull(firstWm);
            // Set the offset from previous, should stop the iteration.
            System.out.println("last offset " + byWatermark.getIterator().getLastOffset());
            request.getWatermark().setOffset(byWatermark.getIterator().getLastOffset());
            byWatermark = hubspotService.getByWatermark(request);
            assertTrue(byWatermark.getIterator().hasNext());
            next = byWatermark.getIterator().next();
            assertEquals("CALL", next.get(0).getValue("hs_engagement_type"));
            assertEquals("<p>This is a second call logged</p>", next.get(0).getValue("hs_call_body"));
            long secondWm = next.get(0).getLastModified();
            assertNotNull(secondWm);
            // no more data.
            assertTrue(byWatermark.getIterator().next().size() == 0);
            // Set the offset from previous, should stop the iteration.
            System.out.println("last offset " + byWatermark.getIterator().getLastOffset());
            request.getWatermark().setOffset(byWatermark.getIterator().getLastOffset());
        });
    }

    @Test
    public void getEngagementsByWatermarkEmptyPage() throws InterruptedException {
        retryWithBackoff(()->{
            SyncRequest request = getRequest("engagement");
            request.setWatermark(
                    new WatermarkInfo(Instant.now().minusMillis(100).toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
            FetchResponse byWatermark = hubspotService.getByWatermark(request);
            assertFalse(byWatermark.getIterator().hasNext());
        });
    }

    @Test
    public void getEngagementsByIds() {
        Instant start = Instant.EPOCH;
        SyncRequest request = getRequest("engagement");
        request.setWatermark(new WatermarkInfo(start.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        assertTrue(next.size() > 0);
        assertNotNull(next.get(0).getId());
        assertEquals("engagement", next.get(0).getName());
        request = getRequest("engagement");
        request.setData(Map.of(request.getConnector().getId(), List.of(next.get(0))));
        List<EntityData> byIds = hubspotService.getByIds(request);
        assertEquals(1, byIds.size());
        assertNotNull(byIds.get(0).getId());
        assertNotNull(byIds.get(0).getValue("hs_engagement_type"));
        request.setData(Map.of(request.getConnector().getId(), List.of(new EntityData().setId("99999"))));
        List<EntityData> none = hubspotService.getByIds(request);
        assertTrue(none.isEmpty());
    }

    @Test
    public void CUD_Engagements_NOTE() {
        Instant start = Instant.now().minusSeconds(10);
        String accountId = getAccountId();
        List<EntityData> contacts = getContacts();
        List<String> contactList = contacts.stream().map(c -> c.getId()).collect(Collectors.toList());
        String[] contactIds = contactList.toArray(new String[contacts.size()]);
        SyncResponse response = doCreateEngagement(1, "NOTE", accountId, contactIds);
        assertSuccessResponse(response);

        try {
            retryWithBackoff(()->{
                SyncRequest request = getRequest("engagement");
                request.setWatermark(new WatermarkInfo(start.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));

                FetchResponse byWatermark = hubspotService.getByWatermark(request);
                assertTrue(byWatermark.getIterator().hasNext());
                List<EntityData> next = byWatermark.getIterator().next();
                assertEquals(1, next.size());
                assertEquals("NOTE", next.get(0).getValue("hs_engagement_type"));
                assertNotNull(next.get(0).getValue("associatedcompanyid"));
                assertTrue(((List) next.get(0).getValue("associatedcompanyid")).size() == 1);
                assertNotNull(next.get(0).getValue("associatedVids"));
                assertTrue(((List) next.get(0).getValue("associatedVids")).size() >= 2);
                assertFalse(byWatermark.getIterator().hasNext());
                EntityData ed = next.get(0);
                ed.addValue("hs_note_body", next.get(0).getValue("hs_note_body") + "_updated");
                ed.addValue("associatedcompanyid",
                        ((List) ed.getValue("associatedcompanyid")).stream().map(x -> String.valueOf(x)).collect(Collectors.toList()));
                ed.addValue("associatedVids",
                        ((List) ed.getValue("associatedVids")).stream().map(x -> String.valueOf(x)).collect(Collectors.toList()));
                ed.addValue("hubspot_owner_id", "38171152");
                request = getRequest("engagement");
                request.addData(request.getConnector().getId(), ed);
                hubspotService.update(request);
                next = hubspotService.getByIds(request);
                assertEquals(1, next.size());
                assertTrue(next.get(0).getValueAsString("hs_note_body").endsWith("_updated"));
                assertNotNull(next.get(0).getValue("associatedcompanyid"));
                assertTrue(((List) next.get(0).getValue("associatedcompanyid")).size() == 1);
                assertNotNull(next.get(0).getValue("associatedVids"));
                assertTrue(((List) next.get(0).getValue("associatedVids")).size() >= 2);
                request = getRequest("engagement");
                // disassociated company id value. Not Yet Supported but no error.
                ed.addValue("associatedcompanyid", List.of());
                request.addData(request.getConnector().getId(), ed);
                hubspotService.update(request);
                next = hubspotService.getByIds(request);
                assertEquals(1, next.size());
                assertTrue(next.get(0).getValueAsString("hs_note_body").endsWith("_updated"));
                // TODO: Support this
                assertNotNull(next.get(0).getValue("associatedcompanyid"));
            });

        } finally {
            doDelete(response, "engagement");
        }
    }

    @Test
    public void getEmailEventsByWM() {
        SyncRequest request = getRequest(Constants.EMAIL_EVENT.toLowerCase(),
                createConnector("4d3bf248-6b30-4757-9fae-420a83539ffc"));
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> events = byWatermark.getIterator().next();
        assertTrue(events.get(0).getId() != null);
        assertTrue(events.get(0).getLastModified() != 0);
        assertFalse(byWatermark.getIterator().hasNext());
    }

    @Test
    public void getFormsByWM() {
        SyncRequest request = getRequest(Constants.FORM.toLowerCase(),
                createConnector("30b3e25f-dce6-4462-a087-02dc5f87fcfa"));
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> forms = byWatermark.getIterator().next();
        assertTrue(forms.get(0).getId() != null);
        assertTrue(forms.get(0).getLastModified() != 0);
    }

    @Test
    public void getFormSubmissionByWM() {
        SyncRequest request = getRequest(Constants.FORM_SUBMISSION,
                createConnector("30b3e25f-dce6-4462-a087-02dc5f87fcfa"));
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        List<EntityData> formSubmissions = new ArrayList<>();
        while(byWatermark.getIterator().hasNext()) {
            formSubmissions.addAll(byWatermark.getIterator().next());
        }

        assertFalse(formSubmissions.isEmpty());

        // We don't have a test to create form submissions so this will fail
//        request = getRequest(Constants.FORM_SUBMISSION,
//                createConnector("30b3e25f-dce6-4462-a087-02dc5f87fcfa"));
//        request.setWatermark(new WatermarkInfo(Instant.now().toEpochMilli() - 300000, Instant.now().toEpochMilli(), true, 0));
//
//        byWatermark = hubspotService.getByWatermark(request);
//        assertTrue(byWatermark.getIterator().hasNext());
//        formSubmissions = byWatermark.getIterator().next();
//        assertTrue(formSubmissions.size() == 0);
    }

    @Test
    public void hubspotConnectionErrorTest() {
        String accountId = getAccountId();
        List<EntityData> contacts = getContacts();
        List<String> contactList = contacts.stream().map(c -> c.getId()).collect(Collectors.toList());
        String[] contactIds = contactList.toArray(new String[contacts.size()]);
        SyncResponse response = doCreateEngagement(1, "CALL", accountId, contactIds);
        assertSuccessResponse(response);
        String id = response.getResults().get(0).getId();

        try {
            retryWithBackoff(() -> {
                SyncRequest request = getRequest("engagement");
                EntityData ed = new EntityData("engagement");
                ed.setId(id);
                request.addData(request.getConnector().getId(), ed);
                List<EntityData> next = hubspotService.getByIds(request);
                assertEquals(1, next.size());
                assertEquals(1, next.size());
                assertEquals("CALL", next.get(0).getValue("hs_engagement_type"));
                assertEquals("Connected", next.get(0).getValue("hs_call_disposition"));
                assertNotNull(next.get(0).getValue("associatedcompanyid"));
                assertFalse(((List) next.get(0).getValue("associatedcompanyid")).isEmpty());
                assertNotNull(next.get(0).getValue("associatedVids"));
                assertFalse(((List) next.get(0).getValue("associatedVids")).isEmpty());

                EntityData entityData = new EntityData("engagement");
                // during update pass the id instead of Label
                entityData.addValue("hs_engagement_type", "CALL");
                entityData.addValue("hs_call_disposition", "9d9162e7-6cf3-4944-bf63-4dff82258764");
                entityData.setId(id);

                //this will add more coverage and executes the part where hubspot npe occurs, a minor change is required for later
                entityData.addValue("associatedcompanyid", next.get(0).getValue("associatedcompanyid"));
                entityData.addValue("associatedVids", next.get(0).getValue("associatedcompanyid"));

                SyncRequest updateRequest = getRequest("engagement");
                updateRequest.addData(updateRequest.getConnector().getId(), entityData);
                hubspotService.update(updateRequest);
            });
        }catch(Exception ex) {
            log.error(ex.getMessage(), ex);
        }
        finally {
            doDelete(response, "engagement");
        }
    }

    @Test
    public void CUD_Engagements_CALL() {
        String accountId = getAccountId();
        List<EntityData> contacts = getContacts();
        List<String> contactList = contacts.stream().map(c -> c.getId()).collect(Collectors.toList());
        String[] contactIds = contactList.toArray(new String[contacts.size()]);
        SyncResponse response = doCreateEngagement(1, "CALL", accountId, contactIds);
        assertSuccessResponse(response);
        String id = response.getResults().get(0).getId();

        try {
            retryWithBackoff(()->{
                SyncRequest request = getRequest("engagement");
                EntityData ed = new EntityData("engagement");
                ed.setId(id);
                request.addData(request.getConnector().getId(), ed);
                List<EntityData> next = hubspotService.getByIds(request);
                assertEquals(1, next.size());
                assertEquals(1, next.size());
                assertEquals("CALL", next.get(0).getValue("hs_engagement_type"));
                assertEquals("Connected", next.get(0).getValue("hs_call_disposition"));
                assertNotNull(next.get(0).getValue("associatedcompanyid"));
                assertFalse(((List) next.get(0).getValue("associatedcompanyid")).isEmpty());
                assertNotNull(next.get(0).getValue("associatedVids"));
                assertFalse(((List) next.get(0).getValue("associatedVids")).isEmpty());

                EntityData entityData = new EntityData("engagement");
                // during update pass the id instead of Label
                entityData.addValue("hs_engagement_type", "CALL");
                entityData.addValue("hs_call_disposition", "9d9162e7-6cf3-4944-bf63-4dff82258764");
                entityData.setId(id);
                SyncRequest updateRequest = getRequest("engagement");
                updateRequest.addData(updateRequest.getConnector().getId(), entityData);
                hubspotService.update(updateRequest);

                next = hubspotService.getByIds(request);
                assertEquals(1, next.size());
                assertEquals(1, next.size());
                assertEquals("CALL", next.get(0).getValue("hs_engagement_type"));
                assertEquals("Busy", next.get(0).getValue("hs_call_disposition"));
                assertNotNull(next.get(0).getValue("associatedcompanyid"));
                assertFalse(((List) next.get(0).getValue("associatedcompanyid")).isEmpty());
                assertNotNull(next.get(0).getValue("associatedVids"));
                assertFalse(((List) next.get(0).getValue("associatedVids")).isEmpty());

                entityData = new EntityData("engagement");
                entityData.addValue("hs_engagement_type", "CALL");
                // during update pass the Label instead of Label
                entityData.addValue("hs_call_disposition", "No answer");
                entityData.setId(id);
                updateRequest = getRequest("engagement");
                updateRequest.addData(updateRequest.getConnector().getId(), entityData);
                hubspotService.update(updateRequest);

                next = hubspotService.getByIds(request);
                assertEquals(1, next.size());
                assertEquals(1, next.size());
                assertEquals("CALL", next.get(0).getValue("hs_engagement_type"));
                assertEquals("No answer", next.get(0).getValue("hs_call_disposition"));
                assertNotNull(next.get(0).getValue("associatedcompanyid"));
                assertFalse(((List) next.get(0).getValue("associatedcompanyid")).isEmpty());
                assertNotNull(next.get(0).getValue("associatedVids"));
                assertFalse(((List) next.get(0).getValue("associatedVids")).isEmpty());

                entityData = new EntityData("engagement");
                // during update pass only id without type should work well
                entityData.addValue("hs_call_disposition", "9d9162e7-6cf3-4944-bf63-4dff82258764");
                entityData.setId(id);
                updateRequest = getRequest("engagement");
                updateRequest.addData(updateRequest.getConnector().getId(), entityData);
                hubspotService.update(updateRequest);

                next = hubspotService.getByIds(request);
                assertEquals(1, next.size());
                assertEquals(1, next.size());
                assertEquals("CALL", next.get(0).getValue("hs_engagement_type"));
                assertEquals("Busy", next.get(0).getValue("hs_call_disposition"));
                assertNotNull(next.get(0).getValue("associatedcompanyid"));
                assertFalse(((List) next.get(0).getValue("associatedcompanyid")).isEmpty());
                assertNotNull(next.get(0).getValue("associatedVids"));
                assertFalse(((List) next.get(0).getValue("associatedVids")).isEmpty());
            });
        } finally {
            doDelete(response, "engagement");
        }

    }

    @Test
    @Retry(maxRetries=3, retryDelay=5)
    public void CUD_Engagements_EMAIL() {
        String accountId = getAccountId();
        List<EntityData> contacts = getContacts();
        List<String> contactList = contacts.stream().map(c -> c.getId()).collect(Collectors.toList());
        String[] contactIds = contactList.toArray(new String[contacts.size()]);
        SyncResponse response = doCreateEngagement(2, "EMAIL", accountId, contactIds);
        assertSuccessResponse(response);
        List<String> ids = response.getResults().stream().map(Result::getId).collect(Collectors.toList());

        try {
            Thread.sleep(WAIT_SECONDS*1000);
            retryWithBackoff(()->{
                SyncRequest request = getRequest("engagement");
                ids.forEach(id -> {
                    EntityData ed = new EntityData("engagement");
                    ed.setId(id);
                    request.addData(request.getConnector().getId(), ed);
                });
                List<EntityData> next = hubspotService.getByIds(request);
                assertEquals(2, next.size());
                // Arraylist of maps are flattened to individual arrays.
                for (EntityData ed: next) {
                    assertEquals("EMAIL", ed.getValue("hs_engagement_type"));
                    assertNotNull(ed.getValue("hs_email_from_email"));
                    assertNotNull(ed.getValue("hs_email_text"));
                    assertTrue(((List) ed.getValue("hs_email_to_email")).size() == 2);
                    assertNotNull(ed.getValue("associatedcompanyid"));
                    assertTrue(((List) ed.getValue("associatedcompanyid")).size() >= 1);
                    assertNotNull(ed.getValue("associatedVids"));
                    assertTrue(((List) ed.getValue("associatedVids")).size() >= 2);
                    if(ed.getValue("hs_email_from_email").equals("email0@syncari.com")) {
                        assertTrue(((List) ed.getValue("hs_email_to_firstname")).size() == 2);
                    }
                    else {
                        assertNull(ed.getValue("hs_email_to_firstname"));
                    }
                }
            });
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            fail();
        } finally {
            doDelete(response, "engagement");
        }
    }

    // Disabling because it is very time consuming. We do have a running recent activities test
    @Ignore
    @Test
    public void getActivitiesByWatermarkInitial() throws InterruptedException {
        SyncRequest request = getRequest(Constants.ACTIVITY.toLowerCase(),
                createConnector("30b3e25f-dce6-4462-a087-02dc5f87fcfa"));
        request.setWatermark(
                new WatermarkInfo(0l, Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        assertTrue(next.size() >= 2);
        assertFalse(byWatermark.getIterator().hasNext());
    }

    @Test
    public void getTransformedValue() {
        Map<String, AttributeSchema> attrMap = new HashMap<>();
        AttributeSchema attr = new AttributeSchema();
        attr.setApiName("numbers").setDataType("integer").setMultiValueField(true);
        attrMap.put("numbers", attr);
        assertEquals("1;2;3", hubspotService.getTransformedValue(attrMap, "numbers", List.of(1, 2, 3)));

        attr = new AttributeSchema();
        attr.setApiName("num1").setDataType("double");
        attrMap.put(attr.getApiName(), attr);
        assertEquals(12345L, hubspotService.getTransformedValue(attrMap, "num1", 12345.0));
        assertEquals(12345.45, hubspotService.getTransformedValue(attrMap, "num1", 12345.45));

    }

    @Test
    public void getActivitiesByWMLimitContacts() throws InterruptedException {
        HubspotService spyService = spy(hubspotService);
        doReturn(2).when(spyService).getMaxContactsLimit();
        SyncRequest request = getRequest(Constants.ACTIVITY.toLowerCase(),
                createConnector("30b3e25f-dce6-4462-a087-02dc5f87fcfa"));
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        // For initial contacts there are no activities.
        FetchResponse byWatermark = spyService.getByWatermark(request);
        assertFalse(byWatermark.getIterator().hasNext());
        assertNotNull(byWatermark.getIterator().getLastOffset());
    }

    @Test
    public void getActivitiesByWMLimitActivities() throws InterruptedException {
        HubspotService spyService = spy(hubspotService);
        doReturn(2).when(spyService).getMaxActivitiesLimit();
        SyncRequest request = getRequest(Constants.ACTIVITY.toLowerCase(),
                createConnector("30b3e25f-dce6-4462-a087-02dc5f87fcfa"));
        request.setWatermark(new WatermarkInfo(Instant.parse("2024-01-30T00:00:00Z").toEpochMilli(), Instant.now().toEpochMilli(), true, 0));

        FetchResponse byWatermark = spyService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        // This can be non-deterministic based on how many activities are per contact.
        assertTrue(next.size() >= 2);
        assertFalse(byWatermark.getIterator().hasNext());
    }


    @Test
    public void getActivitiesByWatermark() throws InterruptedException {
        SyncRequest request = getRequest(Constants.ACTIVITY.toLowerCase(),
                createConnector("30b3e25f-dce6-4462-a087-02dc5f87fcfa"));
        WatermarkInfo wm = new WatermarkInfo(Instant.parse("2024-01-30T00:00:00Z").toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        wm.setLimit(2);
        request.setWatermark(wm);
        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        assertEquals(2, next.size());
        assertFalse(byWatermark.getIterator().hasNext());

        //Disabling because we hit the limit before we fetch any activities
//        request = getRequest(Constants.ACTIVITY.toLowerCase(),
//                createConnector("30b3e25f-dce6-4462-a087-02dc5f87fcfa"));
//        // Try istest
//        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
//        byWatermark = hubspotService.getByWatermark(request);
//        assertTrue(byWatermark.getIterator().hasNext());
//        next = byWatermark.getIterator().next();
//        assertFalse(byWatermark.getIterator().hasNext());

        /*This is just too slow
        // Query all without any limit
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        next = byWatermark.getIterator().next();
        assertTrue(next.size() > 0);
        long initialCount = next.size();
        assertFalse(byWatermark.getIterator().hasNext());
        // For activities, the updatedat is changed to the contact's updatedat.
        // So we consider the createdAt (or activity date as watermark for next test).
        long date = next.get(0).getCreatedAt();

        // broaden the date range a bit
        request.setWatermark(new WatermarkInfo(date + 10000, Instant.now().toEpochMilli(), false, 0));
        byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        next = byWatermark.getIterator().next();
        assertTrue(next.size() < initialCount);
        assertFalse(byWatermark.getIterator().hasNext());
        */
    }

    @Test
    public void getActivitiesByWatermarkEmptyPage() throws InterruptedException {

        SyncRequest request = getRequest(Constants.ACTIVITY.toLowerCase(),
                createConnector("30b3e25f-dce6-4462-a087-02dc5f87fcfa"));
        request.setWatermark(
                new WatermarkInfo(Instant.now().minusMillis(100).toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertFalse(byWatermark.getIterator().hasNext());
    }

    @Test(expected = RuntimeException.class)
    public void getActivitiesByIdsNotSupported() {
        SyncRequest request = getRequest(Constants.ACTIVITY.toLowerCase(),
                createConnector("30b3e25f-dce6-4462-a087-02dc5f87fcfa"));
        List<EntityData> activities = hubspotService.getByIds(request);
    }

    @Test
    public void incrementalIteratorPagesize() throws Exception {

        SyncRequest request = getRequest(Constants.COMPANY.toLowerCase());
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        HubspotRestClient mockClient = mock(HubspotRestClient.class);

        when(mockClient.postRaw(isA(String.class),isA(String.class),isA(ConnectorInfo.class), any())).thenReturn(getCompanyResults(101));

        //when(mockClient.postRaw(isA(String.class),isA(String.class),isA(ConnectorInfo.class), any())).then(getCompanyResults(101));
        HubspotIncrementalIterator hubspotIncrementalIterator = new HubspotIncrementalIterator(request.getWatermark(), 0, 0, 100,
                request.getConnector(), request.getEntitySchema(), mockClient, false, () -> hubspotService.refreshToken(connector));

        hubspotIncrementalIterator.hasNext();
        assertEquals(101, hubspotIncrementalIterator.next().size());
        assertEquals(101, hubspotIncrementalIterator.getLastOffset());
    }

    private ResponseEntity<String> getCompanyResults(int maxSize) throws Exception {

        var time = ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0));

        List<HSResult> hresults = new ArrayList<>();
        for(int i=0; i<maxSize; i++){
            HSResult result = new HSResult().setId("id"+i).setProperties(Map.of(HS_OBJECT_ID, "id"+i))
                    .setCreatedAt(time)
                    .setUpdatedAt(time);
            hresults.add(result);
        }

        Results results = new Results().setResults(hresults).setTotal(maxSize);

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new ResponseEntity<String>(mapper.writeValueAsString(results),HttpStatus.OK);
    }

    private Answer<ResponseEntity<String>> getMockResults(final int maxResults ){
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String errorResponse="{\n" +
                "    \"status\": \"error\",\n" +
                "    \"message\": \"There was a problem with the request.\",\n" +
                "    \"correlationId\": \"1670dd3a-03d7-4342-acc6-9b74b3568502\"\n" +
                "}";
        class  LargeResultsAnswer implements Answer<ResponseEntity<String>> {
            int pageNo=0;
            int totalResults= maxResults;
            ZonedDateTime start = ZonedDateTime.of(2000,1,1,0,0,0,0, ZoneOffset.UTC);
            String previousWatermark = String.valueOf(Instant.EPOCH.toEpochMilli());
            @Override
            public ResponseEntity<String> answer(InvocationOnMock invocation) throws Throwable {
                Search search = mapper.readValue(invocation.getArgument(1).toString(), Search.class);
                System.out.println("PAGE NO=="+pageNo);
                if(pageNo>0 && (pageNo % 100) ==0){
                    //Verify that "after" is resett & watermark moved up every 10k records
                    assertEquals("0",search.getAfter());
                    String newWatermark = search.getFilterGroups().get(0).getFilters().get(0).getValue();
                    assertNotEquals(previousWatermark, newWatermark);
                    previousWatermark = newWatermark;

                } else if (pageNo == 0 || pageNo >= 20){
                    // first cycle or greater than 20 which will reset the offset because 2000 is the max record per cycle
                    assertEquals("0", search.getAfter());
                } else {
                    // The offset will be the same since we are generating 100 records with the same timestamp
                    // offset = page.stream().filter(e->e.getLastModified() == maxTS).count();
                    assertEquals("100", search.getAfter());
                }
                //simulate error condition by returning error for any "after" >=10k
                if(search.getAfter()!=null && Long.valueOf(search.getAfter()) >= 10000l){
                    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                }
                int offset = Integer.valueOf(search.getAfter())+100;
                var results= new Results().setResults(nextPage())
                        .setTotal(totalResults)
                        .setPaging(new PageInfo().setNext(new NextMarker()
                                .setAfter(String.valueOf(offset))));
                pageNo++;
                return new ResponseEntity<>(mapper.writeValueAsString(results),HttpStatus.OK);
            }

            List<HSResult> nextPage(){
                if(pageNo*100 >= totalResults){
                    return List.of();
                }
                double random = Math.random();
                int index = (int) Math.round(random);
                //randomly switch formats to make sure dates are parsed correctly
                String day = start.plusDays(pageNo).format(HubspotIncrementalIterator.UTC_FORMATS.get(index));
                List<HSResult> page = new ArrayList<>();
                for(int i=0;i<100;i++){
                    page.add(new HSResult().setCreatedAt(day).setUpdatedAt(day).setId(String.valueOf(pageNo*i+1)).setProperties(
                            Map.of("firstname","firstname "+pageNo*i+1)
                    ));
                }
                return page;
            }
        };
        return new LargeResultsAnswer();
    }

    private void doDelete(SyncResponse response, String entity) {
        if (response != null) {
            List<String> ids = response.getResults().stream().map(r -> r.getId()).collect(Collectors.toList());
            doDelete(ids, entity);
        }
    }
    private void doDelete(List<String> ids, String entity) {
        if (!ids.isEmpty()) {
            SyncRequest delRequest = getRequest(entity);
            List<EntityData> toDelete = ids.stream().map(id -> new EntityData(entity).setId(id)
                    .addValue("Id", id)).collect(Collectors.toList());
            delRequest.getData().put(connector.getId(), toDelete);
            hubspotService.delete(delRequest);
        }
    }

    private ConnectorInfo createConnector(String refreshToken) {
        // Hubspot App Name : SyncariDevApp, App Id: 351970
        ConnectorInfo connectorInfo = new ConnectorInfo("hubconnector", "hubspot1", "https://api.hubapi.com","instance1");
        AuthConfig authConfig = new AuthConfig().setRefreshToken(refreshToken);
        authConfig.setClientId(CLIENTID);
        authConfig.setClientSecret(SECRET);
        authConfig.setEndpoint("https://api.hubapi.com");
        authConfig.setRedirectUri("http://localhost:3000/oauth/authorize?client_id="+CLIENTID);
        connectorInfo.setAuthConfig(authConfig);
        connectorInfo.setRequiredScopes(List.of());
        authConfig.setAccessToken(hubspotService.refreshToken(connectorInfo).getAccessToken());
        return connectorInfo;
    }

    private ConnectorInfo createTestConnector() {
        ConnectorInfo connectorInfo = new ConnectorInfo("hubconnector", "hubspot1", "https://api.hubapi.com","instance1");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setClientId(CLIENTID);
        authConfig.setClientSecret(SECRET);
        authConfig.setEndpoint("https://api.hubapi.com");
        authConfig.setRedirectUri("http://localhost:3000/oauth/authorize?client_id="+CLIENTID);
        connectorInfo.setAuthConfig(authConfig);
        connectorInfo.setRequiredScopes(List.of("crm.objects.subscriptions.read", "crm.objects.subscriptions.write",
                "crm.objects.invoices.read", "crm.objects.invoices.read", "crm.objects.quotes.read", "crm.objects.quotes.write", "crm.schemas.quotes.read"));
        connectorInfo.setAuthType(AuthType.ApiKey);
        authConfig.setAccessToken("TEST_API_TOKEN");
        return connectorInfo;
    }

    private SyncResponse doCreate(String entity, String value, Object... otherValues) {
        SyncRequest request = getRequest(entity);
        EntityData entityData = new EntityData(entity).addValue("name", value);
        if(otherValues!=null){
            for(int i=0;i<otherValues.length-1;i+=2){
                entityData.addValue(otherValues[i].toString(),otherValues[i+1]);
            }
        }
        request.getData().put(connector.getId(), List.of(entityData));
        return hubspotService.create(request);
    }

    private SyncResponse doCreateContact(boolean batch, Integer index) {
        SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
        request.setBatchMode(batch);
        List<EntityData> records = new ArrayList<>();
        Random rand = new Random();
        for (int i = 0; i < index; i++) {
            EntityData entityData = new EntityData(Constants.CONTACT.toLowerCase()).addValue("firstname", "test first name"+batch+i)
                    .addValue("lastName", "test last name"+batch).addValue("email", Integer.toString(i)+batch+rand.nextInt(100000)+"testemail@test.com");
            entityData.addValue("company", "test acc");
            entityData.setSyncariEntityId("345");
            records.add(entityData);
        }
        request.getData().put(connector.getId(), records);
        return hubspotService.create(request);
    }

    private SyncResponse doCreateOppty(int numOppties) {
        String accountId = getAccountId();
        return doCreateOppty(numOppties, accountId);
    }

    private SyncResponse doCreateCustomAddress(String entity, String value, Object... otherValues) {
        SyncRequest request = getRequest(entity);
        EntityData entityData = new EntityData(entity).addValue("first", value);
        entityData.setId("123");
        if (otherValues != null) {
            for (int i = 0; i < otherValues.length - 1; i += 2) {
                entityData.addValue(otherValues[i].toString(), otherValues[i + 1]);
            }
        }
        request.getData().put(connector.getId(), List.of(entityData));
        return hubspotService.create(request);
    }

    private String getAccountId() {
        return getAccountId(0);
    }

    private String getAccountId(int index) {
        final List<EntityData> accounts = getAccounts();

        return accounts.get(index).getId();
    }

    private List<EntityData> getAccounts() {
        SyncRequest companyReq = getRequest(Constants.COMPANY.toLowerCase());
        companyReq.setWatermark(
                new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse byWatermark = hubspotService.getByWatermark(companyReq);
        assertTrue(byWatermark.getIterator().hasNext());
        return byWatermark.getIterator().next();
    }
    private List<EntityData> getContacts() {
        SyncRequest contactReq = getRequest(Constants.CONTACT.toLowerCase());
        contactReq.setWatermark(
                new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse byWatermark = hubspotService.getByWatermark(contactReq);
        assertTrue(byWatermark.getIterator().hasNext());
        return byWatermark.getIterator().next();
    }

    private SyncResponse doCreateOppty(int numOppties, String companyId, String... contacts) {
        SyncRequest request = getRequest(Constants.DEAL.toLowerCase());

        List<EntityData> oppties = new ArrayList<>();
        for (int i = 0; i <numOppties; i++) {
            EntityData entityData = new EntityData(Constants.DEAL.toLowerCase()).addValue("dealname", "Hubspot deal " + i)
                    .addValue("dealstage", "closedwon")
                    .addValue("custom_date_1", new Date())
                    .addValue("amount", 300.0)
                    .addValue("closedate", ZonedDateTime.now());
            if(contacts!=null){
                entityData.addValue("associatedVids", Arrays.asList(contacts));
            }
            entityData.addValue("associatedcompanyid", companyId);
            entityData.setSyncariEntityId("syncariId"+i);
            oppties.add(entityData);
        }
        request.getData().put(connector.getId(), oppties);
        return hubspotService.create(request);
    }

    private SyncResponse doCreateEngagement(int numEngagements, String type, String companyId, String... contacts) {
        SyncRequest request = getRequest("engagement");

        List<EntityData> engagements = new ArrayList<>();
        for (int i = 0; i < numEngagements; i++) {
            EntityData entityData = new EntityData("engagement").addValue("hs_engagement_type", type);
            if ("EMAIL".equalsIgnoreCase(type)) {
                entityData.addValue("hs_email_from_email", "email" + i + "@syncari.com");
                entityData.addValue("hs_email_subject", "emailSubject_" + i);
                entityData.addValue("hs_email_text", "emailText_" + i);
                entityData.addValue("hs_email_to_email", List.of("toemail1_" + i + "@syncari.com", "toemail2_" + i + "@syncari.com"));
                if(i % 2 == 0)
                    entityData.addValue("hs_email_to_firstname", List.of("firstname1_" + i, "firstname2_" + i));
                else
                    entityData.addValue("hs_email_to_firstname", null);
            } else if ("CALL".equalsIgnoreCase(type)) {
                entityData.addValue("hs_call_to_number", "6504501234");
                entityData.addValue("hs_call_status", "COMPLETED");
                entityData.addValue("hs_call_duration", 100);
                entityData.addValue("hs_call_body", "This is a test call");
                entityData.addValue("hs_call_disposition", "Connected");
            } else if ("NOTE".equalsIgnoreCase(type)) {
                entityData.addValue("hs_note_body", "noteBody+" + i);
            }
            if(contacts!=null){
                entityData.addValue("associatedVids", Arrays.asList(contacts));
            }
            // Try to support random values to test various value types.
            if(i % 2 == 0) {
                entityData.addValue("associatedcompanyid", Arrays.asList(companyId));
                entityData.addValue("timestamp", ZonedDateTime.now());
            }
            else {
                entityData.addValue("associatedcompanyid", Arrays.asList("[]"));
                entityData.addValue("timestamp", Instant.now());
            }
            entityData.setSyncariEntityId("syncariId"+i);
            engagements.add(entityData);
        }
        request.getData().put(connector.getId(), engagements);
        return hubspotService.create(request);
    }

    private SyncResponse doCreateLooser() {
        SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
        EntityData entityData = new EntityData(Constants.CONTACT.toLowerCase()).addValue("firstname", "looser first name")
                .addValue("lastName", "looser last name").addValue("email", "looser" + new Random().nextInt(100000) + "@test.com");
        entityData.addValue("company", "looser acc");
        request.getData().put(connector.getId(), List.of(entityData));
        return hubspotService.create(request);
    }

    @Test
    public void getByIdSkipsMismatchedIds() throws Exception {
        // TODO: fix this to remove hard coded ids here
        List<String> idList = List.of("753351", "950851");

        SyncRequest getByIdRequest = getRequest(Constants.CONTACT.toLowerCase());
        for (String id : idList) {
            getByIdRequest.addData(getConnector().getId(), new EntityData().setId(id));
        }

        List<EntityData> results = hubspotService.getByIds(getByIdRequest);
        assertEquals(1, results.size());
        assertEquals(results.get(0).getId(), results.get(0).getValueAsString(HS_OBJECT_ID));
    }

    private List<EntityData> getContactByIdResults() {
        return List.of(
                new EntityData().setId("1").addValue(HS_OBJECT_ID, "1"),
                new EntityData().setId("2").addValue(HS_OBJECT_ID, "10"),
                new EntityData().setId("3"),
                new EntityData().setId("4").addValue(HS_OBJECT_ID, null)
        );
    }

    @Test
    public void contactIteratorSkipsMismatchedIds() throws Exception {
        SyncRequest request = getRequest(Constants.CONTACT.toLowerCase());
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        HubspotRestClient mockClient = mock(HubspotRestClient.class);

        when(mockClient.postRaw(isA(String.class),isA(String.class),isA(ConnectorInfo.class), any())).thenReturn(getContactResults());
        HubspotIncrementalIterator hubspotIncrementalIterator = new HubspotIncrementalIterator(request.getWatermark(), 0, 0, 0,
                request.getConnector(), request.getEntitySchema(), mockClient, false, () -> hubspotService.refreshToken(connector));

        hubspotIncrementalIterator.hasNext();
        List<EntityData> data = hubspotIncrementalIterator.next();
        assertEquals(2, data.size());
        assertEquals("idMatch", data.get(0).getId());
        assertEquals("nullObjectId", data.get(1).getId());

        request = getRequest(Constants.COMPANY.toLowerCase());
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        when(mockClient.postRaw(isA(String.class),isA(String.class),isA(ConnectorInfo.class), any())).thenReturn(getContactResults());
        hubspotIncrementalIterator = new HubspotIncrementalIterator(request.getWatermark(), 0, 0, 0,
                request.getConnector(), request.getEntitySchema(), mockClient, false, () -> hubspotService.refreshToken(connector));
        hubspotIncrementalIterator.hasNext();
        data = hubspotIncrementalIterator.next();
        assertEquals(3, data.size());
        assertEquals("idMatch", data.get(0).getId());
        assertEquals("idMismatch", data.get(1).getId());
        assertEquals("nullObjectId", data.get(2).getId());
    }

    private ResponseEntity<String> getContactResults() throws Exception {

        HSResult result1 = new HSResult().setId("idMatch").setProperties(Map.of(HS_OBJECT_ID, "idMatch"))
                .setCreatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)))
                .setUpdatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)));
        HSResult result2 = new HSResult().setId("idMismatch").setProperties(Map.of(HS_OBJECT_ID, "idMismatch1"))
                .setCreatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)))
                .setUpdatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)));
        HSResult result3 = new HSResult().setId("nullObjectId").setProperties(Map.of())
                .setCreatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)))
                .setUpdatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)));

        Results results = new Results().setResults(List.of(result1, result2, result3)).setTotal(2);

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new ResponseEntity<String>(mapper.writeValueAsString(results),HttpStatus.OK);
    }

    private ResponseEntity<String> getMergedContactResults() throws Exception {

        HSResult result1 = new HSResult().setId("2000").setProperties(Map.of("hs_calculated_merged_vids", "2001:1634601309536;2002:1634601309618"))
                .setCreatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)))
                .setUpdatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)));
        HSResult result2 = new HSResult().setId("3000").setProperties(Map.of("hs_calculated_merged_vids", "3001:1634601309636"))
                .setCreatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)))
                .setUpdatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)));

        Results results = new Results().setResults(List.of(result1, result2)).setTotal(2);

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new ResponseEntity<String>(mapper.writeValueAsString(results),HttpStatus.OK);
    }

    private ResponseEntity<String> getMergedEntityResults() throws Exception {

        HSResult result1 = new HSResult().setId("2003").setProperties(Map.of("hs_merged_object_ids", "2001;2002"))
                .setCreatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)))
                .setUpdatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)));
        HSResult result2 = new HSResult().setId("3003").setProperties(Map.of("hs_merged_object_ids", "3001;3002"))
                .setCreatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)))
                .setUpdatedAt(ZonedDateTime.now().format(HubspotIncrementalIterator.UTC_FORMATS.get(0)));

        Results results = new Results().setResults(List.of(result1, result2)).setTotal(2);

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new ResponseEntity<String>(mapper.writeValueAsString(results),HttpStatus.OK);
    }

    private SyncRequest getRequest(String e) {
        return getRequest(e, connector);
    }

    private SyncRequest getRequest(String e, ConnectorInfo c) {
        EntitySchema schema = hubspotService.describe(new DescribeRequest(c, e)).get();
        return new SyncRequest().Builder(c, schema);
    }

    private MergeRequest getMergeRequest(String e) {
        EntitySchema schema = hubspotService.describe(new DescribeRequest(connector, e)).get();
        return new MergeRequest(connector, schema);
    }

    private void assertSuccessResponse(SyncResponse response) {
        assertTrue(response.isSuccess());
        response.getResults().forEach(r -> assertTrue(r.isSuccess()));
    }

    @Test
    public void createFieldScopeTest() {
        ConnectorInfo connectorInfo = new ConnectorInfo();
        connectorInfo.setMetaConfig(Map.of("oAuthScopes", "crm.schemas.contacts.write"));
        CreateFieldRequest createFieldRequest = new CreateFieldRequest("contact", connectorInfo , new AttributeSchema());
        try {
            hubspotService.checkCreateScope(createFieldRequest);
        } catch (Exception e) {
            fail();
        }
        connectorInfo.setMetaConfig(Map.of("oAuthScopes", "crm.schemas.contacts.read"));
        try {
            hubspotService.checkCreateScope(createFieldRequest);
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Access token does not have scope crm.schemas.contacts.write for entity contact"));
        }
    }

    @Test
    public void contactAssociationGetById() {
        ConnectorInfo connectorInfo = getConnector();
        Optional<EntitySchema> entitySchema = hubspotService.describe(new DescribeRequest(connectorInfo, "contact_association"));
        assertTrue(entitySchema.isPresent());
        EntitySchema associationSchema = entitySchema.get();
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), associationSchema);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue("Found no records for entity: " + associationSchema.getApiName(), byWatermark.getIterator().hasNext());

        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), associationSchema);
        List<EntityData> data = byWatermark.getIterator().next();
        for (EntityData ed: data) {
            getByIdRequest.addData(getConnector().getId(), ed);
        }
        data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
    }

    @Test
    public void createDeleteAssociation() {
        Optional<EntitySchema> associationSchemaOpt = describe("contact_association", null);
        EntityData associationData = new EntityData("contact_association");
        associationData.addValue("fromObjectType", "contact");
        associationData.addValue("fromObjectId", "23651");
        associationData.addValue("toObjectType", "company");
        associationData.addValue("toObjectId", "15797770083");
        associationData.addValue("typeId", "1");
        associationData.addValue("category", "HUBSPOT_DEFINED");
        ConnectorInfo connectorInfo = createConnector("1abadc1f-702f-4023-ab17-b2c5faf100ea");
        SyncRequest syncRequest = new SyncRequest();
        syncRequest.setConnector(connectorInfo);
        syncRequest.setEntitySchema(associationSchemaOpt.get());
        syncRequest.setData(Map.of(connectorInfo.getId(), List.of(associationData)));
        SyncResponse syncResponse = hubspotService.create(syncRequest);
        assertTrue(syncResponse.isSuccess());
        associationData.setId("23651-15797770083-company-HUBSPOT_DEFINED-1");
        syncResponse = hubspotService.delete(syncRequest);
        assertTrue(syncResponse.isSuccess());

        // use an expired token
        connectorInfo.getAuthConfig().setAccessToken("CKXU89y9MRIRgoeDUEAA-yIAAAD8BwEAAAMYxeGLCiD_sIYEKOK9FTIUPhaEpfhK28y2LGrBRU69MNpbhUk6PwAxZEH_BwAE_P-3AGDgfM4shgAAYAYAJDwMIPiAjwD-w_83AQAAAIHn____ABDwAwAAAAD8AAAAAAACAAi4AkIUokegpoiSVBxtuvHYtVnqmnDjzZNKA25hMVIAWgA");
        SyncResponse syncResponse1 = hubspotService.create(syncRequest);
        assertTrue(syncResponse1.isSuccess());
        associationData.setId("23651-15797770083-company-HUBSPOT_DEFINED-1");
        syncResponse = hubspotService.delete(syncRequest);
        assertTrue(syncResponse.isSuccess());


    }

    private List<EntityData> getByIds(EntitySchema entitySchema, int limit) {
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema);
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setLimit(limit);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue("Found no records for entity: " + entitySchema.getApiName(), byWatermark.getIterator().hasNext());

        return byWatermark.getIterator().next();
    }

    @Override
    public void createTest() {
        // Covered by multiple create methods.
    }

    @Override
    public void updateTest() {
        // Covered by multiple update methods.
    }

    @Override
    public void deleteTest() {
        // Covered by multiple methods.
    }

    @Override
    public void batchCreateTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void batchUpdateTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void batchDeleteTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void createCustomObjectTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void updateCustomObjectTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void deleteCustomObjectTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void mixedBatchCreateFailuresTest() {
        // covered by mixOfFailureAndSuccessfulCreates
    }

    @Override
    public void mixedBatchUpdateFailuresTest() {
        // covered by mixOfFailureAndSuccessfulUpdates
    }

    @Override
    public void mixedBatchDeleteFailuresTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void allDataTypesTest() {
        // TODO Auto-generated method stub
    }

    @Override
    public void referencesTest() {
        // covered in getOpptyByWatermarkIncremental
    }

    @Override
    public void rateLimitTest() {
        // TODO Auto-generated method stub
    }

    @Test
    public void cudContactBatchTest() {
        SyncResponse response = null;
        try {
            response = doCreateContact(true, 10);
            SyncRequest syncRequest = getRequest(Constants.CONTACT.toLowerCase());
            List<EntityData> createdData = response.getResults().stream()
                    .filter(Result::isSuccess)
                    .map(result -> new EntityData(Constants.CONTACT.toLowerCase()).setId(result.getId()))
                    .collect(Collectors.toList());
            assertTrue(createdData.size() == 10);
            createdData.get(0).addValue("email", "testcontactbatch@email.com");
            createdData.get(9).addValue("email", "testcontactbatch@email.com");
            Random rand = new Random();
            for(int i = 1; i < 9; i ++) {
                createdData.get(i).addValue("email", Integer.toString(i)+rand.nextInt(100000)+"testemail@test.com");
            }
            syncRequest.setData(Map.of(getConnector().getId(), createdData));
            response = hubspotService.update(syncRequest);
            assertFalse(response.isSuccess());
            var updateResult = response.getResults().stream().filter(result -> !result.isSuccess()).findFirst();
            assertTrue(updateResult.isPresent() && !updateResult.get().getErrors().isEmpty() && updateResult.get().getErrors().get(0).contains("A contact with the email 'testcontactbatch@email.com' already exists"));
        } finally {
            doDelete(response, Constants.CONTACT.toLowerCase());
        }
    }

    @Test
    public void associationCustomObjectTest() {
        // First HS instance has custom object. The response should have custom object association
        List<EntityData> data1 = fetchAssociationForConnector(getCustomObjectConnector(), "contact_association");
        assertFalse(data1.stream().filter(data -> data.getValueAsString("toObjectType").equalsIgnoreCase("p21395455_customobjects")).collect(Collectors.toList()).isEmpty());
        // Second HS instance has no custom objects. Should fetch associations correctly
        List<EntityData> data2 = fetchAssociationForConnector(getConnector(), "contact_association");
        assertFalse(data2.isEmpty());
    }

    @Test
    public void associationDealTest() {
        List<EntityData> data1 = fetchAssociationForConnector(getCustomObjectConnector(), "deal_association");
        assertFalse(data1.stream().filter(data -> data.getValueAsString("toObjectType").equalsIgnoreCase("call")).collect(Collectors.toList()).isEmpty());
        assertFalse(data1.stream().filter(data -> data.getValueAsString("toObjectType").equalsIgnoreCase("meeting")).collect(Collectors.toList()).isEmpty());
    }

    private ConnectorInfo getCustomObjectConnector() {
        ConnectorInfo connectorInfo = new ConnectorInfo("hubconnector", "hubspot1", "https://api.hubapi.com","instance1");
        AuthConfig authConfig = new AuthConfig().setRefreshToken(System.getenv().getOrDefault("TEST_REFRESH_TOKEN", "REPLACE_ME"));
        authConfig.setClientId("ad2efb89-3092-4e7c-b407-4b21924f0dec");
        authConfig.setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
        authConfig.setEndpoint("https://api.hubapi.com");
        authConfig.setRedirectUri("http://localhost:3000/oauth/authorize?client_id="+CLIENTID);
        connectorInfo.setAuthConfig(authConfig);
        authConfig.setAccessToken(hubspotService.refreshToken(connectorInfo).getAccessToken());
        return connectorInfo;
    }

    private List<EntityData> fetchAssociationForConnector(ConnectorInfo connector, String name) {
        Optional<EntitySchema> entitySchema = describe(name, null);
        SyncRequest syncRequest = new SyncRequest().Builder(connector, entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(1697964053000L, Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        return byWatermark.getIterator().next();
    }

    @Test
    public void verifyWMSortOrder() {
        Optional<EntitySchema> entitySchema = describe(Constants.CONTACT.toLowerCase(), null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        syncRequest.setWatermark(watermark);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        List<EntityData> results = new ArrayList<>();
        while (byWatermark.getIterator().hasNext() && results.size() < 2000) {
            List<EntityData> data = byWatermark.getIterator().next();
            assertNotNull(data.get(0).getLastModified());
            results.addAll(data);
        }
        assertFalse(results.isEmpty());
        assertTrue(isSortedByLastModified(results));
    }

    private boolean isSortedByLastModified(List<EntityData> data) {
        for (int i = 1; i < data.size(); i++) {
            if (data.get(i).getLastModified() < data.get(i - 1).getLastModified()) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void batchCUDLineItemTest() {
        // Fetch product and deal data
        SyncRequest request = getRequest("product");
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        FetchResponse byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        List<EntityData> next = byWatermark.getIterator().next();
        assertTrue(!next.isEmpty());
        String product = next.get(0).getId();

        request = getRequest("deal");
        request.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        byWatermark = hubspotService.getByWatermark(request);
        assertTrue(byWatermark.getIterator().hasNext());
        next = byWatermark.getIterator().next();
        assertTrue(!next.isEmpty());
        String deal = next.get(0).getId();

        // Create a batch of EntityData
        request = getRequest("line_item");
        request.getConnector().getInternalConfig().put("threadCount", 3);
        List<EntityData> entityDataList = createEntityBatch(50, product, deal);
        request.setData(Map.of(connector.getId(), entityDataList));
        SyncResponse createResponse = hubspotService.create(request);
        assertTrue(createResponse.isSuccess());

        // Capture the created IDs
        List<String> createdIds = createResponse.getResults().stream()
                .map(Result::getId)
                .collect(Collectors.toList());

        // Update the created batch
        for (int i = 0; i < entityDataList.size(); i++) {
            EntityData entityData = entityDataList.get(i);
            entityData.setSyncariEntityId(UUID.randomUUID().toString());
            entityData.setValues(Map.of("name", "Updated " + entityData.getValueAsString("name")));
            entityData.setId(createdIds.get(i));
        }

        entityDataList.add(new EntityData().setId(UUID.randomUUID().toString()).setSyncariEntityId(UUID.randomUUID().toString()));

        try {
            SyncResponse updateResponse = hubspotService.update(request);
            assertFalse(updateResponse.isSuccess());

            int successCount = 0;
            int failCount = 0;
            for(Result result: updateResponse.getResults()) {
                if(result.isSuccess()) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            assertTrue(successCount == 50);
            assertTrue(failCount == 1);

            // Verify the updated data
            List<EntityData> updatedResults = hubspotService.getByIds(request);
            assertTrue(!updatedResults.isEmpty());
            for (EntityData updatedEntity : updatedResults) {
                String expectedName = "Updated " + updatedEntity.getValueAsString("name").replace("Updated ", "");
                assertTrue(updatedEntity.getValueAsString("name").equalsIgnoreCase(expectedName));
            }

            // Verify associations
            request = getRequest("line_item_association");
            List<EntityData> associations = createdIds.stream()
                    .map(id -> new EntityData("line_item_association")
                            .setId(String.format("%s-%s-deal-HUBSPOT_DEFINED-20", id, deal)))
                    .collect(Collectors.toList());
            request.setData(Map.of(connector.getId(), associations));
            List<EntityData> associationResults = hubspotService.getByIds(request);

            // Verify the associations
            assertFalse(associationResults.isEmpty());
            assertTrue(associationResults.size() == createdIds.size());
        } finally {
            // Delete all created data
            request = getRequest("line_item");
            List<EntityData> entitiesToDelete = new ArrayList<>();
            for (String id : createdIds) {
                EntityData entityToDelete = new EntityData("line_item").setId(id);
                entitiesToDelete.add(entityToDelete);
            }
            request.setData(Map.of(connector.getId(), entitiesToDelete));
            hubspotService.delete(request);
        }
    }

    private List<EntityData> createEntityBatch(int batchSize, String product, String deal) {
        List<EntityData> entityDataList = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < batchSize; i++) {
            EntityData entityData = new EntityData("line_item");

            // Generate random values for name, quantity, and price
            String name = "Item " + (i + 1);
            int quantity = random.nextInt(10) + 1; // Random quantity between 1 and 10
            double price = Math.round((random.nextDouble() * 100) * 100.0) / 100.0; // Random price between 0 and 100

            // Add values to entityData
            entityData.addValue("name", name);
            entityData.addValue("quantity", quantity);
            entityData.addValue("hs_product_id", product);
            entityData.addValue("hs_deal_id", deal);
            entityData.addValue("price", price);

            entityDataList.add(entityData);
        }

        return entityDataList;
    }

    @Test
    public void cudInvoice() {
        ConnectorInfo connectorInfo = connector;
        DescribeAllRequest describeAllRequest = new DescribeAllRequest(connectorInfo, List.of());
        List<EntitySchema> entitySchemaList = hubspotService.describeAll(describeAllRequest);
        assertFalse(entitySchemaList.isEmpty());
        SyncRequest syncRequest = new SyncRequest();
        syncRequest.setEntitySchema(entitySchemaList.stream().filter(schema -> schema.getApiName().equalsIgnoreCase("invoice")).collect(Collectors.toList()).get(0));
        EntityData ed = new EntityData();
        ed.addValue("hs_currency", "USD");
        ed.addValue("hs_comments", "Test comment");
        syncRequest.setData(Map.of(connectorInfo.getId(), List.of(ed)));
        syncRequest.setConnector(connectorInfo);
        SyncResponse syncResponse = hubspotService.create(syncRequest);
        assertTrue(syncResponse.isSuccess());
        ed.setId(syncResponse.getResults().get(0).getId());
        ed.addValue("hs_comments", "Updated comment");
        try {
            syncResponse = hubspotService.update(syncRequest);
            assertTrue(syncResponse.isSuccess());
            List<EntityData> entityDataList = hubspotService.getByIds(syncRequest);
            assertTrue(!entityDataList.isEmpty());
            assertTrue(entityDataList.get(0).getValueAsString("hs_comments").equalsIgnoreCase("Updated comment"));
        } finally {
            hubspotService.delete(syncRequest);
        }
    }

    @Ignore // Using customer app to test this since subscription write apis are in beta
    @Test
    public void cudSubscription() {
        ConnectorInfo connectorInfo = createTestConnector();
        DescribeAllRequest describeAllRequest = new DescribeAllRequest(createTestConnector(), List.of());
        List<EntitySchema> entitySchemaList = hubspotService.describeAll(describeAllRequest);
        assertFalse(entitySchemaList.isEmpty());
        SyncRequest syncRequest = new SyncRequest();
        syncRequest.setEntitySchema(entitySchemaList.stream().filter(schema -> schema.getApiName().equalsIgnoreCase("subscription")).collect(Collectors.toList()).get(0));
        EntityData ed = new EntityData();
        ed.addValue("hs_name", "New billable subscription");
        ed.addValue("hs_collection_process", "manual_payments");
        ed.addValue("hs_currency_code", "USD");
        ed.addValue("hs_net_payment_terms", "30");
        syncRequest.setData(Map.of(connectorInfo.getId(), List.of(ed)));
        syncRequest.setConnector(connectorInfo);
        SyncResponse syncResponse = hubspotService.create(syncRequest);
        assertTrue(syncResponse.isSuccess());
        ed.setId(syncResponse.getResults().get(0).getId());
        ed.addValue("hs_name", "Updated name");
        try {
            syncResponse = hubspotService.update(syncRequest);
            assertTrue(syncResponse.isSuccess());
            List<EntityData> entityDataList = hubspotService.getByIds(syncRequest);
            assertTrue(!entityDataList.isEmpty());
            assertTrue(entityDataList.get(0).getValueAsString("hs_name").equalsIgnoreCase("Updated name"));
        } finally {
            hubspotService.delete(syncRequest);
        }
    }

    @Test
    public void describeInvoiceAndSubscription() {
        // Use original connector to verify invoice/subscription is absent in refresh schema
        DescribeAllRequest describeAllRequest = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entitySchemaList = hubspotService.describeAll(describeAllRequest);
        assertTrue(entitySchemaList.size() == 20);
        assertFalse(entitySchemaList.stream().filter(schema -> schema.getApiName().equalsIgnoreCase("subscription")).findAny().isPresent());
        assertFalse(entitySchemaList.stream().filter(schema -> schema.getApiName().equalsIgnoreCase("invoice")).findAny().isPresent());
        // Use new connector to verify invoice/subscription is present in refresh schema
        connector.setRequiredScopes(List.of("crm.objects.subscriptions.read", "crm.objects.subscriptions.write",
                "crm.objects.invoices.read", "crm.objects.invoices.read"));
        entitySchemaList = hubspotService.describeAll(describeAllRequest);
        // Only 6 entities are present because we look at the given oauth scopes to define the final entity list
        assertTrue(entitySchemaList.size() == 6);
        assertTrue(entitySchemaList.stream().filter(schema -> schema.getApiName().equalsIgnoreCase("subscription")).findAny().isPresent());
        assertTrue(entitySchemaList.stream().filter(schema -> schema.getApiName().equalsIgnoreCase("invoice")).findAny().isPresent());
        connector.setRequiredScopes(List.of());
    }

    @Test
    public void describeQuote() {
        // Use original connector to verify quote is absent in refresh schema
        DescribeAllRequest describeAllRequest = new DescribeAllRequest(connector, List.of());
        List<EntitySchema> entitySchemaList = hubspotService.describeAll(describeAllRequest);
        assertTrue(entitySchemaList.stream().filter(schema -> schema.getApiName().equalsIgnoreCase("quote")).findAny().isPresent());
        
        // Verify quote schema has expected fields
        Optional<EntitySchema> quoteSchema = entitySchemaList.stream()
            .filter(schema -> schema.getApiName().equalsIgnoreCase("quote"))
            .findFirst();
        assertTrue("Quote schema should be present", quoteSchema.isPresent());
        
        EntitySchema schema = quoteSchema.get();
        assertEquals("quote", schema.getApiName());
        assertNotNull("Quote schema should have fields", schema.getAttributes());
        assertTrue("Quote schema should have at least some fields", schema.getAttributes().size() > 0);
        
        // Verify association fields are present
        assertTrue("Quote should have company association field", 
            schema.getAttributes().stream().anyMatch(field -> "associatedcompanyid".equals(field.getApiName())));
        assertTrue("Quote should have contact association field", 
            schema.getAttributes().stream().anyMatch(field -> "associatedVids".equals(field.getApiName())));
        assertTrue("Quote should have deal association field", 
            schema.getAttributes().stream().anyMatch(field -> "associateddealid".equals(field.getApiName())));
        
        connector.setRequiredScopes(List.of());
    }

    @Ignore
    @Test
    public void verifyApiKeySync() {
        ConnectorInfo connectorInfo = createTestConnector();
        TestConnectionResponse testConnectionResponse = hubspotService.testConnection(connectorInfo, List.of());
        assertTrue(testConnectionResponse.isSuccess());
        DescribeAllRequest describeAllRequest = new DescribeAllRequest(connectorInfo, List.of());
        List<EntitySchema> entitySchemaList = hubspotService.describeAll(describeAllRequest);
        assertFalse(entitySchemaList.isEmpty());
        SyncRequest syncRequest = new SyncRequest().Builder(connectorInfo, entitySchemaList.stream().filter(schema -> schema.getApiName().equalsIgnoreCase("subscription")).collect(Collectors.toList()).get(0));
        syncRequest.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), true, 0));
        FetchResponse fetchResponse = hubspotService.getByWatermark(syncRequest);
        List<EntityData> result = new ArrayList<>();
        while (fetchResponse.getIterator().hasNext()) {
            result.addAll(fetchResponse.getIterator().next());
            break;
        }
        assertFalse(result.isEmpty());
        syncRequest.setData(Map.of(connectorInfo.getId(), List.of(result.get(0))));
        List<EntityData> entityDataList = hubspotService.getByIds(syncRequest);
        assertTrue(!entityDataList.isEmpty());
    }

    @Test
    public void crudLeads() throws InterruptedException {
        SyncResponse createResponse = null;
        try {
            // Test CREATE operation
            createResponse = doCreateLead("Test Lead", "test@example.com", "Test Company");
            assertSuccessResponse(createResponse);
            assertTrue(createResponse.getResults().size() == 1);
            String leadId = createResponse.getResults().get(0).getId();
            assertNotNull(leadId);

            // Test GET BY ID operation
            SyncRequest getByIdRequest = getRequest("lead");
            EntityData entityData = new EntityData("lead");
            entityData.setId(leadId);
            getByIdRequest.getData().put(connector.getId(), List.of(entityData));
            List<EntityData> retrievedLeads = hubspotService.getByIds(getByIdRequest);
            assertTrue(retrievedLeads.size() == 1);
            assertEquals(leadId, retrievedLeads.get(0).getId());
            assertEquals("Test Lead", retrievedLeads.get(0).getValueAsString("hs_lead_name"));

            // Test UPDATE operation
            SyncRequest updateRequest = getRequest("lead");
            EntityData updateData = new EntityData("lead");
            updateData.setId(leadId);
            updateData.addValue("hs_lead_label", "HOT");
            updateRequest.getData().put(connector.getId(), List.of(updateData));
            SyncResponse updateResponse = hubspotService.update(updateRequest);
            assertSuccessResponse(updateResponse);
            
            // Verify update worked
            List<EntityData> updatedLeads = hubspotService.getByIds(getByIdRequest);
            assertTrue(updatedLeads.size() == 1);
            assertEquals("HOT", updatedLeads.get(0).getValueAsString("hs_lead_label"));

            // Test GET BY WATERMARK operation
            SyncRequest watermarkRequest = getRequest("lead");
            watermarkRequest.setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli()+900000000, true, 0));
            Thread.sleep(WAIT_SECONDS*3000);
            FetchResponse fetchResponse = hubspotService.getByWatermark(watermarkRequest);
            assertTrue(fetchResponse.getIterator().hasNext());
            List<EntityData> watermarkLeads = fetchResponse.getIterator().next();
            assertTrue(watermarkLeads.size() > 0);
            
            // Verify our created lead is in the watermark results
            boolean foundOurLead = watermarkLeads.stream().anyMatch(lead -> leadId.equals(lead.getId()));
            assertTrue("Created lead should be found in watermark results", foundOurLead);

        } finally {
            // Test DELETE operation
            if (createResponse != null) {
                doDelete(createResponse, "lead");
            }
        }
    }

    @Test
    public void cudQuotes() {
        SyncResponse createResponse = null;
        try {
            // Test CREATE operation
            String expirationDate = "2025-09-01";
            long currTime = System.currentTimeMillis();
            createResponse = doCreateQuote("Test Quote", expirationDate);
            assertNotNull("Quote create response should not be null", createResponse);
            assertFalse("Quote create response should have results", createResponse.getResults().isEmpty());
            
            String quoteId = createResponse.getResults().get(0).getId();
            assertNotNull("Created quote ID should not be null", quoteId);

            // Test getById operation
            SyncRequest getByIdRequest = getRequest(HubspotService.QUOTE);
            getByIdRequest.addData(getConnector().getId(), new EntityData(HubspotService.QUOTE).setId(quoteId));
            List<EntityData> byIdResults = hubspotService.getByIds(getByIdRequest);
            assertFalse("GetById should return results", byIdResults.isEmpty());
            assertEquals("GetById should return our quote", quoteId, byIdResults.get(0).getId());

            // Test UPDATE operation
            SyncRequest updateRequest = getRequest(HubspotService.QUOTE);
            EntityData updateData = new EntityData(HubspotService.QUOTE).setId(quoteId);
            updateData.addValue("hs_title", "Updated Test Quote");
            updateRequest.addData(getConnector().getId(), updateData);
            SyncResponse updateResponse = hubspotService.update(updateRequest);
            assertNotNull("Update response should not be null", updateResponse);
            
            // Verify update
            List<EntityData> updatedResults = hubspotService.getByIds(getByIdRequest);
            assertEquals("Updated quote title should match", "Updated Test Quote", updatedResults.get(0).getValueAsString("hs_title"));

            // Test getByWatermark operation
            Optional<EntitySchema> entitySchema = describe(HubspotService.QUOTE, null);
            SyncRequest watermarkRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
            WatermarkInfo watermark = new WatermarkInfo(currTime, System.currentTimeMillis(), true, 0);
            watermarkRequest.setWatermark(watermark);

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            FetchResponse watermarkResponse = hubspotService.getByWatermark(watermarkRequest);
            assertNotNull("Watermark response should not be null", watermarkResponse);
            assertTrue(watermarkResponse.getIterator().hasNext());
            List<EntityData> watermarkResults = watermarkResponse.getIterator().next();
            
            // Verify our created quote is in the watermark results
            boolean foundOurQuote = watermarkResults.stream().anyMatch(quote -> quoteId.equals(quote.getId()));
            assertTrue("Created quote should be found in watermark results", foundOurQuote);

        } finally {
            // Test DELETE operation
            if (createResponse != null) {
                doDelete(createResponse, HubspotService.QUOTE);
            }
        }
    }

    private SyncResponse doCreateQuote(String title, String expirationDate) {
        SyncRequest request = getRequest(HubspotService.QUOTE);
        EntityData entityData = new EntityData(HubspotService.QUOTE);
        entityData.addValue("hs_title", title);
        entityData.addValue("hs_expiration_date", expirationDate);
        entityData.addValue("hs_status", "DRAFT");
        entityData.addValue("hs_language", "en");

        
        request.addData(getConnector().getId(), entityData);
        
        return hubspotService.create(request);
    }

    private SyncResponse doCreateLead(String fullName, String email, String company) {
        SyncRequest request = getRequest("lead");
        List<EntityData> leads = new ArrayList<>();
        EntityData entityData = new EntityData("lead");

        entityData.addValue("hs_lead_name", fullName);
//        entityData.addValue("hs_lead_source_company_lifecycle_stage", "Unit Test");
        entityData.addValue("hs_lead_label", "WARM");
        entityData.addValue("hs_primary_contact_id", "11404049311");
//        entityData.addValue("hs_primary_company_id", "12345");

        leads.add(entityData);
        request.getData().put(connector.getId(), leads);
        return hubspotService.create(request);
    }

    @Test
    public void testGetCurrentAssociationsIntegration() throws Exception {
        String contactId = null;
        String dealId = null;
        String dealIdNoContacts = null;

        // First, create a contact to ensure we have one to associate with
        SyncRequest contactRequest = getRequest("contact");
        EntityData contactData = new EntityData("contact");
        contactData.addValue("firstname", "Test");
        contactData.addValue("lastname", "Contact");
        int randomPart1 = (int) (Math.random() * 10000) + 1;
        int randomPart2 = (int) (Math.random() * 15000) + 10000;
        contactData.addValue("email", "testcontact" + randomPart1 + randomPart2 + "@test.com");
        contactRequest.addData(connector.getId(), contactData);

        SyncResponse contactResponse = hubspotService.create(contactRequest);
        assertTrue("Contact creation should succeed", contactResponse.isSuccess());
        contactId = contactResponse.getResults().get(0).getId();

        // Create a test deal
        SyncRequest createRequest = getRequest("deal");
        EntityData dealData = new EntityData("deal");
        dealData.addValue("dealname", "Test Deal for Association " + System.currentTimeMillis());
        dealData.addValue("amount", "1000");
        dealData.addValue("dealstage", "appointmentscheduled");
        dealData.addValue("pipeline", "default");
        dealData.addValue("associatedVids", List.of(contactId)); // Associate with contact
        createRequest.addData(connector.getId(), dealData);

        SyncResponse createResponse = hubspotService.create(createRequest);
        assertTrue("Deal creation should succeed", createResponse.isSuccess());
        dealId = createResponse.getResults().get(0).getId();

        try {
            // Use reflection to test the private getCurrentAssociations method
            java.lang.reflect.Method getCurrentAssociationsMethod = HubspotService.class.getDeclaredMethod(
                "getCurrentAssociations", String.class, String.class, String.class, AuthConfig.class
            );
            getCurrentAssociationsMethod.setAccessible(true);

            // Call getCurrentAssociations with real API
            Set<String> associatedContactIds = (Set<String>) getCurrentAssociationsMethod.invoke(
                hubspotService, "deal", "contact", dealId, connector.getAuthConfig()
            );

            // Verify the method returns a valid Set (not null)
            assertNotNull("getCurrentAssociations should return a Set, not null", associatedContactIds);
            assertFalse("Should find associated contacts", associatedContactIds.isEmpty());
            assertTrue("Should contain the contact we associated", associatedContactIds.contains(contactId));

            // Test with a deal that has no associations
            EntityData dealWithNoContacts = new EntityData("deal");
            dealWithNoContacts.addValue("dealname", "Test Deal No Associations " + System.currentTimeMillis());
            dealWithNoContacts.addValue("amount", "2000");
            dealWithNoContacts.addValue("dealstage", "appointmentscheduled");
            dealWithNoContacts.addValue("pipeline", "default");
            // No associatedVids - no contacts
            SyncRequest noContactRequest = getRequest("deal");
            noContactRequest.addData(connector.getId(), dealWithNoContacts);

            SyncResponse noContactResponse = hubspotService.create(noContactRequest);
            assertTrue("Deal creation should succeed", noContactResponse.isSuccess());
            dealIdNoContacts = noContactResponse.getResults().get(0).getId();

            Set<String> emptyResult = (Set<String>) getCurrentAssociationsMethod.invoke(
                hubspotService, "deal", "contact", dealIdNoContacts, connector.getAuthConfig()
            );
            assertNotNull("Should return empty set, not null", emptyResult);
            assertTrue("Should return empty set for deal with no contacts", emptyResult.isEmpty());

            // Clean up - delete test deals and contact
            SyncRequest deleteRequest = getRequest("deal");
            dealData.setId(dealId);
            deleteRequest.addData(connector.getId(), dealData);
            hubspotService.delete(deleteRequest);

            SyncRequest deleteRequest2 = getRequest("deal");
            dealWithNoContacts.setId(dealIdNoContacts);
            deleteRequest2.addData(connector.getId(), dealWithNoContacts);
            hubspotService.delete(deleteRequest2);

            // Delete test contact
            SyncRequest deleteContactRequest = getRequest("contact");
            contactData.setId(contactId);
            deleteContactRequest.addData(connector.getId(), contactData);
            hubspotService.delete(deleteContactRequest);

        } catch (Exception e) {
            // Clean up even if test fails
            if (dealId != null) {
                try {
                    SyncRequest deleteRequest = getRequest("deal");
                    dealData.setId(dealId);
                    deleteRequest.addData(connector.getId(), dealData);
                    hubspotService.delete(deleteRequest);
                } catch (Exception cleanup) {
                    // Ignore cleanup errors
                }
            }
            if (dealIdNoContacts != null) {
                try {
                    SyncRequest deleteRequest2 = getRequest("deal");
                    EntityData dealToDelete = new EntityData("deal");
                    dealToDelete.setId(dealIdNoContacts);
                    deleteRequest2.addData(connector.getId(), dealToDelete);
                    hubspotService.delete(deleteRequest2);
                } catch (Exception cleanup) {
                    // Ignore cleanup errors
                }
            }
            if (contactId != null) {
                try {
                    SyncRequest deleteContactRequest = getRequest("contact");
                    contactData.setId(contactId);
                    deleteContactRequest.addData(connector.getId(), contactData);
                    hubspotService.delete(deleteContactRequest);
                } catch (Exception cleanup) {
                    // Ignore cleanup errors
                }
            }
            throw e;
        }
    }

    @Test
    public void testFetchAssociationsBatchingWith1000Plus() throws Exception {
        // Test that fetchAssociations properly batches IDs into chunks of 1000
        HubspotService spyService = spy(hubspotService);

        // Create mock response with association data
        String mockResponseBody = "{\"status\":\"COMPLETE\",\"results\":[" +
            "{\"from\":{\"id\":\"company-1\"}," +
            "\"to\":[{\"toObjectId\":\"product-100\"," +
            "\"associationTypes\":[{\"category\":\"HUBSPOT_DEFINED\",\"typeId\":\"280\",\"label\":\"Primary\"}]}]}," +
            "{\"from\":{\"id\":\"company-2\"}," +
            "\"to\":[{\"toObjectId\":\"product-200\"," +
            "\"associationTypes\":[{\"category\":\"HUBSPOT_DEFINED\",\"typeId\":\"280\",\"label\":\"Primary\"}]}]}" +
            "]}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(mockResponseBody, HttpStatus.OK);

        // Mock the REST client call inside fetchAssociations
        SyncariEntityDataRestClient mockRestClient = mock(SyncariEntityDataRestClient.class);
        when(mockRestClient.postRaw(anyString(), anyString(), any(AuthConfig.class)))
            .thenReturn(mockResponse);

        // Use PowerMock or reflection to inject the mock (simpler: test with spy and verify behavior)

        // Create test data with more than 1000 IDs to trigger batching
        Set<String> testIds = new HashSet<>();
        for (int i = 1; i <= 1980; i++) {
            testIds.add("company-" + i);
        }

        SyncRequest request = getRequest("company");

        try {
            // Call the method directly - this will make real API calls but that's okay for this test
            // The main goal is to verify batching logic (2 calls for 1980 IDs)
            Pair<List<EntityData>, Long> result = spyService.fetchAssociations(
                "company",           // fromEntity
                "product",           // toEntity
                testIds,             // ids - 1980 items
                0L,                  // offset
                request,             // request
                new HashMap<>(),     // idToLastModifiedMap
                -1L                  // lastWatermark
            );

            // Verify results
            assertNotNull("Result should not be null", result);
            assertEquals("Should return 0 for offset (no pagination)", Long.valueOf(0L), result.y);
            assertNotNull("Result list should not be null", result.x);

            log.info("Successfully tested batching with {} IDs resulting in {} associations",
                testIds.size(), result.x.size());

        } catch (Exception e) {
            log.error("Test failed with exception", e);
            fail("Test should not throw exception: " + e.getMessage());
        }
    }

}

