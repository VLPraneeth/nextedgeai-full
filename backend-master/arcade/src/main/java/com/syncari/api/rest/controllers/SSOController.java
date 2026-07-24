package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.READ_SSO;
import static com.syncari.core.security.Permissions.WRITE_SSO;
import static com.syncari.core.utils.ValidationUtils.validateCondition;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;

import com.syncari.api.rest.config.security.TokenAttributes;
import com.syncari.api.rest.controllers.exceptions.BadRequestException;
import com.syncari.core.model.Instance;
import com.syncari.utils.TextUtil;
import org.apache.commons.lang3.StringUtils;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.syncari.core.service.authz.AuthzService;
import com.syncari.api.core.util.SSOConfigTransformer;
import com.syncari.api.core.util.Util;
import com.syncari.api.rest.config.security.SecurityConstants;
import com.syncari.api.rest.controllers.data.SSOAuthConfigDTO;
import com.syncari.api.rest.controllers.exceptions.UnauthorizedException;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.Organization;
import com.syncari.core.model.SSOAuthConfig;
import com.syncari.core.model.User;
import com.syncari.core.service.SamlService;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import com.syncari.utils.I18n;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.xml.XMLParserException;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/sso")
@Setter
public class SSOController {

   /* Commenting these to keep values copy when we are testing next time
    private static final String SUPPORT_URL = "https://syncari.zendesk.com/";
    private static final String SUPPORT_URL_ALIAS = "https://support.syncari.com/";
    private static final String ZENDESK_REDIRECT_URL = SUPPORT_URL + "access/jwt?return_to=%s";
    private static final String SUPPORT_DEV_URL = "https://d3v-syncari.zendesk.com";
    private static final String SUPPORT_DEV_URL_ALIAS = "https://d3v-syncari.zendesk.com/";
    private static final String ZENDESK_DEV_REDIRECT_URL = SUPPORT_DEV_URL_ALIAS + "access/jwt?return_to=%s";*/
    private static final String ZENDESK_REDIRECT_SUFFIX = "/access/jwt?return_to=%s";


    @Autowired
    Util util;

    @Autowired
    SamlService samlService;

    @Autowired
    UserService userService;

    @Autowired
    AuthzService authzService;

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    SyncariContextHandler syncariContextHandler;

    @Autowired
    SSOConfigTransformer ssoConfigTransformer;
    
    @Autowired
    AppConfig appConfig;


    @Secured(WRITE_SSO)
    @RequestMapping(method = RequestMethod.POST, value = "/{orgId}")
    public SSOAuthConfigDTO updateSSOConfig(@PathVariable("orgId") String orgId, @RequestBody SSOAuthConfigDTO ssoConfig) {
        validateCondition(!SyncariContext.getOrganziation().getId().equals(orgId), I18n.i18n("invalid_org"));
        SSOAuthConfig ssoAuthConfig = subscriptionService.updateSSOForOrg(SyncariContext.getOrganziation(), ssoConfigTransformer.toSSOAuthConfig(ssoConfig));
        return ssoConfigTransformer.toSSOAuthConfigDTO(ssoAuthConfig);
    }
    
