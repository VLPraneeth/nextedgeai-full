package com.syncari.core.model.insights.provider.ts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TSToken {

    private String token;
    private Double creation_time_in_millis;
    private double expiration_time_in_millis;
    private Scope scope;
    private String valid_for_user_id;
    private String valid_for_username;
}

class Scope{
    private String access_type;
    private String orgId;
    private String metadata_id;
}
