package com.syncari.karibu.rest.config;

import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.User;
import com.syncari.core.service.UserService;
import com.syncari.karibu.rest.config.security.JwtUtil;
import com.syncari.karibu.rest.exceptions.UnauthorizedException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.model.AuditLog;
import com.syncari.core.repositories.customer.AuditLogRepo;
import com.syncari.core.service.InstanceConfigurationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ControllerInterceptor extends HandlerInterceptorAdapter {

    private static final String START_TIME = "startTime";
    public static final String REQUEST_ID = "requestId";

	@Autowired
	AuditLogRepo auditRepo;

	@Autowired
	private UserService userService;

    @Autowired
    InstanceConfigurationService instanceConfigurationService;
	
	@Autowired
	private SyncariContextHandler syncariContextHandler;

	@Autowired
	JwtUtil jwtUtil;

	List<String> excludedPrefixes = List.of("/webjars/springfox-swagger-ui", "/swagger-resources");


    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws UnauthorizedException, Exception {
		MDC.put("requestId", jwtUtil.getRequestId());
		// for oauth calls use client_id for logging
		MDC.put("user", request.getHeader("clientId"));
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if(isAuthenticated(authentication)) {
			UserDetails user = (UserDetails)authentication.getPrincipal();
			Map<String, String> details = (Map<String, String>) authentication.getDetails();
			User apiUser = null;
			try {
				apiUser = userService.getUserByClientId(user.getUsername());
			} catch (NotFoundException e) {
				apiUser = userService.getUserByEmail(user.getUsername()).orElseThrow(() -> new NotFoundException(String.format("User not found")));
			}
			SyncariContext.setUser(apiUser);
			syncariContextHandler.setContext(details.get("syncariId"));
			log.debug("syncariid {}", details.get("syncariId"));
			MDC.put("subscription",SyncariContext.getOrganziation().getName());
			MDC.put("instance",SyncariContext.getInstance().getName());
			MDC.put("syncariId",SyncariContext.getInstance().getSyncariId());
			MDC.put("user",SyncariContext.getUser().getEmail());
            if (instanceConfigurationService.isDebugModeEnabled()) {
                MDC.put("debugMode", "true");
                log.debug("DEBUG mode enabled");
            } else {
                MDC.put("debugMode", "false");
            }
		}
        logRequest(request, response, true);
		return true;
	}

	private boolean isAuthenticated(Authentication authentication) {
		return authentication!=null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
			throws Exception {
		if(ex!=null){
			log.error(ex.getMessage(),ex);
		}
		super.afterCompletion(request, response, handler, ex);

		if (!excluded(request.getRequestURI()) && isAuthenticated(SecurityContextHolder.getContext().getAuthentication())) {
			AuditLog auditLog = new AuditLog();
			auditLog.setWhat(request.getRequestURI());
			auditLog.setWhen(new Date());
			auditLog.setWho(request.getUserPrincipal()!=null? request.getUserPrincipal().getName() : "anonymous");
			auditLog.setWhere(
					String.format("Client IP: %s, Host: %s", request.getRemoteAddr(), request.getRemoteHost()));
			auditLog.setServer(
					String.format("Server IP: %s, Host: %s", request.getLocalAddr(), request.getLocalName()));
			auditLog.setStatus(String.valueOf(response.getStatus()));
			auditRepo.save(auditLog);
		}
        logRequest(request, response, false);
		SyncariContext.resetAll();
		MDC.remove("subscription");
		MDC.remove("instance");
		MDC.remove("syncariId");
		MDC.remove("user");
		MDC.remove("requestId");
        MDC.remove("debugMode");

	}

    private void logRequest(HttpServletRequest request, HttpServletResponse response, boolean incoming) {
        if (StringUtils.isNotEmpty(request.getRequestURI()) && request.getRequestURI().endsWith("/health")) {
            return;
        }
        if (incoming) {
            request.setAttribute(START_TIME, System.currentTimeMillis());
            request.setAttribute(REQUEST_ID, UUID.randomUUID().toString());
            log.info("Starting {} {} {}", request.getAttribute(REQUEST_ID),
                request.getMethod(), request.getRequestURI());
        } else {
            long startTime = (Long) request.getAttribute(START_TIME);
		    log.info("Completed {} {} {} {} ", request.getAttribute(REQUEST_ID), response.getStatus(),
                System.currentTimeMillis() - startTime, request.getRequestURI());
        }
    }

	private boolean excluded(String uri) {
		return excludedPrefixes.stream().anyMatch(prefix -> uri.startsWith(prefix));
	}
}