package com.syncari.core.model.misc;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class UserOAuthDetails {

    private String refreshToken;
    private String clientId;
    private String instanceId;
    private String authorizationCode;
    private String scope;
    private String redirectURL;
    private Instant lastUsed;
    private Instant createdAt;
    private Instant updatedAt;
}
