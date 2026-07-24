package com.syncari.core.repositories.customer;

import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.service.FeatureService;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

@Slf4j
public class AttributeDefinitionProxyRepo implements InvocationHandler {

    private AttributeDefinitionCache attributeDefinitionCache;

    public AttributeDefinitionProxyRepo(AttributeDefinitionCache attributeDefinitionCache) {
        this.attributeDefinitionCache = attributeDefinitionCache;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        try {
            if (attributeDefinitionCache != null) {
                Method newMethod = attributeDefinitionCache.getClass().getMethod(method.getName(), method.getParameterTypes());
                return newMethod.invoke(attributeDefinitionCache, args);
            } else {
                throw new RuntimeException("Attribute Repo Dependencies not initialized");
            }
        } catch (Exception e) {
            log.error("Error invoking method {}", e);
            throw e;
        }
    }
}
