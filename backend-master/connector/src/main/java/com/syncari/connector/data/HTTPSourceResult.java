package com.syncari.connector.data;

import java.time.ZonedDateTime;

import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HTTPSourceResult {
    private HttpHeaders headers;
    private String bodyString;
    private JsonNode body;
    private int statusCode;
    private String status;
    private ZonedDateTime calledAt;
    private HttpHeaders requestHeaders;
}
