package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.EmailConfig;
import com.syncari.core.model.EmailConfigStatus;
import com.syncari.core.model.ErrorCatalog;
import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.ErrorNotification;
import com.syncari.core.model.ErrorNotificationEmailConfig;
import com.syncari.core.model.ErrorNotificationFrequency;
import com.syncari.core.model.ErrorNotificationWebhookConfig;
import com.syncari.core.model.ErrorPriority;
import com.syncari.core.model.errornotification.WebhookRequestBodyNotification;
import com.syncari.core.model.misc.ErrorNotificationConfigStatus;
import com.syncari.core.repositories.customer.ErrorCatalogRepo;
import com.syncari.core.repositories.customer.ErrorNotificationConfigRepo;

public class ErrorNotificationEmailHelperTest extends AbstractSyncariTest {

	@Autowired
	ErrorNotificationConfigRepo configRepo;
	@Autowired
    ObjectMapper objectMapper;
	@Autowired
	ErrorCatalogRepo catalogRepo;
	@Autowired
    ErrorNotificationEmailHelper emailHelper;

    @Override
    public void tearDown() {
        super.tearDown();
    }
    
    @Test
    public void testSendEmail() {
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);
    	
    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test email group");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
    	config.setProcessing(true);
    	configRepo.save(config);
    	
    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	notif.setCreatedAt(new Date());
    	
