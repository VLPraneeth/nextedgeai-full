package com.syncari.core.service.samlidp;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.samlidp.ServiceProviderMetadata;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.*;

public class ServiceProviderMetadataResolverTest extends AbstractSyncariTest {

    @Autowired
    ServiceProviderMetadataResolver resolver;

    @Test
    public void testRelativePathResolver() {

        String samlMetadata = "https://academy.syncari.com/saml/metadata";
        String samlEntityId = "https://academy.syncari.com/saml/metadata";

        //resolver = new ServiceProviderMetadataResolver();
        ServiceProviderMetadata metadata = resolver.entityDescriptor(samlMetadata, samlEntityId);
        assertEquals(samlEntityId, metadata.getServiceProviderEntityID());
        assertEquals("https://academy.syncari.com/saml/consume", metadata.getAssertionConsumerService().getLocation());
        assertEquals("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress", metadata.getNameIDFormat().getFormat().trim());
    }

    @Test
    public void testAbsoluteURLResolver() {

        String samlMetadata = "https://app.workramp.com/saml/metadata";
        String samlEntityId = "https://app.workramp.com/saml/metadata";

        ServiceProviderMetadata metadata = resolver.entityDescriptor(samlMetadata, samlEntityId);
        assertEquals("https://app.workramp.com/saml/metadata", metadata.getServiceProviderEntityID());
        assertEquals("https://app.workramp.com/saml/consume", metadata.getAssertionConsumerService().getLocation());
        assertEquals("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress", metadata.getNameIDFormat().getFormat().trim());
    }

}
