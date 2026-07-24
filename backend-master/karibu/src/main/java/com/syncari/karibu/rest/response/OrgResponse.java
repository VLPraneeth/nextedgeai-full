package com.syncari.karibu.rest.response;

import lombok.Data;

import java.util.List;

@Data
public class OrgResponse {
    private String id;
    private String name;
    private List<? extends InstanceResponse> instances;
    private List<String> errorMessages;
}
