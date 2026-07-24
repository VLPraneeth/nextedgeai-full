package com.syncari.connector.custom;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.Wither;

@Data
@Accessors(chain = true)
@AllArgsConstructor
public class Watermark {
    long start;
    long end;
    long offset;
    boolean initial;
    @JsonProperty(value="isResync")
    boolean isResync;
    @JsonProperty(value="isTest")
    boolean isTest;
    int limit;
    String cursor;

    public Watermark() {
    }
}
