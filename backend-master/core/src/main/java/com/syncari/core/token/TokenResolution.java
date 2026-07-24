package com.syncari.core.token;

import com.syncari.utils.Pair;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenResolution {
    private Object resolvedValue;
    private boolean keyFoundInContext;

    private String resolutionError;
    private boolean hasTokenSyntaxErrors;

    public TokenResolution(Object resolvedValue, boolean keyFoundInContext) {
        this.resolvedValue = resolvedValue;
        this.keyFoundInContext = keyFoundInContext;
    }

    public TokenResolution(Object resolvedValue, boolean keyFoundInContext, boolean hasTokenSyntaxErrors) {
        this(resolvedValue, keyFoundInContext);
        this.hasTokenSyntaxErrors = hasTokenSyntaxErrors;
    }

    public TokenResolution(Object resolvedValue, boolean keyFoundInContext, String resolutionError) {
        this(resolvedValue, keyFoundInContext);
        this.resolutionError = resolutionError;
    }

    public String stringValue() {
        return resolvedValue == null ? "" : resolvedValue.toString();
    }

    public boolean hasTokenSyntaxErrors() {
        return hasTokenSyntaxErrors;
    }

    public Pair<String, Object> toPair() {
        return Pair.of(stringValue(), getResolvedValue());
    }
}
