package com.syncari.core.dashboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WidgetFactory {
    @Autowired
    private ApplicationContext context;

    public WidgetCreator getWidgetCreator(String name) {
        Object clazz = context.getBean(name);
        if (clazz == null || !WidgetCreator.class.isAssignableFrom(clazz.getClass())) {
            throw new RuntimeException(String.format("%s does not implement WidgetCreator interface", name));
        }
        return (WidgetCreator) clazz;
    }
}
