package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.Util;
import com.syncari.api.rest.config.security.SecurityConstants;
import com.syncari.api.rest.controllers.data.ClientRegistrationRequest;
import com.syncari.api.rest.controllers.data.ClientRegistrationResponse;
import com.syncari.api.rest.controllers.data.OauthTokenResponse;
import com.syncari.api.rest.controllers.exceptions.UnauthorizedException;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.UserOAuthDetails;
import com.syncari.core.service.EncryptionService;
import com.syncari.core.service.UserService;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oauth2")
@Slf4j
public class OAuth2Controller {

	private static final long TOKEN_EXPIRY_SECONDS = 60 * 60L;//One hour
	@Autowired
	UserService userService;

	@Autowired
	Util util;

	@Autowired
	EncryptionService encryptionService;

    @RequestMapping(method = RequestMethod.GET, value = "/authorize")
	public void authorize(
			HttpServletRequest request,
			HttpServletResponse response,
			@RequestParam String response_type,
			@RequestParam String client_id,
			@RequestParam String redirect_uri,
			@RequestParam(required = false) String code_challenge,
			@RequestParam(required = false) String code_challenge_method,
			@RequestParam Optional<String> state,
			@RequestParam(required = false) String scope
			) throws IOException {

		log.info("OAuth2 Authorization- Resp Type:{},Client Id:{},redirect:{}," +
						"Challenge:{},Challenge method:{},state:{},scope:{}",
				response_type, client_id, redirect_uri, code_challenge, code_challenge_method, state, scope
		);
		var userId = SyncariContext.getUser().getId();
		var syncariId = SyncariContext.getSyncariId();

		String token = request.getHeader(SecurityConstants.TOKEN_HEADER).replace("Bearer", "");
		String code = util.generateAuthCode(code_challenge, userId, SyncariContext.getInstance().getSyncariId());
		String redirectUrl = redirect_uri + "?code=" + code;
		User user = userService.getUserById(userId);
		UserOAuthDetails oauthDetails = user.getOauthDetails(code).orElseGet(UserOAuthDetails::new);

		oauthDetails.setClientId(client_id).setInstanceId(syncariId).setAuthorizationCode(code)
				.setRedirectURL(redirect_uri).setScope(scope);

		userService.updateUserOAuthDetails(user, oauthDetails);
		if (state.isPresent()) {
			redirectUrl = redirectUrl + "&state=" + state.get();
		}

		String responseUrl = String.format("/arcade/api/v1/oauth2/consent?redirect_uri=%s", redirectUrl);

		response.setHeader("Location", responseUrl);
		response.setContentType("text/html");
		response.setStatus(302);
	}

	@RequestMapping(method = RequestMethod.GET, value = "/consent")
	public void consent(
			HttpServletResponse response,
			@RequestParam String redirect_uri
	) {
		response.setHeader("Location", redirect_uri);
		response.setContentType("text/html");
		response.setStatus(302);
	}

	@RequestMapping(method = RequestMethod.POST, value = "/register")
	public ClientRegistrationResponse register(@RequestBody ClientRegistrationRequest clientRegistrationRequest) {
		log.info("Dynamic Registration- {}", clientRegistrationRequest);
		return new ClientRegistrationResponse()
				.setClient_id(UUID.randomUUID().toString())
				.setClient_secret(UUID.randomUUID().toString())
				.setClient_secret_expires(0)
				.setRedirect_uris(clientRegistrationRequest.getRedirect_uris());
	}

	@RequestMapping(method = RequestMethod.POST, value = "/token")
	public ResponseEntity<OauthTokenResponse> token(HttpServletRequest request, @RequestParam(required = false) String grant_type,
													@RequestParam(required = false) Optional<String> code,
													@RequestParam(required = false) Optional<String> redirect_uri,
													@RequestParam(required = false) Optional<String> client_id,
													@RequestParam(required = false) Optional<String> code_verifier,
													@RequestParam(required = false) Optional<String> refresh_token,
													@RequestParam(required = false) Optional<String> state
									) {

		//https://www.oauth.com/oauth2-servers/access-tokens/access-token-response/
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.set("Cache-Control", "no-store");

		// Check grant type and route to appropriate flow
		log.info("Grant Type: {}, Code:{},Redirect:{},client_id:{},code_verifier:{},refresh_token:{},state:{}",
				grant_type, code.orElse(null), redirect_uri.orElse(null),
				client_id.orElse(null), code_verifier.orElse(null),
				refresh_token.orElse(null), state.orElse(null)
		);
		if ("authorization_code".equals(grant_type)) {
			// Authorization code flow
			if (!code.isPresent() || !redirect_uri.isPresent() || !code_verifier.isPresent()) {
				throw new UnauthorizedException(I18n.i18n("missing_required_params_for_auth_code"));
			}
			
			Map<String,String> userClaim = util.parseAuthCode(code.get());

			String expectedChallenge = base64url(sha256(code_verifier.get()));
			if (!expectedChallenge.equals(userClaim.get("coding_challenge"))) {
				log.warn("Failed Challenge expected:{}, got:{}", expectedChallenge, userClaim.get("coding_challenge"));
				throw new UnauthorizedException(I18n.i18n("invalid_challenge"));
			}

			String userId = userClaim.get("userId");
			String instanceId = userClaim.get("instanceId");
			
			// Generate a refresh token (UUID)
			String refreshToken = UUID.randomUUID().toString();
			// Store refresh token in UserOAuthDetails
			User user = userService.getUserById(userId);
			UserOAuthDetails oauthDetails = user.getOauthDetails(code.get())
					.orElseThrow(() -> new UnauthorizedException("invalid_authorization_code"));
			oauthDetails
				.setRefreshToken(refreshToken)
					.setInstanceId(instanceId);
			code.ifPresent(oauthDetails::setAuthorizationCode);
			client_id.ifPresent(oauthDetails::setClientId);

			// Save the OAuth details
			userService.updateUserOAuthDetails(user, oauthDetails);
			return ResponseEntity.ok()
					.headers(responseHeaders)
					.body(util.getAccessTokenResponse(user, refreshToken, instanceId, TOKEN_EXPIRY_SECONDS, oauthDetails.getScope()));
		}
		else if ("refresh_token".equals(grant_type)) {
			// Refresh token flow
			if (!refresh_token.isPresent()) {
				throw new UnauthorizedException(I18n.i18n("missing_refresh_token"));
			}

			String tokenUUID = refresh_token.get();
			// Lookup user by refresh token

			return client_id.flatMap(c -> userService.getUserOauthDetailByClientIdAndRefreshToken(c, tokenUUID))
					.map(o -> ResponseEntity.ok().headers(responseHeaders).body(
							util.getAccessTokenResponse(o.x, tokenUUID, o.y.getInstanceId(), TOKEN_EXPIRY_SECONDS, o.y.getScope()))
					).orElseThrow(() -> new UnauthorizedException(I18n.i18n("invalid_refresh_token")));
		}
		else {
			throw new UnauthorizedException(I18n.i18n("unsupported_grant_type"));
		}
	}

	public static String base64url(byte[] byteArray) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(byteArray);
	}

	public static byte[] sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digest.digest(input.getBytes("UTF-8"));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
