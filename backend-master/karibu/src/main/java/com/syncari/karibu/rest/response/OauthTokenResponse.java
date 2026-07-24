package com.syncari.karibu.rest.response;


import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper=true)
public class OauthTokenResponse {
    String access_token;
    String refresh_token;
    String token_type;
    Long expires_in;

    public OauthTokenResponse(String accessToken, String refreshToken, Long expiresIn, String tokenType) {
        this.access_token = accessToken;
        this.refresh_token = refreshToken;
        this.expires_in = expiresIn;
        this.token_type = tokenType;
    }
}
