package com.syncari.core.model.samlidp;

import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.saml2.core.impl.AssertionMarshaller;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.security.x509.X509Credential;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.X509Certificate;
import org.opensaml.xmlsec.signature.X509Data;
import org.opensaml.xmlsec.signature.impl.KeyInfoBuilder;
import org.opensaml.xmlsec.signature.impl.SignatureBuilder;
import org.opensaml.xmlsec.signature.impl.X509CertificateBuilder;
import org.opensaml.xmlsec.signature.impl.X509DataBuilder;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.Signer;

import java.security.cert.CertificateEncodingException;

public class AssertionSigner {

    private final Credential signingCredential;

    public static AssertionSigner createWithCredential(Credential signingCredential) {
        return new AssertionSigner(signingCredential);
    }

    private AssertionSigner(Credential signingCredential) {
        this.signingCredential = signingCredential;
    }

    public Assertion signAssertion(Assertion assertion) throws MarshallingException, SignatureException, CertificateEncodingException {
        SignatureBuilder builder = new SignatureBuilder();
        Signature signature = builder.buildObject();
        KeyInfo keyInfo=new KeyInfoBuilder().buildObject(KeyInfo.DEFAULT_ELEMENT_NAME);
        X509Data data=new X509DataBuilder().buildObject(X509Data.DEFAULT_ELEMENT_NAME);
        X509Certificate cert= new X509CertificateBuilder().buildObject(X509Certificate.DEFAULT_ELEMENT_NAME);
        cert.setValue(org.apache.xml.security.utils.Base64.encode(((BasicX509Credential)signingCredential).getEntityCertificate().getEncoded()));
        data.getX509Certificates().add(cert);
        keyInfo.getX509Datas().add(data);
        signature.setKeyInfo(keyInfo);
        signature.setSigningCredential(signingCredential);
        signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
        signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

        assertion.setSignature(signature);
        addXmlSignatureInstanceToAssertion(assertion);
        Signer.signObject(signature);
        return assertion;
    }

    private void addXmlSignatureInstanceToAssertion(Assertion assertion) throws MarshallingException {
        AssertionMarshaller marshaller = new AssertionMarshaller();
        marshaller.marshall(assertion);
    }
}
