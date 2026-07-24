package com.syncari.core.actions.http;

import com.syncari.core.actions.ActionTestResult;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;

@Data
@Accessors(chain = true)
public class HTTPActionTestResult implements ActionTestResult {

    @Data
    @Accessors(chain = true)
    public static class Request {
        private HttpHeaders requestHeaders;
        private String body;
        private String endpoint;
        private HttpMethod method;
    }

    @Data
    @Accessors(chain = true)
    public static class Response {
        private HttpStatus httpStatus;
        private HttpHeaders responseHeaders;
        private String body;
    }

    private Request request;
    private Response response;
}
