package com.syncari.api.rest.controllers.data;

import java.util.Date;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DataQualityRuleDTO {
    private String id;
    private String name;
    private List<String> scope;
    private String scopeType;
    private String category;
    private String policy;
    private Boolean isDeleted;

    private Map<String, Object> ruleConfig;

    private Integer passed;
    private Integer failed;
    private Integer total;

    private Date createdAt;
    private Date updatedAt;
    private String createdBy;
    private String updatedBy;
}
