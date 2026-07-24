package com.syncari.api.rest.controllers.data.quickstart.v2;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AsyncProcessingConfirmationDTO {

    private String iconUrl;
    private String message;
    private String description;
    private Type type;

    public enum Type {
        INFO, WARN, ERROR
    }
}


