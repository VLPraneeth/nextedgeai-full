package com.syncari.core.repositories.customer;

import com.syncari.core.service.FeatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

@Component
public class SchemaRepoConfig {

    @Autowired
    private EntityDefinitionCache entityDefinitionCache;

    @Autowired
    private AttributeDefinitionCache attributeDefinitionCache;

    @Bean(name = "entityProxyRepo")
    public EntityDefinitionRepo getProxyEntityRepo() {
        var proxy =  (EntityDefinitionRepo)Proxy.newProxyInstance(EntityDefinitionRepo.class.getClassLoader(), new Class[]{ EntityDefinitionRepo.class},
                new EntityDefinitionProxyRepo(entityDefinitionCache));
        return proxy;
    }

    @Bean(name = "attributeProxyRepo")
    public AttributeRepo getProxyAttributeRepo() {
        var proxy =  (AttributeRepo)Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{ AttributeRepo.class},
                new AttributeDefinitionProxyRepo(attributeDefinitionCache));
        return proxy;
    }

}
