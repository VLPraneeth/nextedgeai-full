package com.syncari.api.rest.controllers.data;

import lombok.Data;

@Data
public class GhostAccessRequest {
    private String userId;
    private String syncariId;
    private String category;
    private String reason;
    private String accessDetails;
    private String duration;
    private String roleId;
}
