package com.syncari.core.model;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensaml.security.x509.BasicX509Credential;

import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Data
@Accessors(chain = true)
public class SSOAuthConfig {
    private SSOAuthProvider provider;

    // SAML config
    private String entityId;
    private String ssoUrl;
    private String x509Key;

    public void validate(){
        validateCondition(provider == null, i18n("invalid_sso_config_property", "SSO Provider"));
        validateCondition(StringUtils.isBlank(ssoUrl), i18n("invalid_sso_config_property", "SSO Url"));
        validateCondition(StringUtils.isBlank(entityId), i18n("invalid_sso_config_property", "Entity ID"));
        validateCondition(StringUtils.isBlank(x509Key), i18n("invalid_sso_config_property", "X509 Key"));
        try{
            getX509Credential(x509Key);
        } catch (Exception e){
            throw new RuntimeException(i18n("invalid_sso_config_property", "X509 Key"));
        }
    }

    public BasicX509Credential getX509Credential(String x509Key) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) factory.generateCertificate(IOUtils.toInputStream(x509Key));
            return new BasicX509Credential(cert);
        } catch (CertificateException e){
            log.error("Unable to convert x509 key to credential");
            throw new RuntimeException("Invalid x509 key to parse", e);
        }
    }
}
