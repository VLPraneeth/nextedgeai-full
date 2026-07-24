package com.syncari.karibu.rest.controllers;

import com.jayway.jsonpath.JsonPath;
import com.syncari.core.SyncariContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.PlanRepo;
import com.syncari.core.service.ProvisioningService;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.response.OrgResponse;
import com.syncari.karibu.rest.util.OauthUtil;
import com.syncari.restutils.data.ProvisionRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.Optional;

import static com.syncari.core.security.Permissions.*;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
public class PLGSubscriptionControllerTest extends AbstractSyncariTest{

    @Autowired
    PLGSubscriptionController subscriptionController;

    @Autowired
    OrganizationRepo organizationRepo;

    @Autowired
    PlanRepo planRepo;

    @Autowired
    OauthUtil oauthUtil;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    UserService userService;
    
    @Autowired
	ConnectorRepo connectorRepo;
    
    @Autowired
	EntityDefinitionRepo entityProxyRepo;
    
    @Autowired
    MappingGraphRepo graphRepo;

    @Autowired
    ProvisioningService provisioningService;

    ProvisionRequest request;
    ProvisionRequest request2;
    @Autowired
    SubscriptionService subscriptionService;

    @Override
    public void tearDown() {
        Optional<User> user =  userService.getUserByEmail("test1@email.com");
        user.ifPresent(u -> userService.deleteUser(u.getId()));
        var fromRepo = organizationRepo.findByName("Demo Org(test1)");
        fromRepo.ifPresent(fRepo -> organizationRepo.deleteById(fRepo.getId()));
        var fromRepo2 = organizationRepo.findByName("Demo Org(test2)");
        fromRepo2.ifPresent(fRepo -> organizationRepo.deleteById(fRepo.getId()));


        Optional<User> user1 =  userService.getUserByEmail("test2@email.com");
        user1.ifPresent(u -> userService.deleteUser(u.getId()));
    }

