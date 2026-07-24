package com.syncari.core.actions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.RealtimeSyncContext;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.ValidationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component(ActionConstants.RESPOND_TO_WEBHOOK)
@Slf4j
public class RespondToWebhookAction extends DefaultAction {

    @Autowired
    TokenHelper tokenHelper;

    @Override
    public void validate(ValidationContext validationContext) {
        var errors = validateWithoutException(validationContext);
        if (errors != null && !errors.isEmpty()) {
            throw new SyncariValidationException(errors.get(0).getMessage());
        }
    }

    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        List<ValidationError> errors = new ArrayList<ValidationError>();
        errors.addAll(super.validateWithoutException(validationContext));
        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();
        // validate that there is only one inbound edge for respond to webhook
        List<Edge> inboundEdges = graph.getInboundEdges(node);
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), CollectionUtils.isEmpty(inboundEdges),
                i18n("action_node_missing_inbound", node.getName(), graph.getName()), ErrorCode.E1203.getCode()).ifPresent(e -> errors.add(e));
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), inboundEdges.size() > 1,
                i18n("action_node_multiple_inbound", node.getName(), graph.getName()), ErrorCode.E1204.getCode()).ifPresent(e -> errors.add(e));

        return errors;
    }

    public ActionResult execute(GenericActionConfig actionConfig, GraphContext context) {
        RealtimeSyncContext realtimeSyncContext = context.getRealtimeSyncContext();
        try {
            int statusCode = getConfig("statusCode", actionConfig);
            String response = getConfig("response", actionConfig);
            String resolvedResponse = tokenHelper.resolveTokens(context, response);
            if (realtimeSyncContext != null && realtimeSyncContext.getSyncResponse() != null) {
                MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
                headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                realtimeSyncContext.getSyncResponse().complete(new WebhookActionResponse()
                        .setStatusCode(HttpStatus.valueOf(statusCode)).setPayload(resolvedResponse)
                        .setHeaders(headers));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (realtimeSyncContext != null
                    && realtimeSyncContext.getSyncResponse() != null
                    && !realtimeSyncContext.getSyncResponse().isDone()) {

                MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
                headers.set("Content-Type", MediaType.TEXT_PLAIN_VALUE);
                realtimeSyncContext.getSyncResponse().complete(new WebhookActionResponse()
                        .setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR).setPayload(e.getMessage())
                        .setHeaders(headers));
                return new ActionResult(false);
            }
        }
        return new ActionResult(true);
    }
}
