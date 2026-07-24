package com.syncari.core.model.versioning;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@Accessors(chain = true)
public class Version {

    private String id;
    private Integer versionNumber;
    private String name;
    private String summary;
    private Integer numberOfChanges;
    private ActionType actionType;

    public Version(){

    }
}