package com.syncari.core.model.samlidp;

import lombok.Data;
import org.opensaml.security.credential.Credential;

import java.time.Instant;
import java.util.List;

@Data
public class AuthRequest {
    private String issuer;
    private String acsEndpoint;
    private List<SAMLAttribute> attributes;
    private String nameId;
    private int maxSessionTimeoutInMinutes = 1;
    private Credential signingCredential;
    private String audienceRestriction;
    private Instant authenticationTime;

    public AuthRequest(String issuer, String acsEndpoint, List<SAMLAttribute> attributes, String nameId, Credential signingCredential,
                       String audienceRestriction, Instant authenticationTime) {

        this.issuer = issuer;
        this.acsEndpoint = acsEndpoint;
        this.attributes = attributes;
        this.nameId = nameId;
        this.signingCredential = signingCredential;
        this.audienceRestriction = audienceRestriction;
        this.authenticationTime = authenticationTime;
    }
}
