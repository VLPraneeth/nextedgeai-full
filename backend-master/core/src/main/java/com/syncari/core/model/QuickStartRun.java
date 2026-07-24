package com.syncari.core.model;

import com.syncari.core.model.UUIDAuditModel;
import com.syncari.core.quickstart.QuickStartConfig;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.ZonedDateTime;

@Data
@Accessors(chain = true)
public class QuickStartRun extends UUIDAuditModel {

    String runDetail;
    ZonedDateTime executedAt;
    String executedBy;
    Status status = Status.QUEUED;
    String errorMsg;
    QuickStartConfig config;
    String qsType;
    String syncariEntityId;

    public enum Status {
        INPROGRESS, // for intermediate save
        QUEUED,
        PROCESSING,
        SUCCESS,
        ERROR
    }

    public boolean isQueued(){
        return Status.QUEUED.equals(status);
    }

    public boolean isSuccess(){
        return Status.SUCCESS.equals(status);
    }

    public <T extends QuickStartConfig> T getTypedConfiguration(){
        return (T)config;
    }
}
