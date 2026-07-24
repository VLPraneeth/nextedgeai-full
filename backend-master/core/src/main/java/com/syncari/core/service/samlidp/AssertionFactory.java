package com.syncari.core.service.samlidp;

import com.syncari.core.model.samlidp.AssertionSigner;
import com.syncari.core.model.samlidp.AuthRequest;
import com.syncari.core.model.samlidp.SAMLAttribute;
import org.joda.time.DateTime;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.core.xml.schema.impl.XSStringBuilder;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.core.impl.*;
import org.opensaml.xmlsec.signature.support.SignatureException;

import java.security.cert.CertificateEncodingException;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

public class AssertionFactory {

    public static Assertion buildAssertion(AuthRequest request, DateTime authenticationTime) throws MarshallingException, SignatureException, CertificateEncodingException {
        Assertion assertion = new AssertionBuilder().buildObject();
        assertion.setID(randomSAMLId());
        assertion.setIssuer(buildIssuer(request));
        assertion.setIssueInstant(authenticationTime);
        assertion.setVersion(SAMLVersion.VERSION_20);
        assertion.getAuthnStatements().add(buildAuthStatement(request, authenticationTime));
        assertion.getAttributeStatements().add(buildAttributeStatement(request));
        assertion.setConditions(buildConditions(request));
        assertion.setSubject(buildSubject(request, authenticationTime));

        AssertionSigner signingFactory = AssertionSigner.createWithCredential(request.getSigningCredential());
        return signingFactory.signAssertion(assertion);
    }

    private static Subject buildSubject(AuthRequest input, DateTime authenticationTime) {
        SubjectConfirmationData confirmationData = new SubjectConfirmationDataBuilder().buildObject();
        confirmationData.setNotBefore(authenticationTime);
        confirmationData.setNotOnOrAfter(authenticationTime.plusMinutes(2));
        confirmationData.setRecipient(input.getAcsEndpoint());

        SubjectConfirmation subjectConfirmation = new SubjectConfirmationBuilder().buildObject();
        subjectConfirmation.setSubjectConfirmationData(confirmationData);
        subjectConfirmation.setMethod(SubjectConfirmation.METHOD_BEARER);

        Subject subject = new SubjectBuilder().buildObject();
        subject.setNameID(buildNameId(input));
        subject.getSubjectConfirmations().add(subjectConfirmation);
        return subject;
    }

    private static NameID buildNameId(AuthRequest input) {
        NameID nameId = new NameIDBuilder().buildObject();
        nameId.setValue(input.getNameId());
        return nameId;
    }

    private static Conditions buildConditions(AuthRequest input) {
        Conditions conditions = new ConditionsBuilder().buildObject();
        Condition condition = new OneTimeUseBuilder().buildObject();
        conditions.getConditions().add(condition);

        AudienceRestriction audienceRestriction = new AudienceRestrictionBuilder().buildObject();

        Audience audience = new AudienceBuilder().buildObject();
        audience.setAudienceURI(input.getAudienceRestriction());

        audienceRestriction.getAudiences().add(audience);

        conditions.getAudienceRestrictions().add(audienceRestriction);
        return conditions;
    }

    private static AttributeStatement buildAttributeStatement(AuthRequest input) {
        AttributeStatementBuilder attributeStatementBuilder =
                (AttributeStatementBuilder) XMLObjectSupport.getBuilder(AttributeStatement.DEFAULT_ELEMENT_NAME);
        AttributeStatement attrStatement = attributeStatementBuilder.buildObject();
        input.getAttributes().stream().map(AssertionFactory::buildAttribute).forEach(attrStatement.getAttributes()::add);
        return attrStatement;
    }

    private static Attribute buildAttribute(SAMLAttribute attr) {
        XSStringBuilder stringBuilder = new XSStringBuilder();

        Attribute attribute = new AttributeBuilder().buildObject();
        attribute.setName(attr.getName());
        //attribute.setNameFormat("urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
        List<XSString> xsStringList = attr.getValues().stream().map(value -> {
            XSString stringValue = stringBuilder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
            stringValue.setValue(value);
            return stringValue;
        }).collect(toList());

        attribute.getAttributeValues().addAll(xsStringList);
        return attribute;
    }

    private static AuthnStatement buildAuthStatement(AuthRequest input, DateTime authenticationTime) {
        AuthnStatement authnStatement = new AuthnStatementBuilder().buildObject();

        authnStatement.setAuthnInstant(authenticationTime);
        authnStatement.setSessionIndex(randomSAMLId());
        authnStatement.setSessionNotOnOrAfter(authenticationTime.plusMinutes(input.getMaxSessionTimeoutInMinutes()));

        AuthnContext authnContext = new AuthnContextBuilder().buildObject();

        AuthnContextClassRef authnContextClassRef = new AuthnContextClassRefBuilder().buildObject();
        authnContextClassRef.setAuthnContextClassRef(AuthnContext.PASSWORD_AUTHN_CTX);

        authnContext.setAuthnContextClassRef(authnContextClassRef);
        authnStatement.setAuthnContext(authnContext);
        return authnStatement;
    }

    private static Issuer buildIssuer(AuthRequest input) {
        Issuer issuer = new IssuerBuilder().buildObject();
        issuer.setValue(input.getIssuer());
        return issuer;
    }

    private static String randomSAMLId() {
        return "_" + UUID.randomUUID().toString();
    }

}
