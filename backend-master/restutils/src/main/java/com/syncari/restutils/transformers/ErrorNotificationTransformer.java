package com.syncari.restutils.transformers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.rest.controllers.data.notification.ErrorCatalogDTO;
import com.syncari.api.rest.controllers.data.notification.ErrorNotificationConfigDTO;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.ErrorCatalog;
import com.syncari.core.model.ErrorNotificationConfig;
import com.syncari.core.model.ErrorNotificationEmailConfig;
import com.syncari.core.model.ErrorNotificationWebhookConfig;
import com.syncari.core.model.errornotification.WebhookRequestBody;
import com.syncari.core.model.misc.ErrorNotificationChannelType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ErrorNotificationTransformer {
	@Autowired
	ObjectMapper mapper;
	
	public ErrorCatalogDTO toCatalogDTO(ErrorCatalog catalog) {
		return ErrorCatalogDTO.builder()
		.id(catalog.getId())
		.category(catalog.getCategory().name())
		.title(catalog.getTitle())
		.helpText(catalog.getHelpText())
		.build();
	}
	
	public List<ErrorCatalogDTO> toCatalogDTO(List<ErrorCatalog> catalog) {
		return catalog.stream().map(c -> toCatalogDTO(c)).collect(Collectors.toList());
	}
	
	public List<ErrorNotificationConfigDTO> toConfigDTO(List<ErrorNotificationConfig> config) {
		return config.stream().map(c -> toConfigDTO(c)).collect(Collectors.toList());
	}
	
	public ErrorNotificationConfigDTO toConfigDTO(ErrorNotificationConfig config) {
		var dto =  ErrorNotificationConfigDTO.builder()
				.id(config.getId())
				.type(config.getType())
				.name(config.getName())
				.description(config.getDescription())
				.status(config.getStatus())
				.statusMessage(config.getLastError())
				.retries(config.getRetries())
				.firstErrorOccured(config.getFirstErrorTimestamp())
				.lastErrorOccured(config.getLastErrorTimestamp())
				.notificationTypes(config.getNotificationTypes())
				.cadence(config.getCadence())
				.configuration(config.getConfig())
				.build();
		if(config instanceof ErrorNotificationWebhookConfig) {
			try {
				Map<String, Object> whConfig = new HashMap<>();
				whConfig.putAll(dto.getConfiguration());
				whConfig.put("body", mapper.writeValueAsString(WebhookRequestBody.buildSample()));
				dto.setConfiguration(whConfig);
			} catch (JsonProcessingException e) {
				log.error("Error occured while creating webhook sample request ",e);
				throw new RuntimeException(e);
			}
		}
		return dto;
	}
	
	public ErrorNotificationConfig toConfig(ErrorNotificationConfigDTO dto) {
		ErrorNotificationConfig config = null;
		if(dto.getType() == ErrorNotificationChannelType.webhook) {
			config = new ErrorNotificationWebhookConfig();
		} else if(dto.getType() == ErrorNotificationChannelType.email) {
			config = new ErrorNotificationEmailConfig();
		} else {
			throw new SyncariValidationException("Unknow notification type");
		}
		config.setId(dto.getId());
		config.setCadence(dto.getCadence());
		config.setDescription(dto.getDescription());
		config.setName(dto.getName());
		config.setNotificationTypes(dto.getNotificationTypes());
		config.setStatus(dto.getStatus());
		config.loadConfig(dto.getConfiguration());
		return config;
	}

}
