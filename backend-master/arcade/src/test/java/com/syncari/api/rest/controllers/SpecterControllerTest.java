package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.*;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.Util;
import com.syncari.api.rest.controllers.data.GhostAccessRequest;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.GhostAccessAuditRepo;
import com.syncari.core.service.InstanceConfigurationService;
import com.syncari.core.service.authz.AuthzService;
import com.syncari.utils.KeyValue;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.service.ProvisioningService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SpecterControllerTest extends AbstractSyncariTest  {

    @Autowired
    private SpecterController specterController;


    @Autowired
    private ProvisioningService provisioningService;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private Util util;
    
    @Autowired
    private MockMvc mvc;
    
    @Autowired
    ObjectMapper mapper;

    @Autowired
    AuthzService authzService;

    @Autowired
    GhostAccessAuditRepo ghostAccessAuditRepo;

    @Autowired
    private InstanceConfigurationService instanceConfigurationService;

    @Before
    public void setUp() {
        super.setUp();
    }
    
    @Override
    public void tearDown() {
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {GHOST_LOGIN})
    public void ghostLoginInvalidSyncariId() throws Exception{

        try {
            specterController.ghostLogin("", null,null);
            fail("ghostLogin into invalid syncariId should fail");
        } catch (Exception e){
            assertEquals("Please provide a valid subscription name", e.getMessage());
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {GHOST_LOGIN})
    public void ghostLoginValid() throws Exception {

        try {
            Organization org = SyncariContext.getOrganziation();
            User user = SyncariContext.getUser();
            Instance newInstance = provisioningService.provisionInstance(org, "ghostInstance", "ghostInstance", InstanceType.trial, "default", user);

            assertEquals("test_org_instance", SyncariContext.getInstance().getName());
            assertEquals("Test Org", SyncariContext.getOrganziation().getName());
            assertEquals("test@email.com", SyncariContext.getUser().getEmail());

            HttpServletResponse resp = new MockHttpServletResponse();
            String previousToken = util.getToken(user.getEmail(), List.of(),true, UUID.randomUUID().toString());

            specterController.ghostLogin(newInstance.getSyncariId(),previousToken, resp);

            assertEquals("ghostInstance", SyncariContext.getInstance().getName());
            assertEquals("Test Org", SyncariContext.getOrganziation().getName());
            assertEquals("test@email.com", SyncariContext.getUser().getEmail());
            provisioningService.deprovisionInstance(newInstance.getSyncariId(), true);
        } finally {
        	SyncariContext.restore();
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {GHOST_LOGIN,LIST_ORG})
    public void ghostRequestAndRevokeTest() throws Exception {
        Organization org = SyncariContext.getOrganziation();
        User user = SyncariContext.getUser();
        Instance newInstance = provisioningService.provisionInstance(org, "ghostInstance", "ghostInstance", InstanceType.trial, "default", user);
        assertEquals("test_org_instance", SyncariContext.getInstance().getName());
        assertEquals("Test Org", SyncariContext.getOrganziation().getName());
        assertEquals("test@email.com", SyncariContext.getUser().getEmail());
        GhostAccessRequest ghostAccessRequest = new GhostAccessRequest();
        ghostAccessRequest.setSyncariId(newInstance.getSyncariId());
        ghostAccessRequest.setReason("testing");
        ghostAccessRequest.setDuration("10");
        Optional<Role> role = authzService.getRoleByName("Instance Admin");
        role.ifPresentOrElse(r -> {
            ghostAccessRequest.setRoleId(r.getId());
            KeyValue keyValue = specterController.requestAccess(ghostAccessRequest);
            assertEquals("success",keyValue.get("status"));
        },() -> fail());

        Optional<Role> orgAdminrole = authzService.getRoleByName("Org Admin");

        orgAdminrole.ifPresentOrElse(r -> {
            ghostAccessRequest.setRoleId(r.getId());
            KeyValue keyValue = specterController.requestAccess(ghostAccessRequest);
            assertEquals("success",keyValue.get("status"));
            List<GhostAccessAudit> audit = ghostAccessAuditRepo.findByRequesterIdAndSyncariIdAndStatus(SyncariContext.getUser().getId(), newInstance.getSyncariId(), "ACTIVE");
            assertTrue(CollectionUtils.isNotEmpty(audit));
            assertTrue(audit.size() == 1);
            List<GhostAccessAudit> audit1 = ghostAccessAuditRepo.findByRequesterIdAndSyncariIdAndStatus(SyncariContext.getUser().getId(), newInstance.getSyncariId(), "COMPLETED");
            assertTrue(CollectionUtils.isNotEmpty(audit1));
            assertTrue(audit1.size() == 1);
            specterController.revokeAccess(ghostAccessRequest);
            List<GhostAccessAudit> audit2 = ghostAccessAuditRepo.findByRequesterIdAndSyncariIdAndStatus(SyncariContext.getUser().getId(), newInstance.getSyncariId(), "COMPLETED");
            assertTrue(CollectionUtils.isNotEmpty(audit2));
            assertTrue(audit2.size() == 2);
            audit2.forEach(a -> {
                ghostAccessAuditRepo.deleteById(a.getId());
            });
        },() -> fail());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {PROVISION_ORG})
    public void copyInstance() throws JsonProcessingException, Exception{
        appConfig.setEnvironmentName("demo");
        CopyRequest request = new CopyRequest();
        request.getEmailRecipients().add("test@test.com");
        Organization org = SyncariContext.getOrganziation();
        User user = SyncariContext.getUser();
        Instance src = provisioningService.provisionInstance(org, "srcInstance", "srcInstance", InstanceType.sandbox, "default", user);
        Instance dest = provisioningService.provisionInstance(org, "destInstance", "destInstance", InstanceType.sandbox, "default", user);
        
        var result = mvc.perform(
                post("/api/v1/specter/instance/copy/{fromSyncariId}/{toSyncariId}", "123", "234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request))
        ).andReturn();
        assertEquals("Error: Org with syncari id 123 not found", result.getResponse().getContentAsString());
        
        result = mvc.perform(
                post("/api/v1/specter/instance/copy/{fromSyncariId}/{toSyncariId}", src.getSyncariId(), "234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request))
        ).andReturn();
        assertEquals("Error: Org with syncari id 234 not found", result.getResponse().getContentAsString());
        
        result = mvc.perform(
                post("/api/v1/specter/instance/copy/{fromSyncariId}/{toSyncariId}", src.getSyncariId(), dest.getSyncariId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request))
        ).andReturn();
        assertEquals("Copy from "+src.getSyncariId()+" to "+dest.getSyncariId()+" initiated successfully!", result.getResponse().getContentAsString());
        
        result = mvc.perform(
                post("/api/v1/specter/instance/copy/{fromSyncariId}/{toSyncariId}", src.getSyncariId(), dest.getSyncariId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request))
        ).andReturn();
        assertEquals("Error: Copy from "+src.getSyncariId()+" to "+dest.getSyncariId()+" already running", result.getResponse().getContentAsString());
    }

}
