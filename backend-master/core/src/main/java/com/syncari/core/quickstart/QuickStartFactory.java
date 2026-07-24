package com.syncari.core.quickstart;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class QuickStartFactory {

    @Autowired
    private ApplicationContext context;

    public QuickStartService getQuickStartService(QuickStartConfig config) {
        return getQuickStartServiceByName(config.getName());
    }

    public QuickStartService getQuickStartServiceByName(String name) {
        Object clazz = context.getBean(name);
        if (clazz == null || !QuickStartService.class.isAssignableFrom(clazz.getClass())) {
            throw new RuntimeException(String.format("%s does not implement QuickStartService interface", name));
        }

        return (QuickStartService) clazz;
    }
}
