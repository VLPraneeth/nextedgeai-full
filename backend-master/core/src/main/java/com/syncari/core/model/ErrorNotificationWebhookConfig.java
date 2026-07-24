package com.syncari.core.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import com.google.common.net.InetAddresses;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;

import com.syncari.core.model.misc.ErrorNotificationChannelType;
import com.syncari.core.utils.ValidationUtils;
import com.syncari.utils.I18n;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.web.util.UriComponentsBuilder;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Data
@Accessors(chain = true)
public class ErrorNotificationWebhookConfig extends ErrorNotificationConfig {
	private String httpMethod;
	private String url;
	private Map<String, String> headers = Map.of();
	private Map<String, Object> lastResponse;

	private static List<String> PROTOCOL_WHITELIST = List.of("http", "https");

	@Override
	public ErrorNotificationChannelType getType() {
		return ErrorNotificationChannelType.webhook;
	}

	@Override
	public void loadConfig(Map<String, Object> config) {
		ValidationUtils.validateCondition(config == null, "Webhook config is not present");
		httpMethod = (String) config.get("httpMethod");
		url = (String) config.get("url");
		if (config.get("headers") != null) {
			headers = (Map<String, String>) config.get("headers");
		}
		ValidationUtils.validateCondition(httpMethod == null, "Webhook http method is not present");
		ValidationUtils.validateCondition(url == null, "Webhook url is not present");
	}

	private String extractDomainName(String endPoint) {
		try{
			var uri = UriComponentsBuilder.fromUriString(endPoint).build();
			return uri.getHost();
		}catch (Exception e){
			log.error(String.format("Error occured while extracting Domain Name %s",endPoint), e);
		}
		return endPoint;
	}

	private String extractScheme(String endPoint) {
		try{
			URI uri = new URI(endPoint);
			return StringUtils.isEmpty(uri.getScheme()) ? endPoint : uri.getScheme();
		}catch (URISyntaxException e){
			log.error(String.format("Error occured while extracting Scheme %s",endPoint), e);
		}
		return endPoint;
	}

	@Override
	public Map<String, Object> getConfig() {
		if(headers == null) {
			headers = Map.of();
		}
		return Map.of("httpMethod", httpMethod, "url", url, "headers", headers);
	}

	@Override
	public void validate() {
		validateCondition(!UrlValidator.getInstance().isValid(url), i18n("error_notification_invalid_url", url));
		validateCondition(httpMethod == null, "Webhook http method is not present");
		validateCondition(url == null, "Webhook url is not present");

		String domainName = extractDomainName(url).toLowerCase();

		//disallow endPoint having IPAddress
		validateCondition((InetAddresses.isUriInetAddress(domainName)),
				String.format(i18n("error_notification_invalid_url"), "Endpoint"));

		//disallow endPoint having certain domain names
		validateCondition((domainName.contains("syncari.net") || domainName.contains("metadata") || domainName.endsWith(".internal")),
				String.format(i18n("error_notification_invalid_url"), "Endpoint"));

		//allow only certain protocols
		String scheme = extractScheme(url).toLowerCase();
		validateCondition(!PROTOCOL_WHITELIST.contains(scheme),
				String.format(i18n("error_notification_invalid_url"), "Endpoint"));
	}
}
