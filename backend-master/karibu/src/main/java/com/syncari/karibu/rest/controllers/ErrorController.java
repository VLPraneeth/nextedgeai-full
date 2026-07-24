package com.syncari.karibu.rest.controllers;

import com.syncari.analytics.service.AnalyticsService;
import com.syncari.core.model.Connector;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.service.ConnectorService;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.response.ErrorResponse;
import com.syncari.karibu.rest.response.ValidListResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.ErrorUtils;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.restutils.utils.ApiUtils;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

import static com.syncari.core.security.Permissions.ANALYTICS;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/errors")
public class ErrorController {

    @Autowired
    AnalyticsService analyticsService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    DateUtil dateUtil;

    @Autowired
    ApiUtils apiUtils;

    @Autowired
    ErrorUtils errorUtils;

    @Autowired
    ResponseUtils responseUtils;

    @Secured(ANALYTICS)
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?> getSyncErrors(
            @RequestParam String errorType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String synapseName,
            @RequestParam(required = false) String cursorToken,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String syncariEntityName,
            @RequestParam(required = false) String syncariRecordId,
            @RequestParam(required = false) Integer limit
    ) {
        try {
            List<ErrorResponse> errorResponse = new ArrayList<>();
            ValidListResponse validListResponse = new ValidListResponse<>();

            errorUtils.validateRequestParam(errorType, startTime, endTime, synapseName, cursorToken, operation, syncariEntityName,
                    syncariRecordId, limit);

            switch (errorType) {
                case "syncError":

                    String timeZone = "UTC";

                    int pageNumber = (cursorToken != null) ? Integer.parseInt(apiUtils.decodeCursor(cursorToken)) : 0;
                    limit = (limit == null) ? KaribuConstants.MAX_LIMIT : limit;

                    Page<SyncError> syncErrorPage = analyticsService.getSyncErrors(
                            new PageCursor(pageNumber, limit),
                            dateUtil.toInstant(startTime, timeZone),
                            dateUtil.toInstant(endTime, timeZone),
                            synapseName,
                            operation,
                            syncariEntityName,
                            syncariRecordId
                    );

                    errorResponse = errorUtils.getSyncErrorListResponse(syncErrorPage, errorType);

                    validListResponse = responseUtils.populateValidationListResponseWithCursor(errorResponse,
                            syncErrorPage.getPageInfo().getPageNumber() + 1);

                    break;
                case "synapseError":
                    List<Connector> connectors = connectorService.list();

                    errorResponse = errorUtils.getSynapseErrorListResponse(connectors, errorType);

                    validListResponse = responseUtils.convertDTOToResponse(errorResponse);

            }

            // prepare and return response
            return ResponseEntity.status(HttpStatus.OK).body(validListResponse);
        } catch (BadRequestException bre) {
            throw new BadRequestException(bre.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

}
