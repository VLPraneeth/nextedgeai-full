package com.syncari.api.rest.controllers.data;


import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@ToString(callSuper=true)
@Accessors(chain = true)
public class OauthTokenResponse {
    String access_token;
    String refresh_token;
    String token_type;
    Long expires_in;
    String scope;

    public OauthTokenResponse(String accessToken, String refreshToken, Long expiresIn, String tokenType, String scope) {
        this.access_token = accessToken;
        this.refresh_token = refreshToken;
        this.expires_in = expiresIn;
        this.token_type = tokenType;
        this.scope = scope;
    }
}
