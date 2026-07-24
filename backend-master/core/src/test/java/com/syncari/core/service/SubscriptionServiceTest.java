package com.syncari.core.service;

import com.syncari.connector.Constants;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.SSOAuthConfig;
import com.syncari.core.model.SSOAuthProvider;
import com.syncari.core.model.security.OAuthConfig;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubscriptionServiceTest extends AbstractSyncariTest {

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    OrganizationRepo orgRepo;

    @Value("${saml.x509.key}")
    private String x509Key;

    @Test
    public void updateSSOForOrg(){
        Organization org = SyncariContext.getOrganziation();
        try{
            subscriptionService.updateSSOForOrg(org, null);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("SSO Config can't be empty", e.getMessage());
        }

        SSOAuthConfig ssoAuthConfig = new SSOAuthConfig().setProvider(SSOAuthProvider.OKTA)
                .setSsoUrl("http://some_url").setEntityId("http://entity").setX509Key("SOME_KEY");

        try{
            subscriptionService.updateSSOForOrg(org, ssoAuthConfig);
            fail();
        } catch (RuntimeException e){
            assertEquals("Invalid value for X509 Key", e.getMessage());
        }

        ssoAuthConfig.setX509Key(x509Key);
        SSOAuthConfig updated = subscriptionService.updateSSOForOrg(org, ssoAuthConfig);
        assertNotEquals(x509Key, updated.getX509Key()); // key is encrypted and stored in db
        Organization updatedOrg = subscriptionService.getOrgById(org.getId()).get();
        assertTrue(updatedOrg.isSSOEnabled());

        // remove sso details
        updatedOrg.setSsoConfig(null);
        updatedOrg = orgRepo.save(updatedOrg);
        assertFalse(updatedOrg.isSSOEnabled());
        SyncariContext.setOrganziation(updatedOrg);
    }

    @Test
    public void updateOauthConfigForOrg(){
        Organization org = SyncariContext.getOrganziation();
        try{
            subscriptionService.updateOauthConfigForOrg(org, null);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("OAuth config can't be empty", e.getMessage());
        }

        OAuthConfig oAuthConfig = new OAuthConfig("", "CLIENTID", "SECRET", "");
        try{
            subscriptionService.updateOauthConfigForOrg(org, Map.of(Constants.HUBSPOT, oAuthConfig));
            fail();
        } catch (RuntimeException e){
            assertEquals("Invalid Oauth config value for OAuth Provider", e.getMessage());
        }

        oAuthConfig = new OAuthConfig(Constants.HUBSPOT, "", "SECRET", "");
        try{
            subscriptionService.updateOauthConfigForOrg(org, Map.of(Constants.HUBSPOT, oAuthConfig));
            fail();
        } catch (RuntimeException e){
            assertEquals("Invalid Oauth config value for Client Id", e.getMessage());
        }

        oAuthConfig = new OAuthConfig(Constants.HUBSPOT, "CLIENTID", "", "");
        try{
            subscriptionService.updateOauthConfigForOrg(org, Map.of(Constants.HUBSPOT, oAuthConfig));
            fail();
        } catch (RuntimeException e){
            assertEquals("Invalid Oauth config value for Client Secret", e.getMessage());
        }

        oAuthConfig = new OAuthConfig(Constants.HUBSPOT, "CLIENTID", "SECRET", "client_id");
        Map<String, OAuthConfig> updated = subscriptionService.updateOauthConfigForOrg(org, Map.of(Constants.HUBSPOT, oAuthConfig));
        assertTrue(updated.size() > 0);
        Organization updatedOrg = subscriptionService.getOrgById(org.getId()).get();
        assertTrue(updatedOrg.getOauthConfigs().size() > 0);

        // remove oauth configs
        updatedOrg.setOauthConfigs(null);
        updatedOrg = orgRepo.save(updatedOrg);
        updatedOrg = subscriptionService.getOrgById(updatedOrg.getId()).get();
        assertTrue(updatedOrg.getOauthConfigs().isEmpty());
        SyncariContext.setOrganziation(updatedOrg);
    }

    @Test
    public void isActiveInstance(){
        var orgRepo = subscriptionService.orgRepo;
        try{
            OrganizationRepo mockOrgRepo = mock(OrganizationRepo.class);
            subscriptionService.orgRepo = mockOrgRepo;
            Organization org = new Organization("Test");
            org.setStatus(Status.DELETED);
            Instance instance = new Instance("testInstance", "testInstance");
            instance.setStatus(Status.ACTIVE);
            org.setInstances(List.of(instance));

            doReturn(Optional.of(org)).when(mockOrgRepo).findBySyncariId(any());
            assertFalse(subscriptionService.isActiveInstance(instance.getSyncariId()));

            org.setStatus(Status.ACTIVE);
            instance.setStatus(Status.DELETED);
            assertFalse(subscriptionService.isActiveInstance(instance.getSyncariId()));

            instance.setStatus(Status.ACTIVE);
            assertTrue(subscriptionService.isActiveInstance(instance.getSyncariId()));

        } finally {
            subscriptionService.orgRepo = orgRepo;
        }
    }

}
