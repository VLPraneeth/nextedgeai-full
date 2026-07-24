package com.syncari.api.rest.controllers.data;

import lombok.Data;

import java.util.List;

@Data
public class ResyncRequest {
    List<String> synapseEntityIds;
    String fromDate;
    String toDate;
}
