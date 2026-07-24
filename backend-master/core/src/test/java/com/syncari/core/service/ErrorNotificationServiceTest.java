package com.syncari.core.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.errornotification.TestRequest;
import com.syncari.core.model.misc.ErrorNotificationChannelType;
import com.syncari.core.model.misc.ErrorNotificationConfigStatus;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.repositories.customer.ErrorCatalogRepo;
import com.syncari.core.repositories.customer.ErrorNotificationConfigRepo;
import com.syncari.core.repositories.customer.ErrorNotificationInvitationRepo;
import com.syncari.core.repositories.customer.ErrorNotificationRepo;

public class ErrorNotificationServiceTest extends AbstractSyncariTest {

    @Autowired
    ErrorNotificationService notificationService;
    @Autowired
    ErrorNotificationRepo notificationRepo;
    @Autowired
	ErrorCatalogRepo catalogRepo;
    @Autowired
	UserService service;
    @Autowired
	ErrorNotificationConfigRepo configRepo;
    @MockBean
    public ErrorNotificationEmailHelper emailHelper;
    @MockBean
    public ErrorNotificationWebhookHelper webhookHelper;
    @Autowired
	private ErrorNotificationInvitationRepo invitationRepo;

    @Override
    public void tearDown() {
        super.tearDown();
        resetRepos(notificationRepo);
    }
       
    @Test
    public void testGetErrorCatalogs() {
    	assertNotNull(notificationService.getErrorCatalogs());
    	assertEquals(3, notificationService.getErrorCatalogs().size());
    }
    
    @Test
    public void testSendErrorNotification() {
    	boolean status = notificationService.sendErrorNotification(ErrorCategory.PIPELINE, ErrorPriority.P1, "random1", "test subject", "test body", Map.of("testData1", "testVal1"), null);
    	assertTrue("Expected true but was false", status);
    	Mockito.verify(publisher).publishToErrorNotificationQueue(any(Event.class));
    }
    
    @Test
    public void testProcessErrorNotificationFailed() {
    	boolean status = notificationService.processErrorNotification(null);
    	assertFalse("Expected false but was true", status);
    }
    
    @Test
    public void testSaveEmailConfig() {
    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test email group");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", null)));
    	config = (ErrorNotificationEmailConfig) notificationService.saveErrorNotificationConfig(config);
    	assertNotNull(config.getId());
    	assertEquals(config.getEmails().get(0).getStatus(), EmailConfigStatus.Pending);
    	configRepo.deleteAll();
    }
    
    @Test
    public void testSaveWebhookConfig() {
    	ErrorNotificationWebhookConfig config = new ErrorNotificationWebhookConfig();
    	config.setName("Test wh group");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setHttpMethod("POST");
    	config.setUrl("http://example.com");
    	config.setHeaders(Map.of());
    	config = (ErrorNotificationWebhookConfig) notificationService.saveErrorNotificationConfig(config);
    	assertNotNull(config.getId());
    	configRepo.deleteAll();
    }
    
    @Test
    public void testTestApiEmail() {
    	var res = notificationService.test(TestRequest.builder().type(ErrorNotificationChannelType.email).build());
    	assertEquals("true", ((Map) res.getY()).get("success"));
    }
    
    @Test
    public void testProcessErrorNotification1() {
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);
    	
    	User user = new User("testen@email.com", SyncariContext.getInstance().getSyncariId());
    	User saved = service.addUser(user);
    	service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
    	
    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test email group");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", null)));
    	config = (ErrorNotificationEmailConfig) notificationService.saveErrorNotificationConfig(config);
    	config.getEmails().stream().forEach(e -> e.setStatus(EmailConfigStatus.Active));
    	configRepo.save(config);
    	
    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	boolean status = notificationService.processErrorNotification(notif);
    	assertTrue("Expected true but was false", status);
    	assertEquals(1, notificationRepo.count());
    	
    	notificationRepo.deleteAll();
    	configRepo.deleteAll();
    }
    
    @Test
    public void testProcessErrorNotification2() {
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);
    	
    	User user = new User("testen2@email.com", SyncariContext.getInstance().getSyncariId());
    	User saved = service.addUser(user);
    	service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
    	
    	ErrorNotificationWebhookConfig config = new ErrorNotificationWebhookConfig();
    	config.setName("Test wh group");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setHttpMethod("POST");
    	config.setUrl("http://example.com");
    	config.setHeaders(Map.of());
    	config = (ErrorNotificationWebhookConfig) notificationService.saveErrorNotificationConfig(config);
    	
    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	boolean status = notificationService.processErrorNotification(notif);
    	assertTrue("Expected true but was false", status);
    	assertEquals(1, notificationRepo.count());
    	
    	notificationRepo.deleteAll();
    	configRepo.deleteAll();
    }

	@Test
	public void testWebhookValidation() {
		List<String> endpoints = List.of(
				"https://192.168.23.1/",
				"https://syncari.net",
				"https://metadata.com/",
				"https://google.internal"
		);

		endpoints.forEach(endpoint -> {
			ErrorNotificationWebhookConfig errorNotificationConfig = new ErrorNotificationWebhookConfig();
			errorNotificationConfig.setHttpMethod("POST").setUrl(endpoint);
			try {
				errorNotificationConfig.validate();
				fail();
			} catch (SyncariValidationException e){

			}
		});
	}
	
	@Test
	public void testEmailValidation() {
		List<String> emails = List.of(
				"user1@example.com",
				"user2@example.com.",
				"user3@example",
				"user4@example.com"
		);

		List<EmailConfig>  emailConfigs = new ArrayList<EmailConfig>();
		emails.forEach(em -> {
			emailConfigs.add(new EmailConfig(em, EmailConfigStatus.Pending));
		});
		ErrorNotificationEmailConfig enEmailConfig = new ErrorNotificationEmailConfig();
		enEmailConfig.setEmails(emailConfigs);
		try {
			enEmailConfig.validate();
			fail();
		} catch (SyncariValidationException e){
			assertEquals("The email address(es) user2@example.com., user3@example is not valid.", e.getMessage());
		}
	}
	
	@Test
    public void testSendOptin() {
    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test email group1");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", null)));
    	config = (ErrorNotificationEmailConfig) notificationService.saveErrorNotificationConfig(config);
    	var oldInvite = invitationRepo.findByConfigIdAndEmail(config.getId(), "test@example.com");
    	assertNotNull(config.getId());
    	assertEquals(config.getEmails().get(0).getStatus(), EmailConfigStatus.Pending);
    	assertFalse(oldInvite.isEmpty());
    	config = (ErrorNotificationEmailConfig) notificationService.sendOptin(config.getId(), "test@example.com");
    	assertEquals(config.getEmails().get(0).getStatus(), EmailConfigStatus.Pending);
    	var newInvite = invitationRepo.findByConfigIdAndEmail(config.getId(), "test@example.com");
    	assertFalse(newInvite.isEmpty());
    	assertNotEquals(oldInvite.get().getInvitationId(), newInvite.get().getInvitationId());
    	configRepo.deleteAll();
    }
}
