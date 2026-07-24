package com.syncari.karibu.rest.config.security;

import com.syncari.core.SyncariContextHandler;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.SubscriptionService;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.exceptions.UnauthorizedException;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.misc.UserLoginDetails;
import com.syncari.core.service.UserService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
import java.util.*;
import java.util.stream.Collectors;

public class JwtAuthorizationFilter extends BasicAuthenticationFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthorizationFilter.class);
    private UserService userService;
    private SubscriptionService subscriptionService;
    private JwtUtil jwtUtil;
    public JwtAuthorizationFilter(AuthenticationManager authenticationManager, UserService userService, SubscriptionService subscriptionService, JwtUtil jwtUtil) {
        super(authenticationManager);
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.subscriptionService = subscriptionService;
    }



    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ExpiredJwtException, IOException, ServletException {
        try{
            var authentication = getAuthentication(request, response);

            if (authentication == null) {
                filterChain.doFilter(request, response);
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException e){
            response = jwtUtil.getAuthorizationError(response, KaribuConstants.EXPIRED_TOKEN_ERROR);
            return;

        }

    }

    private UsernamePasswordAuthenticationToken getAuthentication(HttpServletRequest request,HttpServletResponse response)
            throws IOException {

        MDC.put("requestId", jwtUtil.getRequestId());
        var requestToken = request.getHeader(SecurityConstants.TOKEN_HEADER);
        var requestSyncariId = request.getHeader(SecurityConstants.SYNCARI_ID);
        UsernamePasswordAuthenticationToken authentication = null;
        if (StringUtils.isNotEmpty(requestToken) && requestToken.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            String username = null;
            List<SimpleGrantedAuthority> authorities = null;
            String syncariIdAfterExpiry = null;
            String tokenUUID = null;
            List<String> roles = List.of();
            try{
                TokenAttributes tokenAttributes = jwtUtil.parseToken(requestToken.replace("Bearer ", ""));
                String tokenSyncariId = (String)tokenAttributes.getSyncariId();
                if (StringUtils.isNotEmpty(requestSyncariId) && (!requestSyncariId.equals(tokenSyncariId))){
                    Organization org = subscriptionService.getOrgBySyncariId(requestSyncariId);
                    Optional<Instance> instance = org.getInstance(requestSyncariId);
                    if (instance.isPresent()){
                        username = tokenAttributes.getUsername();

                        com.syncari.core.model.User user = null;
                        try {
                            user = userService.getUserByClientId(username);
                        } catch (NotFoundException e) {
                            user = userService.getUserByEmail(username).orElseThrow(() -> new NotFoundException(String.format("User not found")));
                        }
                        SyncariContext.setInstance(instance.get());SyncariContext.setOrganziation(org);SyncariContext.setUser(user);
                        List<String> newPerms = user.isSuperAdmin() ? Permissions.allPermissions()
                                : new ArrayList<>(userService.getUserPermissionsForInstance(user.getId(),
                                instance.get()));
                        List<String> tokenPerms =  tokenAttributes.getAuthorities().stream().map(sga -> sga.getAuthority()).collect(Collectors.toList());
                        if (!tokenPerms.containsAll(newPerms) || CollectionUtils.isEmpty(newPerms)){
                            String newToken = jwtUtil.parseJWTTokenAndGetNewToken(requestToken.replace("Bearer ", ""),
                                    user, newPerms);
                            tokenAttributes = jwtUtil.parseToken(newToken.replace("Bearer ", ""));
                        }
                    }
                }
                authorities = tokenAttributes.getAuthorities();
                if(tokenAttributes.getGhosted() != null) {
                    SyncariContext.setGhost((boolean)tokenAttributes.getGhosted());
                } else {
                    SyncariContext.setGhost(false);
                }
                UserDetails principal = new User((String)tokenAttributes.getUsername(),  "",authorities);
                if (StringUtils.isNotEmpty(tokenAttributes.getUsername())) {
                    authentication = new UsernamePasswordAuthenticationToken(principal, "", authorities);
                    if (StringUtils.isNotEmpty(requestSyncariId) && (!requestSyncariId.equals(tokenSyncariId))){
                        authentication.setDetails(Map.of("syncariId",requestSyncariId));
                    }else{
                        authentication.setDetails(Map.of("syncariId",(String)tokenAttributes.getSyncariId()));
                    }
                }
            }catch (ExpiredJwtException exception) {
                //log.warn("Request to parse expired JWT : {} failed : {}", token, exception.getMessage());
                TokenAttributes attributes = jwtUtil.parseExpiredJWTException(exception);
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
                log.warn("Request to parse unsupported JWT : {} failed : {}", requestToken, exception.getMessage());
            } catch (MalformedJwtException exception) {
                log.warn("Request to parse invalid JWT : {} failed : {}", requestToken, exception.getMessage());
            } catch (SignatureException exception) {
                log.warn("Request to parse JWT with invalid signature : {} failed : {}", requestToken, exception.getMessage());
            } catch (IllegalArgumentException exception) {
                log.warn("Request to parse empty or null JWT : {} failed : {}", requestToken, exception.getMessage());
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

            // TODO presist client login details
            // Optional<com.syncari.core.model.User> userToBeLoggedIn =  userService.findActiveUserByEmail(username);
            // userToBeLoggedIn.ifPresent(userTobe -> {
            //      String newToken = jwtUtil.getTokenAndPersistLoginDetails(userTobe,new ArrayList<>(userRoles), SyncariContext.isGhost(),syncariId,tokenUUID);
            //     UserLoginDetails loginDetailsTobeUpdated = new UserLoginDetails(tokenUUID,SecurityConstants.TOKEN_EXPIRATION);
            //     userService.updateUserLoginDetails(user.get(),loginDetailsTobeUpdated);
            //     response.addHeader(SecurityConstants.TOKEN_HEADER, SecurityConstants.TOKEN_PREFIX + newToken);
            // });
            com.syncari.core.model.User apiUser = userService.getUserByClientId(username);
            String newToken = jwtUtil.getTokenAndPersistLoginDetails(apiUser, new ArrayList<>(userRoles), syncariId, tokenUUID);
            UserLoginDetails loginDetailsTobeUpdated = new UserLoginDetails(tokenUUID,SecurityConstants.TOKEN_EXPIRATION);
            // TODO presist client login details
            // userService.updateUserLoginDetails(user.get(),loginDetailsTobeUpdated);
            response.addHeader(SecurityConstants.TOKEN_HEADER, SecurityConstants.TOKEN_PREFIX + newToken);

        }
        return authentication;
    }
}
