package com.syncari.core.service.samlidp;

import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.Criterion;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.FilesystemMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.NameIDFormat;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import com.syncari.core.model.samlidp.ServiceProviderMetadata;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;
import java.util.Timer;
import java.util.UUID;

@Slf4j
@Component
public class ServiceProviderMetadataResolver {

    private static final String SUPPORTED_SAML_PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";

    @PostConstruct
    public void initialization() {
        try {
            InitializationService.initialize();
        } catch (InitializationException e) {
            log.error("SAML Initialization failed");
        }
    }

    public ServiceProviderMetadata entityDescriptor(String metadataUrl, String entityId) {

        ServiceProviderMetadata serviceProviderMetadata = null;
        try {
            HttpClient client = HttpClients.createDefault();

            HTTPMetadataResolver resolver = new HTTPMetadataResolver(client, metadataUrl);

            resolver.setRequireValidMetadata(true);

            BasicParserPool parser = new BasicParserPool();
            parser.initialize();

            resolver.setParserPool(parser);
            resolver.setId(UUID.randomUUID().toString());
            resolver.initialize();

            Criterion criterion = new EntityIdCriterion(entityId);
            CriteriaSet set = new CriteriaSet(criterion);
            EntityDescriptor entityDesc = resolver.resolveSingle(set);

            SPSSODescriptor spssoDesc = entityDesc.getSPSSODescriptor(SUPPORTED_SAML_PROTOCOL);

            NameIDFormat nameIDFormat = spssoDesc.getNameIDFormats().get(0);
            AssertionConsumerService assertionConsumerService = spssoDesc.getAssertionConsumerServices().get(0);

            // This is a hack
            if (!assertionConsumerService.getLocation().startsWith("http")) {
                // assume this is a relative path, get host information from metadata URL
                URL metadataURL = new URL(metadataUrl);
                String protocol = metadataURL.getProtocol();
                String authority = metadataURL.getAuthority();
                assertionConsumerService.setLocation(String.format("%s://%s%s", protocol, authority, assertionConsumerService.getLocation()));
            }
            serviceProviderMetadata = new ServiceProviderMetadata(entityId, assertionConsumerService, nameIDFormat);
        } catch (Exception e) {
            log.error("Invalid SAML Metadata location {}", e);
            throw new RuntimeException(e);
        }
        return serviceProviderMetadata;
    }
}
