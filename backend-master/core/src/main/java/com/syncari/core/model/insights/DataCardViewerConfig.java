package com.syncari.core.model.insights;

import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;

@Data
public class DataCardViewerConfig extends UUIDAuditModel {

    String userId;
    String dashboardId;
    String datacardId;
    DataCardSetting settings; // This will hold viewer configuration
}
