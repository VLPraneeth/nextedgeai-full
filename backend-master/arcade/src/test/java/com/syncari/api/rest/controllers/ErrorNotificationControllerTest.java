package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.syncari.core.model.misc.ErrorNotificationChannelType;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.syncari.api.rest.controllers.data.notification.ErrorCatalogDTO;
import com.syncari.api.rest.controllers.data.notification.ErrorNotificationConfigDTO;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.EmailConfig;
import com.syncari.core.model.EmailConfigStatus;
import com.syncari.core.model.ErrorCatalog;
import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.ErrorNotification;
import com.syncari.core.model.ErrorNotificationEmailConfig;
import com.syncari.core.model.ErrorNotificationFrequency;
import com.syncari.core.model.ErrorPriority;
import com.syncari.core.model.errornotification.TestRequest;
import com.syncari.core.model.errornotification.WebhookRequestBody;
import com.syncari.core.model.misc.ErrorNotificationConfigStatus;
import com.syncari.core.repositories.customer.ErrorCatalogRepo;
import com.syncari.core.repositories.customer.ErrorNotificationConfigRepo;
import com.syncari.core.service.ErrorNotificationService;
import com.syncari.restutils.transformers.ErrorNotificationTransformer;

public class ErrorNotificationControllerTest extends AbstractSyncariTest {

	@Autowired
	ErrorNotificationController controller;
	@Autowired
	ErrorNotificationService errorNotificationService;
	@Autowired
	ErrorNotificationTransformer transformer;
	@Autowired
	ErrorCatalogRepo catalogRepo;
	@Autowired
	ErrorNotificationConfigRepo configRepo;

