package com.syncari.core.pipeline;

import com.syncari.core.pipeline.expression.*;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Dynamic visitor
 */
@Slf4j
public class DynamicDispatchVisitor implements DynamicExpressionVisitor {
    protected Map<Class, Method> visitors = new HashMap<>();
    private Object delegateVisitor;

    public DynamicDispatchVisitor(Object delegateVisitor) {
        this.delegateVisitor = delegateVisitor;
        for (Method m : delegateVisitor.getClass().getMethods()) {
            if (m.getName().equals("visit") && m.getParameterTypes()[0]!=Expression.class) {
                visitors.put(m.getParameterTypes()[0], m);
            }
        }
    }

    public void visit(Expression e) {
        if (visitors.get(e.getClass()) != null) {
            try {
                visitors.get(e.getClass()).invoke(delegateVisitor, e);
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        } else {
            log.error("No visitor found for class {} in Visitor {}", (e == null ? "N/A" : e.getClass()), delegateVisitor.getClass());
        }
    }
}
