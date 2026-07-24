package com.syncari.api.rest.controllers;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.CustomActionTransformer;
import com.syncari.api.rest.controllers.data.CustomActionDTO;
import com.syncari.api.rest.controllers.data.HttpActionDTO;
import com.syncari.api.rest.controllers.data.HttpActionResultDTO;
import com.syncari.core.actions.CustomActionDefinition;
import com.syncari.core.actions.http.HTTPActionTestResult;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.schema.PipelineStatus;
import com.syncari.core.service.ActionService;
import com.syncari.core.service.ComponentDependencyService;
import static com.syncari.core.utils.ValidationUtils.*;
import com.syncari.utils.KeyValue;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import com.syncari.api.rest.controllers.data.NodeDef;
import org.springframework.web.bind.annotation.*;

import com.syncari.api.core.util.ObjectTransformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.utils.I18n.i18n;


@Slf4j
@RestController
@RequestMapping(value = "/api/v1/actions")
public class ActionController {

	@Autowired
	ObjectTransformer transformer;

	@Autowired
	ActionService actionService;

	@Autowired
	ObjectMapper mapper;

	@Autowired
	CustomActionTransformer customActionTransformer;

	@Autowired
	ComponentDependencyService dependencyService;

	@Secured(READ_STUDIO)
	@RequestMapping(method = RequestMethod.GET, value = "/entity/{syncariEntityId}")
	public List<NodeDef> getActionsWithEntity() {
	    return transformer.toActionDef(actionService.getEntityActions());
	}
	
	@Secured(READ_STUDIO)
	@RequestMapping(method = RequestMethod.GET, value = "/field/{syncariAttributeId}")
	public List<NodeDef> getActionsWithField() {
	    return transformer.toActionDef(actionService.getAttributeActions());
	}

	@Secured(ACTION_WRITE)
	@RequestMapping(method = RequestMethod.POST, value = "/http")
	public CustomActionDTO save(@RequestParam(value = "id", required = false) String id,
								@RequestParam(value = "apiName") String apiName, @RequestParam("displayName") String displayName,
								@RequestParam(value = "description", required = false) String description, @RequestParam(value = "tags", required = false) String tags,
								@RequestParam(value = "helpLink", required = false) String helpLink, @RequestParam(value = "basicHelpText") String helpText,
								@RequestParam(name = "icon", required = false) MultipartFile file,
								@RequestParam(value = "endpoint", required = false) String endpoint, @RequestParam(value = "method", required = false) String method,
								@RequestParam(value = "credentialId", required = false) String credentialId,
								@RequestParam(value = "metadataId", required = false) String metadataId,
								@RequestParam(value = "headers", required = false) String headers, @RequestParam(value ="body", required = false) String body,
								@RequestParam(value = "variables", required = false) String variables,
								@RequestParam(value = "isBatch", required = false, defaultValue = "false") boolean isBatch,
								@RequestParam(value = "batchSize", required = false, defaultValue = "10") String batchSize,
								@RequestParam(value = "scope", required = false) String scope // Entity/Field if not passed both
					  ) throws IOException  {

		validateCondition(StringUtils.isBlank(apiName),String.format(i18n("action_required_field"), "API Name"));
		validateCondition(StringUtils.isBlank(displayName),String.format(i18n("action_required_field"), "Display Name"));

		var customActionDTO =  new HttpActionDTO().setBatchSize(Integer.valueOf(batchSize)).setBatch(isBatch).setEndpoint(endpoint).setMethod(method)
				.setCredentialId(credentialId)
				.setMetadataId(metadataId)
				.setHeaders(headers != null ? mapper.readValue(headers, new TypeReference<Map<String, String>>() {}) : Map.of())
				.setBody(body).setVariables(variables != null ? mapper.readValue(variables, new TypeReference<List<KeyValue>>() {}) : List.of()).setScope(scope)
				.setId(id).setApiName(apiName).setDisplayName(displayName).setDescription(description).setHelpLink(helpLink).setBasicHelpText(helpText)
				.setTags(tags != null ? Arrays.asList(mapper.readValue(tags, String[].class)) : List.of());

		if(file != null) {
			customActionDTO.setIconPath(actionService.saveIcon(file.getName(), file));
		}
		return customActionTransformer.toDTO(actionService.save(customActionTransformer.toActionDefinition(customActionDTO)));
	}

