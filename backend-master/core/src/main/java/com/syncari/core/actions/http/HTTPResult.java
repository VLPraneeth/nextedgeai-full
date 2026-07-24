package com.syncari.core.actions.http;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.util.MultiValueMap;

@Data
@Accessors(chain = true)
public class HTTPResult {
    private MultiValueMap<String, String> headers;
    private String bodyString;
    private Object body;
    private int statusCode;
}
