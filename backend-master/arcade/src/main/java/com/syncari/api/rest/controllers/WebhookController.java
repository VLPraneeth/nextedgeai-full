package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.connector.Constants;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.data.WebhookRequest;
import com.syncari.connector.service.def.WebhookService;
import com.syncari.core.DataTransformer;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Publisher;
import com.syncari.core.model.Event;
import com.syncari.core.model.GlobalConfiguration;
import com.syncari.core.repositories.syncari.GlobalConfigurationRepo;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.service.EventDataService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.webhook.receiver.WebhookReceiverService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/webhooks")
public class WebhookController {
	@Autowired
	DataServiceFactory factory;
	@Autowired
	EventDataService service;
	@Autowired
	ObjectTransformer transformer;
	@Autowired
	ConnectorService connectorService;
	@Autowired
	ConnectorMetadataService metaService;
	@Autowired
	DataTransformer dataTransformer;
	@Autowired
	SyncariContextHandler contextHandler;
	@Autowired
	GlobalConfigurationRepo globalRepo;
    @Autowired
    Publisher publisher;
	@Autowired
	AppConfig appConfig;
	@Autowired
	WebhookReceiverService webReceiverService;
	@Autowired
	TokenHelper tokenHelper;

	@RequestMapping(method = RequestMethod.POST, value = "/{synapse}")
	public ResponseEntity process(@PathVariable(value = "synapse") String synapse, @RequestBody String body, HttpServletRequest request)
			throws IOException {
		String md5 = body != null ? DigestUtils.md5Hex(body) : null;
		log.info("Received request for synapse {} with body digest {}", synapse, md5);
		Map<String, Object> params = getQueryParameters(request);
		if(synapse.contains("stripe_")) {
			String[] arr = synapse.split(("_"));
			if(arr.length == 2) params.put("instanceId", arr[1]);
			synapse = "stripe";
		}
		Event event = new Event().setDetails(Map.of("synapse", synapse, "body", body, "headers", getHeaders(request), "params", params))
				.setType(EventTypes.ACCEPT_WEBHOOK);
		log.debug("Event Details: {}", event.getDetails());
		publisher.publishToWebhookQueue(event, false);
		log.debug("Successfully logged event for synapse {}", synapse);
		return ResponseEntity.ok().build();
	}

	@RequestMapping(method = RequestMethod.POST, value = "/slack")
	public ResponseEntity process(@RequestBody String body,
								  @RequestHeader("X-Slack-Request-Timestamp") long timestamp,
								  @RequestHeader("X-Slack-Signature") String signature, HttpServletRequest request) {
		log.debug("Received request for synapse slack");
		ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		try {
			Map<String, String> responseValues = mapper.readValue(body, Map.class);
			if (responseValues != null && responseValues.containsKey("challenge")) {
				return ResponseEntity.ok(responseValues.get("challenge"));
			}
			Event event = new Event().setDetails(Map.of("synapse", "slacksynapse", "body", body, "headers", getHeaders(request), "params", getQueryParameters(request)))
					.setType(EventTypes.ACCEPT_WEBHOOK);
			publisher.publishToWebhookQueue(event, false);
			log.debug("Successfully logged event for synapse {}", "slack");
			return ResponseEntity.ok().build();
		} catch (JsonProcessingException e) {
			throw new RuntimeException(String.format("Error parsing response json. - %s", e.getMessage()));
		}
	}

	private void verifyRequest(String body, long timestamp, String signature) {
		try {
			String basestring = "v0:" + timestamp + ":" + body;
			Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
			sha256_HMAC.init(new SecretKeySpec(appConfig.slackSynapseSigningSecret.getBytes(), "HmacSHA256"));
			byte[] result = sha256_HMAC.doFinal(basestring.getBytes());
			String calculatedSignature = "v0=" + bytesToHex(result);
			log.debug(String.format("Slack synapse - received signature %s, calculated signature %s", signature, calculatedSignature));
			if (!calculatedSignature.equals(signature)) {
				throw new RuntimeException("Invalid request. The signatures do not match.");
			}
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(String.format("Error when calculating signature. - %s", e.getMessage()));
		}
	}

