package com.syncari.karibu.rest.controllers;

import com.syncari.restutils.transformers.QuickStartTransformer;
import com.syncari.api.rest.controllers.data.quickstart.v2.QuickStartRestDTO;
import com.syncari.core.quickstart.v2.QuickStart;
import com.syncari.core.quickstart.v2.QuickStartV2Service;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.response.MarketPlaceQuickStartResponse;
import com.syncari.karibu.rest.response.ValidListResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.restutils.utils.ApiUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/marketplace")
public class MarketplaceController {

    @Autowired
    QuickStartV2Service service;

    @Autowired
    ResponseUtils<MarketPlaceQuickStartResponse, QuickStart> responseUtils;

    @Autowired
    QuickStartTransformer qsTransformer;

    @Autowired
    ApiUtils apiUtils;

    MarketPlaceQuickStartResponse marketPlaceQuickStartResponse = new MarketPlaceQuickStartResponse();

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?> getMarketPlaceQuickStarts(@RequestParam(value = "displayName", required = false) String displayName,
                                                       @RequestParam(value = "cursorToken", required = false) String cursorToken,
                                                       @RequestParam(value = "limit", required = false, defaultValue = KaribuConstants.MAX_LIMIT_STRING) Integer limit) {
        try{
            // validate limit mx value
            if (limit > KaribuConstants.MAX_LIMIT)
                throw new BadRequestException(i18n("limit_max_value_error", limit, KaribuConstants.MAX_LIMIT));

            // get shared item id
            String sharedItemId = null;
            if (cursorToken != null)
                sharedItemId = apiUtils.decodeCursor(cursorToken);

            // get and transform quick starts and
            List<QuickStart> qsList = service.getMarketplaceQuickStarts(displayName, sharedItemId, limit);

            ValidListResponse response = responseUtils.convertDTOToResponse(marketPlaceQuickStartResponse, qsList, true);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @RequestMapping(method = RequestMethod.GET, value = "/{quickStartId}")
    public ResponseEntity<?> getMarketPlaceQuickStartById(@PathVariable String quickStartId) {
        try{
            QuickStartRestDTO quickStart = qsTransformer.toQuickStartRestDTO(service.getMarketplaceQuickStartById(quickStartId));
            ValidResponse response = responseUtils.convertDTOToResponse(quickStart);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
