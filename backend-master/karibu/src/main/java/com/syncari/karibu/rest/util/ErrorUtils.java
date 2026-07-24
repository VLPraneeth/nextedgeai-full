package com.syncari.karibu.rest.util;

import com.syncari.core.model.Connector;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.model.pagination.Page;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.response.ErrorResponse;
import com.syncari.utils.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.syncari.utils.I18n.i18n;

@Component
public class ErrorUtils {

    @Autowired
    DateUtil dateUtil;

    public static final List<String> SUPPORTED_OPERATIONS = List.of("Create", "Update", "Delete", "Disconnect", "Merge");


    public void validateRequestParam(String errorType, String startTime, String endTime, String synapseName, String cursorToken,
                                     String operation, String syncariEntityName, String syncariRecordId, Integer limit) {

        switch (errorType) {
            case "syncError":
                String timeZone = "UTC";

                if(startTime == null || endTime == null)
                    throw new BadRequestException(i18n("error_sync_required_params"));

                try {
                    dateUtil.toInstant(startTime, timeZone);
                    dateUtil.toInstant(endTime, timeZone);
                } catch (Exception e) {
                    throw new BadRequestException(i18n("error_invalid_time_format"));
                }

                if(dateUtil.toInstant(startTime, timeZone).compareTo(dateUtil.toInstant(endTime, timeZone)) >= 1)
                    throw new BadRequestException(i18n("error_invalid_times", startTime, endTime));

                if(operation != null && !SUPPORTED_OPERATIONS.contains(operation))
                    throw new BadRequestException(i18n("error_invalid_operation", operation, SUPPORTED_OPERATIONS.toString()));

                if(limit != null && limit > KaribuConstants.MAX_LIMIT)
                    throw new BadRequestException(i18n("limit_max_value_error", limit, KaribuConstants.MAX_LIMIT));

                break;
            case "synapseError":
                if(startTime != null || endTime != null || synapseName != null || cursorToken != null || operation != null ||
                syncariEntityName!= null || syncariRecordId != null || limit != null)
                    throw new BadRequestException(i18n("error_invalid_request_param"));

                break;
            default:
                throw new BadRequestException(i18n("error_invalid_errortype",errorType));
        }

    }

    public List<ErrorResponse> getSyncErrorListResponse (Page<SyncError> syncErrorPage, String errorType) {
        List<ErrorResponse> responses = new ArrayList<>();
        for (SyncError error : syncErrorPage.getRecords()) {
            ErrorResponse response = new ErrorResponse();
            response.setErrorType(errorType);
            response.setSynapseId(error.getConnectorId());
            response.setSynapseName(error.getConnectorName());
            response.setSyncariEntityName(error.getSyncariEntityName());
            response.setExternalEntityName(error.getExternalEntityName());
            response.setSyncariRecordId(error.getSyncariRecordId());
            response.setExternalRecordId(error.getExternalRecordId());
            response.setOperation(error.getOperation());
            response.setError(error.getErrorCode());
            response.setErrorDetail(error.getErrorDetails());
            response.setOccurredAt(error.getOccuredTime().toString());
            responses.add(response);

        }
        return responses;
    }

    public List<ErrorResponse> getSynapseErrorListResponse (List<Connector> connectors, String errorType) {
        List<ErrorResponse> responses = new ArrayList<>();
        for (Connector connector : connectors) {
            if(connector.getStatus().equals(ConnectorStatus.ERROR)) {
                ErrorResponse response = new ErrorResponse();
                response.setErrorType(errorType);
                response.setSynapseId(connector.getId());
                response.setSynapseName(connector.getName());
                response.setOperation("testConnection");
                response.setError(connector.getErrorMessage());
                response.setErrorDetail(connector.getErrorDetail());
                responses.add(response);
            }

        }
        return responses;
    }

}