    @Secured(WRITE_SSO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{orgId}")
    public void disable(@PathVariable("orgId") String orgId) {
    	validateCondition(!SyncariContext.getOrganziation().getId().equals(orgId), I18n.i18n("invalid_org"));
    	subscriptionService.disableSSO(SyncariContext.getOrganziation());
    }

    @Secured(READ_SSO)
    @RequestMapping(method = RequestMethod.GET, value = "/{orgId}")
    public SSOAuthConfigDTO getSSOConfig(@PathVariable("orgId") String orgId) {
        validateCondition(!SyncariContext.getOrganziation().getId().equals(orgId), I18n.i18n("invalid_org"));
        return ssoConfigTransformer.toSSOAuthConfigDTO(SyncariContext.getOrganziation().getSsoConfig());
    }
    
    @RequestMapping(method = RequestMethod.GET, value = "/zendesk")
    public void zendeskSso(HttpServletResponse response, @RequestParam(required = false) String return_to) {
        String token = util.generateZendeskJwtToken(appConfig.getZendeskSSOSecret());
        String supportUrlAlias = appConfig.getZendeskSupportUrlAlias();
        String supportUrl = appConfig.getZendeskSupportUrl();
        String redirectUrlTemplate = supportUrl+ZENDESK_REDIRECT_SUFFIX;

        if (return_to != null) {
            if(return_to.startsWith(supportUrl) || return_to.startsWith(supportUrlAlias)){
                String redirectUrl = String.format(redirectUrlTemplate, return_to);
                response.setHeader("Location", redirectUrl);
                response.setHeader("x-jwt-token", token);
                response.setHeader("x-redirect-method", "POST");
                response.setStatus(302);
                return;
            }
        }
        // Not a valid return to, hence redirect user to home page
        log.error("invalid support url {}", return_to);
        response.setHeader("Location", appConfig.getSpectrumServerHost());
        return;
    }

    @RequestMapping(method = RequestMethod.POST, value = "/saml/{orgId}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void samlAuthentication(@PathVariable String orgId, HttpServletRequest request, HttpServletResponse response) throws IOException {

        try {
            String samlResponseString = request.getParameter("SAMLResponse");
            validateCondition(StringUtils.isBlank(samlResponseString), "Empty SAML response");
            Organization org = subscriptionService.getOrgById(orgId)
                    .orElseThrow(() -> new NotFoundException(Organization.class, "id", orgId));
            // validate if sso is enabled for the org
            validateCondition(!org.isSSOEnabled(),
                    "Org %s with id %s doesn't have SSO enabled for authentication", org.getName(), org.getId());

            String username = validateAndExtractUsername(samlResponseString, org.getSsoConfig());
            // validate if email provide is valid email
            if (!TextUtil.isValidEmail(username)){
                log.error("Username {} provided is not valid email", username);
                throw new BadRequestException("Invalid username {} provided in saml response, it is not a valid email address", username);
            }
            // validate if user belongs to the org provided in request
            User user = userService.findActiveUserByEmail(username)
                    .orElseThrow(() -> {
                        log.error("Username {} provided does not exists in syncari", username);
                        throw new NotFoundException(User.class, "email", username);
                    });

            Set<String> availableInstances =  user.getAvailableInstances();
            // validate if the user's available instances contains this syncariid
            boolean hasAccess = org.getInstances().stream().anyMatch(instance -> instance.getSyncariId().equals(user.getCurrentInstanceId()));
            boolean hasOrgAccess = org.getInstances().stream().anyMatch(instance -> availableInstances.contains(instance.getSyncariId()));
            validateCondition(!hasAccess && !hasOrgAccess, "User %s doesn't belong to Org %s", user.getId(), org.getName());
            if (!hasAccess && hasOrgAccess){
                Set<Instance> setOfInstances =  org.getInstances().stream().filter(i -> availableInstances.contains(i.getSyncariId())).collect(Collectors.toSet());
                syncariContextHandler.setContext(setOfInstances.stream().findFirst().get().getSyncariId());
            }else{
                syncariContextHandler.setContext(user.getCurrentInstanceId());
            }
            util.setInsightsProviderContext(SyncariContext.getInstance());
            var permissions = authzService.listPrivileges(username).collect(Collectors.toList());
            Optional<String> previousToken = Optional.ofNullable(request.getHeader(SecurityConstants.TOKEN_HEADER));
            String tokenUUID = previousToken.map(prevToken -> {
                TokenAttributes attribs = util.parseTokenExpiredOrNot(prevToken.replace("Bearer ", ""));
                if (null != attribs.getTokenId()){
                    return attribs.getTokenId();
                }else{
                    return UUID.randomUUID().toString();
                }}).orElse(UUID.randomUUID().toString());
            String token = util.getTokenAndPersistLoginDetails(user,permissions, false,SyncariContext.getInstance().getSyncariId(),tokenUUID);
            response.addHeader(SecurityConstants.TOKEN_HEADER, SecurityConstants.TOKEN_PREFIX + token);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new UnauthorizedException(I18n.i18n("sso_user_authentication_failed"));
        }
    }

    private String validateAndExtractUsername(String samlResponseString, SSOAuthConfig ssoConfig)
            throws UnsupportedEncodingException, XMLParserException, UnmarshallingException {
        // decode Base64 response string to extract xmlString
        byte[] base64Decoded = DatatypeConverter.parseBase64Binary(samlResponseString);
        String xmlSamlString = new String(base64Decoded);
        Response samlResponse = samlService.getSamlResponse(xmlSamlString);

        Assertion assertion;
        if(samlService.hasEncryptedSamlAssertion(samlResponse)){
            assertion = samlService.decryptAssertion(samlService.getEncryptedSamlAssertion(samlResponse));
        } else{
            assertion = samlService.getSamlAssertion(samlResponse);
        }

        samlService.validateSignature(assertion, ssoConfig);
        return assertion.getSubject().getNameID().getValue();
    }
    
    private static String encode(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException ignore) {
            log.error("UTF-8 is not supported!");
            return url;
        }
    }
    
    private String base64Encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
