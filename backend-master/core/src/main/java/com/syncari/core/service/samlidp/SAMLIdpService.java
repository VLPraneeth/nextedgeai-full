package com.syncari.core.service.samlidp;

import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.User;
import com.syncari.core.model.samlidp.AuthRequest;
import com.syncari.core.model.samlidp.IdpConfiguration;
import com.syncari.core.model.samlidp.SAMLAttribute;
import com.syncari.core.model.samlidp.ServiceProviderMetadata;
import lombok.extern.slf4j.Slf4j;
import org.opensaml.core.config.InitializationService;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.BasicX509Credential;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class SAMLIdpService {

    @Autowired
    private AppConfig appConfig;

    @PostConstruct
    public void init() {
        try {
            InitializationService.initialize();
        } catch (Exception e) {
            log.error("Error Initializing SAMLService: ", e);
        }
    }

    public Response authenticate(ServiceProviderMetadata serviceProviderMetadata, IdpConfiguration idpConfiguration) throws Exception {
        User user = SyncariContext.getUser();
        
        AuthRequest authRequest = new AuthRequest(idpConfiguration.getIssuer(), serviceProviderMetadata.getAssertionConsumerService().getLocation(),
                buildAttributes(user), user.getEmail(), buildCredential(idpConfiguration.getCertificate(), idpConfiguration.getPrivateKey()),
                serviceProviderMetadata.getServiceProviderEntityID(), Instant.now());

        return SAMLResponseFactory.buildResponse(authRequest);
    }

    private List<SAMLAttribute> buildAttributes(User user) {
        return List.of(new SAMLAttribute("emailAddress", List.of(user.getEmail())),
                new SAMLAttribute("FirstName", List.of(user.getFirstName())),
                new SAMLAttribute("LastName", List.of(user.getLastName()))
                );
    }

    private Credential buildCredential(String certStr, String privateKey) {

        Credential credential = null;
        try {
            X509Certificate certificate = (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(
                    new ByteArrayInputStream(Base64.getDecoder().decode(certStr.getBytes(StandardCharsets.UTF_8))));

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey.getBytes(StandardCharsets.UTF_8)));
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privKey = kf.generatePrivate(keySpec);

            credential = new BasicX509Credential(certificate, privKey);
        } catch (Exception e) {
            log.error("Invalid Certificate or Private key", e);
            throw new RuntimeException(e);
        }
        return credential;
    }

}
