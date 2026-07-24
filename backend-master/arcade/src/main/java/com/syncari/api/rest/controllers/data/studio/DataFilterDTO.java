package com.syncari.api.rest.controllers.data.studio;

import java.util.Date;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DataFilterDTO {
    private String id;
    private String name;
    private String description;
    private Map criteria;
    private String syncariEntityId;
    private List<String> tags;
    boolean bookmarked;
    private String createdBy;
    private String updatedBy;
    private Date createdAt;
    private Date updatedAt;
}
