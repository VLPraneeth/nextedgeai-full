package com.syncari.api.core.util;

import com.syncari.api.rest.config.security.SecurityConstants;
import com.syncari.api.rest.config.security.TokenAttributes;
import com.syncari.api.rest.controllers.data.OauthTokenResponse;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.Feature;
import com.syncari.core.model.Instance;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.UserLoginDetails;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.EncryptionService;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class Util {
	@Autowired
	AppConfig appConfig;

	@Autowired
	EncryptionService encryptionService;

	@Autowired
	UserService userService;

	@Autowired
	InsightsProviderIntegrator insightsProviderIntegrator;

	@Autowired
	@Qualifier("defaultEmailService")
	EmailService emailService;

	@Autowired
	SubscriptionService subscriptionService;

	public String getCurrentUserName() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication.getName();
	}


	public OauthTokenResponse getAccessTokenResponse(com.syncari.core.model.User user, String refreshToken, String instanceId, long tokenExpirySeconds, String scope) {
		var roles = new ArrayList<>(userService.getUserPermissionsForInstance(user.getId(), subscriptionService.getInstance(instanceId)));
		var accessToken = getToken(user.getEmail(), roles, false, instanceId, refreshToken, tokenExpirySeconds * 1000L);
		return new OauthTokenResponse(accessToken, refreshToken, tokenExpirySeconds, "Bearer", scope);
	}

	public String generateJwtTokenWithSpecifiedRoles(List<String> permissions, boolean isGhost, String tokenId) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		var user = ((UserDetails) authentication.getPrincipal());
		return getToken(user.getUsername(), permissions, isGhost,tokenId);
	}

	public String generateZendeskJwtToken(String secret) {
		User user = SyncariContext.getUser();
		return Jwts.builder()
				.signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
				.setHeaderParam("typ", SecurityConstants.TOKEN_TYPE)
				.claim("name", String.format("%s %s", user.getFirstName(), user.getLastName()))
				.claim("email", user.getEmail())
				.claim("iat", Instant.now().getEpochSecond())
				.claim("jti", UUID.randomUUID().toString())
				.claim("external_id", user.getId())
				.compact();
	}


	public String generateAuthCode(String codingChallenge, String user, String instanceId) {
		var signingKey = appConfig.getJwtSecret().getBytes();
		JwtBuilder claim = Jwts.builder()
				.signWith(Keys.hmacShaKeyFor(signingKey), SignatureAlgorithm.HS256)
				.setHeaderParam("typ", SecurityConstants.TOKEN_TYPE)
				.claim("iat", Instant.now().getEpochSecond())
				.claim("jti", UUID.randomUUID().toString())
				.claim("userId", user)
				.claim("instanceId", instanceId);
		if(!StringUtils.isBlank(codingChallenge)) {
			claim = claim.claim("coding_challenge", codingChallenge);
		}
		return claim.compact();
	}

	public Map<String, String> parseAuthCode(String token) {
		var signingKey = appConfig.getJwtSecret().getBytes();
		var parsedCode = Jwts.parser()
				.setSigningKey(signingKey)
				.parseClaimsJws(token);

		Map<String, String> authCode = new HashMap<>();
		authCode.put("userId", parsedCode.getBody().get("userId").toString());
		authCode.put("coding_challenge", parsedCode.getBody().get("coding_challenge").toString());
		authCode.put("instanceId", parsedCode.getBody().get("instanceId").toString());

		return authCode;
	}



	public String getToken(String username, List<String> roles, boolean isGhost,String tokenUUID){
		return getToken(username, roles, isGhost,SyncariContext.getInstance().getSyncariId(),tokenUUID);
	}

	private String getToken(String username, List<String> roles, boolean isGhost,String syncariId, String tokenUUID){
		return getToken(username, roles, isGhost, syncariId, tokenUUID, SecurityConstants.TOKEN_EXPIRATION);
	}

	private String getToken(String username, List<String> roles, boolean isGhost, String syncariId, String tokenUUID, long tokenExpiryMillis) {
		var signingKey = appConfig.getJwtSecret().getBytes();
		return Jwts.builder()
				.signWith(Keys.hmacShaKeyFor(signingKey), SignatureAlgorithm.HS512)
				.setHeaderParam("typ", SecurityConstants.TOKEN_TYPE)
				.setIssuer(SecurityConstants.TOKEN_ISSUER)
				.setAudience(SecurityConstants.TOKEN_AUDIENCE)
				.setSubject(username)
				.setExpiration(new Date(System.currentTimeMillis() + tokenExpiryMillis))
				.claim("rol", roles)
				.claim("syncariId",syncariId)
				.claim("ghosted", isGhost)
				.claim("token", tokenUUID)
				.compact();
	}

	public String getTokenAndPersistLoginDetails(User user, List<String> roles, boolean isGhost,String syncariId, String tokenUUID) {
		String token = this.getToken(user.getEmail(),roles,isGhost,syncariId,tokenUUID);
		UserLoginDetails userLoginDetails = new UserLoginDetails(tokenUUID,SecurityConstants.TOKEN_EXPIRATION);
		userService.updateUserLoginDetails(user, userLoginDetails);
		return token;
	}

	public TokenAttributes parseExpiredJWTException(ExpiredJwtException expiredJwtException) {
		String username = expiredJwtException.getClaims().getSubject();
		String tokenUUID = null;
		if (null != expiredJwtException.getClaims().get("token")){
			tokenUUID = (String)expiredJwtException.getClaims().get("token");
		}
		String syncariId = (String)expiredJwtException.getClaims().get("syncariId");
		Boolean ghosted = null;
		if (null != expiredJwtException.getClaims().get("ghosted")) {
			ghosted = (Boolean)expiredJwtException.getClaims().get("ghosted");
		}
		return new TokenAttributes(username, syncariId, tokenUUID, ghosted, null);
	}

	public String parseJWTTokenAndUpdateUserWithNewLoginDetails(String previousToken, User user, List<String> permissions,boolean isGhost) {
		TokenAttributes attributes = this.parseTokenExpiredOrNot(previousToken);
		String tokenId = attributes.getTokenId();
		String token = this.generateJwtTokenWithSpecifiedRoles(permissions, isGhost,tokenId);
		UserLoginDetails userLoginDetailsToUpdate = new UserLoginDetails(tokenId,SecurityConstants.TOKEN_EXPIRATION);
		userService.updateUserLoginDetails(user, userLoginDetailsToUpdate);
		return token;
	}

	public TokenAttributes parseToken(String token){
		var signingKey = appConfig.getJwtSecret().getBytes();
		var parsedToken = Jwts.parser()
				.setSigningKey(signingKey)
				.parseClaimsJws(token);
		String username = parsedToken
				.getBody()
				.getSubject();
		String tokenUUID = null;
		if (null != parsedToken.getBody().get("token")) {
			tokenUUID = (String)parsedToken.getBody().get("token");
		}

		List<SimpleGrantedAuthority> authorities = ((List<?>) parsedToken.getBody()
				.get("rol")).stream()
				.map(authority -> new SimpleGrantedAuthority((String) authority))
				.collect(Collectors.toList());
		String syncariId = (String)parsedToken.getBody().get("syncariId");
		Boolean ghosted = null;
		if (null != parsedToken.getBody().get("ghosted")){
			ghosted = (Boolean) parsedToken.getBody().get("ghosted");
		}
		return new TokenAttributes(username,syncariId,tokenUUID,ghosted,authorities);
	}

	public TokenAttributes parseTokenExpiredOrNot(String token){
		try{
			return this.parseToken(token);
		}catch (ExpiredJwtException exception) {
			return this.parseExpiredJWTException(exception);
		}
	}

	public void setInsightsProviderContext(Instance instance){
		if (null != instance){
			Map<String, Feature> enabledFeatures = instance.getFeatures();
			if ((MapUtils.isNotEmpty(enabledFeatures)) && (enabledFeatures.containsKey(Features.InsightsProvider.name()))){
				User user =  SyncariContext.getUser();
				if ((user !=null) && user.isGhostUser() && StringUtils.isEmpty(user.getInsightsProviderUserId())){
					// if user does not exists in insights provider create a user
					try{
						insightsProviderIntegrator.createUserByAdmin(user);
					}catch (Exception e){
						log.error("Create user failed for user {}", user.getId());
						String createUserFailedBody = String.format("Create User with instance provider failed for instance name %s, for user email %s", SyncariContext.getInstance().getName(), user.getEmail()) ;
						emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(),
								String.format("Create user failed with Insights Provider for syncariid %s", SyncariContext.getSyncariId()), createUserFailedBody);
					}
				}else if ((user !=null) && (!user.isSystemUser()) && (!user.isApiUser())
						&& StringUtils.isNotEmpty(user.getInsightsProviderUserId())){
					try{
						log.info("Calling Remove user from group for TS right user setup");
						insightsProviderIntegrator.removeUserFromGroups(user.getInsightsProviderUserId());
					}catch (Exception e){
						log.error("Remove TS user from groups failed for user {}", user.getId());
						String failedBody = String.format("Remove TS user from groups failed for instance name %s, for user email %s", SyncariContext.getInstance().getName(), user.getEmail()) ;
						emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(),
								String.format("Remove TS user from groups failed with Insights Provider for syncariid %s", SyncariContext.getSyncariId()), failedBody);
					}
					try{
						log.info("Calling addUserToGroupAndCurrentOrg for TS right group setup");
						insightsProviderIntegrator.addUserToGroupAndCurrentOrg(user);
					}catch (Exception e){
						log.error("Add TS user to group of syncariId {} failed for user {}", SyncariContext.getSyncariId(), user.getId());
						String failedBody = String.format("Add TS user to groups failed for instance name %s, for user email %s", SyncariContext.getInstance().getName(), user.getEmail()) ;
						emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(),
								String.format("Add TS user to group failed with Insights Provider for syncariId %s", SyncariContext.getSyncariId()), failedBody);
					}

				}

			}

		}
	}
}
