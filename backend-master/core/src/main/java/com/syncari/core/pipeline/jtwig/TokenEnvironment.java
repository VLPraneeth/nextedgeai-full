package com.syncari.core.pipeline.jtwig;

import org.jtwig.environment.Environment;

import java.util.Map;

public class TokenEnvironment extends Environment {
    public TokenEnvironment(Environment environment, Map<String, Object> parameters) {
        super(environment.getParser(), parameters,
                environment.getResourceEnvironment(),
                environment.getFunctionResolver(),
                environment.getPropertyResolverEnvironment(),
                environment.getRenderEnvironment(),
                environment.getValueEnvironment(),
                environment.getListEnumerationStrategy(),
                environment.getEscapeEnvironment());
    }

}
