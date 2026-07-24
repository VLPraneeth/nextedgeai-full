package com.syncari.core.cloudfunctions;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SyncariCloudFunctionStatus {

    public static enum CODE {
        DEPLOY_IN_PROGRESS,
        ACTIVE,
        ERROR,
        DELETE_IN_PROGRESS
    }
    private CODE code;
    private String errorStatusMessage;

    public SyncariCloudFunctionStatus(CODE code, String errorStatusMessage) {
        this.code = code;
        this.errorStatusMessage = errorStatusMessage;
    }
}
