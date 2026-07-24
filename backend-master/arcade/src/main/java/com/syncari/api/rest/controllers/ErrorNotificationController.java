package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.READ_ERROR_NOTIFICATION_EMAIL;
import static com.syncari.core.security.Permissions.WRITE_ERROR_NOTIFICATION_EMAIL;
import static com.syncari.core.security.Permissions.READ_ERROR_NOTIFICATION_WEBHOOK;
import static com.syncari.core.security.Permissions.WRITE_ERROR_NOTIFICATION_WEBHOOK;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.syncari.api.rest.controllers.data.notification.ErrorCatalogDTO;
import com.syncari.api.rest.controllers.data.notification.ErrorNotificationConfigDTO;
import com.syncari.core.model.EmailConfigStatus;
import com.syncari.core.model.ErrorNotificationFrequency;
import com.syncari.core.model.errornotification.TestRequest;
import com.syncari.core.model.errornotification.WebhookRequestBody;
import com.syncari.core.service.ErrorNotificationService;
import com.syncari.core.utils.ValidationUtils;
import com.syncari.restutils.transformers.ErrorNotificationTransformer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/errorNotifications")
public class ErrorNotificationController {
	@Autowired
	ErrorNotificationService errorNotificationService;
	@Autowired
	ErrorNotificationTransformer transformer;

	@Secured({READ_ERROR_NOTIFICATION_EMAIL, READ_ERROR_NOTIFICATION_WEBHOOK})
	@GetMapping(value = "/types")
    public List<ErrorCatalogDTO> getTypes() {
		return transformer.toCatalogDTO(errorNotificationService.getErrorCatalogs());
    }
	
	@Secured({READ_ERROR_NOTIFICATION_EMAIL, READ_ERROR_NOTIFICATION_WEBHOOK})
	@GetMapping(value = "/cadences")
    public List<Map<String, String>> getCadences() {
		return Arrays.stream(ErrorNotificationFrequency.values())
				.map(v -> Map.of("frequency", v.name(), "label", v.getLabel())).collect(Collectors.toList());
    }
	
	@Secured({READ_ERROR_NOTIFICATION_EMAIL, READ_ERROR_NOTIFICATION_WEBHOOK})
	@GetMapping(value = "/configurations")
    public List<ErrorNotificationConfigDTO> getAllConfigurations() {
        return transformer.toConfigDTO(errorNotificationService.getErrorNotificationConfig());
    }
	
	@Secured({READ_ERROR_NOTIFICATION_EMAIL, READ_ERROR_NOTIFICATION_WEBHOOK})
	@GetMapping(value = "/configurations/{id}")
    public ErrorNotificationConfigDTO getConfiguration(@PathVariable String id) {
        return transformer.toConfigDTO(errorNotificationService.getErrorNotificationConfig(id));
    }
	
	@Secured({WRITE_ERROR_NOTIFICATION_EMAIL, WRITE_ERROR_NOTIFICATION_WEBHOOK})
	@PostMapping(value = "/configurations")
    public ErrorNotificationConfigDTO createConfiguration(@RequestBody ErrorNotificationConfigDTO request) {
		var res = errorNotificationService.saveErrorNotificationConfig(transformer.toConfig(request));
        return transformer.toConfigDTO(res);
    }
	
	@Secured({WRITE_ERROR_NOTIFICATION_EMAIL, WRITE_ERROR_NOTIFICATION_WEBHOOK})
	@PutMapping(value = "/configurations/{id}")
    public ErrorNotificationConfigDTO editConfiguration(@PathVariable String id, @RequestBody ErrorNotificationConfigDTO request) {
		ValidationUtils.validateCondition(StringUtils.isEmpty(id), "Id cannot be empty");
		request.setId(id);
		var res = errorNotificationService.saveErrorNotificationConfig(transformer.toConfig(request));
        return transformer.toConfigDTO(res);
    }
	
	@Secured({WRITE_ERROR_NOTIFICATION_EMAIL, WRITE_ERROR_NOTIFICATION_WEBHOOK})
	@DeleteMapping(value = "/configurations/{id}")
    public Object deleteConfiguration(@PathVariable String id) {
		errorNotificationService.deleteErrorNotificationConfig(id);
        return Map.of("success", "true");
    }
	
	@Secured({READ_ERROR_NOTIFICATION_EMAIL, READ_ERROR_NOTIFICATION_WEBHOOK})
	@PostMapping(value = "/configurations/test")
    public Map<String, Object> testConfiguration(@RequestBody TestRequest request) {
		var res = errorNotificationService.test(request);
		return Map.of("request", res.getX(), "response", res.getY());
    }
	
	@Secured(READ_ERROR_NOTIFICATION_WEBHOOK)
	@GetMapping(value = "/configurations/webhook/body")
    public WebhookRequestBody bodyWebhookBody() {
		return WebhookRequestBody.buildSample();
    }
	
	@RequestMapping(method = RequestMethod.GET, value = "/invitation/{encInstanceId}/{invitationId}/{status}")
	public Map<String, String> invitation(@PathVariable String encInstanceId, @PathVariable String invitationId,
			@PathVariable EmailConfigStatus status) {
		errorNotificationService.processInvitationAcceptance(encInstanceId, invitationId, status);
		return Map.of("success", "true");
	}
	
	@Secured({WRITE_ERROR_NOTIFICATION_EMAIL, WRITE_ERROR_NOTIFICATION_WEBHOOK})
	@PostMapping(value = "/configurations/{id}/{email}/resendOptIn")
    public ErrorNotificationConfigDTO resendOptIn(@PathVariable String id, @PathVariable String email) {
		ValidationUtils.validateCondition(StringUtils.isEmpty(id), "Id cannot be empty");
		ValidationUtils.validateCondition(StringUtils.isEmpty(email), "Id cannot be empty");
		var res = errorNotificationService.sendOptin(id, email);
        return transformer.toConfigDTO(res);
    }
}
