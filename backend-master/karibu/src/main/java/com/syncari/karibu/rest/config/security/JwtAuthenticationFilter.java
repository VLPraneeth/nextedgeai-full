package com.syncari.karibu.rest.config.security;

import com.syncari.core.SyncariContext;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private UserService userService;

    private JwtUtil jwtUtil;

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager, UserService userService, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        setFilterProcessesUrl(SecurityConstants.AUTH_LOGIN_URL);
        setPostOnly(true);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        var username =  request.getParameter("username");
        var password =  request.getParameter("password");
        var authenticationToken = new UsernamePasswordAuthenticationToken(username.toLowerCase(), password);
        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        return authentication;
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
            TokenAttributes attribs = jwtUtil.parseTokenExpiredOrNot(prevToken.replace("Bearer ", ""));
            if (null != attribs.getTokenId()){
                return attribs.getTokenId();
            }else{
                return UUID.randomUUID().toString();
            }
        }).orElse(UUID.randomUUID().toString());

        // TODO clean up with provisioning changes
        // Optional<com.syncari.core.model.User> userToBeLoggedInOptional = userService.findActiveUserByEmail(user.getUsername());
        //Instance clientToBeLoggedIn = subscriptionService.getInstanceByClientId(user.getUsername());
        //userToBeLoggedInOptional.ifPresent(userToBeLoggedIn -> {
        //    var token = jwtUtil.getTokenAndPersistLoginDetails(userToBeLoggedIn,roles,false,SyncariContext.getInstance().getSyncariId(),tokenUUID);
        //    response.addHeader(SecurityConstants.TOKEN_HEADER, SecurityConstants.TOKEN_PREFIX + token);
        //});

        com.syncari.core.model.User apiUser = userService.getUserByClientId(user.getUsername());

        var token = jwtUtil.getTokenAndPersistLoginDetails(apiUser, roles,
                SyncariContext.getInstance().getSyncariId(), tokenUUID);
        response.addHeader(SecurityConstants.TOKEN_HEADER, SecurityConstants.TOKEN_PREFIX + token);

    }

}
