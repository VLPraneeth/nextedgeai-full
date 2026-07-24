package com.syncari.core.model.samlidp;

import lombok.Data;
import lombok.Getter;

@Getter
public class IdpConfiguration {
    private String issuer;
    private String entityId;
    private String privateKey;
    private String certificate;

    public IdpConfiguration(String issuer, String entityId, String privateKey, String certificate) {
        this.issuer = issuer;
        this.entityId = entityId;
        this.privateKey = privateKey;
        this.certificate = certificate;
    }

}
