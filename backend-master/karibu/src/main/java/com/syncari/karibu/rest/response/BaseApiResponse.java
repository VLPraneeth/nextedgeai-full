package com.syncari.karibu.rest.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
public class BaseApiResponse implements ModelResponse{
    protected Boolean success = Boolean.TRUE;
    protected String requestId;
    protected String timestamp;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected List<ErrorType> errors = new ArrayList<ErrorType>();
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected ErrorType error;

    public void addError(String message) {
        this.success = false;
        // this.errors.add(new ErrorType(message));
        this.error = new ErrorType(message);
    }
    public void addErrorDetail(String message, String detail) {
        this.success = false;
        // this.errors.add(new ErrorType(message));
        this.error = new ErrorType(message, detail);
    }
    public BaseApiResponse setErrors(List<ErrorType> errors) {
        this.success = false;
        this.errors= errors;
        return this;
    }
    public void addErrorDetails(ErrorType errorType) {
        this.success = false;
        // this.errors.add(new ErrorType(message));
        this.error = errorType;
    }

}
