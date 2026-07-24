package com.syncari.core.model;

import javax.validation.constraints.NotNull;

import com.syncari.core.model.misc.*;

import com.syncari.core.model.misc.fragment.FragmentSharePreference;
import com.syncari.utils.KeyValue;

import lombok.Data;

@Data
public class UserPreference extends UUIDAuditModel {

    @NotNull(message = "User id is required")
    private String userId;
    private DashboardPreference dashboard;
    private GraphPreference entityGraph;
    private GraphPreference connectorGraph;
    private ZoomPreference zoom;
    private SchemaStudioPreference schemaStudio;
    private SyncStudioPreference syncStudio;
    private DataStudioPreference dataStudio;
    private FragmentSharePreference fragmentShare;
    private ErrorNotificationPreference errorNotification;
    private KeyValue customPreference;

    public UserPreference() {}

    public UserPreference(String userId) {
        this.userId = userId;
    }

}
