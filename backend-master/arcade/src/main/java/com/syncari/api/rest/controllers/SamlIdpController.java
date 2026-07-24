package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.exceptions.UnauthorizedException;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.User;
import com.syncari.core.model.samlidp.IdpConfiguration;
import com.syncari.core.model.samlidp.ServiceProviderMetadata;
import com.syncari.core.service.samlidp.SAMLIdpService;
import com.syncari.core.service.samlidp.ServiceProviderMetadataResolver;
import com.syncari.utils.I18n;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.SAMLObjectBuilder;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPRedirectDeflateEncoder;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.ecp.impl.ResponseMarshaller;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.Endpoint;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Element;
//import org.opensaml.saml.saml2.core.


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/saml/sso")
@Setter
public class SamlIdpController {

    private final String SYNCARI_IDP_ENTITYID = "https://app.syncari.com/idp";

    @Autowired
    SAMLIdpService samlIdpService;

    @Autowired
    ServiceProviderMetadataResolver serviceProviderMetadataResolver;

    @Autowired
    AppConfig appConfig;

    @RequestMapping(method = RequestMethod.GET, value = "/workramp/internal")
    public void workrampInternal(HttpServletRequest request, HttpServletResponse response) {

        log.info("Metadata descriptor for Workramp internal {}", appConfig.getWorkRampSamlInternalMetadata());
        ServiceProviderMetadata spMetadata = serviceProviderMetadataResolver.entityDescriptor(appConfig.getWorkRampSamlInternalMetadata(), appConfig.getWorkRampSamlInternalMetadata());
        IdpConfiguration configuration = buildIdpConfiguration(appConfig.workrampPrivateKey, appConfig.workrampCertificate);
        authAndRespond(configuration, spMetadata, response);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/workramp/academies")
    public void academiesSSO(HttpServletRequest request, HttpServletResponse response) {

        log.info("Metadata descriptor for Workramp academies {}", appConfig.getWorkRampSamlAcademiesMetadata());
        ServiceProviderMetadata spMetadata = serviceProviderMetadataResolver.entityDescriptor(appConfig.getWorkRampSamlAcademiesMetadata(), appConfig.getWorkRampSamlAcademiesMetadata());
        IdpConfiguration configuration = buildIdpConfiguration(appConfig.academiesPrivateKey, appConfig.academiesCertificate);
        authAndRespond(configuration, spMetadata, response);
    }

    private IdpConfiguration buildIdpConfiguration(String privateKey, String certificate) {
        return new IdpConfiguration(SYNCARI_IDP_ENTITYID, SYNCARI_IDP_ENTITYID,
                privateKey, certificate);
    }

    private void authAndRespond(IdpConfiguration idpConfiguration, ServiceProviderMetadata spMetadata, HttpServletResponse response) {
        try {
            Response samlResponse = samlIdpService.authenticate(spMetadata, idpConfiguration);
            MessageContext<SAMLObject> messageContext = new MessageContext<>();
            messageContext.setMessage(samlResponse);

            XMLObjectBuilderFactory builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();

            SAMLObjectBuilder<Endpoint> endpointBuilder =
                    (SAMLObjectBuilder<Endpoint>) builderFactory.getBuilder(AssertionConsumerService.DEFAULT_ELEMENT_NAME);

            Endpoint samlEndpoint = endpointBuilder.buildObject();
            samlEndpoint.setLocation(spMetadata.getAssertionConsumerService().getLocation());
            SAMLBindingSupport.setRelayState(messageContext, "");
            SAMLPeerEntityContext peerEntityContext = messageContext.getSubcontext(SAMLPeerEntityContext.class, true);
            SAMLEndpointContext endpointContext = peerEntityContext.getSubcontext(SAMLEndpointContext.class, true);
            endpointContext.setEndpoint(samlEndpoint);

            HTTPPostEncoder httpPostEncoder = new HTTPPostEncoder();
            VelocityEngine engine = new VelocityEngine();
            Properties properties = new Properties();
            properties.setProperty("resource.loader", "file");
            properties.setProperty("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
            engine.init(properties);
            engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "file");

            httpPostEncoder.setVelocityEngine(engine);
            httpPostEncoder.setVelocityTemplateId("templates/saml2-post-binding.vm");
            httpPostEncoder.setMessageContext(messageContext);
            httpPostEncoder.setHttpServletResponse(response);
            httpPostEncoder.initialize();
            httpPostEncoder.encode();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UnauthorizedException(I18n.i18n("saml_user_authentication_failed"));
        }
    }

}
