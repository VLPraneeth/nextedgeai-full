package com.syncari.api.rest.controllers.data;

import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WebhookTestResponse {
	@Data
    @Accessors(chain = true)
    public static class Request {
        private String body;
        private String schema;
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
    private List<Map<String, Object>> records;
}
