package com.syncari.core.model.insights;

import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;

@Data
@Deprecated
public class DataCardAuthorConfig extends UUIDAuditModel {

    String dashboardId;
    String datacardId;
    DataCardSetting dataCardSetting;
}
