package com.syncari.core.repositories.customer;

import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.service.FeatureService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class EntityDefinitionProxyRepo implements InvocationHandler {

    private EntityDefinitionCache entityDefinitionCache;

    private EntityDefinitionRepo entityDefinitionRepo;

        public EntityDefinitionProxyRepo(EntityDefinitionCache entityDefinitionCache) {
        this.entityDefinitionCache = entityDefinitionCache;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        try {
            if (entityDefinitionCache != null) {
                Method newMethod = entityDefinitionCache.getClass().getMethod(method.getName(), method.getParameterTypes());
                return newMethod.invoke(entityDefinitionCache, args);
            } else {
                throw new RuntimeException("Entity Repo Dependencies not initialized");
            }
        } catch (Exception e) {
            log.error("Error invoking method {} and stack trace is {}", e.getCause(), ExceptionUtils.getStackTrace(e));
            throw e.getCause();
        }
    }
}