	@Override
	public void tearDown() {
		configRepo.deleteAll();
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {READ_ERROR_NOTIFICATION_EMAIL,READ_ERROR_NOTIFICATION_WEBHOOK})
	public void testGetTypes() {
		// Test GET /api/v1/errorNotifications/types
		List<ErrorCatalogDTO> types = controller.getTypes();

		assertNotNull("Types should not be null", types);
		assertFalse("Types should not be empty", types.isEmpty());

		// Verify that the returned types match error catalogs
		List<ErrorCatalog> catalogs = errorNotificationService.getErrorCatalogs();
		assertEquals("Type count should match catalog count", catalogs.size(), types.size());
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {READ_ERROR_NOTIFICATION_EMAIL,READ_ERROR_NOTIFICATION_WEBHOOK})
	public void testGetCadences() {
		// Test GET /api/v1/errorNotifications/cadences
		List<Map<String, String>> cadences = controller.getCadences();

		assertNotNull("Cadences should not be null", cadences);
		assertFalse("Cadences should not be empty", cadences.isEmpty());

		// Verify all ErrorNotificationFrequency values are included
		assertEquals("Cadence count should match enum values",
				ErrorNotificationFrequency.values().length, cadences.size());

		// Verify each cadence has frequency and label
		for (Map<String, String> cadence : cadences) {
			assertTrue("Cadence should have frequency", cadence.containsKey("frequency"));
			assertTrue("Cadence should have label", cadence.containsKey("label"));
		}
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {READ_ERROR_NOTIFICATION_EMAIL,READ_ERROR_NOTIFICATION_WEBHOOK})
	public void testGetAllConfigurations() {
		// Test GET /api/v1/errorNotifications/configurations
		// Create test configuration
		ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
		config.setName("Test Config");
		config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
		config.setNotificationTypes(catalogRepo.findAll().stream()
				.map(cat -> cat.getId()).collect(Collectors.toList()));
		config.setStatus(ErrorNotificationConfigStatus.Active);
		config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
		configRepo.save(config);

		List<ErrorNotificationConfigDTO> configurations = controller.getAllConfigurations();

		assertNotNull("Configurations should not be null", configurations);
		assertFalse("Configurations should not be empty", configurations.isEmpty());

		// Verify our test config is in the list
		boolean foundConfig = configurations.stream()
				.anyMatch(c -> "Test Config".equals(c.getName()));
		assertTrue("Test config should be in the list", foundConfig);

		configRepo.deleteAll();
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {READ_ERROR_NOTIFICATION_EMAIL,READ_ERROR_NOTIFICATION_WEBHOOK})
	public void testGetConfiguration() {
		// Test GET /api/v1/errorNotifications/configurations/{id}
		// Create test configuration
		ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
		config.setName("Test Single Config");
		config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
		config.setNotificationTypes(catalogRepo.findAll().stream()
				.map(cat -> cat.getId()).collect(Collectors.toList()));
		config.setStatus(ErrorNotificationConfigStatus.Active);
		config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
		config = (ErrorNotificationEmailConfig) configRepo.save(config);

		ErrorNotificationConfigDTO result = controller.getConfiguration(config.getId());

		assertNotNull("Configuration should not be null", result);
		assertEquals("Configuration name should match", "Test Single Config", result.getName());
		assertEquals("Configuration ID should match", config.getId(), result.getId());

		configRepo.deleteAll();
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {WRITE_ERROR_NOTIFICATION_EMAIL,WRITE_ERROR_NOTIFICATION_WEBHOOK})
	public void testEditConfiguration() {
		// Test PUT /api/v1/errorNotifications/configurations/{id}
		// Create initial configuration
		ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
		config.setName("Original Name");
		config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
		config.setNotificationTypes(catalogRepo.findAll().stream()
				.map(cat -> cat.getId()).collect(Collectors.toList()));
		config.setStatus(ErrorNotificationConfigStatus.Active);
		config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
		config = (ErrorNotificationEmailConfig) configRepo.save(config);

		// Update the configuration
		ErrorNotificationConfigDTO dto = transformer.toConfigDTO(config);
		dto.setName("Updated Name");
		dto.setCadence(ErrorNotificationFrequency.DAILY);

		ErrorNotificationConfigDTO result = controller.editConfiguration(config.getId(), dto);

		assertNotNull("Updated configuration should not be null", result);
		assertEquals("Configuration ID should remain the same", config.getId(), result.getId());
		assertEquals("Configuration name should be updated", "Updated Name", result.getName());
		assertEquals("Configuration cadence should be updated",
				ErrorNotificationFrequency.DAILY, result.getCadence());

		configRepo.deleteAll();
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {WRITE_ERROR_NOTIFICATION_EMAIL,WRITE_ERROR_NOTIFICATION_WEBHOOK})
	public void testDeleteConfiguration() {
		// Test DELETE /api/v1/errorNotifications/configurations/{id}
		// Create test configuration
		ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
		config.setName("To Be Deleted");
		config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
		config.setNotificationTypes(catalogRepo.findAll().stream()
				.map(cat -> cat.getId()).collect(Collectors.toList()));
		config.setStatus(ErrorNotificationConfigStatus.Active);
		config.setEmails(List.of(new EmailConfig("test@example.com", EmailConfigStatus.Active)));
		config = (ErrorNotificationEmailConfig) configRepo.save(config);

		String configId = config.getId();

		// Delete the configuration
		Object result = controller.deleteConfiguration(configId);

		assertNotNull("Delete result should not be null", result);
		assertTrue("Delete should return success", result instanceof Map);
		assertEquals("Delete should return success=true", "true", ((Map) result).get("success"));

		// Verify configuration is deleted
		assertTrue("Configuration should be deleted", configRepo.findById(configId).isEmpty());

		configRepo.deleteAll();
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {READ_ERROR_NOTIFICATION_EMAIL,READ_ERROR_NOTIFICATION_WEBHOOK})
	public void testBodyWebhookBody() {
		// Test GET /api/v1/errorNotifications/configurations/webhook/body
		WebhookRequestBody body = controller.bodyWebhookBody();

		assertNotNull("Webhook body should not be null", body);
		assertNotNull("Webhook body should have notifications", body.getNotifications());
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {WRITE_ERROR_NOTIFICATION_EMAIL,WRITE_ERROR_NOTIFICATION_WEBHOOK})
	public void testResendOptIn() {
		// Test POST /api/v1/errorNotifications/configurations/{id}/{email}/resendOptIn
		// Create test configuration
		ErrorNotificationEmailConfig config = new ErrorNotificationEmailConfig();
		config.setName("Test Resend OptIn");
		config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
		config.setNotificationTypes(catalogRepo.findAll().stream()
				.map(cat -> cat.getId()).collect(Collectors.toList()));
		config.setStatus(ErrorNotificationConfigStatus.Active);
		config.setEmails(List.of(new EmailConfig("optin@example.com", EmailConfigStatus.Pending)));
		config = (ErrorNotificationEmailConfig) configRepo.save(config);

		ErrorNotificationConfigDTO result = controller.resendOptIn(config.getId(), "optin@example.com");

		assertNotNull("Resend OptIn result should not be null", result);
		assertEquals("Configuration ID should match", config.getId(), result.getId());

		configRepo.deleteAll();
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {READ_ERROR_NOTIFICATION_EMAIL,READ_ERROR_NOTIFICATION_WEBHOOK})
	public void testGetTypesContainsAllCategories() {
		// Verify that getTypes returns all error categories
		List<ErrorCatalogDTO> types = controller.getTypes();

		// Get all catalogs from database
		List<ErrorCatalog> allCatalogs = catalogRepo.findAll();

		assertEquals("Should return all error catalogs", allCatalogs.size(), types.size());

		// Verify each catalog is represented in the DTOs
		for (ErrorCatalog catalog : allCatalogs) {
			boolean found = types.stream()
					.anyMatch(dto -> dto.getId().equals(catalog.getId()));
			assertTrue("Catalog " + catalog.getCategory() + "_" + catalog.getPriority()
					+ " should be in types", found);
		}
	}
}