	@Secured(ACTION_WRITE)
	@RequestMapping(method = RequestMethod.POST, value = "/{id}/publish")
	public CustomActionDTO publish(@PathVariable String id) throws IOException  {
		var actionDefinition = actionService.findById(id).orElseThrow(() -> new SyncariValidationException("Invalid Action ID: " + id));
		actionService.validate(actionDefinition);
		actionDefinition = actionService.approveDraft(id);
		actionService.deployAction(actionDefinition);
		return customActionTransformer.toDTO(actionDefinition);
	}

	@Secured(ACTION_READ)
	@RequestMapping(method = RequestMethod.GET, value = "/{id}/draft")
	public CustomActionDTO getDraft(@PathVariable String id) throws IOException  {
		return customActionTransformer.toDTO(actionService.findDraft(id).get());
	}

	@Secured(ACTION_WRITE)
	@RequestMapping(method = RequestMethod.POST, value = "/{id}/createDraft")
	public CustomActionDTO createDraft(@PathVariable String id) throws IOException  {
		return customActionTransformer.toDTO(actionService.createDraftFor(id));
	}

	@Secured(ACTION_WRITE)
	@RequestMapping(method = RequestMethod.POST, value = "/{id}/discardDraft")
	public void discardDraft(@PathVariable String id) throws IOException  {
		actionService.discardDraft(id);
	}

	@Secured(ACTION_READ)
	@RequestMapping(method = RequestMethod.GET, value = "/author/list")
	public List<CustomActionDTO> list() throws IOException  {
		return actionService.getEditableActions().stream().map(action -> {
				if (action.isApproved()) {
					return actionService.findDraft(action).map(draft -> customActionTransformer.toDTO(draft, PipelineStatus.PUBLISHED_WITH_DRAFT))
							.orElse(customActionTransformer.toDTO(action));
				} else if (action.isDraft() && action.getParentId() == null) {
					return customActionTransformer.toDTO(action);
				}
				return null;
		}).filter(Objects::nonNull).collect(Collectors.toList());
	}

	@Secured(ACTION_SHARE)
	@RequestMapping(method = RequestMethod.POST, value = "/share/{id}")
	public CustomActionDTO share(@PathVariable String id, @RequestParam(value = "shareWithOrg", required = false, defaultValue = "false") boolean shareWithOrg,
								 @RequestParam(value = "shareGlobally", required = false, defaultValue = "false") boolean shareGlobally) {
		// share with org, if approved action
		// check if this id is approved

		return actionService.findById(id).map(action -> {
			if (action.isCustom() && ((CustomActionDefinition)action).isSharedWithMe()) {
				throw new SyncariValidationException(String.format("Action shared with this instance cannot be shared"));
			}

			if (action.isApproved()) {
				actionService.shareAction((CustomActionDefinition) action, shareWithOrg, shareGlobally);
				return customActionTransformer.toDTO(action);
			} else if (action.getParentId() != null){
				var actionMaybe = actionService.findById(action.getParentId());
				actionMaybe.filter(a -> a.isApproved()).ifPresent(a -> actionService.shareAction((CustomActionDefinition) action, shareWithOrg, shareGlobally));
				return customActionTransformer.toDTO(action);
			} else {
				throw new SyncariValidationException(String.format("Action with Id %s not approved", id));
			}
		}).orElseThrow(() -> new SyncariValidationException(String.format("Action with Id %s not found", id)));
	}

	@Secured(ACTION_WRITE)
	@RequestMapping(method = RequestMethod.DELETE, value = "/{id}")
	public void delete(@PathVariable String id) throws IOException  {
		actionService.delete(id);
	}

