package com.syncari.api.core.util;

import com.syncari.api.rest.controllers.data.CustomActionDTO;
import com.syncari.api.rest.controllers.data.HttpActionDTO;
import com.syncari.api.rest.controllers.data.HttpActionResultDTO;
import com.syncari.core.SyncariContext;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.actions.ActionTestResult;
import com.syncari.core.actions.CustomActionDefinition;
import com.syncari.core.actions.http.AuthenticationInfo;
import com.syncari.core.actions.http.HTTPActionTestResult;
import com.syncari.core.actions.http.HttpActionProperties;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.Sharable;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.model.util.Type;
import com.syncari.core.repositories.syncari.SharedItemRepo;
import com.syncari.core.schema.PipelineStatus;
import com.syncari.core.service.ConnectorService;
import com.syncari.utils.KeyValue;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CustomActionTransformer {

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private SharedItemRepo sharedItemRepo;

    public ActionDefinition toActionDefinition(CustomActionDTO customActionDTO) {
        if (customActionDTO instanceof HttpActionDTO) {
            return toHTTPActionDefinition((HttpActionDTO) customActionDTO);
        } else {
            // should not come here
            return null;
        }
    }

    private ActionDefinition toHTTPActionDefinition(HttpActionDTO httpActionDTO) {

        var userId = SyncariContext.getUser().getId();
        HttpMethod method = HttpMethod.resolve(httpActionDTO.getMethod());
        String credentialId = httpActionDTO.getCredentialId();

        AuthenticationInfo authenticationInfo = new AuthenticationInfo().setCredentialId(credentialId).setMetadataId(httpActionDTO.getMetadataId());

        List<Tag> tags = httpActionDTO.getTags().stream().map(tag -> new Tag(tag, true, Taggable.action, null)).collect(Collectors.toList());

        var configuration = toFunctionConfiguration(httpActionDTO.getVariables());

        var httpActionProperties = new HttpActionProperties().setEndPoint(httpActionDTO.getEndpoint())
                .setMethod(method).setEndPoint(httpActionDTO.getEndpoint()).setBody(httpActionDTO.getBody())
                .setHeaders(httpActionDTO.getHeaders()).setAuthenticationInfo(authenticationInfo)
                .setBatchSize(httpActionDTO.getBatchSize()).setBatch(httpActionDTO.isBatch());

        var httpAction = new CustomActionDefinition().setTags(tags)
                .setAuthor(userId).setApiName(httpActionDTO.getApiName()).setName(httpActionDTO.getApiName())
                .setDisplayName(httpActionDTO.getDisplayName()).setConfiguration(configuration).setHelpSummary(httpActionDTO.getBasicHelpText()).setHelpPath(httpActionDTO.getHelpLink())
                .setDescription(httpActionDTO.getDescription()).setType(Type.CUSTOM).setProperties(httpActionProperties);

        httpAction.setIconPath(httpActionDTO.getIconPath());
        httpAction.setId(httpActionDTO.getId());
        return httpAction;
    }

    private List<FunctionConfiguration> toFunctionConfiguration(List<KeyValue> variables) {
        return variables.stream().map(v -> {
            var name = v.get("name") != null ? v.get("name").toString() : "";
            var displayName = v.get("displayName") != null ? v.get("displayName").toString() : "";
            var helpText = v.get("helpText") != null ? v.get("helpText").toString() : "";
            var dataType = v.get("dataType") != null ? DatatypeFactory.getDatatype(v.get("dataType").toString()) : StringType.VALUE;
            var isRequired = v.get("required") != null ? (Boolean)v.get("required") : false;
            var isMultiValued = null != v.get("multivalued")  ? (boolean) v.get("multivalued") : false;

            return new FunctionConfiguration().setName(name.toString()).setLabel(displayName)
                    .setHelpSummary(helpText).setDatatype(dataType).setRequired(isRequired)
                    .setMultiValuedVariable(isMultiValued);
        }).collect(Collectors.toList());
    }

    private List<KeyValue> toVariables(List<FunctionConfiguration> configurations) {
        return configurations.stream().map(fc -> KeyValue.of("name", fc.getName(), "dataType", fc.getDatatype().getName(),
                "displayName", fc.getLabel(), "helpText", fc.getHelpSummary(), "required", fc.isRequired(),
                "multivalued" , fc.isMultiValuedVariable())).collect(Collectors.toList());
    }

    public CustomActionDTO toDTO(ActionDefinition actionDefinition) {
        return toHttpActionDTO((CustomActionDefinition) actionDefinition, Optional.empty());
    }

    public CustomActionDTO toDTO(ActionDefinition actionDefinition, PipelineStatus newStatus) {
        return toHttpActionDTO((CustomActionDefinition) actionDefinition, Optional.of(newStatus));
    }

    private CustomActionDTO toHttpActionDTO(CustomActionDefinition httpActionDefinition, Optional<PipelineStatus> newStatus) {

        var tags = httpActionDefinition.getTags().stream().map(tag -> tag.getName()).collect(Collectors.toList());
        var httpAction = (HttpActionProperties)httpActionDefinition.getProperties();
        var metadataId = !StringUtils.isBlank(httpAction.getAuthenticationInfo().getMetadataId()) ?
                httpAction.getAuthenticationInfo().getMetadataId() :
                connectorService.find(httpAction.getAuthenticationInfo().getCredentialId()).map(Connector::getMetadataId).orElse("");

        var sharedItem = !StringUtils.isBlank(httpActionDefinition.getParentId())  ?
                sharedItemRepo.findSharedItemBySourceIdAndItemType(httpActionDefinition.getParentId(), Sharable.ACTION)
                : sharedItemRepo.findSharedItemBySourceIdAndItemType(httpActionDefinition.getId(), Sharable.ACTION);

        return new HttpActionDTO().setMethod(httpAction.getMethod() != null ? httpAction.getMethod().name() : "")
                .setEndpoint(httpAction.getEndPoint()).setBody(httpAction.getBody()).setHeaders(httpAction.getHeaders())
                .setBatch(httpAction.isBatch())
                .setBatchSize(httpAction.getBatchSize())
                .setCredentialId(httpAction.getAuthenticationInfo().getCredentialId()).setMetadataId(metadataId)
                .setVariables(toVariables(httpActionDefinition.getConfiguration()))
                .setId(httpActionDefinition.getId()).setApiName(httpActionDefinition.getApiName()).setDisplayName(httpActionDefinition.getDisplayName())
                .setBasicHelpText(httpActionDefinition.getHelpSummary()).setHelpLink(httpActionDefinition.getHelpPath())
                .setDescription(httpActionDefinition.getDescription()).setStatus(newStatus.map(status -> status.name()).orElse(httpActionDefinition.getDraftStatus().name()))
                .setScope(httpActionDefinition.getScope().name()).setTags(tags).setIconPath(httpActionDefinition.getIconPath())
                .setShareWithOrg(sharedItem.map(SharedItem::isSharedWithOrg).orElse(false))
                .setShareGlobally(sharedItem.map(SharedItem::isPublishedToMarketplace).orElse(false));
    }

    public HttpActionResultDTO toHttpActionTestResultDTO(ActionTestResult actionTestResult) {
        var httpActionResult = (HTTPActionTestResult) actionTestResult;

        var requestHeaders = httpActionResult.getRequest().getRequestHeaders().entrySet().stream().map(header -> Pair.of(header.getKey(),
                String.join(",", header.getValue()))).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        var request = new HttpActionResultDTO.Request().setRequestHeaders(requestHeaders).setBody(Base64.getEncoder()
                .encodeToString(httpActionResult.getRequest().getBody().getBytes(StandardCharsets.UTF_8)) )
                .setURL(httpActionResult.getRequest().getEndpoint()).setMethod(httpActionResult.getRequest().getMethod().name());

        var responseHeaders= httpActionResult.getResponse().getResponseHeaders().entrySet().stream().map(header -> Pair.of(header.getKey(),
                String.join(",", header.getValue()))).collect(Collectors.toMap(Pair::getKey, Pair::getValue));

		String body = httpActionResult.getResponse().getBody();
		var response = new HttpActionResultDTO.Response().setResponseHeaders(responseHeaders)
				.setHttpStatusCode(httpActionResult.getResponse().getHttpStatus().toString());
		if(body != null) {
			response.setBody(Base64.getEncoder()
					.encodeToString(body.getBytes(StandardCharsets.UTF_8)));
		}

        return new HttpActionResultDTO().setRequest(request).setResponse(response);
    }
}
