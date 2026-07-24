package com.syncari.karibu.rest.controllers;

import static com.syncari.core.security.Permissions.READ_REFERENCE_DATA;
import static com.syncari.core.security.Permissions.WRITE_REFERENCE_DATA;
import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.misc.ReferenceData;
import com.syncari.core.model.misc.ReferenceDataSource;
import com.syncari.core.model.misc.ReferenceDataSourceType;
import com.syncari.core.service.ReferenceDataService;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import com.syncari.karibu.rest.request.ReferenceDataRequest;
import com.syncari.karibu.rest.response.ReferenceDataItemResponse;
import com.syncari.karibu.rest.response.ReferenceDataResponse;
import com.syncari.karibu.rest.response.ValidListResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.ReferenceDataUtils;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.restutils.utils.ApiUtils;
import com.syncari.utils.file.FileUtil;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/referencedata")

public class ReferenceDataController {
    @Autowired
    ReferenceDataService service;

    @Autowired
    ReferenceDataUtils referenceDataUtils;

    @Autowired
    ResponseUtils responseUtils;
    
    @Autowired
    FileUtil fileUtil;
    
    @Autowired
    ApiUtils apiUtils;

    @Secured(READ_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?> listReferenceDatasets() {
        try {
            List<ReferenceDataMeta> refDataMeta = service.listMeta(0);

            List<ReferenceDataResponse> referenceDataResponseList = referenceDataUtils.getReferenceDataResponses(refDataMeta);

            ValidListResponse response = responseUtils.convertDTOToResponse(referenceDataResponseList);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(READ_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.GET, value = "/{referenceDataId}")
    public ResponseEntity<?> getReferenceDataset(@PathVariable String referenceDataId) {
        try {
            ReferenceDataMeta refDataMeta = service.findReferenceData(referenceDataId).orElseThrow(() ->
                    new NotFoundException(i18n("reference_data_not_found", referenceDataId)));

            ReferenceDataResponse referenceDataResponse = referenceDataUtils.getReferenceDataResponse(refDataMeta);

            ValidResponse response = responseUtils.convertDTOToResponse(referenceDataResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (NotFoundException nfe) {
            throw new NotFoundException(nfe.getMessage());
        } catch (Exception e) {
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<?> createReferenceData(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                 @Valid @RequestBody ReferenceDataRequest referenceDataRequest) {
        try {
            ReferenceDataMeta dataset = new ReferenceDataMeta();
            dataset.setName(referenceDataRequest.getName());
            String fullyQualifiedFileName = SyncariContext.getSyncariId() + "/" + referenceDataRequest.getName()+".csv";
            dataset.setSource(new ReferenceDataSource(ReferenceDataSourceType.upload, fullyQualifiedFileName));

            MultipartFile file = referenceDataUtils.getMultipartFile(referenceDataRequest);

            ReferenceDataMeta refDataMeta = service.createMeta(dataset, file.getInputStream(), file.getInputStream(), true);

            ReferenceDataResponse referenceDataResponse = referenceDataUtils.getReferenceDataResponse(refDataMeta);

            ValidResponse response = responseUtils.convertDTOToResponse(referenceDataResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }


    @Secured(WRITE_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.POST, value = "/upload")
    public ResponseEntity<?> createUploadReferenceData(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                       @RequestParam("name") String name,
                                                       @RequestParam("fileName") String fileName,
                                                       @RequestParam(name = "file", required = false) MultipartFile file) {
        try {
            ReferenceDataMeta dataset = new ReferenceDataMeta();
            dataset.setName(name);
            referenceDataUtils.validateFile(file);
            String fixedFileName = fileUtil.sanitizeFileName(file.getOriginalFilename());
            String fullyQualifiedFileName = SyncariContext.getSyncariId() + "/" + fixedFileName;
            dataset.setSource(new ReferenceDataSource(ReferenceDataSourceType.upload, fullyQualifiedFileName));
            ReferenceDataMeta refDataMeta = service.createMeta(dataset, file.getInputStream(), file.getInputStream(), true);

            ReferenceDataResponse referenceDataResponse = referenceDataUtils.getReferenceDataResponse(refDataMeta);

            ValidResponse response = responseUtils.convertDTOToResponse(referenceDataResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }


    @Secured(WRITE_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.POST, value = "/{datasetId}/items")
    public ResponseEntity<?> addData(
            @NotBlank @RequestHeader(value = "clientRequestId") String requestClientId, @PathVariable String datasetId,
            @RequestBody List<Map<String, Object>> rows) {
        try {
            List<String> persisted = service.addItems(datasetId, rows);
            ValidListResponse response = responseUtils.convertDTOToResponse(persisted);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
        	log.error(ExceptionUtils.getStackTrace(e));
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(ReferenceData.class, "referenceDataId", datasetId);
            } else {
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }
    @Secured(WRITE_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.PUT, value = "/{datasetId}/items")
    public ResponseEntity<?> replaceData(
            @NotBlank @RequestHeader(value = "clientRequestId") String requestClientId, @PathVariable String datasetId,
            @RequestBody List<Map<String, Object>> rows) {
        try {
            List<String> persisted = service.replaceItems(datasetId, rows);
            ValidListResponse response = responseUtils.convertDTOToResponse(persisted);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(ReferenceData.class, "referenceDataId", datasetId);
            } else {
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }

    @Secured(WRITE_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.PATCH, value = "/{datasetId}/items")
    public ResponseEntity<ValidResponse> updateData(
            @NotBlank @RequestHeader(value = "clientRequestId") String requestClientId, @PathVariable String datasetId,
            @RequestBody Map<String, Map<String, Object>> rows) {
        try {

            long updated = service.updateItems(datasetId, rows);
            ValidResponse response = new ValidResponse<>().addResult(Map.of("updatedCount", updated));
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(ReferenceData.class, "referenceDataId", datasetId);
            } else {
            	ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }

    @Secured(WRITE_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{datasetId}/items")
    public ResponseEntity<ValidResponse> deleteData(
            @NotBlank @RequestHeader(value = "clientRequestId") String requestClientId, @PathVariable String datasetId,
            @RequestBody List<String> ids) {
        try {
            long deleted = service.deleteItems(datasetId, ids);
            ValidResponse response = new ValidResponse<>().addResult(Map.of("deletedCount", deleted));
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
        	log.error(ExceptionUtils.getStackTrace(e));
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(ReferenceData.class, "referenceDataId", datasetId);
            } else {
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }

    @Secured(WRITE_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{datasetId}")
    public ResponseEntity<ValidResponse> delete(
            @NotBlank @RequestHeader(value = "clientRequestId") String requestClientId, @PathVariable String datasetId) {
        try {
            service.deleteMeta(datasetId);
            ValidResponse response = new ValidResponse<>();
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(ReferenceData.class, "referenceDataId", datasetId);
            } else {
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }
    
    @Secured(READ_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.GET, value = "/{datasetId}/items")
    public ResponseEntity<?> queryData(
            @NotBlank @RequestHeader(value = "clientRequestId", required = false) String requestClientId, @PathVariable String datasetId,
            @RequestParam(value = "cursorToken", required = false) String cursorToken,
            @RequestParam(value = "limit", required = false, defaultValue = KaribuConstants.MAX_LIMIT_STRING) Integer limit) {
        try {
        	List<Map<String,String>> page = service.query(datasetId, StringUtils.isBlank(cursorToken) ? cursorToken : apiUtils.decodeCursor(cursorToken), limit+1);
        	List<ReferenceDataItemResponse> resp = page.stream().map(i -> new ReferenceDataItemResponse(i.get("_id"), i)).collect(Collectors.toList());
			ValidListResponse response = responseUtils.convertDTOToResponse(resp, limit);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
        	log.error(ExceptionUtils.getStackTrace(e));
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(ReferenceData.class, "referenceDataId", datasetId);
            } else {
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }

}