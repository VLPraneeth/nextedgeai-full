package com.syncari.connector.service.def;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.exception.RetriableException;

public interface AuthenticationService {

    @Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames);

    default void handleAuthenticationErrorMessage(TestConnectionResponse response, Exception e) {
        response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
        String message = TestConnectionResponse.AUTH_FAILED_MESSAGE;
        Set<String> errors = new HashSet<>();
        if (e != null) {
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                errors.add(e.getCause().getMessage());
            } else if (ExceptionUtils.getRootCause(e) != null && ExceptionUtils.getRootCause(e).getMessage() != null) {
                errors.add(ExceptionUtils.getRootCause(e).getMessage());
            } else if (e.getMessage() != null){
                errors.add(e.getMessage());
            }
        }
        extractErrorIfJSON(errors);
        // if no error messages from synapses, just use it
        if (message.equalsIgnoreCase(TestConnectionResponse.AUTH_FAILED_MESSAGE)) {
            message += " " + (StringUtils.isNotBlank(response.getMessage()) ? response.getMessage() : "Please verify credentials and try again.");
        }
        response.setMessage(message);
        if(errors.isEmpty()) {
            response.setErrors(List.of(message));
        }
        else {
            response.setErrors(new ArrayList<>(errors));
        }
    }

    private void extractErrorIfJSON(Set<String> errors) {
        Set<String> errorClone = new HashSet<>(errors);
        for(String error: errorClone) {
            if(isJSONValid(error)) {
                ObjectMapper mapper = new ObjectMapper();
                try {
                    Map<String, Object> map = mapper.readValue(error, Map.class);
                    if(map.containsKey("message") && map.get("message") instanceof String) {
                        errors.remove(error);
                        errors.add((String)map.get("message"));
                    }
                } catch (JsonProcessingException ex) {
                    // noop
                }
            }
        }
    }

    private static boolean isJSONValid(String jsonInString ) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(jsonInString);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

}