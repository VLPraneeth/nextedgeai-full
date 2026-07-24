package com.syncari.core.service.samlidp;

import com.syncari.core.model.samlidp.AuthRequest;
import org.joda.time.DateTime;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.impl.IssuerBuilder;
import org.opensaml.saml.saml2.core.impl.ResponseBuilder;
import org.opensaml.saml.saml2.core.impl.StatusBuilder;
import org.opensaml.saml.saml2.core.impl.StatusCodeBuilder;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SAMLResponseFactory {

    static Response buildResponse(AuthRequest request) throws Exception {
        long instant = TimeUnit.SECONDS.toMillis(request.getAuthenticationTime().getEpochSecond());
        DateTime authenticationTime = new DateTime(instant);

        Response response = new ResponseBuilder().buildObject();
        response.setID(randomSAMLId());
        response.setIssueInstant(authenticationTime);
        response.setVersion(SAMLVersion.VERSION_20);
        response.setIssuer(buildIssuer(request));
        response.setDestination(request.getAcsEndpoint());
        response.setStatus(buildStatus());
        response.getAssertions().add(AssertionFactory.buildAssertion(request, authenticationTime));

        return response;
    }

    private static Issuer buildIssuer(AuthRequest input) {
        Issuer issuer = new IssuerBuilder().buildObject();
        issuer.setValue(input.getIssuer());
        return issuer;
    }

    private static Status buildStatus() {
        StatusCode statusCode = new StatusCodeBuilder().buildObject();
        statusCode.setValue(StatusCode.SUCCESS);
        Status status = new StatusBuilder().buildObject();
        status.setStatusCode(statusCode);
        return status;
    }

    public static String randomSAMLId() {
        return "_" + UUID.randomUUID().toString();
    }
}
