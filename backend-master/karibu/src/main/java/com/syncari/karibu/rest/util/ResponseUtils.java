package com.syncari.karibu.rest.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.UUIDAuditModel;
import com.syncari.karibu.rest.response.BaseKaribuResponse;
import com.syncari.karibu.rest.response.ErrorType;
import com.syncari.karibu.rest.response.IdResponse;
import com.syncari.karibu.rest.response.KaribuResponse;
import com.syncari.karibu.rest.response.ValidListResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.restutils.utils.ApiUtils;

@Component
public class ResponseUtils<k extends KaribuResponse, h extends UUIDAuditModel> {

    ResponseUtilHelper responseUtilHelper;

    @Autowired
    ApiUtils apiUtils;

    @Autowired
    public void setResponseUtilHelper(ResponseUtilHelper responseUtilHelper) {
        this.responseUtilHelper = responseUtilHelper;
    }

    public ResponseUtilHelper getResponseUtilHelper() {
        return this.responseUtilHelper;
    }

    // Create a response when the null is being returned. This is called when search result is empty.
    public ValidResponse<Object> populateEmptyResponse(){
        return responseUtilHelper.populateEmptyResponse();
    }

    public ValidResponse<Object> populateResponse(Object object) {
        return responseUtilHelper.populateResponse(object);
    }

    public ValidListResponse<Object> populateResponseList(List<Object> object) {
        return responseUtilHelper.populateResponseList(object);
    }

    public ValidResponse<Object> populateErrorResponse(String errorMessage) {
        ValidResponse<Object> validationResponse = new ValidResponse<>();
        validationResponse.setResult(null);
        addRequestId(validationResponse);
        addTimestamp(validationResponse);
        validationResponse.addError(errorMessage);
        return validationResponse;
    }

    public ValidResponse<Object> populateErrorDetailResponse(String errorMessage, String errorDetail) {
        ValidResponse<Object> validationResponse = new ValidResponse<>();
        validationResponse.setResult(null);
        addRequestId(validationResponse);
        addTimestamp(validationResponse);
        validationResponse.addErrorDetail(errorMessage, errorDetail);
        return validationResponse;
    }

    public ValidResponse<Object> populateErrorResponse(List<ErrorType> errorMessages) {
        ValidResponse<Object> validationResponse = new ValidResponse<>();
        validationResponse.setResult(null);
        addRequestId(validationResponse);
        addTimestamp(validationResponse);
        validationResponse.setErrors(errorMessages);
        return validationResponse;
    }

    public ValidResponse<Object> populateErrorResponse(ErrorType errorMessages) {
        ValidResponse<Object> validationResponse = new ValidResponse<>();
        validationResponse.setResult(null);
        addRequestId(validationResponse);
        addTimestamp(validationResponse);
        validationResponse.addErrorDetails(errorMessages);
        return validationResponse;
    }

    private  <p extends Object> void addRequestId(ValidResponse<p> validationResponse) {
        validationResponse.setRequestId(responseUtilHelper.getRequestId());
    }

    private  <p extends Object> void addRequestId(ValidListResponse<p> validationListResponse) {
        validationListResponse.setRequestId(responseUtilHelper.getRequestId());
    }

    private  <p extends Object> void addTimestamp(ValidResponse<p> validationResponse) {
        validationResponse.setTimestamp(responseUtilHelper.getTimestamp());
    }

    private  <p extends Object> void addTimestamp(ValidListResponse<p> validationListResponse) {
        validationListResponse.setTimestamp(responseUtilHelper.getTimestamp());
    }

    // Convert the DTO into response object based on the populate method.
    public ValidResponse<k> convertDTOToResponse(k response, h syncariDTO) {
        if (syncariDTO == null) {
            return responseUtilHelper.populateEmptyResponse();
            //return null;
        }
        k karibuResponse =  (k) response.populate(syncariDTO);
        return populateValidationResponse(karibuResponse, syncariDTO);
    }


    // Convert a response to ValidationResponse
    public ValidResponse<k> convertDTOToResponse(Object karibuResponse, h syncariDTO) {
        ValidResponse validationResponse = responseUtilHelper.populateResponse(karibuResponse);
        return validationResponse;
    }

    private ValidResponse<k> populateValidationResponse(k karibuResponse, h syncariDTO) {
        ValidResponse validationResponse = populateValidationResponse(karibuResponse);
        return validationResponse;
    }


    // Convert the DTO into response object based on the populate method.
    public ValidListResponse<k> convertDTOToResponse(k response, List<h> syncariDTO, boolean hasCursor) {
        List<k> list = new ArrayList<k>();
        for (h h : syncariDTO) {
            k karibuResponse = (k) response.populate(h);
            list.add(karibuResponse);
        }
        return populateValidationListResponse(list, hasCursor);
    }

    // Create the Response for given response object.
    public ValidResponse<k> populateValidationResponse(k response) {
        if(response == null){
            return null;
        }
        //List<k> list = new ArrayList<>();
        ///list.add(response);
        ValidResponse<k> validationResponse = new ValidResponse<>();
        validationResponse.setResult(response);
        addRequestId(validationResponse);
        addTimestamp(validationResponse);
        return validationResponse;
    }

    // Create response object for a given response object list.
    public ValidListResponse<k> populateValidationListResponse(List<k> response, boolean hasCursor) {
        ValidListResponse<k> validationListResponse = new ValidListResponse<>();
        validationListResponse.setResult(response);
        if(hasCursor && !response.isEmpty())
            validationListResponse.setCursorToken(getCursor(response));
        addRequestId(validationListResponse);
        addTimestamp(validationListResponse);
        return validationListResponse;
    }

    // Create response object for a given response object list.
    public ValidListResponse<k> populateValidationListResponseWithCursor(List<k> response, int cursor) {
        ValidListResponse<k> validationListResponse = new ValidListResponse<>();
        validationListResponse.setResult(response);
        validationListResponse.setCursorToken(apiUtils.encodeCursor(String.valueOf(cursor)));
        addRequestId(validationListResponse);
        addTimestamp(validationListResponse);
        return validationListResponse;
    }

    // Convert a response to ValidationResponse
    public ValidResponse<k> convertDTOToResponse(Object response) {
        ValidResponse validationResponse = responseUtilHelper.populateResponse(response);
        return validationResponse;
    }

    // Convert for list calls before we had cursors.
    public ValidListResponse<k> convertDTOToResponse(List<k> syncariDTO) {
        return convertDTOToResponse(syncariDTO, false);
    }


    // Convert the DTO into response object based on the populate method.
    public ValidListResponse<k> convertDTOToResponse(List<k> syncariDTO, boolean hasCursor) {
        return populateValidationListResponse(syncariDTO, hasCursor);
    }

    public ValidListResponse<k> convertDTOToResponse(List<k> syncariDTO, int limit) {
        if (CollectionUtils.isNotEmpty(syncariDTO) && syncariDTO.size() > limit){
            return populateValidationListResponse(syncariDTO.subList(0, syncariDTO.size()-1), true);
        }else{
            return populateValidationListResponse(syncariDTO, false);
        }
    }

    // Convert the DTO into response object based on the populate method.
    public ValidResponse<IdResponse> convertDTOToResponse(String id) {
        return responseUtilHelper.populateIdResponse(id);
    }

    private String getCursor(List<k> response) {
        BaseKaribuResponse lastRecord = (BaseKaribuResponse) response.get(response.size() - 1);
        return apiUtils.encodeCursor(lastRecord.getId());
    }

}
