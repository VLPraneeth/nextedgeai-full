package com.syncari.karibu.rest.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Data
@ToString
public class ErrorType {
    private String message;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected String errorDetail;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected List<String> errorDetails;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected List<Map<String, String>> validationErrors;


    public ErrorType() {

    }

    public ErrorType(String message) {
        this.message = message;
    }

    public ErrorType(String message, String  detail) {
        this.message = message;
        this.errorDetail = detail;
    }

    public ErrorType(String message, List<String> errorDetails) {
        this.message = message;
        this.errorDetails = errorDetails;
    }

    public ErrorType(String message, List<Map<String, String>> validationErrors, boolean showDetails) {
        this.message = message;
        this.validationErrors = validationErrors;
    }

}
