package com.syncari.core.token.parser;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.syncari.core.token.TokenResolution;
import com.syncari.core.token.TokenResolver;
import com.syncari.core.token.TokenSyntaxError;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.apache.commons.lang3.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
public class TokenResolverVisitor extends TokenBaseVisitor<Object> implements TokenResolver {
    private boolean hasError;

    private List<Function<TokenResolution, TokenResolution>> callChain = new ArrayList<>();
    private static Cache<Pair<Class, String>, Field> fieldCache = CacheBuilder.newBuilder().maximumSize(10000)
            .build();
    private static Cache<Pair<Class, String>, Method> methodCache = CacheBuilder.newBuilder().maximumSize(10000)
            .build();
    private List<TokenSyntaxError> tokenSyntaxErrors;

    private String toString(Throwable ex) {
        final StringWriter stringWriter = new StringWriter();
        ex.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    public TokenResolution resolveToken(Map<String, Object> context) {
        if (hasErrors()) {
            return new TokenResolution(null, false, true);
        }
        TokenResolution current = new TokenResolution(context, false);
        for (Function<TokenResolution, TokenResolution> resolver : callChain) {
            current = resolver.apply(current);
        }
        return current;
    }

    private Field findField(Object currentContext, String key) throws NoSuchFieldException {

        final Pair<Class, String> fieldKey = Pair.of(currentContext.getClass(), key);
        Field declaredField = fieldCache.getIfPresent(fieldKey);
        if (declaredField == null) {
            declaredField = currentContext.getClass().getDeclaredField(key);
            declaredField.setAccessible(true);
            fieldCache.put(fieldKey, declaredField);
        }
        return declaredField;
    }

    private Method findMethod(Object currentContext, String key) {

        final Pair<Class, String> methodKey = Pair.of(currentContext.getClass(), key);
        Method declaredMethod = methodCache.getIfPresent(methodKey);
        if (declaredMethod == null) {
            try {
                declaredMethod = currentContext.getClass().getDeclaredMethod(key);
                methodCache.put(methodKey, declaredMethod);
            } catch (NoSuchMethodException noSuchMethodException) {
            }
        }
        return declaredMethod;
    }

    private String toGetter(String key) {
        return "get" + StringUtils.capitalize(key);
    }

    private static Function<TokenResolution, TokenResolution> resolveIndexedToken(String token, int index) {
        return (TokenResolution incoming) -> {
            TokenResolution currentResolution = incoming;
            Object currentContext = currentResolution.getResolvedValue();
            if (index > -1 && currentContext != null) {
                if (currentContext instanceof List) {
                    final List cast = List.class.cast(currentContext);
                    currentResolution = cast.size() > index ? new TokenResolution(cast.get(index), true) :
                            new TokenResolution(null, false, index + ": Index out of range for key " + token);
                } else if (currentContext.getClass().isArray()) {
                    final Object[] cast = (Object[]) currentContext;
                    currentResolution = cast.length > index ? new TokenResolution(cast[index], true) :
                            new TokenResolution(null, false, index + ": Index out of range for key " + token);

                }
            }
            return currentResolution;
        };
    }

    public Function<TokenResolution, TokenResolution> traversePath(String key) {
        return (TokenResolution incoming) -> {
            TokenResolution currentResolution = incoming;
            Object currentContext = currentResolution.getResolvedValue();
            if (currentContext == null)
                return new TokenResolution(null, false, "The parent context resolved to null for key " + key);
            if (currentContext instanceof Map) {
                return new TokenResolution(Map.class.cast(currentContext).get(key), Map.class.cast(currentContext).containsKey(key));
            } else {
                try {
                    Field declaredField = findField(currentContext, key);
                    if (declaredField != null) {
                        return new TokenResolution(declaredField.get(currentContext), true);
                    }
                } catch (Exception e) {
                    final String getterName = toGetter(key);
                    Method method = findMethod(currentContext, getterName);
                    if (method == null) {
                        method = findMethod(currentContext, toBooleanGetter(key));
                    }
                    if (method == null) {
                        method = findMethod(currentContext, key);
                    }
                    if (method != null) {
                        try {
                            return new TokenResolution(method.invoke(currentContext), true);
                        } catch (IllegalAccessException ex) {
                            return new TokenResolution(null, false, toString(ex));
                        } catch (InvocationTargetException ex) {
                            return new TokenResolution(null, false, toString(ex));
                        }
                    }
                }
            }
            return new TokenResolution(null, false);
        };
    }

    private String toBooleanGetter(String key) {
        return "is" + StringUtils.capitalize(key);
    }

    public TokenResolverVisitor(List<TokenSyntaxError> tokenSyntaxErrors) {
        this.tokenSyntaxErrors = tokenSyntaxErrors;
    }

    @Override
    public Object visitTok_string(TokenParser.Tok_stringContext ctx) {
        super.visitTok_string(ctx);
        return null;
    }

    @Override
    public Object visitIdx(TokenParser.IdxContext ctx) {
        super.visitIdx(ctx);
        callChain.add(resolveIndexedToken("", Integer.parseInt(ctx.INT().getSymbol().getText())));
        return null;
    }

    @Override
    public Object visitTok_part(TokenParser.Tok_partContext ctx) {
        super.visitTok_part(ctx);
        callChain.add(traversePath(ctx.getText()));
        return null;
    }

    @Override
    public Object visitErrorNode(ErrorNode node) {
        log.error("Error parsing token {}", node.getText());
        hasError = true;
        return super.visitErrorNode(node);
    }

    public boolean hasErrors() {
        return hasError || !tokenSyntaxErrors.isEmpty();
    }
}