package com.syncari.api.rest.controllers.data;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
@Data
@Accessors(chain = true)
public class HttpActionResultDTO {

    @Data
    @Accessors(chain = true)
    public static class Request {
        private Map<String, String> requestHeaders;
        private String body;
        private String URL;
        private String method;
    }

    @Data
    @Accessors(chain = true)
    public static class Response {
        private String httpStatusCode;
        private Map<String, String> responseHeaders;
        private String body;
    }

    private Request request;
    private Response response;
}
