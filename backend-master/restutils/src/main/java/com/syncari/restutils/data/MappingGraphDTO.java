package com.syncari.restutils.data;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.PipelineSettings;
import com.syncari.core.model.SyncStream.Status;
import com.syncari.core.model.util.Scope;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Date;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class MappingGraphDTO extends GraphDTO {
    private String id;
    private String targetId;
    private String parentId;
    private Scope scope;
    private String name;
    protected String createdBy;
    protected String updatedBy;
    protected Date createdAt;
    protected Date updatedAt;
    protected Instant lastSyncedTime;
    private Status syncStatus;
    private boolean ready;

    private DraftStatus draftStatus = DraftStatus.NEW;
    private boolean readOnly;
    private String readOnlyReason = "";

    private MappingGraphDTO draft;

    private ResyncDetailDTO resyncDetail;
    private String pausedBy;

    private PipelineSettings settings;

    public boolean hasDraft() {
        return draft != null;
    }


    public MappingGraphDTO setSettings(PipelineSettings settings) {
        this.settings = settings;
        return this;
    }
}
