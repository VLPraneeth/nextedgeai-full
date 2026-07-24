package com.syncari.core.service;


import com.amazonaws.util.StringInputStream;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.core.model.SSOAuthConfig;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.EncryptedAssertion;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;

@Slf4j
@Component
public class SamlService {

    @Autowired
    EncryptionService encryptionService;

    @PostConstruct
    public void init() {
        try {
            InitializationService.initialize();
        } catch (Exception e) {
            log.error("Error Initializing SAMLService: ", e);
        }
    }

    public Response getSamlResponse(String samlResponseString)
            throws UnsupportedEncodingException, UnmarshallingException, XMLParserException {
        Response response = (Response) XMLObjectSupport.unmarshallFromInputStream(
                XMLObjectProviderRegistrySupport.getParserPool(), new
                        StringInputStream(samlResponseString));
        return response;
    }

    public Assertion getSamlAssertion(Response response) {
        return response.getAssertions().get(0);
    }

    public EncryptedAssertion getEncryptedSamlAssertion(Response response) {
        return response.getEncryptedAssertions().get(0);
    }

    public boolean hasEncryptedSamlAssertion(Response response){
        return !response.getEncryptedAssertions().isEmpty();
    }

    public Assertion decryptAssertion(EncryptedAssertion encrypted) {
        // TODO: decrypt assertion
        throw new NotSupportedException("Encrypted Assertions are not supported yet");
    }

    public void validateSignature(Assertion assertion, SSOAuthConfig ssoConfig) {

        String decryptedx509Key = encryptionService.decrypt(ssoConfig.getX509Key());
        Credential x509Cred = ssoConfig.getX509Credential(decryptedx509Key);
        SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
        try {
            profileValidator.validate(assertion.getSignature());
            SignatureValidator.validate(assertion.getSignature(), x509Cred);
        } catch (SignatureException e) {
            log.error("Error validating signature in assertion", e);
            throw new RuntimeException("Error Validating Assertion Signature");
        }

    }

}
