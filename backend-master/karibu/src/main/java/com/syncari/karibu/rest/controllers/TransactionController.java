package com.syncari.karibu.rest.controllers;

import com.syncari.api.rest.controllers.TransactionServiceWrapper;
import com.syncari.api.rest.controllers.data.TransactionKpis;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.TransactionLogService;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.response.EntityResponse;
import com.syncari.karibu.rest.response.TransactionResponse;
import com.syncari.karibu.rest.response.ValidListResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.karibu.rest.util.TransactionUtils;
import com.syncari.restutils.utils.ApiUtils;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.*;

import static com.syncari.core.security.Permissions.VIEW_TRANSACTIONS;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/transactions")
public class TransactionController  {

    @Autowired
    TransactionServiceWrapper serviceWrapper;

    @Autowired
    TransactionLogService service;

    @Autowired
    TransactionUtils transactionUtils;

    @Autowired
    ApiUtils apiUtils;

    @Autowired
    DateUtil dateUtil;

    @Autowired
    ResponseUtils responseUtils;

    @Autowired
    ConnectorService connectorService;

    @Secured(VIEW_TRANSACTIONS)
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?> getTransactions(@RequestParam(required = false) String startTime,
                                             @RequestParam(required = false) String endTime,
                                             @RequestParam(required = false) String syncariEntityName,
                                             @RequestParam(required = false) String operation,
                                             @RequestParam(required = false) String syncariRecordId,
                                             @RequestParam(required = false) String cursorToken,
                                             @RequestParam(required = false) Integer limit
    ) {

        transactionUtils.validateRequestParam(startTime, endTime, syncariEntityName, cursorToken,
                operation, syncariRecordId, limit);

        if (limit == null)
            limit = KaribuConstants.MAX_LIMIT;

        // get transaction id from cursor
        String transactionId = null;
        if (cursorToken != null)
            transactionId = apiUtils.decodeCursor(cursorToken);

        if (operation != null)
            operation = operation.toLowerCase();

        String timeZone = "UTC";
        String format = "yyyy-MM-ddTHH:mm:ss.SSSZ";

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

        try {
            List<TransactionLog> transactions = service.queryByCursor(transactionId, formatter.parse(startTime), formatter.parse(endTime),
                    syncariEntityName, syncariRecordId, operation, limit+1);

            List<TransactionResponse> transactionResponses = transactionUtils.getTransactionListResponse(transactions);

            // prepare and return response
            ValidListResponse validListResponse = responseUtils.convertDTOToResponse(transactionResponses, limit);
            return ResponseEntity.status(HttpStatus.OK).body(validListResponse);
        } catch(Exception e) {
            e.printStackTrace();
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(VIEW_TRANSACTIONS)
    @RequestMapping(method = RequestMethod.GET, value = "/kpis")
    public TransactionKpis getKpis(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String entityName
    ) {
        Date start = DateUtil.parse(startDate, DateUtil.dateFormatMillis);
        Date end = DateUtil.parse(endDate, DateUtil.dateFormatMillis);

        return new TransactionKpis(
                serviceWrapper.countByRange(start, end, entityName),
                serviceWrapper.mostActiveEntity(start, end),
                mostActiveSynapse(start, end),
                serviceWrapper.countNewByRange(start, end, entityName),
                serviceWrapper.countUpdateByRange(start, end, entityName)
        );
    }

    private String mostActiveSynapse(Date start, Date end) {
        return serviceWrapper.mostActiveSynapse(start, end)
                .flatMap(connectorId -> connectorService.find(connectorId))
                .map(connector -> connector.getName())
                .orElse("-");
    }

}
