package com.syncari.karibu.rest.config.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import com.syncari.core.config.AppConfig;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.misc.UserLoginDetails;
import com.syncari.core.service.UserService;
import com.syncari.karibu.rest.exceptions.UnauthorizedException;
import com.syncari.karibu.rest.response.OauthTokenResponse;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtUtil {

    @Autowired
    protected AuthenticationManager authenticationManager;

    @Autowired
    UserService userService;

    @Autowired
    AppConfig appConfig;

    public OauthTokenResponse getAccessTokenResponse(String grantType, String clientId, String clientSecret, com.syncari.core.model.User apiUser) {

        var authenticationToken = new UsernamePasswordAuthenticationToken(clientId, clientSecret);
        Authentication authentication =  authenticationManager.authenticate(authenticationToken);
        var user = ((User) authentication.getPrincipal());
        var roles = user.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        HttpServletResponse response = null;
        String tokenUUID = UUID.randomUUID().toString();

        var token = getTokenAndPersistLoginDetails(apiUser, roles,
                SyncariContext.getInstance().getSyncariId(), tokenUUID);

        return new OauthTokenResponse(token, tokenUUID, SecurityConstants.TOKEN_EXPIRATION/1000, "Bearer");

    }

    public String getCurrentUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }

    public String generateJwtTokenWithSpecifiedRoles(List<String> permissions, String tokenId, String clientSecret) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        var user = ((UserDetails) authentication.getPrincipal());
        return getToken(user.getUsername(), clientSecret, permissions, tokenId);
    }

    public String getToken(String clientId, String clientSecret, List<String> roles, String tokenUUID){
        return getToken(clientId, clientSecret, roles, SyncariContext.getInstance().getSyncariId(),tokenUUID);
    }

    private String getToken(String clientId, String clientSecret, List<String> roles, String syncariId, String tokenUUID){

        var signingKey = appConfig.getJwtSecret().getBytes();
        return Jwts.builder()
                .signWith(Keys.hmacShaKeyFor(signingKey), SignatureAlgorithm.HS512)
                .setHeaderParam("typ", SecurityConstants.TOKEN_TYPE)
                .setIssuer(SecurityConstants.TOKEN_ISSUER)
                .setAudience(SecurityConstants.TOKEN_AUDIENCE)
                .setSubject(clientId)
                .setExpiration(new Date(System.currentTimeMillis() + SecurityConstants.TOKEN_EXPIRATION))
                .claim("rol", roles)
                .claim("syncariId",syncariId)
                .claim("ghosted", false)
                .claim("token", tokenUUID)
                .compact();

    }

    public String getTokenAndPersistLoginDetails(com.syncari.core.model.User apiUser, List<String> roles, String syncariId, String refreshToken) {
        String token = this.getToken(apiUser.getClientId(), apiUser.getClientSecret(), roles,syncariId, refreshToken);
        UserLoginDetails userLoginDetails = new UserLoginDetails(refreshToken, SecurityConstants.TOKEN_EXPIRATION);
        userService.updateUserApiRefreshToken(apiUser, refreshToken);
        return token;
    }

    public TokenAttributes parseExpiredJWTException(ExpiredJwtException expiredJwtException) {
        String username = expiredJwtException.getClaims().getSubject();
        String tokenUUID = null;
        if (null != expiredJwtException.getClaims().get("token")){
            tokenUUID = (String)expiredJwtException.getClaims().get("token");
        }
        String syncariId = (String)expiredJwtException.getClaims().get("syncariId");
        return new TokenAttributes(username, syncariId, tokenUUID, false,null);
    }

    public String parseJWTTokenAndUpdateUserWithNewLoginDetails(String previousToken, com.syncari.core.model.User apiUser,
                                                                List<String> permissions) {
        TokenAttributes attributes = this.parseTokenExpiredOrNot(previousToken);
        String tokenId = attributes.getTokenId();
        String token = this.getToken(apiUser.getClientId(),apiUser.getClientSecret(),permissions,tokenId);
        UserLoginDetails userLoginDetailsToUpdate = new UserLoginDetails(tokenId, SecurityConstants.TOKEN_EXPIRATION);
        userService.updateUserLoginDetails(apiUser, userLoginDetailsToUpdate);
        return token;
    }

    public String parseJWTTokenAndGetNewToken(String previousToken, com.syncari.core.model.User apiUser,
                                                                List<String> permissions) {
        TokenAttributes attributes = this.parseTokenExpiredOrNot(previousToken);
        String tokenId = attributes.getTokenId();
        String token = this.getToken(apiUser.getClientId(),apiUser.getClientSecret(),permissions,tokenId);
        return token;
    }


    public TokenAttributes parseToken(String token) throws UnauthorizedException, ExpiredJwtException {
        try {
            var signingKey = appConfig.getJwtSecret().getBytes();
            var parsedToken = Jwts.parser()
                    .setSigningKey(signingKey)
                    .parseClaimsJws(token);
            String username = parsedToken
                    .getBody()
                    .getSubject();
            String tokenUUID = null;
            if (null != parsedToken.getBody().get("token")) {
                tokenUUID = (String) parsedToken.getBody().get("token");
            }

            List<SimpleGrantedAuthority> authorities = ((List<?>) parsedToken.getBody()
                    .get("rol")).stream()
                    .map(authority -> new SimpleGrantedAuthority((String) authority))
                    .collect(Collectors.toList());
            String syncariId = (String) parsedToken.getBody().get("syncariId");
            return new TokenAttributes(username, syncariId, tokenUUID, false, authorities);
        } catch (ExpiredJwtException e ) {
            throw new UnauthorizedException(e);
        }

    }

    public TokenAttributes parseTokenExpiredOrNot(String token){
        try{
            return this.parseToken(token);
        }catch (ExpiredJwtException exception) {
            return this.parseExpiredJWTException(exception);
        }
    }

    public HttpServletResponse getAuthorizationError (HttpServletResponse response, String message) throws IOException {
        Map<String, Object> bodyMap = new HashMap<>();
        Map<String, String> subBodyMap = new HashMap<>();
        subBodyMap.put("message", message);
        bodyMap.put("error", subBodyMap);
        bodyMap.put("success", "false");
        bodyMap.put("requestId", MDC.get("requestId"));
        bodyMap.put("timestamp", Instant.now().toString());
        byte[] body = new ObjectMapper().writeValueAsBytes(bodyMap);
        response.getOutputStream().write(body);
        SecurityContextHolder.getContext().setAuthentication(null);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        return response;
    }

    public String getRequestId() {
        long number = (long) Math.floor(Math.random() * 9_000_000_000L) + 1_000_000_000L;
        return String.format("%10d", number);
    }
}
