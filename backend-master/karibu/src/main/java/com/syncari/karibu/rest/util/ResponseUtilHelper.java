package com.syncari.karibu.rest.util;

import com.syncari.karibu.rest.controllers.data.ErrorDTO;
import com.syncari.karibu.rest.response.IdResponse;
import com.syncari.karibu.rest.response.ValidListResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import org.apache.commons.collections.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class ResponseUtilHelper {

    String logFileRequesId = "requestId";

    // Extracting requestId from the requestContextHolder.
    public String getRequestId() {
        return MDC.get(logFileRequesId);
    }

    public String getTimestamp() {
        return Instant.now().toString();
    }

    public <T> ValidResponse<T> populateResponse(T object) {

        if(object == null){
            return null;
        }

        if(object.getClass().isAssignableFrom(ErrorDTO.class)){
            return populateErrorResponse(object);
        }

        ValidResponse<T> validationResponse = new ValidResponse<>();
        //List<T> response = new ArrayList<T>();
        //response.add(object);
        //validationResponse.setResult(response);
        validationResponse.setResult(object);
        validationResponse.setRequestId(getRequestId());
        validationResponse.setTimestamp(getTimestamp());
        return validationResponse;
    }

    public <T> ValidResponse<T> populateErrorResponse(T object) {
        ErrorDTO errorDTO = (ErrorDTO) object;
        ValidResponse<T> validationResponse = new ValidResponse<>();
        validationResponse.addResult(object);
        validationResponse.setRequestId(getRequestId());
        validationResponse.setTimestamp(getTimestamp());
        //validationResponse.addError(errorDTO.getMessage(), String.valueOf(errorDTO.getCode()));
        return validationResponse;
    }

    public <T> ValidListResponse<T> populateResponseList(Collection<T> object) {
        if(CollectionUtils.isEmpty(object)){
            return null;
        }
        ValidListResponse<T> validationListResponse = new ValidListResponse<>();
        List<T> response = new ArrayList<T>();
        response.addAll(object);
        validationListResponse.setResult(response);
        validationListResponse.setRequestId(getRequestId());
        validationListResponse.setTimestamp(getTimestamp());
        //validationListResponse.setCursor("placeholder");
        return validationListResponse;
    }

    public ValidResponse<IdResponse> populateIdResponse(String id) {
        ValidResponse<IdResponse> validationResponse = new ValidResponse<>();
        IdResponse idResponse = new IdResponse(id);
        validationResponse.setResult(idResponse);
        validationResponse.setRequestId(getRequestId());
        validationResponse.setTimestamp(getTimestamp());
        return validationResponse;
    }

    // Create a response when the null is being returned. This is called when search result is empty.
    public <T> ValidResponse<T> populateEmptyResponse(){
        ValidResponse<T> validationResponse = new ValidResponse<>();
        validationResponse.addResult(null);
        //validationResponse.addWarning(Errors.NO_DATA_FOUND.getMessage());
        validationResponse.setRequestId(getRequestId());
        validationResponse.setTimestamp(getTimestamp());
        validationResponse.setResult("");
        return validationResponse;
    }

}
