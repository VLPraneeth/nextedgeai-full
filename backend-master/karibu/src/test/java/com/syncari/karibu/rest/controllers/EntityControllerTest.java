package com.syncari.karibu.rest.controllers;

import com.jayway.jsonpath.JsonPath;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.EntityScore;
import com.syncari.connector.FieldScore;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.*;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.AttributeDefinitionCache;
import com.syncari.core.repositories.customer.EntityDefinitionCache;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.TagService;
import com.syncari.karibu.rest.request.CreateSyncariEntityRequest;
import com.syncari.karibu.rest.request.FieldRequest;
import com.syncari.karibu.rest.util.EntityTestUtil;
import com.syncari.karibu.rest.util.OauthUtil;
import com.syncari.karibu.rest.util.SchemaUtils;
import com.syncari.utils.DateUtil;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EntityControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    OauthUtil oauthUtil;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    TagService tagService;

    @Autowired
    EntityTestUtil entityTestUtil;

    @Autowired
    ConnectorMetadataService metaService;

    @Autowired
    EntityRepo entityRepo;

    @Autowired
    AttributeDefinitionCache attrRepo;

    @Autowired
    IdMappingRepo mappingRepo;

    @Autowired
    EntityDefinitionCache defRepo;

    @Autowired
    SchemaUtils schemaUtils;

    private static final String DATA_STUDIO_ACCOUNT = "DataStudioAccount";
    static Connector c1;
    static Connector c2;
    static EntityDefinition entityDef;
    static EntityDefinition hubspotEntity;
    static EntityDefinition netsuiteEntity;
    static EntityDefinition updateEntity;
    static EntityData e1;
    static EntityData e2;
    static EntityData e3;
    static EntityData e4;
    private int expectedColumns;

    @Override
    public void setUp() {
        super.setUp();
        if(c1 == null) {
            c1 = new Connector("Hubspot", metaService.findByName(Constants.HUBSPOT).get().getId(), "");
            c1 = connectorService.save(c1);
            c2 = new Connector("Netsuite", metaService.findByName(Constants.NETSUITE).get().getId(), "");
            c2 = connectorService.save(c2);
            entityDef = getEntity();
            hubspotEntity = getHubspotEntity();
            netsuiteEntity = getNetsuiteEntity();
            updateEntity = getEntityForValidation();
            createRecords();
        }
        expectedColumns = 10;

    }

    // ------------------------------------- getEntities ---------------------------------------------------------------

    @Test
    public void getEntitiesTest5Records() {
        try {
            Connector connector = connectorService.getSyncariConnector();

            EntityDefinition entityDefinition = schemaService.getEntity(connector.getId(), "account");
            tagService.assign(Map.of("entity tag 1", true), Taggable.entity, entityDefinition.getId());

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultEntities = mockMvc.perform(get("/api/v1/entities")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("synapseId", connector.getId())
                            .param("status", "approved")
                            .param("includeFields", "false")
                            .param("limit", "5"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(5)))
                    .andExpect(jsonPath("$.result.[0].apiName", is("account")))
                    .andExpect(jsonPath("$.result.[0].fields").doesNotExist())
                    .andExpect(jsonPath("$.result.[0].tags", hasSize(1)))
                    .andExpect(jsonPath("$.result.[0].tags.[0]", is("entity tag 1")))
                    .andExpect(status().isOk());

            ResultActions resultEntity = mockMvc.perform(get("/api/v1/entities/{entityId}", entityDefinition.getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("account")))
                    .andExpect(jsonPath("$.result.fields").doesNotExist())
                    .andExpect(jsonPath("$.result.tags", hasSize(1)))
                    .andExpect(jsonPath("$.result.tags.[0]", is("entity tag 1")))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void getEntitiesTestMaxRecords() {
        try {
            Connector connector = connectorService.getSyncariConnector();

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("synapseId", connector.getId())
                            .param("status", "approved")
                            .param("includeFields", "false"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(greaterThanOrEqualTo(11))))
                    .andExpect(jsonPath("$.result.[0].apiName", is("account")))
                    .andExpect(jsonPath("$.result.[0].fields").doesNotExist())
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void getEntitiesTestDraftRecords() {
        try {
            //
            Connector connector = connectorService.getSyncariConnector();

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("synapseId", connector.getId())
                            .param("status", "draft")
                            .param("includeFields", "false"))
                    .andDo(print())
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Ignore
    @Test
    public void getEntitiesTestWithFields() {
        try {
            Connector connector = connectorService.getSyncariConnector();
            EntityDefinition entityDefinition = schemaService.getEntity(connector.getId(), "account");
            AttributeDefinition attributeDefinition = schemaService.getAttributeByName(entityDefinition.getId(), "AboutUs");
            tagService.assign(Map.of("field tag 1", true), Taggable.attribute, attributeDefinition.getId());

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("synapseId", connector.getId())
                            .param("status", "approved")
                            .param("includeFields", "true")
                            .param("limit", "5"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(5)))
                    .andExpect(jsonPath("$.result.[0].apiName", is("account")))
                    .andExpect(jsonPath("$.result.[0].fields", hasSize(38)))
                    // fix this, not based on index
                    .andExpect(jsonPath("$.result.[0].fields.[0].apiName", is("AboutUs")))
                    .andExpect(jsonPath("$.result.[0].fields.[0].tags", hasSize(1)))
                    .andExpect(jsonPath("$.result.[0].fields.[0].tags.[0]]", is("field tag 1")))
                    .andExpect(status().isOk());

            ResultActions resultEntity = mockMvc.perform(get("/api/v1/entities/{entityId}", entityDefinition.getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("includeFields", "true"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("account")))
                    .andExpect(jsonPath("$.result.fields", hasSize(38)))
                    .andExpect(jsonPath("$.result.fields.[0].apiName", is("AboutUs")))
                    .andExpect(jsonPath("$.result.fields.[0].tags", hasSize(1)))
                    .andExpect(jsonPath("$.result.fields.[0].tags.[0]]", is("field tag 1")))
                    .andExpect(status().isOk());

            ResultActions resultEntityFields = mockMvc.perform(get("/api/v1/entities/{entityId}/fields", entityDefinition.getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(38)))
                    .andExpect(jsonPath("$.result.[0].apiName", is("AboutUs")))
                    .andExpect(jsonPath("$.result.[0].tags", hasSize(1)))
                    .andExpect(jsonPath("$.result.[0].tags.[0]]", is("field tag 1")))
                    .andExpect(status().isOk());

            ResultActions resultEntityField = mockMvc.perform(get("/api/v1/entities/{entityId}/fields/{fieldId}", entityDefinition.getId(), attributeDefinition.getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("AboutUs")))
                    .andExpect(jsonPath("$.result.tags", hasSize(1)))
                    .andExpect(jsonPath("$.result.tags.[0]]", is("field tag 1")))
                    .andExpect(status().isOk());




        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void getEntitiesTestBadSynapseId() {
        try {
            //
            Connector connector = connectorService.getSyncariConnector();

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("synapseId", "badSynapseId")
                            .param("status", "approved")
                            .param("includeFields", "false"))
                    .andDo(print())
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void getEntitiesTestBadStatus() {
        try {
            //
            Connector connector = connectorService.getSyncariConnector();

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("synapseId", connector.getId())
                            .param("status", "badStatus")
                            .param("includeFields", "false"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void getEntitiesTestBadIncludeFields() {
        try {
            //
            Connector connector = connectorService.getSyncariConnector();

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("synapseId", connector.getId())
                            .param("status", "approved")
                            .param("includeFields", "badIncludeFields"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void getEntitiesTestBadLimit() {
        try {
            //
            Connector connector = connectorService.getSyncariConnector();

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("synapseId", connector.getId())
                            .param("status", "approved")
                            .param("limit", "200")
                            .param("includeFields", "badIncludeFields"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    // ------------------------------------- getEntityById -------------------------------------------------------------

    @Test
    public void getEntityByIdTest() {
        try {
            Connector connector = connectorService.getSyncariConnector();
            List<EntityDefinition> entityDefinitionList = schemaService.getEntities(connector.getId());

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities/{entityId}", entityDefinitionList.get(0).getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("account")))
                    .andExpect(jsonPath("$.result.fields").doesNotExist())
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Ignore
    @Test
    public void getEntityByIdTestWithFields() {
        try {
            Connector connector = connectorService.getSyncariConnector();
            List<EntityDefinition> entityDefinitionList = schemaService.getEntities(connector.getId());

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities/{entityId}", entityDefinitionList.get(0).getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("includeFields", "true"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("account")))
                    .andExpect(jsonPath("$.result.fields", hasSize(38)))
                    .andExpect(jsonPath("$.result.fields.[0].apiName", is("AboutUs")))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void getEntityByIdTestBadEntityId() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities/{entityId}", "badEntityId")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    // ------------------------------------- getFields -----------------------------------------------------------------

    @Ignore
    @Test
    public void getFieldsTest() {
        try {
            Connector connector = connectorService.getSyncariConnector();
            List<EntityDefinition> entityDefinitionList = schemaService.getEntities(connector.getId());

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities/{entityId}/fields", entityDefinitionList.get(0).getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(38)))
                    .andExpect(jsonPath("$.result.[0].apiName", is("AboutUs")))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void getFieldsTestBadEntityId() {
        try {
            Connector connector = connectorService.getSyncariConnector();
            List<EntityDefinition> entityDefinitionList = schemaService.getEntities(connector.getId());

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities/{entityId}/fields", "badEntityId")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }
    }


    // ------------------------------------- getFieldById --------------------------------------------------------------

    @Test
    public void getFieldByIdTest() {
        try {
            Connector connector = connectorService.getSyncariConnector();
            List<EntityDefinition> entityDefinitionList = schemaService.getEntities(connector.getId());
            List<AttributeDefinition> attributeDefinitionList = schemaService.getActiveAttributes(connector.getId(), "account");

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities/{entityId}/fields/{fieldId}", entityDefinitionList.get(0).getId(), attributeDefinitionList.get(0).getId() )
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("AboutUs")))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void getFieldByIdTestBadEntityId() {
        try {
            Connector connector = connectorService.getSyncariConnector();
            List<EntityDefinition> entityDefinitionList = schemaService.getEntities(connector.getId());
            List<AttributeDefinition> attributeDefinitionList = schemaService.getActiveAttributes(connector.getId(), "account");

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities/{entityId}/fields/{fieldId}", "badEntityId", attributeDefinitionList.get(0).getId() )
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void getFieldByIdTestBadFieldId() {
        try {
            Connector connector = connectorService.getSyncariConnector();
            List<EntityDefinition> entityDefinitionList = schemaService.getEntities(connector.getId());
            List<AttributeDefinition> attributeDefinitionList = schemaService.getActiveAttributes(connector.getId(), "account");

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/entities/{entityId}/fields/{fieldId}", entityDefinitionList.get(0).getId(), "badFieldId" )
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void yCreateFieldTest() {
        try {
            Connector connector = connectorService.getSyncariConnector();
            EntityDefinition entityDefinition = schemaService.getEntity(connector.getId(), "contact");

            String accessToken = oauthUtil.getTestAccessToken();

            // get entity
            ResultActions resultSyncariSchemaEntity = mockMvc.perform(get("/api/v1/entities/{entityId}", entityDefinition.getId() )
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("includeFields", "true"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("contact")))
                    .andExpect(status().isOk());

            MvcResult entityResult = resultSyncariSchemaEntity.andReturn();
            String fieldId = JsonPath.read(entityResult.getResponse().getContentAsString(), "$.result.fields.[0].id");

            // create draft
            ResultActions resultCreateDraft = mockMvc.perform(post("/api/v1/entities/{entityId}/createDraft", entityDefinition.getId() )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("contact")))
                    .andExpect(jsonPath("$.result.draft", is(true)))
                    .andExpect(status().isOk());

            MvcResult draftEntityResult = resultCreateDraft.andReturn();
            String draftEntityId = JsonPath.read(draftEntityResult.getResponse().getContentAsString(), "$.result.id");

            // add field 1
            String fieldRequest1 = entityTestUtil.getNewField("newField", "string", 40, true, true, false);

            ResultActions resultAddField = mockMvc.perform(post("/api/v1/entities/{entityId}/fields", draftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(fieldRequest1))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("newField")))
                    .andExpect(jsonPath("$.result.displayName", is("newField")))
                    .andExpect(jsonPath("$.result.datastoreName", is("newFieldDatastore")))
                    .andExpect(jsonPath("$.result.description", is("newField description")))
                    .andExpect(jsonPath("$.result.dataType", is("string")))
                    .andExpect(jsonPath("$.result.length", is(40)))
                    .andExpect(jsonPath("$.result.multiValueField", is(false)))
                    .andExpect(jsonPath("$.result.required", is(true)))
                    .andExpect(jsonPath("$.result.unique", is(false)))
                    .andExpect(jsonPath("$.result.draft", is(true)))
                    .andExpect(jsonPath("$.result.tags", hasSize(2)))
                    .andExpect(status().isOk());

            MvcResult draftFieldResult = resultAddField.andReturn();
            String draftFieldId = JsonPath.read(draftFieldResult.getResponse().getContentAsString(), "$.result.id");

            // add field 2
            String fieldRequest2 = entityTestUtil.getNewField("newField2", "boolean", null, true, false, false);

            ResultActions resultAddField2 = mockMvc.perform(post("/api/v1/entities/{entityId}/fields", draftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(fieldRequest2))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("newField2")))
                    .andExpect(jsonPath("$.result.displayName", is("newField2")))
                    .andExpect(jsonPath("$.result.datastoreName", is("newField2Datastore")))
                    .andExpect(jsonPath("$.result.description", is("newField2 description")))
                    .andExpect(jsonPath("$.result.dataType", is("boolean")))
                    .andExpect(jsonPath("$.result.length", is(0)))
                    .andExpect(jsonPath("$.result.multiValueField", is(false)))
                    .andExpect(jsonPath("$.result.required", is(true)))
                    .andExpect(jsonPath("$.result.unique", is(false)))
                    .andExpect(jsonPath("$.result.draft", is(true)))
                    .andExpect(jsonPath("$.result.tags", hasSize(2)))
                    .andExpect(status().isOk());

            // add field 2
            String fieldRequest3 = entityTestUtil.getNewField("newField3", "picklist", null, true, false, true);

            ResultActions resultAddField3 = mockMvc.perform(post("/api/v1/entities/{entityId}/fields", draftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(fieldRequest3))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("newField3")))
                    .andExpect(jsonPath("$.result.displayName", is("newField3")))
                    .andExpect(jsonPath("$.result.datastoreName", is("newField3Datastore")))
                    .andExpect(jsonPath("$.result.description", is("newField3 description")))
                    .andExpect(jsonPath("$.result.dataType", is("picklist")))
                    .andExpect(jsonPath("$.result.length", is(0)))
                    .andExpect(jsonPath("$.result.multiValueField", is(false)))
                    .andExpect(jsonPath("$.result.required", is(true)))
                    .andExpect(jsonPath("$.result.unique", is(false)))
                    .andExpect(jsonPath("$.result.draft", is(true)))
                    .andExpect(jsonPath("$.result.picklistValues", hasSize(2)))
                    .andExpect(jsonPath("$.result.picklistValues.[0]", is("Pick1")))
                    .andExpect(jsonPath("$.result.tags", hasSize(2)))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }


    @Test
    public void createSyncariEntityTest() {
            Connector connector = connectorService.getSyncariConnector();
            EntityDefinition entityDefinition = schemaService.getEntity(connector.getId(), "contact");
            CreateSyncariEntityRequest request = new CreateSyncariEntityRequest().setCreatedAt(new Date()).setApiName("contact_copy").setCreatedBy("test")
                    .setDataStoreName("contact_copy").setDisplayName("contact_copy").setDescription("contact_copy");
            List<FieldRequest> fieldRequestList = new ArrayList<>();
            entityDefinition.getAttributes().forEach(at -> {
                if ((!at.isIdField()) && (!at.isSystem()) && (!at.getApiName().equalsIgnoreCase("LastModifiedDate")) && (!at.getApiName().equalsIgnoreCase("CreatedDate"))) {
                    FieldRequest fieldRequest = entityTestUtil.convertAttributeDefinition(at);
                    fieldRequestList.add(fieldRequest);
                };
            });
            request.setFields(fieldRequestList);
        try {
            Organization org = SyncariContext.getOrganziation();
            Instance i = SyncariContext.getInstance();
            User u = SyncariContext.getUser();
            SyncariContext.runWithContext(org,i,u, ()-> {
                String accessToken = oauthUtil.getTestAccessToken();
                try{
                    String entityRequest = entityTestUtil.getNewEntity(request);
                    // create entity
                    mockMvc.perform(post("/api/v1/entities/createDraft")
                            .header("Authorization", accessToken).header("clientRequestId", "placeholder")
                            .contentType(APPLICATION_JSON_UTF8)
                            .content(entityRequest))
                            .andDo(print())
                            .andExpect(jsonPath("$.result.apiName", is("contact_copy")))
                            .andExpect(status().isOk());
                }catch (Exception e) {
                    assertTrue(false);
                }
            });
        } catch (Exception e) {
            assertTrue(false);
        }finally {
            Optional<EntityDefinition> entityDefinition1 = schemaService.getDraft(connector.getId(), "contact_copy");
            entityDefinition1.ifPresent(e -> schemaService.discardDraftEntity(e));
        }
    }




    @Ignore
    @Test
    public void zLifeCycle() {
        try {
            Connector connector = connectorService.getSyncariConnector();
            EntityDefinition entityDefinition = schemaService.getEntity(connector.getId(), "account");

            String accessToken = oauthUtil.getTestAccessToken();

            // get entity
            ResultActions resultSyncariSchemaEntity = mockMvc.perform(get("/api/v1/entities/{entityId}", entityDefinition.getId() )
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("includeFields", "true"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("account")))
                    .andExpect(status().isOk());

            MvcResult entityResult = resultSyncariSchemaEntity.andReturn();
            String fieldId = JsonPath.read(entityResult.getResponse().getContentAsString(), "$.result.fields.[0].id");

            // create draft
            ResultActions resultCreateDraft = mockMvc.perform(post("/api/v1/entities/{entityId}/createDraft", entityDefinition.getId() )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("account")))
                    .andExpect(jsonPath("$.result.draft", is(true)))
                    .andExpect(status().isOk());

            MvcResult draftEntityResult = resultCreateDraft.andReturn();
            String draftEntityId = JsonPath.read(draftEntityResult.getResponse().getContentAsString(), "$.result.id");

            // negative create draft
            ResultActions resultNegativeCreateDraft1 = mockMvc.perform(post("/api/v1/entities/{entityId}/createDraft", entityDefinition.getId() )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Entity with Id "+entityDefinition.getId()+" already has a draft with Id "+ draftEntityId)))
                    .andExpect(status().isConflict());

            ResultActions resultNegativeCreateDraft2 = mockMvc.perform(post("/api/v1/entities/{entityId}/createDraft", draftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Entity with Id "+draftEntityId+" is not approved")))
                    .andExpect(status().isConflict());

            ResultActions resultNegativeCreateDraft3 = mockMvc.perform(post("/api/v1/entities/{entityId}/createDraft", "badEntityId" )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Entity with Id badEntityId not found")))
                    .andExpect(status().isNotFound());

            // negative add field
            String fieldRequestBadApiName = entityTestUtil.getNewField("__", "string", 40, true, true, false);

            ResultActions resultNegativeAddField1 = mockMvc.perform(post("/api/v1/entities/{entityId}/fields", draftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(fieldRequestBadApiName))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field with apiName __ is invalid")))
                    .andExpect(status().isBadRequest());

            String fieldRequestNoApiName = entityTestUtil.getNewField("validApiName", "string", null,false, false, false);

            ResultActions resultNegativeAddField2 = mockMvc.perform(post("/api/v1/entities/{entityId}/fields", draftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(fieldRequestNoApiName))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field apiName is empty. Please verify this request parameter")))
                    .andExpect(status().isBadRequest());

            // add field
            String fieldRequest = entityTestUtil.getNewField("newField", "string", 40, true, true, false);

            ResultActions resultAddField = mockMvc.perform(post("/api/v1/entities/{entityId}/fields", draftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(fieldRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("newField")))
                    .andExpect(jsonPath("$.result.displayName", is("newField")))
                    .andExpect(jsonPath("$.result.datastoreName", is("newFieldDatastore")))
                    .andExpect(jsonPath("$.result.description", is("newField description")))
                    .andExpect(jsonPath("$.result.dataType", is("string")))
                    .andExpect(jsonPath("$.result.length", is(40)))
                    .andExpect(jsonPath("$.result.multiValueField", is(false)))
                    .andExpect(jsonPath("$.result.required", is(true)))
                    .andExpect(jsonPath("$.result.unique", is(false)))
                    .andExpect(jsonPath("$.result.draft", is(true)))
                    .andExpect(jsonPath("$.result.tags", hasSize(2)))
                    .andExpect(status().isOk());

            MvcResult draftFieldResult = resultAddField.andReturn();
            String draftFieldId = JsonPath.read(draftFieldResult.getResponse().getContentAsString(), "$.result.id");

            // negative delete field
            ResultActions resultNegativeDeleteField1 = mockMvc.perform(delete("/api/v1/entities/{entityId}/fields/{fieldId}", "BadEntityId", draftFieldId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field with Id "+draftFieldId+" is not valid for entity with id BadEntityId")))
                    .andExpect(status().isConflict());

            ResultActions resultNegativeDeleteField2 = mockMvc.perform(delete("/api/v1/entities/{entityId}/fields/{fieldId}", draftEntityId, "badFieldId" )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field with Id badFieldId not found")))
                    .andExpect(status().isNotFound());

            ResultActions resultNegativeDeleteField3 = mockMvc.perform(delete("/api/v1/entities/{entityId}/fields/{fieldId}", draftEntityId, fieldId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field with Id "+fieldId+" is not valid for entity with id "+draftEntityId)))
                    .andExpect(status().isConflict());

            // delete field
            ResultActions resultDeleteField = mockMvc.perform(delete("/api/v1/entities/{entityId}/fields/{fieldId}", draftEntityId, draftFieldId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.id", is(draftFieldId)))
                    .andExpect(jsonPath("$.result.apiName", is("newField")))
                    .andExpect(jsonPath("$.result.status", is("DELETED")))
                    .andExpect(status().isOk());

            // negative publish draft
            ResultActions resultNegativePublishDraft1 = mockMvc.perform(post("/api/v1/entities/{entityId}/publish", entityDefinition.getId() )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Entity with Id "+entityDefinition.getId()+" is not a draft")))
                    .andExpect(status().isConflict());

            // publish draft
            ResultActions resultPublishDraft = mockMvc.perform(post("/api/v1/entities/{entityId}/publish", draftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.id", is(entityDefinition.getId())))
                    .andExpect(jsonPath("$.result.apiName", is("account")))
                    .andExpect(jsonPath("$.result.draft", is(false)))
                    .andExpect(status().isOk());

            // create second draft
            ResultActions resultCreateSecondDraft = mockMvc.perform(post("/api/v1/entities/{entityId}/createDraft", entityDefinition.getId() )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("account")))
                    .andExpect(status().isOk());

            MvcResult secondDraftEntityResult = resultCreateSecondDraft.andReturn();
            String secondDraftEntityId = JsonPath.read(secondDraftEntityResult.getResponse().getContentAsString(), "$.result.id");

            // negative delete draft
            ResultActions resultNegativeDeleteSecondDraft1 = mockMvc.perform(delete("/api/v1/entities/{entityId}", "BadEntityId" )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Entity with Id BadEntityId not found")))
                    .andExpect(status().isNotFound());

            ResultActions resultNegativeDeleteSecondDraft2 = mockMvc.perform(delete("/api/v1/entities/{entityId}", entityDefinition.getId() )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Entity with Id "+entityDefinition.getId()+" is not a draft, only entities in draft status can be deleted")))
                    .andExpect(status().isConflict());

            // delete draft
            ResultActions resultDeleteSecondDraft = mockMvc.perform(delete("/api/v1/entities/{entityId}", secondDraftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.id", is(secondDraftEntityId)))
                    .andExpect(jsonPath("$.result.apiName", is("account")))
                    .andExpect(jsonPath("$.result.draft", is(true)))
                    .andExpect(jsonPath("$.result.status", is("DELETED")))
                    .andExpect(status().isOk());

            // create third draft
            ResultActions resultCreateThirdDraft = mockMvc.perform(post("/api/v1/entities/{entityId}/createDraft", entityDefinition.getId() )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("account")))
                    .andExpect(jsonPath("$.result.draft", is(true)))
                    .andExpect(status().isOk());

            MvcResult thirdDraftEntityResult = resultCreateThirdDraft.andReturn();
            String thirdDraftEntityId = JsonPath.read(thirdDraftEntityResult.getResponse().getContentAsString(), "$.result.id");

            //add second field
            ResultActions resultAddSecondField = mockMvc.perform(post("/api/v1/entities/{entityId}/fields", thirdDraftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(fieldRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.apiName", is("newField")))
                    .andExpect(jsonPath("$.result.displayName", is("newField")))
                    .andExpect(jsonPath("$.result.datastoreName", is("newFieldDatastore")))
                    .andExpect(jsonPath("$.result.description", is("newField description")))
                    .andExpect(jsonPath("$.result.dataType", is("string")))
                    .andExpect(jsonPath("$.result.length", is(40)))
                    .andExpect(jsonPath("$.result.multiValueField", is(false)))
                    .andExpect(jsonPath("$.result.required", is(true)))
                    .andExpect(jsonPath("$.result.unique", is(false)))
                    .andExpect(jsonPath("$.result.draft", is(true)))
                    .andExpect(jsonPath("$.result.tags", hasSize(2)))
                    .andExpect(status().isOk());

            MvcResult createFieldResult = resultAddSecondField.andReturn();
            String createFieldId = JsonPath.read(createFieldResult.getResponse().getContentAsString(), "$.result.id");

            String updateFieldRequest = "[{\"fieldId\" : \""+createFieldId+"\", \"displayName\" : \"newFieldDisplayName\"}]";

            // updateField
            ResultActions resultupdateField = mockMvc.perform(patch("/api/v1/entities/{entityId}/fields/batch", thirdDraftEntityId )
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8).content(updateFieldRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.successfulUpdatedFieldIds", hasSize(1)))
                    .andExpect(jsonPath("$.result.successfulUpdatedFieldIds.[0]", is(createFieldId)))
                    .andExpect(jsonPath("$.result.failedUpdatedFieldIds", hasSize(0)))
                    .andExpect(status().isOk());

            String updateFieldRequestBadFieldId = "[{\"fieldId\" : \"badId\", \"displayName\" : \"newFieldDisplayName\"}]";

            // updateField bad field id
            ResultActions resultupdateFieldBadFieldId = mockMvc.perform(patch("/api/v1/entities/{entityId}/fields/batch", thirdDraftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(updateFieldRequestBadFieldId))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("The following errors were found with the batch field update request")))
                    .andExpect(jsonPath("$.error.errorDetails.[0]", is("Field with Id badId not found")))
                    .andExpect(status().isBadRequest());

            String updateFieldRequestMissingFieldId = "[{\"displayName\" : \"newFieldDisplayName\"}]";

            // updateField bad field id
            ResultActions resultupdateFieldMissingFieldId = mockMvc.perform(patch("/api/v1/entities/{entityId}/fields/batch", thirdDraftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(updateFieldRequestMissingFieldId))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("The following errors were found with the batch field update request")))
                    .andExpect(jsonPath("$.error.errorDetails.[0]", is("fieldId cannot be null")))
                    .andExpect(status().isBadRequest());

            String updateFieldRequestBadDataType = "[{\"fieldId\" : \""+createFieldId+"\", \"dataType\" : \"badDataType\"}]";

            // updateField
            ResultActions resultupdateFieldBadDataType = mockMvc.perform(patch("/api/v1/entities/{entityId}/fields/batch", thirdDraftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(updateFieldRequestBadDataType))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("The following errors were found with the batch field update request")))
                    .andExpect(jsonPath("$.error.errorDetails.[0]", is("Invalid dataType badDataType for update field for fieldId "+createFieldId)))
                    .andExpect(status().isBadRequest());

            // get update field
            ResultActions resultFetUpdateField = mockMvc.perform(get("/api/v1/entities/{entityId}/fields/{fieldId}", thirdDraftEntityId, createFieldId )
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.displayName", is("newFieldDisplayName")))
                    .andExpect(status().isOk());

            // publish draft
            ResultActions resultPublishThirdDraft = mockMvc.perform(post("/api/v1/entities/{entityId}/publish", thirdDraftEntityId )
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.id", is(entityDefinition.getId())))
                    .andExpect(jsonPath("$.result.apiName", is("account")))
                    .andExpect(jsonPath("$.result.draft", is(false)))
                    .andExpect(status().isOk());

            // get entity
            ResultActions resultSyncariSchemaEntityFields = mockMvc.perform(get("/api/v1/entities/{entityId}/fields", entityDefinition.getId() )
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.[38].apiName", is("newField")))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void listDataTest() {
        try {

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultGetData = mockMvc.perform(post("/api/v1/entities/{pipelineId}/data", entityDef.getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(4)))
                    .andExpect(jsonPath("$.result.[0].values.City", is("弗里蒙特")))
                    .andExpect(jsonPath("$.result.[0].idMapping.['Hubspot / DataStudioAccount_hubspot']", is("1234")))
                    .andExpect(jsonPath("$.result.[0].idMapping.['Netsuite / DataStudioAccount_netsuite']", is("454")))
                    .andExpect(jsonPath("$.result.[0].dataFitnessIndex", is("0")))
                    .andExpect(status().isOk());

            resultGetData = mockMvc.perform(post("/api/v1/entities/{pipelineId}/data", entityDef.getId())
                    .header("Authorization", accessToken)
                    .param("limit", "2")
                    .contentType(APPLICATION_JSON_UTF8)
            )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(2)))
                    .andExpect(jsonPath("$.result.[0].values.City", is("弗里蒙特")))
                    .andExpect(jsonPath("$.result.[0].idMapping.['Hubspot / DataStudioAccount_hubspot']", is("1234")))
                    .andExpect(jsonPath("$.result.[0].idMapping.['Netsuite / DataStudioAccount_netsuite']", is("454")))
                    .andExpect(jsonPath("$.result.[0].dataFitnessIndex", is("0")))
                    .andExpect(jsonPath("$.cursorToken", notNullValue()))
                    .andExpect(status().isOk());

            String filter1 = "{\"predicates\": [{\"left\": {\"type\": \"variable\", \"apiName\": \"Name\"},\"operator\": \"eq\",\"right\": {\"value\": \"test1\",\"type\": \"literal\"}}  ],\"operator\": \"AND\"}";

            ResultActions resultGetDataWithFilter = mockMvc.perform(post("/api/v1/entities/{pipelineId}/data", entityDef.getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(filter1)
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(1)))
                    .andExpect(jsonPath("$.result.[0].values.City", is("弗里蒙特")))
                    .andExpect(jsonPath("$.result.[0].idMapping.['Hubspot / DataStudioAccount_hubspot']", is("1234")))
                    .andExpect(jsonPath("$.result.[0].idMapping.['Netsuite / DataStudioAccount_netsuite']", is("454")))
                    .andExpect(jsonPath("$.result.[0].dataFitnessIndex", is("0")))
                    .andExpect(status().isOk());

            String filter2 = "{\"predicates\": [{\"left\": {\"type\": \"variable\", \"apiName\": \"Name\"},\"operator\": \"eq\",\"right\": {\"value\": \"badValue1\",\"type\": \"literal\"}}  ],\"operator\": \"AND\"}";

            ResultActions resultGetDataWithEmptyFilter = mockMvc.perform(post("/api/v1/entities/{pipelineId}/data", entityDef.getId())
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(filter2)
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(0)))
                    .andExpect(status().isOk());

            String filter3 = "{\"predicates\": [{\"left\": {\"type\": \"variable\", \"apiName\": \"lastModified\"},\"operator\": \"gte\",\"right\": {\"value\": \"1610735983000\",\"type\": \"literal\"}}  ],\"operator\": \"AND\"}";

            ResultActions resultGetDataWithLastModifiedFilter = mockMvc.perform(post("/api/v1/entities/{pipelineId}/data", entityDef.getId())
                    .header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8).content(filter3)
            )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(0)))
                    .andExpect(status().isOk());

            String filter4 = "{\"predicates\": [{\"left\": {\"type\": \"variable\", \"apiName\": \"isSyncariDeleted\"},\"operator\": \"eq\",\"right\": {\"value\": \"true\",\"type\": \"literal\"}}  ],\"operator\": \"AND\"}";

            ResultActions resultGetDataWithisDeleted = mockMvc.perform(post("/api/v1/entities/{pipelineId}/data", entityDef.getId())
                    .header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8).content(filter4)
            )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(0)))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }

    }

    private void createRecords() {
        EntityData input = new EntityData();
        input.setName(DATA_STUDIO_ACCOUNT);
        input.addValue("Name", "test1");
        input.addValue("Website", "test1.com");
        input.addValue("Age", 5);
        input.addValue("Name_with_underscore", "test3");
        input.addValue("City", "弗里蒙特");
        EntityScore syncariScore = new EntityScore();
        FieldScore fScore = new FieldScore();
        fScore.addByRule("isNotEmpty", 10);
        syncariScore.addFieldScore("Name_with_underscore", fScore);
        input.setSyncariScore(syncariScore);
        e1 = entityRepo.save(entityDef,input);
        IdMapping mapping = new IdMapping().setSyncariId(e1.getSyncariEntityId())
                .setEntityName(DATA_STUDIO_ACCOUNT)
                .addMapping(c1.getId(), "1234", hubspotEntity.getId())
                .addMapping(c2.getId(), "454", netsuiteEntity.getId());
        mappingRepo.save(mapping);
        EntityData input1 = new EntityData();
        input1.setName(DATA_STUDIO_ACCOUNT);
        input1.addValue("Name", "test2");
        input1.addValue("Website", "test2.com");
        input1.addValue("Age", 10);
        input1.addValue("City", "班加罗尔");
        input1.addValue("LastModified", new DateUtil().parse("2020-12-15T20:35:54.828Z", DateUtil.dateFormatMillis));
        e2 = entityRepo.save(entityDef,input1);
        mapping = new IdMapping().setSyncariId(e2.getSyncariEntityId())
                .setEntityName(DATA_STUDIO_ACCOUNT)
                .addMapping(c1.getId(), "2345", hubspotEntity.getId())
                .addMapping(c2.getId(), "56546", netsuiteEntity.getId());
        mappingRepo.save(mapping);
        EntityData input2 = new EntityData();
        input2.setName(DATA_STUDIO_ACCOUNT);
        input2.addValue("Name", "Test3 Name");
        input2.addValue("Website", "test3.com");
        input2.addValue("City", "圣荷西");
        e3 = entityRepo.save(entityDef, input2);
        mapping = new IdMapping().setSyncariId(e3.getSyncariEntityId())
                .setEntityName(DATA_STUDIO_ACCOUNT)
                .addMapping(c1.getId(), "4545", hubspotEntity.getId())
                .addMapping(c2.getId(), "678", netsuiteEntity.getId());
        mappingRepo.save(mapping);
        EntityData input3 = new EntityData();
        input3.setName(DATA_STUDIO_ACCOUNT);
        input3.addValue("Name", "test4 with space");
        input3.addValue("Website", "test4.com");
        input3.addValue("Age", 5);
        input3.addValue("City", "San Francisco");
        e4 = entityRepo.save(entityDef,input3);
        mapping = new IdMapping().setSyncariId(e4.getSyncariEntityId())
                .setEntityName(DATA_STUDIO_ACCOUNT)
                .addMapping(c1.getId(), "86767", hubspotEntity.getId())
                .addMapping(c2.getId(), "787", netsuiteEntity.getId());
        mappingRepo.save(mapping);
    }

    private EntityDefinition getEntity() {
        EntityDefinition sourceEntity = new EntityDefinition(DATA_STUDIO_ACCOUNT, DATA_STUDIO_ACCOUNT);
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity.setConnectorId(connectorService.getSyncariConnector().getId());
        sourceEntity = defRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attrRepo.save(name);
        AttributeDefinition longName = new AttributeDefinition().setApiName("Name_with_underscore")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        longName.setDraftStatus(DraftStatus.APPROVED);
        longName.setStatus(Status.ACTIVE);
        longName = attrRepo.save(longName);
        AttributeDefinition website = new AttributeDefinition().setApiName("Website")
                .setDataType(new StringType()).setDisplayName("Website").setEntityId(sourceEntity.getId());
        website.setDraftStatus(DraftStatus.APPROVED);
        website.setStatus(Status.ACTIVE);
        website = attrRepo.save(website);
        AttributeDefinition age = new AttributeDefinition().setApiName("Age")
                .setDataType(new IntegerType()).setDisplayName("Age").setEntityId(sourceEntity.getId());
        age.setDraftStatus(DraftStatus.APPROVED);
        age.setStatus(Status.ACTIVE);
        age = attrRepo.save(age);
        AttributeDefinition modified = new AttributeDefinition().setApiName("LastModified")
                .setDataType(new DatetimeType()).setDisplayName("LastModified").setEntityId(sourceEntity.getId());
        modified.setDraftStatus(DraftStatus.APPROVED);
        modified.setStatus(Status.ACTIVE);
        modified = attrRepo.save(modified);
        AttributeDefinition city = new AttributeDefinition().setApiName("City")
                .setDataType(new StringType()).setDisplayName("City").setEntityId(sourceEntity.getId());
        city.setDraftStatus(DraftStatus.APPROVED);
        city.setStatus(Status.ACTIVE);
        city = attrRepo.save(city);
        sourceEntity.addField(name);
        sourceEntity.addField(website);
        sourceEntity.addField(age);
        sourceEntity.addField(city);
        sourceEntity.addField(modified);
        return sourceEntity;
    }

    private EntityDefinition getHubspotEntity() {
        EntityDefinition sourceEntity = new EntityDefinition(DATA_STUDIO_ACCOUNT+"_hubspot", DATA_STUDIO_ACCOUNT+"_hubspot");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity.setConnectorId(c1.getId());
        sourceEntity = defRepo.save(sourceEntity);
        AttributeDefinition id = new AttributeDefinition().setApiName("Id")
                .setDataType(new IntegerType()).setDisplayName("Id").setEntityId(sourceEntity.getId());
        id.setDraftStatus(DraftStatus.APPROVED);
        id.setStatus(Status.ACTIVE);
        id.setIdField(true);
        id = attrRepo.save(id);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attrRepo.save(name);
        AttributeDefinition website = new AttributeDefinition().setApiName("Website")
                .setDataType(new StringType()).setDisplayName("Website").setEntityId(sourceEntity.getId());
        website.setDraftStatus(DraftStatus.APPROVED);
        website.setStatus(Status.ACTIVE);
        website = attrRepo.save(website);
        AttributeDefinition age = new AttributeDefinition().setApiName("Age")
                .setDataType(new IntegerType()).setDisplayName("Age").setEntityId(sourceEntity.getId());
        age.setDraftStatus(DraftStatus.APPROVED);
        age.setStatus(Status.ACTIVE);
        age = attrRepo.save(age);
        sourceEntity.addField(name);
        sourceEntity.addField(website);
        sourceEntity.addField(age);
        return sourceEntity;
    }

    private EntityDefinition getNetsuiteEntity() {
        EntityDefinition sourceEntity = new EntityDefinition(DATA_STUDIO_ACCOUNT+"_netsuite", DATA_STUDIO_ACCOUNT+"_netsuite");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity.setConnectorId(c2.getId());
        sourceEntity = defRepo.save(sourceEntity);
        AttributeDefinition id = new AttributeDefinition().setApiName("Id")
                .setDataType(new StringType()).setDisplayName("Id").setEntityId(sourceEntity.getId());
        id.setDraftStatus(DraftStatus.APPROVED);
        id.setStatus(Status.ACTIVE);
        id.setIdField(true);
        id = attrRepo.save(id);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attrRepo.save(name);
        AttributeDefinition website = new AttributeDefinition().setApiName("Website")
                .setDataType(new StringType()).setDisplayName("Website").setEntityId(sourceEntity.getId());
        website.setDraftStatus(DraftStatus.APPROVED);
        website.setStatus(Status.ACTIVE);
        website = attrRepo.save(website);
        AttributeDefinition age = new AttributeDefinition().setApiName("Age")
                .setDataType(new IntegerType()).setDisplayName("Age").setEntityId(sourceEntity.getId());
        age.setDraftStatus(DraftStatus.APPROVED);
        age.setStatus(Status.ACTIVE);
        age = attrRepo.save(age);
        sourceEntity.addField(name);
        sourceEntity.addField(website);
        sourceEntity.addField(age);
        return sourceEntity;
    }

    private EntityDefinition getEntityForValidation() {
        EntityDefinition sourceEntity = new EntityDefinition("validation", "validation");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity.setConnectorId(connectorService.getSyncariConnector().getId());
        sourceEntity = defRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name.setNillable(false);
        name = attrRepo.save(name);
        AttributeDefinition website = new AttributeDefinition().setApiName("Website")
                .setDataType(new StringType()).setDisplayName("Website").setEntityId(sourceEntity.getId());
        website.setDraftStatus(DraftStatus.APPROVED);
        website.setStatus(Status.ACTIVE);
        website.setUpdatable(false);
        website = attrRepo.save(website);
        AttributeDefinition age = new AttributeDefinition().setApiName("Age")
                .setDataType(new IntegerType()).setDisplayName("Age").setEntityId(sourceEntity.getId());
        age.setDraftStatus(DraftStatus.APPROVED);
        age.setStatus(Status.ACTIVE);
        age = attrRepo.save(age);
        AttributeDefinition dob = new AttributeDefinition().setApiName("Dob")
                .setDataType(new DateType()).setDisplayName("Dob").setEntityId(sourceEntity.getId());
        dob.setDraftStatus(DraftStatus.APPROVED);
        dob.setStatus(Status.ACTIVE);
        dob = attrRepo.save(dob);
        AttributeDefinition contacted = new AttributeDefinition().setApiName("Contacted")
                .setDataType(new DatetimeType()).setDisplayName("Contacted").setEntityId(sourceEntity.getId());
        contacted.setDraftStatus(DraftStatus.APPROVED);
        contacted.setStatus(Status.ACTIVE);
        contacted = attrRepo.save(contacted);
        AttributeDefinition active = new AttributeDefinition().setApiName("active")
                .setDataType(new BooleanType()).setDisplayName("active").setEntityId(sourceEntity.getId());
        active.setDraftStatus(DraftStatus.APPROVED);
        active.setStatus(Status.ACTIVE);
        active = attrRepo.save(active);
        AttributeDefinition longstr = new AttributeDefinition().setApiName("longstr")
                .setDataType(new StringType()).setDisplayName("longstr").setEntityId(sourceEntity.getId());
        longstr.setDraftStatus(DraftStatus.APPROVED);
        longstr.setStatus(Status.ACTIVE);
        longstr.setLength(10);
        longstr = attrRepo.save(longstr);
        sourceEntity.addField(name);
        sourceEntity.addField(website);
        sourceEntity.addField(age);
        return sourceEntity;
    }

}
