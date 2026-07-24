package com.syncari.api.rest.config;

import com.syncari.api.core.util.Util;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.model.AuditLog;
import com.syncari.core.repositories.customer.AuditLogRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.InstanceConfigurationService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class ControllerInterceptor extends HandlerInterceptorAdapter {

	private static final String START_TIME = "startTime";
	private static final String REQUEST_ID = "requestId";

	@Autowired
	AuditLogRepo auditRepo;
	@Autowired
	private UserService userService;

	@Autowired
	private OrganizationRepo orgRepo;

	@Autowired
	InstanceConfigurationService instanceConfigurationService;

	@Autowired
	private Util util;

	@Autowired
	private SyncariContextHandler syncariContextHandler;

	List<String> excludedPrefixes = List.of("/webjars/springfox-swagger-ui", "/swagger-resources");


	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if(isAuthenticated(authentication)) {
			UserDetails user = (UserDetails)authentication.getPrincipal();
			Map<String, String> details = (Map<String, String>) authentication.getDetails();
			com.syncari.core.model.User byEmail = userService.getUserByEmail(user.getUsername()).get();
			SyncariContext.setUser(byEmail);
			syncariContextHandler.setContext(details.get("syncariId"));
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
		syncariContextHandler.resetSyncariContext();
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