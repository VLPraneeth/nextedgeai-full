package com.syncari.api.rest.controllers.data;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ClientRegistrationRequest {
    private String client_name;
    private List<String> redirect_uris;
    private List<String> grant_types;
    private List<String> response_types;
    private String token_endpoint_auth_method;
}
