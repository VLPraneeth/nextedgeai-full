package com.syncari.karibu.rest.response;

import com.syncari.core.model.Quota;
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
    private String subscriptionName;
    private List<Quota> quota;
}
