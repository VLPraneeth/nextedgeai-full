package com.syncari.restutils.data;

import java.util.Date;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.util.Status;
import lombok.Data;

import java.util.List;

@Data
public class InstanceResponse {
    private String name;
    private String displayName;
    private String syncariId;
    private InstanceType type;
    private Status status;
    private String planName;
    private String orgId;
    private String orgName;
    private List<String> features;
    private String createdBy;
    private Date createdAt;
    private String deletedBy;
    private Date deletedAt;
}
