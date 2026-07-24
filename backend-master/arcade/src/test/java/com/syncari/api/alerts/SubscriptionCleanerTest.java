package com.syncari.api.alerts;

import com.syncari.api.rest.controllers.AbstractSyncariTest;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.ProvisioningService;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import com.syncari.core.utils.SyncariMongoUtils;
import com.syncari.restutils.data.ProvisionRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.time.DateUtils;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static com.syncari.core.security.Permissions.PROVISION_ORG;
import static org.junit.Assert.*;

public class SubscriptionCleanerTest extends AbstractSyncariTest {

    @Autowired
    SubscriptionCleaner orgCleaner;

    @Autowired
    OrganizationRepo organizationRepo;

    @Autowired
    UserService userService;

    @Autowired
    UserRepo userRepo;

    @Autowired
    ProvisioningService provisioningService;

    ProvisionRequest request;
    @Autowired
    SyncariMongoUtils mongoUtils;

    @Override
    public void tearDown() {
        Optional<User> user = userService.getUserByEmail("test1@email.com");
        user.ifPresent(u -> userService.deleteUser(u.getId()));
        var fromRepo = organizationRepo.findByName("Demo Org");
        fromRepo.ifPresent(fRepo -> organizationRepo.deleteById(fRepo.getId()));

        Optional<User> user2 = userService.getUserByEmail("test2@email.com");
        user2.ifPresent(u -> userService.deleteUser(u.getId()));
        var fromRepo2 = organizationRepo.findByName("Demo Org(test2)");
        fromRepo2.ifPresent(fRepo -> organizationRepo.deleteById(fRepo.getId()));
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
        request.setInstanceName("Trial Instance");
    }

