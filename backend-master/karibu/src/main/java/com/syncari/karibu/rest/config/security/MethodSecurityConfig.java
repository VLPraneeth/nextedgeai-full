package com.syncari.karibu.rest.config.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.acls.AclPermissionEvaluator;
import org.springframework.security.acls.model.AclService;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;

import com.syncari.core.security.PrivilegeVoter;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)

public class MethodSecurityConfig extends GlobalMethodSecurityConfiguration {
	@Autowired
	AclService aclService;
	protected MethodSecurityExpressionHandler createExpressionHandler() {
        System.out.println("MethodSecurityConfig.createExpressionHandler");
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setPermissionEvaluator(new AclPermissionEvaluator(aclService));
        return expressionHandler;
    }

	@Override
	protected AccessDecisionManager accessDecisionManager() {
		// TODO Auto-generated method stub
		AffirmativeBased accessDecisionManager = (AffirmativeBased)super.accessDecisionManager();
		accessDecisionManager.getDecisionVoters().add(new PrivilegeVoter());
		return accessDecisionManager;
	}
	
}
