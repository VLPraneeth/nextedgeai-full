package com.syncari.api.rest.config;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import com.syncari.core.Features;
import com.syncari.core.service.FeatureService;

@Component
public class AbacFeatureInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    FeatureService featureService;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
      if(!featureService.isEnabled(Features.ABAC, true)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The requested API could not be found");
      }
		return true;
	}
}