package com.syncari.api.rest.controllers.data;

import com.syncari.utils.KeyValue;
import lombok.Data;

@Data
public class ErrorDTO {

    String type;
    String title;
    String body;
    KeyValue details; // any additional details about the error
}
