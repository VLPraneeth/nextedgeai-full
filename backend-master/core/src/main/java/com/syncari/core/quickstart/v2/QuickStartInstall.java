package com.syncari.core.quickstart.v2;

import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class QuickStartInstall extends UUIDAuditModel {

    ZonedDateTime installedAt;
    String installedBy;
    Status status = Status.INPROGRESS;
    String jobQueueId;
    String errorMsg;
    QuickStart quickStart;
    List<QSInstallConfig> installConfigs = new ArrayList<>();

    public enum Status {
        INPROGRESS, // for intermediate save
        QUEUED,
        PROCESSING,
        SUCCESS,
        ERROR
    }

}
