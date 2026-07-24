package com.syncari.core.model;


import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain=true)
public class RequeueRequest extends UUIDAuditModel{
    @NotNull
    private String entityDefinitionId;
    @NotNull
    private String recordId;
    @NotNull
    private String graphId;
    @NotNull
    private RecordType recordType = RecordType.SYNCARI;
    @NotNull
    private ZonedDateTime retryTimeLimit;
    //Send emails when retryLimit has reached
    private List<String> emailAddresses = new ArrayList<>();
    //Used when requeing due to a tech failure (
    private String requeueReason;
    private boolean processExpiredRecord;
    public enum RecordType {
        SOURCE, SYNCARI, DESTINATION
    }
    public boolean hasExpired(){
        return retryTimeLimit !=null && ZonedDateTime.now().isAfter(retryTimeLimit);
    }
}
