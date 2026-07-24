package com.syncari.core.model;

import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DataFilter extends UUIDAuditModel {
    @NotNull(message = "Data Filter name is required")
    private String name;
    private String description;
    @NotNull(message = "Search criteria is required")
    private Map criteria;
    @NotNull(message = "Syncari entity id is required")
    private String syncariEntityId;
    private List<String> tags;
}