    	emailHelper.sendEmail(config, List.of(notif));
    	var updatedConfig = configRepo.findById(config.getId());
    	assertFalse(updatedConfig.isEmpty());
    	assertNotNull(updatedConfig.get().getLastNotificationTimestamp());
    	assertNull(updatedConfig.get().getLastErrorTimestamp());
    	assertFalse(updatedConfig.get().isProcessing());
    	configRepo.deleteAll();
    	verify(emailService, times(1)).sendHtml(anyList(), anyString(), anyString());
    }
    
    @Test
    public void testSendEmailEmptyEmails() {
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);
    	
    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test email group");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of());
    	config.setProcessing(true);
    	configRepo.save(config);
    	
    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	notif.setCreatedAt(new Date());
    	
    	emailHelper.sendEmail(config, List.of(notif));
    	var updatedConfig = configRepo.findById(config.getId());
    	assertFalse(updatedConfig.isEmpty());
    	assertNotNull(updatedConfig.get().getLastNotificationTimestamp());
    	assertNull(updatedConfig.get().getLastErrorTimestamp());
    	assertFalse(updatedConfig.get().isProcessing());
    	configRepo.deleteAll();
    	verify(emailService, times(0)).sendHtml(anyList(), anyString(), anyString()); // only to support email
    }

    @Test
    public void testTransientErrorResetsRetryCounter() {
    	// Test that transient errors (network timeout) reset retry counter to 0
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);

    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test transient error");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
    	config.setRetries(2); // Simulate previous failures
    	config.setProcessing(true);
    	configRepo.save(config);

    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	notif.setCreatedAt(new Date());

    	// Mock transient error (SocketTimeoutException wrapped in RuntimeException)
    	doThrow(new RuntimeException(new java.net.SocketTimeoutException("Connection timed out")))
    		.when(emailService).sendHtml(anyList(), anyString(), anyString());

    	emailHelper.sendEmail(config, List.of(notif));

    	var updatedConfig = configRepo.findById(config.getId());
    	assertFalse(updatedConfig.isEmpty());
    	assertEquals("Retry counter should be reset to 0 for transient errors",
    			Integer.valueOf(0), updatedConfig.get().getRetries());
    	assertEquals("Status should remain Active for transient errors",
    			ErrorNotificationConfigStatus.Active, updatedConfig.get().getStatus());
    	assertNotNull("Last error timestamp should be set", updatedConfig.get().getLastErrorTimestamp());
    	assertFalse(updatedConfig.get().isProcessing());

    	configRepo.deleteAll();
    	verify(emailService, times(1)).sendHtml(anyList(), anyString(), anyString());
    }

    @Test
    public void testPermanentErrorIncrementsRetryCounter() {
    	// Test that permanent errors increment retry counter
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);

    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test permanent error");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
    	config.setRetries(0);
    	config.setProcessing(true);
    	configRepo.save(config);

    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	notif.setCreatedAt(new Date());

    	// Mock permanent error (Send Failed wrapped in RuntimeException)
    	doThrow(new RuntimeException(new javax.mail.SendFailedException("550 Invalid recipient address")))
    		.when(emailService).sendHtml(anyList(), anyString(), anyString());

    	emailHelper.sendEmail(config, List.of(notif));

    	var updatedConfig = configRepo.findById(config.getId());
    	assertFalse(updatedConfig.isEmpty());
    	assertEquals("Retry counter should be incremented to 1 for permanent errors",
    			Integer.valueOf(1), updatedConfig.get().getRetries());
    	assertEquals("Status should remain Active after 1 failure",
    			ErrorNotificationConfigStatus.Active, updatedConfig.get().getStatus());
    	assertNotNull("Last error timestamp should be set", updatedConfig.get().getLastErrorTimestamp());

    	configRepo.deleteAll();
    	verify(emailService, times(1)).sendHtml(anyList(), anyString(), anyString());
    }

    @Test
    public void testThreeConsecutivePermanentErrorsDisableConfig() {
    	// Test that 3 consecutive permanent errors disable the configuration
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);

    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test disable after 3 failures");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
    	config.setRetries(2); // Already 2 failures
    	config.setProcessing(true);
    	configRepo.save(config);

    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	notif.setCreatedAt(new Date());

    	// Mock permanent error for 3rd failure
    	doThrow(new RuntimeException(new javax.mail.SendFailedException("550 Invalid recipient address")))
    		.when(emailService).sendHtml(anyList(), anyString(), anyString());

    	emailHelper.sendEmail(config, List.of(notif));

    	var updatedConfig = configRepo.findById(config.getId());
    	assertFalse(updatedConfig.isEmpty());
    	assertEquals("Retry counter should be 3 after 3rd failure",
    			Integer.valueOf(3), updatedConfig.get().getRetries());
    	assertEquals("Status should be Disabled after 3 consecutive permanent failures",
    			ErrorNotificationConfigStatus.Disabled, updatedConfig.get().getStatus());
    	assertNotNull("Last error timestamp should be set", updatedConfig.get().getLastErrorTimestamp());

    	configRepo.deleteAll();
    	verify(emailService, times(1)).sendHtml(anyList(), anyString(), anyString());
    }

    @Test
    public void testTransientErrorBetweenPermanentErrorsBreaksStreak() {
    	// Test that transient error in between permanent errors resets the counter
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);

    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test transient breaks streak");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
    	config.setProcessing(true);

    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	notif.setCreatedAt(new Date());

    	// First permanent error
    	config.setRetries(0);
    	configRepo.save(config);
    	doThrow(new RuntimeException(new javax.mail.SendFailedException("550 Invalid recipient address")))
    		.when(emailService).sendHtml(anyList(), anyString(), anyString());
    	emailHelper.sendEmail(config, List.of(notif));
    	assertEquals("Retry counter should be 1", Integer.valueOf(1), configRepo.findById(config.getId()).get().getRetries());

    	// Second permanent error
    	config = (ErrorNotificationEmailConfig) configRepo.findById(config.getId()).get();
    	doThrow(new RuntimeException(new javax.mail.SendFailedException("550 Invalid recipient address")))
    		.when(emailService).sendHtml(anyList(), anyString(), anyString());
    	emailHelper.sendEmail(config, List.of(notif));
    	assertEquals("Retry counter should be 2", Integer.valueOf(2), configRepo.findById(config.getId()).get().getRetries());

    	// Transient error - should reset counter
    	config = (ErrorNotificationEmailConfig) configRepo.findById(config.getId()).get();
    	doThrow(new RuntimeException(new java.net.ConnectException("Connection refused")))
    		.when(emailService).sendHtml(anyList(), anyString(), anyString());
    	emailHelper.sendEmail(config, List.of(notif));
    	var updatedConfig = configRepo.findById(config.getId()).get();
    	assertEquals("Retry counter should be reset to 0 after transient error",
    			Integer.valueOf(0), updatedConfig.getRetries());
    	assertEquals("Status should remain Active",
    			ErrorNotificationConfigStatus.Active, updatedConfig.getStatus());

    	// Another permanent error - should start from 1 again
    	doThrow(new RuntimeException(new javax.mail.SendFailedException("550 Invalid recipient address")))
    		.when(emailService).sendHtml(anyList(), anyString(), anyString());
    	emailHelper.sendEmail((ErrorNotificationEmailConfig) updatedConfig, List.of(notif));
    	updatedConfig = configRepo.findById(config.getId()).get();
    	assertEquals("Retry counter should restart at 1 after transient error reset",
    			Integer.valueOf(1), updatedConfig.getRetries());
    	assertEquals("Status should still be Active",
    			ErrorNotificationConfigStatus.Active, updatedConfig.getStatus());

    	configRepo.deleteAll();
    }

    @Test
    public void testSuccessfulSendResetsRetryCounter() {
    	// Test that successful send resets retry counter
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);

    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test success resets counter");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
    	config.setRetries(2); // Had previous failures
    	config.setProcessing(true);
    	configRepo.save(config);

    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	notif.setCreatedAt(new Date());

    	// Successful send (no exception)
    	emailHelper.sendEmail(config, List.of(notif));

    	var updatedConfig = configRepo.findById(config.getId());
    	assertFalse(updatedConfig.isEmpty());
    	assertEquals("Retry counter should be reset to 0 after successful send",
    			Integer.valueOf(0), updatedConfig.get().getRetries());
    	assertEquals("Status should remain Active",
    			ErrorNotificationConfigStatus.Active, updatedConfig.get().getStatus());
    	assertNull("Last error timestamp should be null on success", updatedConfig.get().getLastErrorTimestamp());

    	configRepo.deleteAll();
    	verify(emailService, times(1)).sendHtml(anyList(), anyString(), anyString());
    }

    @Test
    public void testMultipleTransientErrorTypes() {
    	// Test various types of transient errors all reset counter
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);

    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test multiple transient types");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
    	config.setProcessing(true);

    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	notif.setCreatedAt(new Date());

    	// Test SocketTimeoutException
    	config.setRetries(1);
    	configRepo.save(config);
    	doThrow(new RuntimeException(new java.net.SocketTimeoutException("Read timed out")))
    		.when(emailService).sendHtml(anyList(), anyString(), anyString());
    	emailHelper.sendEmail(config, List.of(notif));
    	assertEquals("SocketTimeoutException should reset counter",
    			Integer.valueOf(0), configRepo.findById(config.getId()).get().getRetries());

    	// Test ConnectException
    	config = (ErrorNotificationEmailConfig) configRepo.findById(config.getId()).get();
    	config.setRetries(2);
    	configRepo.save(config);
    	doThrow(new RuntimeException(new java.net.ConnectException("Connection refused")))
    		.when(emailService).sendHtml(anyList(), anyString(), anyString());
    	emailHelper.sendEmail(config, List.of(notif));
    	assertEquals("ConnectException should reset counter",
    			Integer.valueOf(0), configRepo.findById(config.getId()).get().getRetries());

    	// Test UnknownHostException
    	config = (ErrorNotificationEmailConfig) configRepo.findById(config.getId()).get();
    	config.setRetries(1);
    	configRepo.save(config);
    	doThrow(new RuntimeException(new java.net.UnknownHostException("smtp.server.com")))
    		.when(emailService).sendHtml(anyList(), anyString(), anyString());
    	emailHelper.sendEmail(config, List.of(notif));
    	assertEquals("UnknownHostException should reset counter",
    			Integer.valueOf(0), configRepo.findById(config.getId()).get().getRetries());

    	configRepo.deleteAll();
    }

    @Test
    public void testDeeplyNestedTransientError() {
    	// Test that transient errors nested multiple levels deep are still detected
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);

    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test deeply nested transient error");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
    	config.setRetries(2); // Had previous failures
    	config.setProcessing(true);
    	configRepo.save(config);

    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	notif.setCreatedAt(new Date());

    	// Create deeply nested exception: RuntimeException -> IllegalStateException -> IOException -> SocketTimeoutException
    	java.net.SocketTimeoutException rootCause = new java.net.SocketTimeoutException("Connection timed out");
    	java.io.IOException level2 = new java.io.IOException("I/O error", rootCause);
    	IllegalStateException level1 = new IllegalStateException("Invalid state", level2);
    	RuntimeException wrapper = new RuntimeException("Wrapper exception", level1);

    	doThrow(wrapper).when(emailService).sendHtml(anyList(), anyString(), anyString());

    	emailHelper.sendEmail(config, List.of(notif));

    	var updatedConfig = configRepo.findById(config.getId());
    	assertFalse(updatedConfig.isEmpty());
    	assertEquals("Retry counter should be reset to 0 for deeply nested transient errors",
    			Integer.valueOf(0), updatedConfig.get().getRetries());
    	assertEquals("Status should remain Active for deeply nested transient errors",
    			ErrorNotificationConfigStatus.Active, updatedConfig.get().getStatus());

    	configRepo.deleteAll();
    	verify(emailService, times(1)).sendHtml(anyList(), anyString(), anyString());
    }

    @Test
    public void testCircularExceptionReferenceProtection() {
    	// Test that our circular reference protection in isTransientError works
    	// Note: We can't actually create a circular exception in the test because
    	// logging frameworks will cause StackOverflowError before our code runs.
    	// This test verifies the visited set works for deep (but not circular) chains.
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);

    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test deep exception chain");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
    	config.setRetries(1);
    	config.setProcessing(true);
    	configRepo.save(config);

    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	notif.setCreatedAt(new Date());

    	// Create a very deep exception chain (10 levels) to test performance
    	RuntimeException ex = new RuntimeException("Level 10");
    	for (int i = 9; i >= 1; i--) {
    		ex = new RuntimeException("Level " + i, ex);
    	}

    	doThrow(ex).when(emailService).sendHtml(anyList(), anyString(), anyString());

    	// This should complete quickly without hanging
    	long startTime = System.currentTimeMillis();
    	emailHelper.sendEmail(config, List.of(notif));
    	long duration = System.currentTimeMillis() - startTime;

    	// Should complete in reasonable time (not hang)
    	assert(duration < 5000); // Should take less than 5 seconds

    	var updatedConfig = configRepo.findById(config.getId());
    	assertFalse(updatedConfig.isEmpty());
    	// Should be treated as permanent error (not transient)
    	assertEquals("Retry counter should be incremented for non-transient deep exception",
    			Integer.valueOf(2), updatedConfig.get().getRetries());

    	configRepo.deleteAll();
    	verify(emailService, times(1)).sendHtml(anyList(), anyString(), anyString());
    }

    @Test
    public void testSMTPCodeWithWordBoundaries() {
    	// Test that SMTP codes with word boundaries don't cause false positives
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);

    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test SMTP code boundaries");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
    	config.setRetries(0);
    	config.setProcessing(true);
    	configRepo.save(config);

    	ErrorNotification notif = ErrorNotification.builder()
    			.body("test message body")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject")
    			.build();
    	notif.setCreatedAt(new Date());

    	// Test false positive: Error code 54502 should NOT be detected as SMTP 450
    	doThrow(new RuntimeException("Error code 54502: Database error"))
    		.when(emailService).sendHtml(anyList(), anyString(), anyString());
    	emailHelper.sendEmail(config, List.of(notif));
    	assertEquals("False positive '54502' should be treated as permanent error",
    			Integer.valueOf(1), configRepo.findById(config.getId()).get().getRetries());

    	// Test true positive: SMTP 450 should be detected as transient
    	config = (ErrorNotificationEmailConfig) configRepo.findById(config.getId()).get();
    	config.setRetries(2);
    	configRepo.save(config);
    	doThrow(new RuntimeException("SMTP error 450 Mailbox unavailable"))
    		.when(emailService).sendHtml(anyList(), anyString(), anyString());
    	emailHelper.sendEmail(config, List.of(notif));
    	assertEquals("True SMTP 450 should be detected as transient and reset counter",
    			Integer.valueOf(0), configRepo.findById(config.getId()).get().getRetries());

    	configRepo.deleteAll();
    }

    @Test
    public void testDigestEmailSubjectFormatting() {
    	// Test for ErrorNotificationEmailHelper.java:235
    	// Tests that the email subject is formatted correctly for digest notifications (multiple notifications)
    	ErrorCatalog catalog = catalogRepo.findByCategoryAndPriority(ErrorCategory.PIPELINE, ErrorPriority.P1).get(0);

    	ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
    	config.setName("Test digest subject");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
    	config.setProcessing(true);
    	config.setLastNotificationTimestamp(new Date());
    	configRepo.save(config);

    	// Create multiple notifications to trigger digest email path (line 221-235)
    	ErrorNotification notif1 = ErrorNotification.builder()
    			.body("test message body 1")
    			.catalogId(catalog.getId())
    			.componentId("random1")
    			.details(Map.of())
    			.key("PIPELINE_P1_random1")
    			.subject("test subject 1")
    			.build();
    	notif1.setCreatedAt(new Date());

    	ErrorNotification notif2 = ErrorNotification.builder()
    			.body("test message body 2")
    			.catalogId(catalog.getId())
    			.componentId("random2")
    			.details(Map.of())
    			.key("PIPELINE_P1_random2")
    			.subject("test subject 2")
    			.build();
    	notif2.setCreatedAt(new Date());

    	ErrorNotification notif3 = ErrorNotification.builder()
    			.body("test message body 3")
    			.catalogId(catalog.getId())
    			.componentId("random3")
    			.details(Map.of())
    			.key("PIPELINE_P1_random3")
    			.subject("test subject 3")
    			.build();
    	notif3.setCreatedAt(new Date());

    	List<ErrorNotification> notificationList = List.of(notif1, notif2, notif3);

    	// Call sendEmail with multiple notifications
    	emailHelper.sendEmail(config, notificationList);

    	// Capture the arguments passed to emailService.sendHtml
    	ArgumentCaptor<List> emailsCaptor = ArgumentCaptor.forClass(List.class);
    	ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
    	ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

    	verify(emailService, times(1)).sendHtml(emailsCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture());

    	// Verify the subject was formatted correctly on line 235
    	String capturedSubject = subjectCaptor.getValue();
    	assertNotNull("Email subject should not be null", capturedSubject);

    	// Verify subject contains expected components (line 235 formatting)
    	// subject = String.format(subject, SyncariContext.getInstance().getName(),
    	//                         SyncariContext.getInstance().getSyncariId(),
    	//                         SyncariContext.getOrganziation().getName(),
    	//                         notifList.size());
    	assertTrue("Subject should contain instance name",
    			capturedSubject.contains(com.syncari.core.SyncariContext.getInstance().getName()));
    	assertTrue("Subject should contain syncari ID",
    			capturedSubject.contains(com.syncari.core.SyncariContext.getInstance().getSyncariId()));
    	assertTrue("Subject should contain organization name",
    			capturedSubject.contains(com.syncari.core.SyncariContext.getOrganziation().getName()));
    	assertTrue("Subject should contain notification count",
    			capturedSubject.contains("3"));

    	// Verify repository was updated correctly
    	var updatedConfig = configRepo.findById(config.getId());
    	assertFalse(updatedConfig.isEmpty());
    	assertNotNull(updatedConfig.get().getLastNotificationTimestamp());
    	assertFalse(updatedConfig.get().isProcessing());

    	configRepo.deleteAll();
    }
}