	@Secured(ACTION_READ)
	@RequestMapping(method = RequestMethod.POST, value = "/http/validate")
	public CustomActionDTO validate(@RequestParam(value = "id", required = false) String id,
						 @RequestParam(value = "apiName") String apiName, @RequestParam("displayName") String displayName,
						 @RequestParam(value = "description", required = false) String description, @RequestParam(value = "tags", required = false) String tags,
						 @RequestParam(name = "icon", required = false) MultipartFile file,
						 @RequestParam(value = "endpoint", required = false) String endpoint, @RequestParam(value = "method", required = false) String method,
						 @RequestParam(value = "credentialId", required = false) String credentialId,
						 @RequestParam(value = "headers", required = false) String headers, @RequestParam(value ="body", required = false) String body,
						 @RequestParam(value = "variables", required = false) String variables,
						 @RequestParam(value = "scope", required = false) String scope) throws IOException  {

		var customActionDTO = new HttpActionDTO().setEndpoint(endpoint).setMethod(method)
				.setCredentialId(credentialId)
				.setHeaders(headers != null ? mapper.readValue(headers, new TypeReference<Map<String, String>>() {}) : Map.of())
				.setBody(body).setVariables(variables != null ? mapper.readValue(variables, new TypeReference<List<KeyValue>>() {}) : List.of()).setScope(scope)
				.setId(id).setApiName(apiName).setDisplayName(displayName).setDescription(description)
				.setTags(tags != null ? Arrays.asList(mapper.readValue(tags, String[].class)) : List.of());

		var actionDefinition = customActionTransformer.toActionDefinition(customActionDTO);
		actionService.validate(actionDefinition);
		return customActionTransformer.toDTO(actionDefinition);
	}

	@Secured(ACTION_READ)
	@RequestMapping(method = RequestMethod.POST, value = "/http/testing")
	public ResponseEntity<Object> test(@RequestParam(value = "id", required = false) String id,
									@RequestParam(value = "apiName", required = false) String apiName, @RequestParam(value = "displayName", required = false) String displayName,
									@RequestParam(value = "description", required = false) String description, @RequestParam(value = "tags", required = false) String tags,
									@RequestParam(name = "icon", required = false) MultipartFile file,
									@RequestParam(value = "endpoint", required = false) String endpoint, @RequestParam(value = "method", required = false) String method,
									@RequestParam(value = "credentialId", required = false) String credentialId,
									@RequestParam(value = "headers", required = false) String headers, @RequestParam(value ="body", required = false) String body,
									@RequestParam(value = "variables", required = false) String variables,
									@RequestParam(value = "isBatch", required = false, defaultValue = "false") Boolean isBatch,
									@RequestParam(value = "batchSize", required = false, defaultValue = "10") String batchSize,
									@RequestParam(value = "scope", required = false) String scope, @RequestParam(value = "variableValues") String variablesValues) throws IOException  {

		var customActionDTO =  new HttpActionDTO().setBatchSize(Integer.valueOf(batchSize)).setBatch(isBatch).setEndpoint(endpoint).setMethod(method)
				.setCredentialId(credentialId)
				.setHeaders(headers != null ? mapper.readValue(headers, new TypeReference<Map<String, String>>() {}) : Map.of())
				.setBody(body).setVariables(variables != null ? mapper.readValue(variables, new TypeReference<List<KeyValue>>() {}) : List.of()).setScope(scope)
				.setId(id).setApiName(apiName).setDisplayName(displayName).setDescription(description).setIconPath("")
				.setTags(tags != null ? Arrays.asList(mapper.readValue(tags, String[].class)) : List.of());


		List<KeyValue> variablesKeyValue = variablesValues != null ? mapper.readValue(variablesValues, new TypeReference<List<KeyValue>>() {}) : List.of();

		var actionDefinition = customActionTransformer.toActionDefinition(customActionDTO);

		var variablesMap = variablesKeyValue.stream().filter(kv -> kv.containsKey("name") && kv.containsKey("value"))
				.collect(Collectors.toMap(kv -> kv.get("name").toString(), kv -> kv.get("value")));
		var resAction = (HTTPActionTestResult) actionService.test(actionDefinition, variablesMap);
		if(resAction.getResponse() != null && resAction.getResponse().getHttpStatus() != null && resAction.getResponse().getHttpStatus().isError()) {
			var resDto = customActionTransformer.toHttpActionTestResultDTO(resAction);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
					Map.of(
							"error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
							"message", resAction.getResponse().getHttpStatus().toString(),
							"timestamp", Instant.now(),
							"status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
							"request", resDto.getRequest(),
							"response", resDto.getResponse()
							)
					);
		}
		return ResponseEntity.ok (customActionTransformer.toHttpActionTestResultDTO(resAction));
	}
}
