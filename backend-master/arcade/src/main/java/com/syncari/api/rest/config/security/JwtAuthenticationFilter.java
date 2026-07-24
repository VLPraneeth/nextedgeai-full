package com.syncari.api.rest.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.Util;
import com.syncari.core.SyncariContext;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.UserService;
import com.syncari.utils.I18n;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private UserService userService;
    private Util util;
    private ObjectMapper mapper;

   public JwtAuthenticationFilter(AuthenticationManager authenticationManager, UserService userService,Util util, ObjectMapper mapper) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.util = util;
        this.mapper = mapper;
        setFilterProcessesUrl(SecurityConstants.AUTH_LOGIN_URL);
        setPostOnly(true);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        var username = request.getParameter("username");
        var password = request.getParameter("password");
        var authenticationToken = new UsernamePasswordAuthenticationToken(username.toLowerCase(), password);
        try {
	        var authentication = authenticationManager.authenticate(authenticationToken);
	        userService.clearFailedLoginAttempts(username.toLowerCase());
	        return authentication;
        }catch (BadCredentialsException e) {
        	log.error("User {} attempted login with incorrect password", username);
        	userService.incrementFailedLoginAttempts(username.toLowerCase());
        	throw e;
		}
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain filterChain, Authentication authentication) {
        var user = ((User) authentication.getPrincipal());
        Optional<String> previousToken = Optional.ofNullable(request.getHeader(SecurityConstants.TOKEN_HEADER));

        var roles = user.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        String tokenUUID = previousToken.map(prevToken -> {
            TokenAttributes attribs = util.parseTokenExpiredOrNot(prevToken.replace("Bearer ", ""));
            if (null != attribs.getTokenId()){
                return attribs.getTokenId();
            }else{
               return UUID.randomUUID().toString();
            }
            }).orElse(UUID.randomUUID().toString());

        //String tokenUUID = UUID.randomUUID().toString();

        //var token = util.getToken(user.getUsername(),roles,false,SyncariContext.getInstance().getSyncariId(),tokenUUID);
        Optional<com.syncari.core.model.User> userToBeLoggedInOptional = userService.findActiveUserByEmail(user.getUsername());
        userToBeLoggedInOptional.ifPresent(userToBeLoggedIn -> {
        	if(userToBeLoggedIn.hasPasswordExpired()) {
        		roles.clear();
        		roles.addAll(Permissions.getProfilePermissions());
        	}
            var token = util.getTokenAndPersistLoginDetails(userToBeLoggedIn,roles,false,SyncariContext.getInstance().getSyncariId(),tokenUUID);
            util.setInsightsProviderContext(SyncariContext.getInstance());
        	response.addHeader(SecurityConstants.TOKEN_HEADER, SecurityConstants.TOKEN_PREFIX + token);
        });

    }
    
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
    		AuthenticationException failed) throws IOException, ServletException {
    	Map<String, Object> responseMap = new HashMap<String, Object>();
    	responseMap.put("timestamp", Instant.now());
    	responseMap.put("status", HttpStatus.UNAUTHORIZED.value());
    	responseMap.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
    	response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    	response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    	response.setCharacterEncoding("UTF-8");
		if (failed instanceof LockedException) {
			responseMap.put("message", I18n.i18n("account_locked"));
		} else {
			responseMap.put("message", I18n.i18n("invalid_user_password"));
		}
		PrintWriter writer = response.getWriter();
		writer.print(mapper.writeValueAsString(responseMap));
		writer.flush();
    }

}