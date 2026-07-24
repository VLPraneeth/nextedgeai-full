package com.syncari.core.token;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.token.parser.TokenLexer;
import com.syncari.core.token.parser.TokenParser;
import com.syncari.core.token.parser.TokenResolverVisitor;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SyncariTokenResolver {
    private static final String TOKEN = "\\{\\{([\\s\\S]+?)\\}\\}";
    private static final Pattern regex = Pattern.compile(TOKEN);
    private Cache<String, ParsedTokens> tokenCache = CacheBuilder.newBuilder().maximumSize(10000).build();


    class ParsedTokens {
        List<TokenResolver> tokens = new ArrayList<>();
        List<String> tokenStrings = new ArrayList<>();
        String token;

        public ParsedTokens(String token) {
            this.token = token;
            Matcher regexMatcher = regex.matcher(token);
            while (regexMatcher.find()) {
                tokenStrings.add(regexMatcher.group());
                final String sanitizedToken = sanitizedToken(regexMatcher.group(1));
                if (isXPathExpression(sanitizedToken)) {
                    addXPathTokenResolver(sanitizedToken);
                } else {
                    addStandardTokenResolver(sanitizedToken);
                }
            }
        }

        private void addXPathTokenResolver(String sanitizedToken) {
            tokens.add(new XPathTokenResolver(sanitizedToken));
        }

        private void addStandardTokenResolver(String sanitizedToken) {
            CharStream input = CharStreams.fromString(sanitizedToken);
            TokenSyntaxErrorListener listener = new TokenSyntaxErrorListener();
            TokenLexer lexer = new TokenLexer(input);
            lexer.addErrorListener(listener);
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            TokenParser parser = new TokenParser(tokenStream);
            parser.addErrorListener(listener);
            TokenResolverVisitor visitor = new TokenResolverVisitor(listener.getTokenSyntaxErrors());
            visitor.visitTok_string(parser.tok_string());
            tokens.add(visitor);
        }

        private boolean isXPathExpression(String sanitizedToken) {
            return sanitizedToken.startsWith("/");
        }

        public TokenResolution resolve(Map<String, Object> context) {
            List<TokenResolution> resolutions = new ArrayList<>();
            for (TokenResolver visitor : tokens) {
                TokenResolution currentResolution = visitor.resolveToken(context);
                if (currentResolution != null && currentResolution.getResolvedValue() == context) {
                    resolutions.add(new TokenResolution(null, false));
                } else {
                    resolutions.add(currentResolution);
                }
            }
            return resolve(resolutions);
        }

        private TokenResolution resolve(List<TokenResolution> resolutions) {
            if (tokens.isEmpty()) {
                return new TokenResolution(token, true);
            }
            final boolean shouldRenderAsString = tokens.size() > 1 || token.length() > tokenStrings.get(0).length();
            if (shouldRenderAsString) {
                return renderAsString(resolutions);
            } else {
                return resolutions.get(0);
            }
        }

        private TokenResolution renderAsString(List<TokenResolution> resolutions) {
            String resolved = token;
            for (int i = 0; i < resolutions.size(); i++) {
                if (resolutions.get(i).hasTokenSyntaxErrors()) {
                    return new TokenResolution(null, false, true);
                } else {
                    resolved = resolved.replace(tokenStrings.get(i), resolutions.get(i).stringValue());
                }
            }
            return new TokenResolution(resolved, true);
        }
    }

    protected String sanitizedToken(String token) {
        return token.replaceAll("\\R", "");
    }

    public TokenResolution resolve(String token, Map<String, Object> context) {
        ParsedTokens parsedTokens = tokenCache.getIfPresent(token);
        if (parsedTokens == null) {
            parsedTokens = getTokens(token);
            tokenCache.put(token, parsedTokens);
        }
        return parsedTokens.resolve(context);
    }

    public TokenResolution resolve(String token, GraphContext context) {
        return resolve(token, (Map<String, Object>) context);
    }


    private ParsedTokens getTokens(String token) {
        ParsedTokens tokens = new ParsedTokens(token);
        tokenCache.put(token, tokens);
        return tokens;
    }
}