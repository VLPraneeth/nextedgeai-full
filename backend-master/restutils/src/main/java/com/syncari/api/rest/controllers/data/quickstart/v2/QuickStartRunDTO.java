package com.syncari.api.rest.controllers.data.quickstart.v2;

import com.syncari.utils.KeyValue;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

@Data
public class QuickStartRunDTO {

    String details;
    ZonedDateTime executedAt;
    String executedBy;
    String executedByName;
    Status status;
    String errorMsg;
    List<KeyValue> inputs; // TODO
    String qsType;

    public enum Status {
        INPROGRESS, // for intermediate save
        QUEUED,
        PROCESSING,
        SUCCESS,
        ERROR
    }
}
