package com.syncari.karibu.rest.config;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.Organization;
import com.syncari.core.model.misc.UserLoginDetails;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import com.syncari.core.service.authz.AuthzService;
import com.syncari.karibu.rest.config.security.*;
import com.syncari.karibu.rest.exceptions.UnauthorizedException;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableWebSecurity
public class SyncariSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private SubscriptionService subService;

    @Autowired
    AuthzService authzService;

    @Autowired
    UserService userService;

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    JwtUtil jwtUtil;

    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/v2/api-docs",
                "/configuration/**",
                "/health",
                "/health/**",
                "/api/v1/oauth/**",
                "/api/v1/marketplace/**",
                "/configuration",
                "/swagger-resources",
                "/swagger-resources/**",
                "/swagger-ui.html",
                "/webjars/**");
    }
    protected void configure(HttpSecurity http) throws Exception {

        AuthenticationEntryPoint unauthorized = new AuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
                    throws ExpiredJwtException, UnauthorizedException, IOException, ServletException {
                try {
                    response = jwtUtil.getAuthorizationError(response, authException.getMessage());
                }catch (UnauthorizedException e){
                    response = jwtUtil.getAuthorizationError(response, e.getMessage());
                }
            }
        };

        http.cors().and()
                .csrf().disable()
                .exceptionHandling().authenticationEntryPoint(unauthorized).and()
                .authorizeRequests()
                .antMatchers("/api/v1/oauth/**").permitAll()
                .antMatchers("/api/v1/webhooks/**").permitAll()
                .antMatchers("/api/v1/sso/saml/**").permitAll()
                .antMatchers(SecurityConstants.AUTH_LOGIN_URL).permitAll()
                .antMatchers(SecurityConstants.SET_PASSWORD).permitAll()
                .antMatchers(SecurityConstants.FORGOT_PASSWORD).permitAll()
//                .antMatchers("/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
                .and()
                .addFilter(new JwtAuthenticationFilter(authenticationManager(),userService,jwtUtil))
                .addFilter(new JwtAuthorizationFilter(authenticationManager(), userService,subscriptionService, jwtUtil))
                .logout()
                .logoutSuccessHandler(new LogoutSuccessHandler() {
                    @Override
                    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
                        var token = request.getHeader(SecurityConstants.TOKEN_HEADER);
                        String username = null;
                        String tokenUUID = null;
                        if (StringUtils.isNotEmpty(token) && token.startsWith(SecurityConstants.TOKEN_PREFIX)) {
                            try{
                                TokenAttributes attributes = jwtUtil.parseToken(token.replace("Bearer ", ""));
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
                Optional<com.syncari.core.model.User> activeUser = Optional.ofNullable(userService.getUserByClientId(username));
                return activeUser.map(user ->{
                    if( StringUtils.isBlank(user.getCurrentInstanceId())) return null;
                    Organization org = subService.getOrgBySyncariId(user.getCurrentInstanceId());
                    if(org == null || (org.isSSOEnabled() && !user.isApiUser())) {
                        log.error("User {} does not belong to an Org or has SSO enabled authentication", username);
                        return null;
                    }
                    SyncariContext.setUser(user);
                    SyncariContext.setOrganziation(org);
                    SyncariContext.setInstance(org.getInstance(user.getCurrentInstanceId()).get());
                    // return createUserProxy(user);
                    try {
                        User userP = createUserProxy(user);
                        return userP;
                    } catch (Exception e){
                        throw new RuntimeException(e);

                    }
                    // return createUserProxy(user);

                }).orElse(null);
            }
        };
    }


    private User createUserProxy(com.syncari.core.model.User user) {
        List<SimpleGrantedAuthority> authorities = authzService.listPrivileges(user.getEmail())
                .map(p -> new SimpleGrantedAuthority(p)).collect(Collectors.toList());
        // user.setLastLoggedIn(Instant.now());
        // userRepo.save(user);

        return new User(user.getClientId(), user.getClientSecret(), authorities);
    }

    @Bean(name = BeanIds.AUTHENTICATION_MANAGER)
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

}
