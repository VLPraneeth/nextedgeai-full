package com.syncari.restutils.data;

import java.util.Date;

import com.syncari.core.model.versioning.ActionType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
@SuperBuilder(toBuilder = true)
public class MappingGraphVersionResponseDTO {
	private String versionId;
    private Integer versionNumber;
    private String name;
    protected String createdBy;
    protected Date createdAt;
    protected Integer numberOfChanges;
    private String summary;
    private ActionType actionType;
}
