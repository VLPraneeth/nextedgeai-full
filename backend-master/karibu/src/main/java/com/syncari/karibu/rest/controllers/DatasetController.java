package com.syncari.karibu.rest.controllers;

import com.syncari.api.rest.controllers.data.insights.DatasetDTO;
import com.syncari.api.rest.controllers.data.insights.DatasetReadDataDTO;
import com.syncari.api.rest.controllers.data.insights.DatasetSampleDTO;
import com.syncari.api.rest.controllers.data.insights.DatasetTransformer;
import com.syncari.connector.data.DatastoreFieldMetadata;
import com.syncari.core.exceptions.AbacException;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.model.insights.dataset.DatasetPageInfo;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.service.DatasetService;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.exceptions.UnauthorizedException;
import com.syncari.karibu.rest.response.ValidListResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.InsightsUtils;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.restutils.utils.ApiUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.VIEW_DATASET;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/datasets")
public class DatasetController {

    @Autowired
    DatasetTransformer datasetTransformer;

    @Autowired
    DatasetService datasetService;

    @Autowired
    ResponseUtils responseUtils;

    @Autowired
    ApiUtils apiUtils;

    @Autowired
    InsightsUtils insightsUtils;

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?> listDatasets(@RequestParam(required = false) String cursorToken,
                                         @RequestParam(required = false) Integer limit) {
        if (limit == null)
            limit = KaribuConstants.MAX_LIMIT;

        // get dataset id from cursor
        String cursorId = null;
        if (cursorToken != null)
            cursorId = apiUtils.decodeCursor(cursorToken);

        try {
            List<Dataset> datasets = datasetService.getAllApprovedDatasetsFromPageCursor(new PageCursor(cursorId, PageDirection.next, limit+1));
            // return only published datacards until we cleanup all drafts
            List<DatasetDTO> dtos =  datasets.stream()
                    .filter(d -> d.isApproved())
                    .map(d -> datasetTransformer.transformToDTO(d))
                    .collect(Collectors.toList());

            ValidListResponse validListResponse = responseUtils.convertDTOToResponse(dtos.stream().map(d -> insightsUtils.getDatasetResponse(d)).collect(Collectors.toList()), limit);
            return ResponseEntity.status(HttpStatus.OK).body(validListResponse);
        } catch(Exception e) {
            log.error("Exception occured for cursorToken {} with stacktrace : {}",cursorToken, ExceptionUtils.getStackTrace(e));
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(VIEW_DATASET)
    @RequestMapping(method = RequestMethod.POST, value = "/{datasetId}")
    public ResponseEntity<?> readData(@PathVariable String datasetId,@RequestParam(required = false) String cursorToken,
                                      @RequestParam(required = false) Integer limit) {
        try {
            Dataset datasetLocal = datasetService.getDataset(datasetId);
            if (limit == null)
                limit = KaribuConstants.MAX_LIMIT;

            // get dataset id from cursor
            String cursorId = null;
            if (cursorToken != null)
                cursorId = apiUtils.decodeCursor(cursorToken);
            PageCursor pageCursor = new PageCursor(cursorId, PageDirection.next,limit);

            Long offset = 0l;
            try{
                offset = null != pageCursor.getCursor() ? Long.valueOf(pageCursor.getCursor()) : offset;
                offset = datasetService.getOffsetBasedOnDirection(offset, pageCursor.getDirection(), limit);
            }catch (Exception e){
                log.error("Cursor {} is not parsable to long, always returns first page, exception is {}", pageCursor.getCursor(), ExceptionUtils.getStackTrace(e));
            }
            Map<String, Object> dataAndCols =  datasetService.readDataWithPagination(datasetLocal, Map.of(),limit,offset);
            if (MapUtils.isEmpty(dataAndCols) || (null == dataAndCols.get("data"))){
                log.info("Not able to fetch data, something went wrong for datasetId {}, cursor {}", datasetId, cursorToken);
                ValidResponse response = responseUtils.populateErrorResponse(i18n("dataset_data_fetch_error"));
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            long recordCount = ((List<Map<String, Object>>)dataAndCols.get("data")).size();
            DatasetSampleDTO dto = new DatasetSampleDTO();
            dto.setColumns(datasetTransformer.toDatasetSampleColumnsDTOSFromDatastoreMetadata((List<DatastoreFieldMetadata>)dataAndCols.get("columns")));
            dto.setData(datasetTransformer.toDatasetSampleDataDTOS((List<Map<String, Object>>)dataAndCols.get("data")));
            Dataset datasetForCount = datasetTransformer.transformRequestToDatasetForCount(datasetLocal, Map.of());
            Map<String, Object> dataAndColForCount = datasetService.readSampleData(datasetForCount, Map.of());
            List<Map<String, Object>> dataMap = (List<Map<String, Object>>)dataAndColForCount.getOrDefault("data", List.of());
            Long count = (Long)dataMap.stream().findFirst().get().getOrDefault("totalCount",0l);
            DatasetPageInfo pageInfo = datasetService.addPageInfo(recordCount,offset,datasetForCount, count);
            pageInfo.setStart(apiUtils.encodeCursor(pageInfo.getStart()));
            pageInfo.setEnd(apiUtils.encodeCursor(pageInfo.getEnd()));
            dto.setPageInfo(pageInfo);
            ValidResponse validResponse = responseUtils.convertDTOToResponse(insightsUtils.getDatasetDataResponse(dto));
            return ResponseEntity.status(HttpStatus.OK).body(validResponse);
        } catch (AbacException e) {
          throw e;
        } catch(Exception e) {
            log.error("Exception occured while fetching data for datasetId {}, cursorToken {} wtih stacktrace : {}",datasetId,cursorToken, ExceptionUtils.getStackTrace(e));
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

    }
}
