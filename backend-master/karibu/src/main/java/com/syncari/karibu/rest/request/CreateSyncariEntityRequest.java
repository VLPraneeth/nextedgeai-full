package com.syncari.karibu.rest.request;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.util.Status;
import com.syncari.core.schema.EntityType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Accessors(chain = true)
public class CreateSyncariEntityRequest {

    private String displayName;
    private String apiName;
    private String dataStoreName;
    private String description;
    EntityType type=EntityType.standard;
    Set<String> tags = new HashSet<>();
    Status status = Status.ACTIVE;
    private List<FieldRequest> fields;
    String createdBy;
    String updatedBy;
    Date createdAt;
    Date updatedAt;
    boolean isReadonly;
    boolean isChild;
    boolean syncariSource;
}
