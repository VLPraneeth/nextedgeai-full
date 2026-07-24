package com.syncari.karibu.rest.util;

import com.google.api.client.util.ArrayMap;
import com.syncari.connector.EntityData;
import com.syncari.core.model.FieldChange;
import com.syncari.core.model.MergeOperation;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.misc.Destination;
import com.syncari.core.model.misc.ExternalValue;
import com.syncari.core.model.misc.Source;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.NodeError;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.response.TransactionResponse;
import com.syncari.utils.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.syncari.utils.I18n.i18n;

@Component
public class TransactionUtils {

    @Autowired
    DateUtil dateUtil;

    public static final List<String> SUPPORTED_OPERATIONS = List.of("Create", "Update", "Delete", "Disconnect", "Merge", "Merge_Report_Only");

    public void validateRequestParam(String startTime, String endTime, String syncariEntityName, String cursorToken,
                                     String operation, String syncariRecordId, Integer limit) {

        String timeZone = "UTC";

        if (startTime == null || endTime == null)
            throw new BadRequestException(i18n("transaction_required_params"));

        try {
            dateUtil.toInstant(startTime, timeZone);
            dateUtil.toInstant(endTime, timeZone);
        } catch (Exception e) {
            throw new BadRequestException(i18n("error_invalid_time_format"));
        }

        if (dateUtil.toInstant(startTime, timeZone).compareTo(dateUtil.toInstant(endTime, timeZone)) >= 1)
            throw new BadRequestException(i18n("error_invalid_times", startTime, endTime));

        if (operation != null && !SUPPORTED_OPERATIONS.contains(operation))
            throw new BadRequestException(i18n("error_invalid_operation", operation, SUPPORTED_OPERATIONS.toString()));

        if (limit != null && limit > KaribuConstants.MAX_LIMIT)
            throw new BadRequestException(i18n("limit_max_value_error", limit, KaribuConstants.MAX_LIMIT));

    }

    public List<TransactionResponse> getTransactionListResponse (List<TransactionLog> transactions) {
        List<TransactionResponse> responses = new ArrayList<>();

        for (TransactionLog transaction : transactions) {
            TransactionResponse response = new TransactionResponse();
            response.setId(transaction.getId());
            response.setSyncariId(transaction.getSyncariId());
            response.setOccurredAt(dateUtil.format(transaction.getOccurredAt(), KaribuConstants.DATE_FORMAT));
            response.setEntityId(transaction.getEntityId());
            response.setEntityName(transaction.getEntityName());
            response.setSourceId(transaction.getSyncariId());
            response.setOperation(transaction.getOperation().name());
            response.setCreatedAt(transaction.getCreatedAt());
            response.setCreatedBy(transaction.getCreatedBy());
            response.setUpdatedAt(transaction.getUpdatedAt());
            response.setUpdatedBy(transaction.getUpdatedBy());

            if (!transaction.getChanges().isEmpty())
                response.setTransactionDetails(getTransactionsDetails(transaction.getChanges()));
            if (!transaction.getSources().isEmpty())
                response.setSources(getSources(transaction.getSources()));
            if (!transaction.getDestinations().isEmpty())
                response.setDestination(getDestination(transaction.getDestinations()));
            if (!transaction.getErrors().isEmpty())
                response.setErrors(getErrors(transaction.getErrors()));
            if (transaction.getAdditionalInfo().get("mergeDetails")  != null) {
                response.setWinningRecord(getWinningRecord((MergeOperation) transaction.getAdditionalInfo().get("mergeDetails")));
                response.setLosingRecords(getLosingRecords((MergeOperation) transaction.getAdditionalInfo().get("mergeDetails")));
            }

            responses.add(response);
        }

        return responses;
    }