    @Override
    public void setUp() {
        super.setUp();
        request = new ProvisionRequest();
        request.setOrganizationName("Demo Org");
        request.setAdminUserName("test1@email.com");
        request.setAdminFirstName("testFirstName");
        request.setAdminLastName("testLastName");
        request.setPlanName("trial");
        request.setInstanceType(InstanceType.trial.toString());

        request2 = new ProvisionRequest();
        request2.setOrganizationName("Demo Org");
        request2.setAdminUserName("test2@email.com");
        request2.setAdminFirstName("testFirstName");
        request2.setAdminLastName("testLastName");
        request2.setPlanName("trial");
        request2.setInstanceType(InstanceType.trial.toString());
    }

    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_TRIAL_ORG })
    public void provisionOrganizationWithTrialPlan() {
        var saved = subscriptionController.addOrganization(request);
        assertNotNull(saved);
        assertEquals(HttpStatus.OK, saved.getStatusCode());
        assertTrue( saved.getBody().getResult() instanceof OrgResponse);
    }

    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_TRIAL_ORG })
    public void provisionOrganizationWithBlankPlan() {
        request.setPlanName("");
        var saved = subscriptionController.addOrganization(request);
        assertNotNull(saved);
        assertEquals(HttpStatus.OK, saved.getStatusCode());
        assertTrue( saved.getBody().getResult() instanceof OrgResponse);

        /*var fromRepo = organizationRepo.findByName("Demo Org(test1)");
        assertTrue(fromRepo.isPresent());
        assertEquals("Demo Org(test1)", fromRepo.get().getName());
        assertEquals(((OrgResponse)saved.getBody().getResult()).getId(),fromRepo.get().getId());
        assertEquals(1,fromRepo.get().getActiveInstances().size());
        Optional<Plan> plan = planRepo.findById(fromRepo.get().getActiveInstances().get(0).getPlanId());
        assertTrue(plan.isPresent());
        assertEquals("trial",plan.get().getName());
        provisioningService.deprovisionInstance(fromRepo.get().getInstances().get(0).getSyncariId(), true);*/
    }


    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_TRIAL_ORG })
    public void provisionTrialOrganizationWithSameNameinRequest() {
        var saved = subscriptionController.addOrganization(request);
        assertNotNull(saved);
        assertEquals(HttpStatus.OK, saved.getStatusCode());
        assertTrue( saved.getBody().getResult() instanceof OrgResponse);


        var secondSaved = subscriptionController.addOrganization(request2);
        assertNotNull(secondSaved);
        assertEquals(HttpStatus.OK, secondSaved.getStatusCode());
        assertTrue( secondSaved.getBody().getResult() instanceof OrgResponse);
        String name = ((OrgResponse)secondSaved.getBody().getResult()).getName();
    }

    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_TRIAL_ORG })
    public void provisionTrialOrganizationSameUserThrowsException() {
        var saved = subscriptionController.addOrganization(request);
        assertNotNull(saved);
        assertEquals(HttpStatus.OK, saved.getStatusCode());
        assertTrue( saved.getBody().getResult() instanceof OrgResponse);

        /*var fromRepo = organizationRepo.findByName("Demo Org(test1)");
        assertTrue(fromRepo.isPresent());
        assertEquals("Demo Org(test1)", fromRepo.get().getName());
        assertEquals(1,fromRepo.get().getActiveInstances().size());
        assertEquals(((OrgResponse)saved.getBody().getResult()).getId(),fromRepo.get().getId());*/

        var secondSaved = subscriptionController.addOrganization(request);
        assertNotNull(secondSaved);
    }

    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_TRIAL_ORG })
    public void provisionOrganizationWithLongOrgName() {
        ProvisionRequest request = new ProvisionRequest("chromedriver_chrome_on_windows_09df0365e74c3a9504ebcea68f151ba6_2022030406541", "Demo instance", "trial",
                OrganizationType.standard.name(), "Demo instance", "test1@email.com", "trial", "test", "test",null);
        try{
            var saved = subscriptionController.addOrganization(request);
            assertNotNull(saved);
            assertEquals(HttpStatus.OK, saved.getStatusCode());
            assertTrue( saved.getBody().getResult() instanceof OrgResponse);
            //assertTrue( ((OrgResponse)saved.getBody().getResult()).getName().equals("chromedriver_chrome_(test1)"));
        }catch (SyncariValidationException exception){
            fail();
        }
    }

    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_TRIAL_ORG })
    public void provisionOrganizationWithLongUserName() {
        ProvisionRequest request = new ProvisionRequest("chromedriver_chromt_on_windows_09df0365e74c3a9504ebcea68f151ba6_202203040678981", "Demo instance", "trial",
                OrganizationType.standard.name(),"Demo instance", "test1emailabcneauiabiuezvydaddefxerf@ieac.com", "trial", "test", "test",null);
        try{
            var saved = subscriptionController.addOrganization(request);
            assertNotNull(saved);
            assertEquals(HttpStatus.OK, saved.getStatusCode());
            assertTrue( saved.getBody().getResult() instanceof OrgResponse);
            String usernameShouldbe = "test1emailabcneauiabiuezvydad".substring(0,Math.min("test1emailabcneauiabiuezvydaddefxerf".length(),15));
        }catch (SyncariValidationException exception){
            fail();
        }
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(username = "admin", authorities = { PROVISION_TRIAL_ORG })
    public void provisionTrialOrganizationWithBlankFirstNameThrowsException() {
        request.setAdminFirstName(" ");
        var saved = subscriptionController.addOrganization(request);
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(username = "admin", authorities = { PROVISION_TRIAL_ORG })
    public void provisionOrganizationWithDefaultPlan() {
        ProvisionRequest request = new ProvisionRequest("Demo Org Standard", "Demo instance standard", "production",
                OrganizationType.standard.name(), "Demo instance", "test1@email.com", "default", "test", "test",null);
        var saved = subscriptionController.addOrganization(request);
    }

    @Test
    public void createAsyncInstanceTestForTrialType() {
        try {
            String adminEmail = "admintest@email.com";
            ProvisioningResponse nonTrial = provisioningService.provision("nonTrialTestInstance", InstanceType.production, "nonTrialTestOrg", "nonTrialTestOrg",
                    adminEmail, null, RoleConstants.ORG_ADMIN, "nonTrialTestFirstName", "nonTrialTestLastName", OrganizationType.standard, null);
            Organization newSub = nonTrial.getOrganization();

            ProvisioningResponse trial = provisioningService.provision("trialTestInstance", InstanceType.trial, "trialTestOrg", "trialTestOrg",
                    adminEmail, null, RoleConstants.ORG_ADMIN, "trialTestFirstName", "trialTestLastName", OrganizationType.partner, null);
            Organization trialSub = trial.getOrganization();

            String accessToken = oauthUtil.getTestAccessToken();
            Instance nonTrialInstance = newSub.getInstances().stream().filter(i -> i.getName().equalsIgnoreCase("nonTrialTestInstance")).findFirst().get();
            Instance trialInstance = trialSub.getInstances().stream().filter(i -> i.getName().equalsIgnoreCase("trialTestInstance")).findFirst().get();

            ResultActions deleteInstance = mockMvc.perform(delete("/api/v1/plg/instance/trial/"+nonTrialInstance.getSyncariId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
            ResultActions deleteTrialInstance = mockMvc.perform(delete("/api/v1/plg/instance/trial/"+trialInstance.getSyncariId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(status().isOk());
        }catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            assertTrue(false);
        }
    }
}
