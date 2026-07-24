package com.syncari.karibu.rest.controllers;

import com.syncari.core.model.Organization;
import com.syncari.core.model.ProvisioningResponse;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.service.ProvisioningService;
import com.syncari.karibu.rest.response.OrgResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import static com.syncari.core.security.Permissions.PROVISION_TRIAL_ORG;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SubscriptionControllerTest extends AbstractSyncariTest{

    @Autowired
    ProvisioningService provisioningService;

    @Autowired
    SubscriptionController subscriptionController;

    @Override
    public void setUp() {super.setUp();}

    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_TRIAL_ORG })
    public void getOrgByNameTest() {

        String syncariId = null;
        String orgId = null;
        try{
            ProvisioningResponse response = provisioningService.provision("testtrialInstance", InstanceType.trial, "tsttrialDisname",
                    "testtrialOrg","test@test.com", "trial",
                    RoleConstants.ORG_ADMIN, "fname","lname", OrganizationType.trial,null);

            Organization org = response.getOrganization();
            syncariId = org.getInstances().get(0).getSyncariId();
            String orgName = org.getName();
            orgId = org.getId();
            assertNotNull(orgName);
            assertNotNull(orgId);
            var saved = subscriptionController.getOrgByName(orgName);
            assertNotNull(saved);
            assertEquals(HttpStatus.OK, saved.getStatusCode());
            assertTrue( ((ValidResponse)saved.getBody()).getResult() instanceof OrgResponse);
            assertEquals(((OrgResponse)((ValidResponse)saved.getBody()).getResult()).getInstances().get(0).getSubscriptionName(),orgName);
        }finally {
            provisioningService.deprovisionInstance(syncariId, true);
            provisioningService.deprovision(orgId, false);
        }

    }
}
