package com.syncari.karibu.rest.config;

import com.syncari.core.model.InstanceConfiguration;
import com.syncari.core.service.InstanceConfigurationService;
import com.syncari.karibu.rest.exceptions.UnauthorizedException;
import com.syncari.karibu.rest.util.IPValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class    RealtimeIpWhiteListInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    InstanceConfigurationService instanceConfigurationService;

    @Autowired
    IPValidator ipValidator;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws UnauthorizedException, Exception{
        Optional<InstanceConfiguration> instanceConfiguration = instanceConfigurationService.getInstanceConfigurationByKey(InstanceConfigurationService.INSTANCE_CONFIG_IPWHITELIST_KEY);
        log.info("RealtimeIpWhiteListInterceptor pre handle entrance with instanceConfiguration {}",instanceConfiguration);
        if (instanceConfiguration.isPresent() && (null != instanceConfiguration.get().getValue())){
            String requestIpAddress = request.getHeader("X-FORWARDED-FOR");
            if (requestIpAddress == null) {
                requestIpAddress = request.getRemoteAddr();
            }
            log.info("RealtimeIpWhiteListInterceptor pre handle entrance with requestIpAddress {}",requestIpAddress);
            List<String> listIps = Arrays.stream(instanceConfiguration.get().getValue().toString().split("\n")).collect(Collectors.toList());
            try{
                if (!ipValidator.isClientIpPermitted(requestIpAddress, listIps)){
                    throw new AccessDeniedException(String.format("Access Denied"));
                }
            }catch (Exception e){
                log.error("Exception Occurred while validating client ip for access from instance config,not blocking pipeline", e);
            }
        }
        return true;
    }
}
