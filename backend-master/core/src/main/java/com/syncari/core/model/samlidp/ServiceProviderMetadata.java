package com.syncari.core.model.samlidp;

import com.google.common.base.Stopwatch;
import lombok.Data;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.NameIDFormat;
import org.springframework.util.StopWatch;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ServiceProviderMetadata {
    private String serviceProviderEntityID;
    private AssertionConsumerService assertionConsumerService;
    private NameIDFormat nameIDFormat;

    public ServiceProviderMetadata(String serviceProviderEntityID, AssertionConsumerService assertionConsumerService, NameIDFormat nameIDFormat) {
        this.serviceProviderEntityID = serviceProviderEntityID;
        this.assertionConsumerService = assertionConsumerService;
        this.nameIDFormat = nameIDFormat;
    }
}
