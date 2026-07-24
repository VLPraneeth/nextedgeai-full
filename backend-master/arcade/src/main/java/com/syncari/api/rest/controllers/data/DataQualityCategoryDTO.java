package com.syncari.api.rest.controllers.data;

import lombok.Data;
import lombok.experimental.Accessors;
import java.util.Date;

@Data
@Accessors(chain = true)
public class DataQualityCategoryDTO {
    private String id;
    private String name;
    private String type;
    private Date createdAt;
    private Date updatedAt;
    private String createdBy;
    private String updatedBy;
}
