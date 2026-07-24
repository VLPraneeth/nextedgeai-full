package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.SSOConfigTransformer;
import com.syncari.api.rest.controllers.data.SSOAuthConfigDTO;
import com.syncari.api.rest.controllers.exceptions.UnauthorizedException;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Organization;
import com.syncari.core.model.SSOAuthConfig;
import com.syncari.core.model.SSOAuthProvider;
import com.syncari.core.model.User;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import net.shibboleth.utilities.java.support.codec.HTMLEncoder;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_SSO;
import static com.syncari.core.security.Permissions.WRITE_SSO;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SamlIdpControllerTest extends AbstractSyncariTest {

    @Autowired
    SamlIdpController samlIdpController;

    private static final String FORM_ACTION_PREFIX = "<form action=\"";
    private static final String INPUT_SAML_RESPONSE_PREFIX = "<input type=\"hidden\" name=\"SAMLResponse\" value=\"";

    @Test
    public void testWorkrampInternalSSO() throws IOException {

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        samlIdpController.workrampInternal(request, response);
        String consumeUrl = HTMLEncoder.encodeForHTMLAttribute("https://app.workramp.com/saml/consume");
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContentAsString().contains(FORM_ACTION_PREFIX + consumeUrl));
    }

    @Test
    public void testWorkrampAcademiesSSO() throws IOException {

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        samlIdpController.academiesSSO(request, response);
        String consumeUrl = HTMLEncoder.encodeForHTMLAttribute("https://academy.syncari.com/saml/consume");
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContentAsString().contains(FORM_ACTION_PREFIX + consumeUrl));
    }


}
