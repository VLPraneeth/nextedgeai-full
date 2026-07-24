package com.syncari.api.rest.controllers.data;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)

public class ClientRegistrationResponse {
    private String client_id;
    private String client_secret;
    private long client_secret_expires;
    private List<String> redirect_uris;
}
