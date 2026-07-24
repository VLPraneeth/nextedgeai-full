package com.syncari.core.model;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class WebhookActionResponse implements Serializable {
    private HttpStatus statusCode;
    private String payload;
    private MultiValueMap<String, String> headers;
}
