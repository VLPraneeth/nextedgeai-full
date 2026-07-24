package com.syncari.core.model;

import lombok.Data;

import java.util.List;

@Data
public class ProvisioningResponse {

    private Organization organization;
    private List<String> messages;
}
