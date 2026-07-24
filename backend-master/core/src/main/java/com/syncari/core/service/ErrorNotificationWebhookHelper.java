package com.syncari.core.service;

import static com.syncari.utils.I18n.i18n;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.ErrorNotificationWebhookConfig;
import com.syncari.core.model.errornotification.WebhookRequest;
import com.syncari.core.model.errornotification.WebhookRequestBody;
import com.syncari.core.model.errornotification.WebhookRequestBodyNotification;
import com.syncari.core.model.misc.ErrorNotificationConfigStatus;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.repositories.customer.ErrorNotificationConfigRepo;
import com.syncari.utils.I18n;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ErrorNotificationWebhookHelper {
	@Autowired
    ObjectMapper objectMapper;
	@Autowired
	ErrorNotificationConfigRepo configRepo;
	@Autowired
    private AppConfig appConfig;
	@Autowired
	private NotificationService notificationService;
	
	@Retryable(
		include = {RuntimeException.class},
		maxAttempts = 3,
		backoff = @Backoff(random = true, delay = 1000, multiplier = 2, maxDelay = 10000)
	)
	public void execute(ErrorNotificationWebhookConfig whConfig, List<WebhookRequestBodyNotification> notification, int count, Date calledTime) {
		log.info("ErrorNotificationWebhookHelper webhook request for {}",  whConfig.getName());
		WebhookRequestBody body = WebhookRequestBody.builder()
				.instanceId(SyncariContext.getSyncariId())
				.instanceName(SyncariContext.getInstance().getName())
				.lastNotificationTimestamp(whConfig.getLastNotificationTimestamp())
				.timestamp(new Date())
				.notificationCount(count)
				.notifications(notification)
				.build();
		try {
			var out = execute(WebhookRequest.builder()
					.method(HttpMethod.valueOf(whConfig.getHttpMethod()))
					.headers(whConfig.getHeaders())
					.url(whConfig.getUrl())
					.body(objectMapper.writeValueAsString(body))
					.build());
			whConfig.setLastNotificationTimestamp(new Date());
			whConfig.setLastResponse(objectMapper.convertValue(out.getY(), Map.class));
			whConfig.setLastError(null);
			whConfig.setLastErrorTimestamp(null);
			whConfig.setProcessing(false);
			configRepo.save(whConfig);
			log.info("ErrorNotificationWebhookHelper webhook request for {} completed",  whConfig.getName());
		}catch (JsonProcessingException e) {
			log.error("Json processing failed ", e);
			throw new RuntimeException(e);
		}
	}
	
	@Recover
	public void executeFallback(RuntimeException e, ErrorNotificationWebhookConfig whConfig, List<WebhookRequestBodyNotification> notification, int count, Date calledTime) {
		log.error("ErrorNotificationWebhookHelper webhook fallback request for {} due to {}",  whConfig.getName(), e);
		String message = null;
		if(e instanceof HttpClientErrorException) {
			message = translateStatusCode(((HttpClientErrorException) e).getStatusCode());
		} else {
			message = e.getMessage();
		}
		whConfig.setLastError(message);
		whConfig.setRetries(3);
		whConfig.setFirstErrorTimestamp(calledTime);
		whConfig.setLastErrorTimestamp(new Date());
		whConfig.setLastResponse(Map.of());
		whConfig.setStatus(ErrorNotificationConfigStatus.Disabled);
		whConfig.setProcessing(false);
		sendDisabledNotification(whConfig, message, 3);
		configRepo.save(whConfig);
		log.info("ErrorNotificationWebhookHelper webhook fallback request for {} completed",  whConfig.getName());
	}

	public Pair<RequestEntity<String>, ResponseEntity<String>> execute(WebhookRequest req) {
		RestTemplate restTemplate = getTemplate();
		var uri = UriComponentsBuilder.fromUriString(req.getUrl());
		var endpoint = java.net.URLDecoder.decode(uri.toUriString(), StandardCharsets.UTF_8);
		HttpHeaders headers = new HttpHeaders();
		if (MapUtils.isNotEmpty(req.getHeaders())) {
			req.getHeaders().entrySet().stream().forEach(header -> {
				headers.set(header.getKey(), header.getValue());
			});
		}
		RequestEntity<String> requestEntity = new RequestEntity<>(req.getBody(), headers, req.getMethod(),
				uri.build().toUri());
		switch (req.getMethod()) {
		case POST:
		case PUT:
		case PATCH:
			var responseEntity = restTemplate.exchange(endpoint, req.getMethod(),
					new HttpEntity<String>(req.getBody(), headers), String.class);
			return Pair.of(requestEntity, responseEntity);
		default:
			throw new RuntimeException("Invalid Method " + req.getMethod());
		}
	}

	private String translateStatusCode(HttpStatus statusCode) {
		if(statusCode == HttpStatus.PROXY_AUTHENTICATION_REQUIRED) {
			return I18n.i18n("forbidden_http_request");
		} else if(statusCode.is4xxClientError()) {
			return I18n.i18n("error_notification_webhook_4xx");
		} else if(statusCode.is3xxRedirection()) {
			return I18n.i18n("error_notification_webhook_3xx");
		} else if(statusCode.is5xxServerError()) {
			return I18n.i18n("error_notification_webhook_5xx");
		} else {
			return I18n.i18n("error_notification_webhook_generic");
		}
	}

	public RestTemplate getTemplate() {
		int timeout = 30000;
		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create().disableRedirectHandling();
		if(appConfig.isProxyEnabled() && StringUtils.isNotEmpty(appConfig.getProxyHost())) {
			HttpHost httpProxy = new HttpHost(appConfig.getProxyHost(), appConfig.getProxyPort());
			httpClientBuilder.setProxy(httpProxy);
			log.debug("Setting proxy with {} {}", appConfig.getProxyHost(), appConfig.getProxyPort());
		}
		CloseableHttpClient client = httpClientBuilder.build();
		HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(
				client);
		clientHttpRequestFactory.setConnectTimeout(timeout);
		clientHttpRequestFactory.setReadTimeout(timeout);
		RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory);
		restTemplate.getMessageConverters().forEach(a -> {
			if (a instanceof StringHttpMessageConverter) {
				((StringHttpMessageConverter) a).setWriteAcceptCharset(false);
			}
		});
		return restTemplate;
	}

	private void sendDisabledNotification(ErrorNotificationWebhookConfig whConfig, String errorMessage, int retries) {
		try {
			String subject = I18n.i18n("error_notification_webhook_disabled_subject", whConfig.getName());
			String body = I18n.i18n("error_notification_webhook_disabled_body", whConfig.getName(), retries, errorMessage);

			notificationService.broadcast(subject, body, NotificationType.ERROR);

			log.info("Sent in-app notification for disabled webhook config: {}", whConfig.getName());
		} catch (Exception ex) {
			log.error("Failed to send in-app notification for disabled webhook config: {}", whConfig.getName(), ex);
		}
	}
}
