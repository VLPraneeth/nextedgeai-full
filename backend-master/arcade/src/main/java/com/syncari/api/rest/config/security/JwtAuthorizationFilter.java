package com.syncari.api.rest.config.security;

import com.syncari.api.core.util.Util;
import com.syncari.api.rest.controllers.exceptions.UnauthorizedException;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.misc.UserLoginDetails;
import com.syncari.core.service.UserService;
import com.syncari.utils.I18n;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class JwtAuthorizationFilter extends BasicAuthenticationFilter {

    public static final String SYNCARI_LOGIN_REDIRECT_WORKRAMP = "/login?redirectTo=/action/redirectpage/arcade/api/v1/saml/sso/workramp/academies?SAMLRequest=%s";
    private static final Logger log = LoggerFactory.getLogger(JwtAuthorizationFilter.class);
    private UserService userService;
    private Util util;
    public JwtAuthorizationFilter(AuthenticationManager authenticationManager, UserService userService, Util util) {
        super(authenticationManager);
        this.userService = userService;
        this.util = util;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws IOException, ServletException {
        try{
            var authentication = getAuthentication(request, response);
            if (authentication == null) {
                filterChain.doFilter(request, response);
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        }catch (UnauthorizedException ex){
            SecurityContextHolder.getContext().setAuthentication(null);
            if ((null != request.getRequestURL()) && (request.getRequestURL().toString().contains("/workramp/academies"))){
                String samlParam = request.getParameter("SAMLRequest");
                String redirectUrl = String.format(SYNCARI_LOGIN_REDIRECT_WORKRAMP, samlParam);
                response.setHeader("Location", redirectUrl);
                response.setStatus(HttpStatus.MOVED_PERMANENTLY.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                return;
            }
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getOutputStream().print("{ \" message \": \"" + ex.getMessage() + "\"}");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            return;
        }

    }
    private UsernamePasswordAuthenticationToken getAuthentication(HttpServletRequest request,HttpServletResponse response) throws IOException{
        var token = request.getHeader(SecurityConstants.TOKEN_HEADER);
        UsernamePasswordAuthenticationToken authentication = null;
        if (StringUtils.isNotEmpty(token) && token.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            String username = null;
            List<SimpleGrantedAuthority> authorities = null;
            String syncariIdAfterExpiry = null;
            String tokenUUID = null;
            List<String> roles = List.of();
            try{
                TokenAttributes tokenAttributes = util.parseToken(token.replace("Bearer ", ""));
                username = tokenAttributes.getUsername();
                tokenUUID = tokenAttributes.getTokenId();
                authorities = tokenAttributes.getAuthorities();
                if(tokenAttributes.getGhosted() != null) {
                    SyncariContext.setGhost((boolean)tokenAttributes.getGhosted());
                } else {
                    SyncariContext.setGhost(false);
                }

                UserDetails principal = new User((String)tokenAttributes.getUsername(),  "",authorities);
                if (StringUtils.isNotEmpty(tokenAttributes.getUsername())) {
                    authentication = new UsernamePasswordAuthenticationToken(principal, "", authorities);
                    authentication.setDetails(Map.of("syncariId", (String)tokenAttributes.getSyncariId()));
                }
            }catch (ExpiredJwtException exception) {
                //log.warn("Request to parse expired JWT : {} failed : {}", token, exception.getMessage());
                TokenAttributes attributes = util.parseExpiredJWTException(exception);
                username = attributes.getUsername();
                roles = (List<String> )exception.getClaims().get("rol");
                authorities = (roles).stream()
                        .map(authority -> new SimpleGrantedAuthority((String) authority))
                        .collect(Collectors.toList());
                syncariIdAfterExpiry = attributes.getSyncariId();
                tokenUUID = attributes.getTokenId();
                var ghosted = attributes.getGhosted();
                if(ghosted != null) {
                    SyncariContext.setGhost(ghosted);
                } else {
                    SyncariContext.setGhost(false);
                }
            }catch (UnsupportedJwtException exception) {
                log.warn("Request to parse unsupported JWT : {} failed : {}", token, exception.getMessage());
            } catch (MalformedJwtException exception) {
                log.warn("Request to parse invalid JWT : {} failed : {}", token, exception.getMessage());
            } catch (SignatureException exception) {
                log.warn("Request to parse JWT with invalid signature : {} failed : {}", token, exception.getMessage());
            } catch (IllegalArgumentException exception) {
                log.warn("Request to parse empty or null JWT : {} failed : {}", token, exception.getMessage());
            }
            if (StringUtils.isNotEmpty(username)) {
                if (userService.isExpiredToken(username, tokenUUID)){
                    throw new UnauthorizedException(I18n.i18n("session_invalid"));
                }else if (null == authentication) {
                    UserDetails principal = new User(username,  "",authorities);
                    authentication = getAuth(principal, "", authorities, syncariIdAfterExpiry, true,tokenUUID, response);
                    }
                }
            }

        return authentication;
    }
    private UsernamePasswordAuthenticationToken getAuth (UserDetails principal,Object credentials ,
                                                         List<SimpleGrantedAuthority> authorities, String syncariId, boolean isRefresh,String tokenUUID, HttpServletResponse response) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, "", authorities);
        authentication.setDetails(Map.of("syncariId", syncariId));
        String username = principal.getUsername();
        if (isRefresh) {
            Optional<com.syncari.core.model.User> user = userService.getUserByEmail(username);

            List<String> userRoles = authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            Optional<com.syncari.core.model.User> userToBeLoggedIn =  userService.findActiveUserByEmail(username);
            userToBeLoggedIn.ifPresent(userTobe -> {
                String newToken = util.getTokenAndPersistLoginDetails(userTobe,new ArrayList<>(userRoles), SyncariContext.isGhost(),syncariId,tokenUUID);
                UserLoginDetails loginDetailsTobeUpdated = new UserLoginDetails(tokenUUID,SecurityConstants.TOKEN_EXPIRATION);
                userService.updateUserLoginDetails(user.get(),loginDetailsTobeUpdated);
                response.addHeader(SecurityConstants.TOKEN_HEADER, SecurityConstants.TOKEN_PREFIX + newToken);
            });

        }
        return authentication;
    }
}