    // test to remove 1 trial instance
    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_ORG })
    public void provisionOrganizationWithTrialPlan() {
        var saved = provisioningService.provision(
                request.getInstanceName(),
                InstanceType.trial,
                request.getInstanceDisplayName(),
                request.getOrganizationName(),
                request.getAdminUserName(),
                request.getPlanName(),
                RoleConstants.ORG_ADMIN,
                request.getAdminFirstName(),
                request.getAdminLastName(),
                OrganizationType.trial,null
        );
        Organization organization = saved.getOrganization();
        Date expiryDate = Date.from(Instant.now().minus(35, ChronoUnit.DAYS));
        organization.setCreatedAt(expiryDate);
        organization.getInstances().get(0).setCreatedAt(expiryDate);
        organizationRepo.save(organization);
        assertEquals(saved.getOrganization().getName(), "Demo Org");
        SubscriptionService.TRIAL_PERIOD = 0;
        orgCleaner.removeExpiredInstance();
        assertFalse(organizationRepo.findByName("Demo Org").isPresent());
    }

    // test to delete recent deleted instance
    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_ORG })
    public void testNotDeprovisionRecentInstance() {
        var saved = provisioningService.provision(
                request.getInstanceName(),
                InstanceType.trial,
                request.getInstanceDisplayName(),
                request.getOrganizationName(),
                request.getAdminUserName(),
                request.getPlanName(),
                RoleConstants.ORG_ADMIN,
                request.getAdminFirstName(),
                request.getAdminLastName(),
                OrganizationType.trial,null
        );
        assertNotNull(saved);
        assertEquals(saved.getOrganization().getName(), "Demo Org");
        SubscriptionService.TRIAL_PERIOD = 0;
        orgCleaner.removeExpiredInstance();
        //Demo Org should still exist as default trial period is 60 days
        assertNotNull(organizationRepo.findByName("Demo Org"));

        // deprovision the instance anyway
        SubscriptionService.TRIAL_PERIOD = 0;
        orgCleaner.removeExpiredInstance();
    }

    // test to check trial period, it should be non negative
    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_ORG })
    public void invalidTrialPeriod() {
        var saved = provisioningService.provision(
                "Trial Instance(test2)",
                InstanceType.trial,
                "Trial Instance(test2)",
                "Demo Org(test2)",
                "test2@email.com",
                "trial",
                RoleConstants.ORG_ADMIN,
                "testFirstName",
                "testLastName",
                OrganizationType.trial,null
        );
        assertEquals(saved.getOrganization().getName(), "Demo Org(test2)");
        SubscriptionService.TRIAL_PERIOD = -10;
        try {
            orgCleaner.removeExpiredInstance();
            fail();
        }
        catch (RuntimeException e){
            assertTrue(e.getMessage().equals("Custom set trial period must be non-negative"));
        }
    }



    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_ORG })
    public void avoidDeprovisionProdInstance() {
        var saved = provisioningService.provision(
                request.getInstanceName(),
                InstanceType.production,
                request.getInstanceDisplayName(),
                "Demo Org2",
                request.getAdminUserName(),
                request.getPlanName(),
                RoleConstants.ORG_ADMIN,
                request.getAdminFirstName(),
                request.getAdminLastName(),
                OrganizationType.trial,null
        );
        assertNotNull(saved);
        assertEquals(saved.getOrganization().getName(), "Demo Org2");
        SubscriptionService.TRIAL_PERIOD = 0;
        orgCleaner.removeExpiredInstance();
        assertTrue(organizationRepo.findByName("Demo Org2").isPresent());
        assertNotNull(saved.getOrganization().getId());
        provisioningService.deprovision(saved.getOrganization().getId(), true);
    }

    // trial week notice
    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_ORG })
    public void provisionTrialWithWeekNotice() {
        var saved = provisioningService.provision(
                request.getInstanceName(),
                InstanceType.trial,
                request.getInstanceDisplayName(),
                "Demo Org3",
                request.getAdminUserName(),
                request.getPlanName(),
                RoleConstants.ORG_ADMIN,
                request.getAdminFirstName(),
                request.getAdminLastName(),
                OrganizationType.trial,null
        );
        assertNotNull(saved);
        assertEquals(saved.getOrganization().getName(), "Demo Org3");
        Organization organization = saved.getOrganization();
        Date expiryDate = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));
        organization.setCreatedAt(expiryDate);
        organization.getInstances().get(0).setCreatedAt(expiryDate);
        organizationRepo.save(organization);
        assertEquals(saved.getOrganization().getName(), "Demo Org3");
        EmailService emailService = Mockito.mock(EmailService.class);
        orgCleaner.setEmailService(emailService);
        Mockito.doNothing().when(emailService).sendText(Mockito.any(),Mockito.any(),Mockito.any());
        orgCleaner.removeExpiredInstance();
        Mockito.verify(emailService, Mockito.times(1)).sendText(Mockito.any(),Mockito.any(),Mockito.any());
        assertNotNull(saved.getOrganization().getId());
        provisioningService.deprovision(saved.getOrganization().getId(), true);
    }

    // test deleted org after 60 days of org deletion
    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_ORG })
    public void testDeleteOrg() {
        // Non deleted orgs are not touched by cleaner
        var saved = provisioningService.provision(
                "cleaner",
                InstanceType.production,
                "cleaner",
                "Cleaner Org",
                request.getAdminUserName(),
                request.getPlanName(),
                RoleConstants.ORG_ADMIN,
                request.getAdminFirstName(),
                request.getAdminLastName(),
                OrganizationType.standard,null
        ).getOrganization();
        assertEquals(saved.getName(), "Cleaner Org");
        orgCleaner.removeExpiredInstance();
        assertTrue(organizationRepo.findByName("Cleaner Org").isPresent());

        try {
            // Deleted orgs in the last 30 days are not touched by cleaner
            saved.setStatus(Status.DELETED);
            organizationRepo.save(saved);
            orgCleaner.removeDeletedSubscriptions();
            assertTrue(organizationRepo.findByName("Cleaner Org").isPresent());

            // Deleted orgs > 30 days is deleted by cleaner
            Date dateBefore30Days = DateUtils.addDays(new Date(),-31);
//            saved.setUpdatedAt(dateBefore60Days);
            mongoUtils.updateMany("organization", Map.of(saved.getId(), Map.of("deletedAt", dateBefore30Days, "status", Status.DELETED.name())));
            orgCleaner.removeDeletedSubscriptions();
            assertFalse(organizationRepo.findByName("Cleaner Org").isPresent());
        } finally {
            provisioningService.deprovision(saved.getId(), true);
        }
    }

    // test deleted instance
    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_ORG })
    public void testDeletedInstance() {
        // Non deleted orgs are not touched by cleaner
        var saved = provisioningService.provision(
                "cleanertest",
                InstanceType.production,
                "cleanertest",
                "Cleaner Orgtest",
                request.getAdminUserName(),
                request.getPlanName(),
                RoleConstants.ORG_ADMIN,
                request.getAdminFirstName(),
                request.getAdminLastName(),
                OrganizationType.standard,null
        ).getOrganization();
        assertEquals(saved.getName(), "Cleaner Orgtest");
        orgCleaner.removeExpiredInstance();
        assertTrue(organizationRepo.findByName("Cleaner Orgtest").isPresent());
        SubscriptionService.TRIAL_PERIOD = 60;
        try {
            orgCleaner.removeExpiredInstance();
            assertTrue(organizationRepo.findByName("Cleaner Orgtest").isPresent());
            assertFalse(CollectionUtils.isNotEmpty(organizationRepo.findDeletedInstancesOrg()));
            // Set instance to be deleted
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -8);
            saved.getInstances().get(0).setDeletedAt(cal.getTime());
            saved.getInstances().get(0).setStatus(Status.DELETED);
            organizationRepo.save(saved);
            assertTrue(CollectionUtils.isNotEmpty(organizationRepo.findDeletedInstancesOrg()));
            orgCleaner.removeExpiredInstance();
            assertTrue(organizationRepo.findByName("Cleaner Orgtest").isPresent());
            assertFalse(CollectionUtils.isNotEmpty(organizationRepo.findDeletedInstancesOrg()));
            // Deleted orgs > 30 days is deleted by cleaner
            Date dateBefore30Days = DateUtils.addDays(new Date(), -31);
            mongoUtils.updateMany("organization", Map.of(saved.getId(), Map.of("deletedAt", dateBefore30Days, "status", Status.DELETED.name())));
            orgCleaner.removeExpiredInstance();
            assertFalse(organizationRepo.findByName("Cleaner Orgtest").isPresent());
        } finally {
            provisioningService.deprovision(saved.getId(), true);
        }
    }

    // Test cases
    // Create a trial subscription and a Create production instance in same org, Delete instance , call removeExpiredInstance, Trial subscription should not be deleted as it is not expired
    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_ORG })
    public void addOneTrialInstanceAndOneProdInstanceAndDeleteProdInstance() {
        var saved = provisioningService.provision(
                request.getInstanceName(),
                InstanceType.trial,
                request.getInstanceDisplayName(),
                "Demo Org Trial test",
                request.getAdminUserName(),
                request.getPlanName(),
                RoleConstants.ORG_ADMIN,
                request.getAdminFirstName(),
                request.getAdminLastName(),
                OrganizationType.standard,null
        );
        assertNotNull(saved);
        assertEquals(saved.getOrganization().getName(), "Demo Org Trial test");
        Organization organization = saved.getOrganization();
        Optional<User> user = userRepo.findByEmail(request.getAdminUserName());
        assertTrue(user.isPresent());
        Instance instance1 = provisioningService.provisionInstance(saved.getOrganization(), "Production Instance", "Production Instance",
                InstanceType.production, "default", user.get());
        assertNotNull(instance1);
        Optional<Instance> in = organization.getInstance(instance1.getSyncariId());
        in.ifPresent(i -> {
            i.setStatus(Status.DELETED);
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -8);
            i.setDeletedAt(cal.getTime());
        });
        organizationRepo.save(organization);
        SubscriptionService.TRIAL_PERIOD = 60;
        orgCleaner.removeExpiredInstance();
        Optional<Organization> org = organizationRepo.findByName("Demo Org Trial test");
        assertTrue(org.isPresent());
        assertTrue(CollectionUtils.isNotEmpty(org.get().getInstances()));
        assertEquals(1, org.get().getInstances().size());
        provisioningService.deprovision(saved.getOrganization().getId(), true);
    }

    // Create a 2 trial instances under 1 subscription, Create production instance, Expire 1 trial instance, call removeExpiredInstance, 1 trial instance and production instance should stay as it is.
    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_ORG })
    public void addTwoTrialInstanceAndDeprovisionOne() {
        var saved = provisioningService.provision(
                request.getInstanceName(),
                InstanceType.trial,
                request.getInstanceDisplayName(),
                "Demo Org Trial test1",
                request.getAdminUserName(),
                request.getPlanName(),
                RoleConstants.ORG_ADMIN,
                request.getAdminFirstName(),
                request.getAdminLastName(),
                OrganizationType.standard,null
        );
        assertNotNull(saved);
        assertEquals(saved.getOrganization().getName(), "Demo Org Trial test1");
        Organization organization = saved.getOrganization();
        Date expiryDate = Date.from(Instant.now().minus(35, ChronoUnit.DAYS));
        organization.getInstances().get(0).setCreatedAt(expiryDate);
        Optional<User> user = userRepo.findByEmail(request.getAdminUserName());
        assertTrue(user.isPresent());
        Instance instance1 = provisioningService.provisionInstance(saved.getOrganization(), "Trial 2", "Trial 2",
                InstanceType.trial,"trial",user.get());
        Instance instance2 = provisioningService.provisionInstance(saved.getOrganization(), "Production Instance", "Production Instance",
                InstanceType.trial,"default",user.get());
        assertNotNull(instance1);
        assertNotNull(instance2);
        SubscriptionService.TRIAL_PERIOD = 30;
        orgCleaner.removeExpiredInstance();
        Optional<Organization> org = organizationRepo.findByName("Demo Org Trial test1");
        assertTrue(org.isPresent());
        assertTrue(CollectionUtils.isNotEmpty(org.get().getInstances()));
        assertEquals(2,org.get().getInstances().size());
        provisioningService.deprovision(saved.getOrganization().getId(), true);
    }

    // Create a 2 trial instances under 2 subscription, Expire 1 trial instance, call removeExpiredInstance, 1 trial instance and production instance should stay as it is.
    @Test
    @WithMockUser(username = "admin", authorities = { PROVISION_ORG })
    public void addTwoTrialSubsAndDeprovisionOne() {
        var saved = provisioningService.provision(
                request.getInstanceName(),
                InstanceType.trial,
                request.getInstanceDisplayName(),
                "Demo Org Trial",
                request.getAdminUserName(),
                request.getPlanName(),
                RoleConstants.ORG_ADMIN,
                request.getAdminFirstName(),
                request.getAdminLastName(),
                OrganizationType.trial,null
        );
        assertNotNull(saved);
        assertEquals(saved.getOrganization().getName(), "Demo Org Trial");

        var saved2 = provisioningService.provision(
                request.getInstanceName(),
                InstanceType.trial,
                request.getInstanceDisplayName(),
                "Demo Org Trial2",
                request.getAdminUserName(),
                request.getPlanName(),
                RoleConstants.ORG_ADMIN,
                request.getAdminFirstName(),
                request.getAdminLastName(),
                OrganizationType.trial,null
        );
        Organization organization = saved2.getOrganization();
        Date expiryDate = Date.from(Instant.now().minus(35, ChronoUnit.DAYS));
        organization.setCreatedAt(expiryDate);
        organization.getInstances().get(0).setCreatedAt(expiryDate);
        organizationRepo.save(organization);

        assertNotNull(saved2);
        assertEquals(saved2.getOrganization().getName(), "Demo Org Trial2");
        SubscriptionService.TRIAL_PERIOD = 30;
        orgCleaner.removeExpiredInstance();
        assertTrue(organizationRepo.findByName("Demo Org Trial").isPresent());
        provisioningService.deprovision(saved.getOrganization().getId(), true);
    }


}