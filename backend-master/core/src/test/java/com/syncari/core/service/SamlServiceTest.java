package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.SSOAuthConfig;
import com.syncari.core.model.SSOAuthProvider;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.junit.Test;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class SamlServiceTest extends AbstractSyncariTest {

    @Autowired
    SamlService samlService;

    @Autowired
    EncryptionService encryptionService;

    private String samlXmlString;

    @Value("${saml.x509.key}")
    private String x509Key;

    @Override
    public void setUp() {
        super.setUp();
        try {
            samlXmlString = Files.readString(Path.of("src/test/resources/saml/valid_response.xml"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void getValidSamlResponse() throws IOException, XMLParserException, UnmarshallingException {
        Response response = samlService.getSamlResponse(samlXmlString);
        assertNotNull(response);
        assertFalse(response.getAssertions().isEmpty());
        assertNotNull(response.getSignature());
    }

    @Test
    public void getAssertion() throws UnsupportedEncodingException, UnmarshallingException, XMLParserException {
        Response response = samlService.getSamlResponse(samlXmlString);
        Assertion assertion = samlService.getSamlAssertion(response);
        assertNotNull(assertion);
        assertNotNull(assertion.getSignature());
        assertNotNull(assertion.getSubject());
        assertEquals("abhinav@syncari.com", assertion.getSubject().getNameID().getValue());
    }

    @Test
    public void validateSignature() throws UnsupportedEncodingException, UnmarshallingException, XMLParserException {
        String encryptedX509Key = encryptionService.encrypt(x509Key);
        SSOAuthConfig ssoAuthConfig = new SSOAuthConfig().setSsoUrl("http://some_url").setEntityId("http://entityId")
                .setProvider(SSOAuthProvider.OKTA).setX509Key(encryptedX509Key);

        Response response = samlService.getSamlResponse(samlXmlString);
        Assertion assertion = samlService.getSamlAssertion(response);

        samlService.validateSignature(assertion, ssoAuthConfig);

    }

    @Test
    public void validateSignature_Fail() throws UnsupportedEncodingException, UnmarshallingException, XMLParserException {
        String encryptedX509Key = encryptionService.encrypt("INVALID_KEY");
        SSOAuthConfig ssoAuthConfig = new SSOAuthConfig().setSsoUrl("http://some_url").setEntityId("http://entityId")
                .setProvider(SSOAuthProvider.OKTA).setX509Key(encryptedX509Key);

        Response response = samlService.getSamlResponse(samlXmlString);
        Assertion assertion = samlService.getSamlAssertion(response);

        try {
            samlService.validateSignature(assertion, ssoAuthConfig);
            fail();
        } catch (RuntimeException e){
            assertEquals("Invalid x509 key to parse", e.getMessage());
        }

    }
}
