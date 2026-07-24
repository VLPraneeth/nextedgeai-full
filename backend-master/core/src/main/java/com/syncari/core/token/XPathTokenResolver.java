package com.syncari.core.token;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jxpath.FunctionLibrary;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathInvalidSyntaxException;
import org.apache.commons.jxpath.JXPathNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class XPathTokenResolver implements TokenResolver {
    /**
     * This is very important - https://github.com/apache/commons-jxpath/pull/25
     * https://github.com/apache/commons-jxpath/pull/26
     * https://hackinglab.cz/en/blog/remote-code-execution-in-jxpath-library-cve-2022-41852/
     */
    private final static FunctionLibrary functions = new FunctionLibrary();
    private String xpathExpression;
    private Pattern regex = Pattern.compile("\\[(\\d+)\\]");
    private static Cache<String, String> expressionCache = CacheBuilder.newBuilder().maximumSize(10000)
            .build();


    public XPathTokenResolver(String xpathExpression) {
        this.xpathExpression = xpathExpression;
    }

    @Override
    public TokenResolution resolveToken(Map<String, Object> context) {
        try {
            final JXPathContext jxPathContext = JXPathContext.newContext(context);
            jxPathContext.setFunctions(functions);
            String updated = updateToOneBasedIndex(xpathExpression);
            List<Object> values = jxPathContext.selectNodes(updated);
            if (values == null || values.isEmpty()) {
                return new TokenResolution(null, false, "No value found for token: " + xpathExpression);
            }
            Object value = values.size() == 1 ? values.get(0) : values;
            return new TokenResolution(value, true);
        } catch (JXPathNotFoundException e) {
            log.error(e.getMessage(), e);
            return new TokenResolution(null, false, e.getMessage());
        } catch (JXPathInvalidSyntaxException e) {
            log.error(e.getMessage(), e);
            return new TokenResolution(null, false, e.getMessage(), true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new TokenResolution(null, false, e.getMessage());
        }
    }

    private String updateToOneBasedIndex(String xpathExpression) {
        String updated = expressionCache.getIfPresent(xpathExpression);
        if (updated == null) {
            StringBuffer resultString = new StringBuffer();
            Matcher regexMatcher = regex.matcher(xpathExpression);
            while (regexMatcher.find()) {
                String replace = regexMatcher.group(1).strip();
                final int index = Integer.valueOf(replace) + 1;
                regexMatcher.appendReplacement(resultString, "[" + index + "]");
            }
            regexMatcher.appendTail(resultString);
            updated = resultString.toString();
            expressionCache.put(xpathExpression, updated);
        }
        return updated;
    }
}