    private List<Map<String, Object>> getTransactionsDetails(Map<String, FieldChange> changes) {
        List<Map<String, Object>> transactionDetails = new ArrayList<>();

        changes.values().forEach(change -> {
            Map<String, Object> c = new ArrayMap<>() {};
            c.put("fieldId", change.getFieldId());
            c.put("fieldApiName", change.getApiName());
            c.put("fieldDisplayName", change.getDisplayName());
            if (change.getOldValue() != null)
                c.put("oldValue", change.getOldValue());
            c.put("newValue", change.getNewValue());
            if (change.getAuthoritativeSource() != null) {
                c.put("sourceSynapseId", change.getAuthoritativeSource().getConnectorId());
                c.put("sourceSynapseName", change.getAuthoritativeSource().getConnectorName());
                c.put("sourceFieldId", change.getAuthoritativeSource().getFieldId());
                c.put("sourceFieldName", change.getAuthoritativeSource().getApiName());
            }
            if (change.getIncomingExternalValues() != null && change.getIncomingExternalValues().size() > 0) {
                c.put("incomingValues", getExternalValues(change.getIncomingExternalValues()));
            }
            if (change.getOutgoingExternalValues() != null && change.getOutgoingExternalValues().size() > 0) {
                c.put("outgoingValues", getExternalValues(change.getOutgoingExternalValues()));
            }

            transactionDetails.add(c);
        });

        return transactionDetails;
    }

    private List<Map<String, Object>> getExternalValues(Map<String, ExternalValue> externalValues) {
        List<Map<String, Object>> values = new ArrayList<>();
        externalValues.values().forEach(ev -> {
            Map<String, Object> value = new HashMap<>();
            value.put("synapseId", ev.getConnectorId());
            value.put("synapseName", ev.getConnectorName());
            value.put("fieldId", ev.getFieldId());
            value.put("apiName", ev.getApiName());
            value.put("displayName", ev.getDisplayName());
            value.put("dataType", ev.getDataType());
            value.put("value", ev.getValue());
            values.add(value);
        });
        return values;
    }

    private List<Map<String, Object>> getSources(List<Source> sourceList) {
        List<Map<String, Object>> sources = new ArrayList<>();

        sourceList.forEach(source -> {
            Map<String, Object> s = new ArrayMap<>() {};
            s.put("synapseId", source.getConnectorId());
            s.put("synapseName", source.getConnectorName());
            s.put("externalId", source.getExternalId());
            s.put("lastModified", dateUtil.format(source.getLastModified(), KaribuConstants.DATE_FORMAT));

            sources.add(s);
        });

        return sources;
    }

    private List<Map<String, Object>> getDestination(List<Destination> destinationList) {
        List<Map<String, Object>> destinations = new ArrayList<>();

        destinationList.forEach(destination -> {
            Map<String, Object> d = new HashMap<>();
            d.put("synapseId", destination.getConnectorId());
            d.put("synapseName", destination.getConnectorName());
            d.put("externalId", destination.getExternalId());
            d.put("details", destination.getDetails());
            d.put("isSkipped", destination.isSkipped());
            d.put("isError", destination.isError());

            destinations.add(d);
        });

        return destinations;
    }

    private List<Map<String, Object>> getErrors(List<NodeError> errorList) {
        List<Map<String, Object>> errors = new ArrayList<>();

        errorList.forEach(error -> {
            Map<String, Object> e = new ArrayMap<>() {};
            e.put("synapseId", error.getScope());
            if (error.getScope().equals(Scope.ENTITY)) {
                e.put("pipelineId", error.getGraphId());
                e.put("pipelineName", error.getGraphName());
            }
            if (error.getScope().equals(Scope.ATTRIBUTE)) {
                e.put("fieldPipelineId", error.getGraphId());
                e.put("fieldPipelineName", error.getGraphName());
            }
            e.put("nodeId", error.getNodeId());
            e.put("nodeName", error.getNodeName());
            e.put("error", error.getError());
            e.put("errorDetails", error.getErrorDetails());
            e.put("request", error.getRequest());
            e.put("response", error.getResponse());

            errors.add(e);
        });

        return errors;
    }

    private Map<String, Object> getWinningRecord(MergeOperation mergeOperation){
        EntityData entityData = mergeOperation.getWinningRecord();
        return entityData.getValues();
    }

    private List<Map<String, Object>> getLosingRecords(MergeOperation mergeOperation){
        List<Map<String, Object>> losingRecords = new ArrayList<>();
        List<EntityData> entityData = mergeOperation.getLosingRecords();
        entityData.forEach(e -> {
            Map<String, Object> losingReord = new HashMap<>();
            losingReord.put("id", e.getId());
            losingReord.putAll(e.getValues());
            losingReord.remove("syncariScore");
            losingReord.remove("_id");
            losingRecords.add(losingReord);});

        return losingRecords;
    }
}