package com.syncari.core.service.samlidp;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.samlidp.IdpConfiguration;
import com.syncari.core.model.samlidp.ServiceProviderMetadata;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.joda.time.DateTime;
import org.junit.Test;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.Assert.*;

public class SAMLIdpServiceTest extends AbstractSyncariTest {

    @Autowired
    ServiceProviderMetadataResolver serviceProviderMetadataResolver;

    @Autowired
    SAMLIdpService samlIdpService;

    @Autowired
    AppConfig appConfig;

    @Test
    public void testWorkrampInternalSSO() throws Exception {
        Response response = getWorkrampSAMLAssertion(appConfig.workRampSamlInternalMetadata, appConfig.workrampPrivateKey, appConfig.workrampCertificate);
        assertTrue(response.getAssertions().size() > 0);
        assertEquals(response.getAssertions().get(0).getIssuer().getValue(), "https://app.syncari.com/idp");
        assertEquals(response.getDestination(), "https://app.workramp.com/saml/consume");
    }

    @Test
    public void testWorkrampAcademiesSSO() throws Exception {
        Response response = getWorkrampSAMLAssertion(appConfig.workRampSamlAcademiesMetadata, appConfig.academiesPrivateKey, appConfig.academiesCertificate);
        assertTrue(response.getAssertions().size() > 0);
        assertEquals(response.getAssertions().get(0).getIssuer().getValue(), "https://app.syncari.com/idp");
        assertEquals(response.getDestination(), "https://academy.syncari.com/saml/consume");
        DateTime sessionNotOrAfter = response.getAssertions().get(0).getAuthnStatements().get(0).getSessionNotOnOrAfter();
        DateTime authTime = response.getAssertions().get(0).getAuthnStatements().get(0).getAuthnInstant();
        assertEquals(sessionNotOrAfter.getMillis(), authTime.plusMinutes(1).getMillis());
    }

    public Response getWorkrampSAMLAssertion(String samlSpMetadata, String privateKey, String certificate) throws Exception {
        ServiceProviderMetadata serviceProviderMetadata = serviceProviderMetadataResolver.entityDescriptor(
                samlSpMetadata, samlSpMetadata);

        IdpConfiguration idpConfigurationAcademies = new IdpConfiguration("https://app.syncari.com/idp", "https://app.syncari.com/idp",
                privateKey, certificate);
        return samlIdpService.authenticate(serviceProviderMetadata, idpConfigurationAcademies);
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidPrivateKey() throws Exception {
        getWorkrampSAMLAssertion(appConfig.workRampSamlInternalMetadata, "adsdaeffssfsf", appConfig.workrampCertificate);
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidCertificate() throws Exception {
        getWorkrampSAMLAssertion(appConfig.workRampSamlInternalMetadata, appConfig.workrampPrivateKey, "ddsdasadsad");
    }
}