	@RequestMapping(method = RequestMethod.POST, value = "/slackinteractivity")
	public ResponseEntity process(@RequestParam("payload") String payload, @RequestBody String requestBody,
								  @RequestHeader("X-Slack-Request-Timestamp") long timestamp,
								  @RequestHeader("X-Slack-Signature") String signature, HttpServletRequest request) {
		log.debug("Received request for synapse slack");
		verifyRequest(requestBody, timestamp, signature);
		Event event = new Event().setDetails(Map.of("synapse", "slacksynapse", "body", payload, "headers", getHeaders(request),"params", getQueryParameters(request)))
				.setType(EventTypes.ACCEPT_WEBHOOK);
		publisher.publishToWebhookQueue(event, false);
		log.debug("Successfully logged event for synapse {}", "slack");
		return ResponseEntity.ok().build();
	}

	private static String bytesToHex(byte[] hash) {
		StringBuilder hexString = new StringBuilder(2 * hash.length);
		for (int i = 0; i < hash.length; i++) {
			String hex = Integer.toHexString(0xff & hash[i]);
			if(hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}

	private Map<String, Object> getHeaders(HttpServletRequest request) {
		Map<String, Object> headers = new HashMap<>();
		Enumeration<String> headerNames = request.getHeaderNames();
		if (headerNames != null) {
			while (headerNames.hasMoreElements()) {
				String header = headerNames.nextElement();
				headers.put(header, request.getHeader(header));
			}
		}
		return headers;
	}

	private Map<String, Object> getQueryParameters(HttpServletRequest request) {
		Map<String, Object> params = new HashMap<>();

		String fullUri = request.getRequestURI();
		String queryString = request.getQueryString();
		if (!fullUri.contains("?") && queryString != null) {
			fullUri = fullUri + "?" + queryString;
		}

		MultiValueMap<String, String> queryParams = fromUriString(fullUri).build().getQueryParams();
		queryParams.forEach((key, value) -> {
			params.put(key, value);
		});
		return params;
	}

	@RequestMapping(value = "/whs/{uniqueId}")
	public ResponseEntity acceptWebhookRequest(@PathVariable(value = "uniqueId") String uniqueId, @RequestBody(required = false) String body, HttpServletRequest request)
            throws IOException {
        log.debug("Received request with uniqueid {}", uniqueId);
        String key = String.format("%s_%s_%s", WebhookService.PREFIX, Constants.WEBHOOK_RECEIVER,
            uniqueId);
        Optional<GlobalConfiguration> gc = globalRepo.findByKey(key);
        if(gc.isPresent()) {
          List<String> valueList = (List<String>) gc.get().getValue();
          if(valueList.size() == 1) {
            String parts[] = valueList.get(0).split("_");
            String syncariId = Arrays.stream(parts).limit(parts.length - 1).collect(Collectors.joining("_"));
            String connectorId = parts[parts.length - 1];
            contextHandler.setContext(syncariId, false);
            var connector = connectorService.find(connectorId);
            if(connector.isPresent()) {
              String synapse = connector.get().getMetadata().getName();
              Map<String, Object> params = getQueryParameters(request);
              params.put("uniqueId", uniqueId);
              Map<String, Object> headers = getHeaders(request);
              
              WebhookRequest whReq = new WebhookRequest().setBody(body).setHeaders(params).setHeaders(headers).setConfig(dataTransformer.toConnectorInfo(connector.get()));
              boolean authenticated = webReceiverService.isValidRequest(whReq);
              boolean verified = authenticated && whReq.getConfig().getAuthType() == AuthType.Hmac;
              int responseCode = Optional.ofNullable(connector.get().getMetadata().getResponseCode()).orElse(200);
              String response = Optional
                  .ofNullable(connector.get().getMetadata().getResponseTemplate()).map(template -> {
                    return tokenHelper.resolveTokens(
                        Map.of("requestPayload", Optional.ofNullable(body).map(b -> b).orElse(""),
                            "requestHeaders", headers),
                        template).x;
                  }).orElse("");
              
              Event event = new Event()
                  .setDetails(Map.of("synapse", synapse, "body", Optional.ofNullable(body).map(b -> b).orElse(""), "headers", headers, "params",
                      params, "authenticated", authenticated, "connectorId", connectorId, "verified", verified, "syncariId", syncariId))
                  .setType(EventTypes.ACCEPT_WEBHOOK);
              log.debug("Event Details: {}", event.getDetails());
              publisher.publishToWebhookQueue(event, false);
              log.debug("Successfully logged event with uniqueid {}", uniqueId);
              if(authenticated) {
                return ResponseEntity.status(responseCode).body(response);
              } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
              }
            } else {
              log.error("Connector with id {} not found", uniqueId, valueList);
            }
          } else {
            log.error("Invalid configuration for {} - {}", uniqueId, valueList);
          }
        } else {
          log.error("Global configuration for {} not found", uniqueId);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
