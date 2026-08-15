package com.syncari.api.rest.config;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.Util;
import com.syncari.api.rest.config.security.JwtAuthenticationFilter;
import com.syncari.api.rest.config.security.JwtAuthorizationFilter;
import com.syncari.api.rest.config.security.SecurityConstants;
import com.syncari.api.rest.config.security.TokenAttributes;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Organization;
import com.syncari.core.model.misc.UserLoginDetails;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import com.syncari.core.service.authz.AuthzService;
import com.syncari.utils.I18n;

import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSecurity
public class SyncariSecurityConfig extends WebSecurityConfigurerAdapter {
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private SubscriptionService subService;
    @Autowired
    AuthzService authzService;

    @Autowired
    UserService userService;

    @Autowired
    Util util;
    @Autowired
    ObjectMapper mapper;

    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/v2/api-docs",
                "/configuration/**",
                "/health",
                "/health/**",
                "/configuration",
                "/swagger-resources",
                "/swagger-resources/**",
                "/swagger-ui.html",
                "/api/v1/oauth2/register",
                "/api/v1/oauth2/token",
                "/webjars/**");
    }
    protected void configure(HttpSecurity http) throws Exception {
    	 AuthenticationEntryPoint unauthorized = new AuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        I18n.i18n("session_invalid"));
            }
        };

        http.cors().and()
                .csrf().disable()
                .exceptionHandling().authenticationEntryPoint(unauthorized).and()
                .authorizeRequests()
                .antMatchers("/api/v1/auth/google", "/api/v1/auth/google/**").permitAll()
                .antMatchers("/api/v1/oauth/**").permitAll()
                .antMatchers("/api/v1/oauth2/register").permitAll()
                .antMatchers("/api/v1/oauth2/token").permitAll()
                .antMatchers("/api/v1/oauth2/validate").permitAll()
                .antMatchers("/api/v1/webhooks/**").permitAll()
                .antMatchers("/api/v1/sso/saml/**").permitAll()
                .antMatchers(SecurityConstants.AUTH_LOGIN_URL).permitAll()
                .antMatchers(SecurityConstants.SET_PASSWORD).permitAll()
                .antMatchers(SecurityConstants.FORGOT_PASSWORD).permitAll()
                .antMatchers(SecurityConstants.ERROR_NOTIFICATION_ACCEPT_INVITE).permitAll()
//                .antMatchers("/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
                .and()
                .addFilter(new JwtAuthenticationFilter(authenticationManager(),userService,util, mapper))
                .addFilter(new JwtAuthorizationFilter(authenticationManager(), userService,util))
                .logout()
                .logoutSuccessHandler(new LogoutSuccessHandler() {
                    @Override
                    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
                        var token = request.getHeader(SecurityConstants.TOKEN_HEADER);
                        String username = null;
                        String tokenUUID = null;
                        if (StringUtils.isNotEmpty(token) && token.startsWith(SecurityConstants.TOKEN_PREFIX)) {
                            try{
                                TokenAttributes attributes = util.parseToken(token.replace("Bearer ", ""));
                                username = attributes.getUsername();
                                tokenUUID = attributes.getTokenId();
                            } catch (ExpiredJwtException exception) {
                                log.warn("Request to parse expired JWT : {} failed : {}", token, exception.getMessage());
                                username = exception.getClaims().getSubject();
                                tokenUUID = (String)exception.getClaims().get("token");
                            }}
                        if (StringUtils.isNotEmpty(username)) {
                            Optional<com.syncari.core.model.User> user = userService.findActiveUserByEmail(username);
                            String tokenToLook = tokenUUID;
                            user.ifPresent(userToBeUpdated -> {
                                UserLoginDetails userLoginDetails = new UserLoginDetails(tokenToLook,SecurityConstants.TOKEN_EXPIRATION);
                                userService.removeUserLoginDetails(userToBeUpdated,userLoginDetails);
                            });
                        }
                    }}).permitAll()
                .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("OPTIONS");
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("PATCH");
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    @Override
    public UserDetailsService userDetailsService() {
        return new UserDetailsService() {
            @Override
            public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                Optional<com.syncari.core.model.User> activeUser = userService.getUserByEmail(username);
                return activeUser.map(user ->{
                    if( StringUtils.isBlank(user.getCurrentInstanceId())) return null;
                    if(user.isRestrictedFromLogin()){
                        log.error("Attempting to login as a User {} who is restricted.", username);
                        return null;
                    }
                    Optional<Organization> org = subService.getOptionalOrgBySyncariId(user.getCurrentInstanceId());
                    if (!org.isPresent() || org.get().isSSOEnabled()){
                        log.error("User {} does not belong to an Org or has SSO enabled authentication", username);
                        return null;
                    }
                    SyncariContext.setUser(user);
                    SyncariContext.setOrganziation(org.get());
                    SyncariContext.setInstance(org.get().getInstance(user.getCurrentInstanceId()).get());
                    return createUserProxy(user);

                }).orElse(null);
            }
        };
    }

    private User createUserProxy(com.syncari.core.model.User user) {
    	boolean accountLocked = user.isAccountLocked();
        List<SimpleGrantedAuthority> authorities = authzService.listPrivileges(user.getEmail())
                .map(p -> new SimpleGrantedAuthority(p)).collect(Collectors.toList());
        user.setLastLoggedIn(Instant.now());
        userRepo.save(user);

        return new User(user.getEmail(), user.getPassword(), true, true, true, !accountLocked, authorities);
    }

}
