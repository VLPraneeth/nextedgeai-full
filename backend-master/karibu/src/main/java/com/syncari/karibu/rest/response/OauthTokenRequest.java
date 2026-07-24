package com.syncari.karibu.rest.response;


import lombok.Data;

@Data
public class OauthTokenRequest {
    String grant_type;
    String client_id;
    String client_secret;
    String refresh_token;

}
