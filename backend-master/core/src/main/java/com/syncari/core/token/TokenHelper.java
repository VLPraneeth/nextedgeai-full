package com.syncari.core.token;

import com.syncari.connector.EntityData;
import com.syncari.core.exceptions.TokenResolutionException;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.jtwig.JTwigResult;
import com.syncari.core.pipeline.jtwig.TokenEnvironment;
import com.syncari.core.pipeline.jtwig.functions.SideChannelFunction;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;
import org.jtwig.resource.reference.ResourceReference;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TokenHelper {
    protected TokenEnvironment environment;
    private static final String TOKEN = "\\{\\{(.+?)\\}\\}";
    private static final Pattern regex = Pattern.compile(TOKEN);
    private static final String TOKEN_START = "\\{\\{";
    private static final Pattern startRegex = Pattern.compile(TOKEN_START);
    private static final String TOKEN_END = "\\}\\}";
    private static final Pattern endRegex = Pattern.compile(TOKEN_END);
    private static final String FORMULA_DELIM = "((?=\\+|-|\\*|/)|(?<=\\+|-|\\*|/))";
	private static final List<String> OPERATORS = List.of("+", "-", "*", "/");
	private static final String FORMULA_EXP = "FORMULA\\((.+?)\\)";
	private static final Pattern FORMULA_REGEX = Pattern.compile(FORMULA_EXP);
    private SyncariTokenResolver tokenResolver = new SyncariTokenResolver();
    private static final String SYNCARI_TEMP_VAR_PREFIX = "syncari.temp.";


    public TokenHelper(TokenEnvironment environment){
        this.environment = environment;
    }

    protected BiPredicate<Pair<String, Object>, Pair<String, Object>> tokenComparator = (newResult, baseline) -> {
        if (baseline == null) {
            return false;
        }

        if (newResult == null) {
            return StringUtils.isBlank(baseline.getX()) && (baseline.getY() == null ||  StringUtils.isBlank(baseline.getY().toString()));
        }

        // ignore certain patterns
        if (SideChannelFunction.SIDE_CHANNEL_RESULT.equals(baseline.getY())) {
            return true;
        }

        return Objects.equals(newResult.getX(), baseline.getX()) && Objects.equals(newResult.getY(), baseline.getY());
    };

    public Pair<String, Object> resolveTokens(Map<String, Object> context, String template) {

        try {
            if (template == null) {
                return Pair.of(null, null);
            }
            final boolean hasFormula = hasFormula(template);
            //fallback to JTWig for formulas

            Pair<String, Object> v2Result = null;
            if (!hasFormula) {
                v2Result = resolveTokenV2(context, template);
                //or if we don't find a key at all
                if (v2Result != null) {
                    return v2Result;
                }
                log.debug("Token {} not found in context with Token v2 implementation, defaulting to v1", template);
            }
            var v1Result = resolveJTwigToken(context, template);
            if (v1Result != null && !StringUtils.isEmpty(v1Result.getX())) {
                log.error("Token {} has syntax errors in Token v2, but not in Token v1: Result {}", template, v1Result);
            }
            return v1Result;
        } catch (Exception e) {
            log.debug("Error resolving token {}", template);
            throw new TokenResolutionException("Error resolving token " + template, e);
        }
    }

    public Pair<String, Object> resolveJTwigToken(Map<String, Object> context, String template) {

        if (template == null) {
            return Pair.of(null, null);
        }

        if (!StringUtils.isBlank(template) && !template.contains("{{") && !template.contains("{%")) {
            return Pair.of(template,template);
        }

        String evaluated = evaluateFormula(context, template);
        evaluated = sanitizeTempVar(template, context).orElse(evaluated);
        String sanitized = sanitizeTemplate(evaluated).orElse(evaluated);
        return evaluateExpression(context, sanitized);
    }

    /**
     * Requires valid expression.
     * @param context
     * @param expression
     * @return
     */
    public Pair<String, Object> evaluateExpression(Map<String, Object> context, String expression) {
        long now = System.currentTimeMillis();
        final boolean hasMultipleTokens = hasMultipleTokens(expression);
        JtwigTemplate jtwigTemplate = new JtwigTemplate(environment, ResourceReference.inline(expression));

        JtwigModelSanitizer sanitizedModel = JtwigModelSanitizer.newModel(context);
        JtwigModel model = JtwigModel.newModel(sanitizedModel.getValues());
        String rendered = jtwigTemplate.render(model);

        Object resultObject = JTwigResult.remove();
        // JTwigResult.remove() just returns last evaluated token in expression and resultObject cannot be used accurately in this case
        // Possible solution: if expression has multiple tokens its inherently a concatenated string so use the rendered string as resultObject
        resultObject = hasMultipleTokens ? rendered : resultObject;
        //Numbers are rendered as bigdecimal always in JTWig
        if (resultObject instanceof BigDecimal) {
            resultObject = BigDecimal.class.cast(resultObject).doubleValue();
        }
        log.debug("Using template {},time={} and rendered output {}, Object output {}", expression, (System.currentTimeMillis() - now), rendered, resultObject);
        return Pair.of(rendered, resultObject);
    }


    protected Pair<String, Object> resolveTokenV2(Map<String, Object> context, String expression) {

        long now = System.currentTimeMillis();

        expression = sanitizeTempVar(expression, context).orElse(expression);

        final TokenResolution resolved = tokenResolver.resolve(expression, context);
        if (!resolved.isKeyFoundInContext()) {
            log.debug("TokenResolverV2 KeyNotFound {}", expression);
        }
        if (resolved.hasTokenSyntaxErrors()) {
            log.error("TokenResolverV2 HasSyntaxErrors {}", expression);
        }
        if (!resolved.hasTokenSyntaxErrors()) {
            log.debug("Using tokenv2 template {},time={} and rendered output {}, Object output {}", expression, (System.currentTimeMillis() - now), resolved.toPair().x, resolved.toPair().y);
            return resolved.toPair();
        }
        return null;
    }

    public String resolveTokens(GraphContext input, String template) {
        return resolveTokens((Map<String, Object>) input, template).x;
    }

    public Object resolveTokensObject(GraphContext input, String template) {
        return resolveTokens((Map<String, Object>) input, template).y;
    }

    public static boolean hasTokens(String template) {
        if (StringUtils.isBlank(template)) return false;
        Matcher regexMatcher = regex.matcher(template);
        return regexMatcher.find();
    }

    public static boolean isValid(String template) {
        Matcher start = startRegex.matcher(template);
        long startCount = start.results().count();
        Matcher end = endRegex.matcher(template);
        long endCount = end.results().count();
        return (endCount == startCount);
    }

    public static boolean isValidSyntax(String template) {
        //More work to be done here, for token v2 syntax validation
        if (template.startsWith("/")) {
            final TokenResolution resolution = new XPathTokenResolver(template).resolveToken(Map.of());
            return !resolution.hasTokenSyntaxErrors();
        }
        return true;
    }

    public static boolean hasMultipleTokens(String template) {
        if (StringUtils.isBlank(template)) return false;
        Matcher regexMatcher = regex.matcher(template);
        return regexMatcher.results().count() > 1;
    }

    public static boolean hasOneTokenOnly(String template) {
        if (StringUtils.isBlank(template)) return false;
        StringBuilder builder = new StringBuilder();
        Matcher matcher = regex.matcher(template);
        if(matcher.find()) {
        	builder.append(template.substring(0, matcher.start()));
        	builder.append("");
        	builder.append(template.substring(matcher.end(), template.length()));
        } else {
        	builder.append(template);
        }
        return StringUtils.isBlank(builder.toString().trim());
    }
    

    public String sanitizeAndStripTokenDelimiters(String template){
        return sanitizeTemplate(template).map(sanitizied -> sanitizied.replace("{{", "").replace("}}", "")).orElse(template);
    }

    public Optional<String> sanitizeTemplate(String template) {
        if (StringUtils.isBlank(template) || !hasTokens(template)) return Optional.ofNullable(template);
        Matcher regexMatcher = regex.matcher(template);
        StringBuffer resultString = new StringBuffer();
        while (regexMatcher.find()) {
            String replace = regexMatcher.group().strip();
            // Strip the token identifiers to validate the token.
            replace = replace.substring(2, replace.length()-2);
            String[] finalTempParts = replace.split("\\.");
            for (int j = 0; j < finalTempParts.length; j++) {
                finalTempParts[j] = JtwigModelSanitizer.sanitizeToken(finalTempParts[j]);
            }
            // Glue together and reattach the token identifiers
            replace = "{{" + StringUtils.join(finalTempParts, ".") + "}}";
            regexMatcher.appendReplacement(resultString, replace);
        }
        return Optional.of(regexMatcher.appendTail(resultString).toString());
    }

    public Optional<String> sanitizeTempVar(String template, Map<String, Object> context) {
        if (StringUtils.isBlank(template) || !hasTokens(template)) return Optional.ofNullable(template);
        Matcher regexMatcher = regex.matcher(template);
        StringBuffer resultString = new StringBuffer();
        while (regexMatcher.find()) {
            String replace = regexMatcher.group().strip();
            // Strip the token identifiers to validate the token.
            replace = replace.substring(2, replace.length()-2);
            if (replace.startsWith(SYNCARI_TEMP_VAR_PREFIX)){
                String[] finalTempParts = replace.split("\\.");
                String contextKey = null;
                if ((null != context.get("record")) && (context.get("record") instanceof EntityData)){
                    contextKey = "_"+ ((EntityData)context.get("record")).getSyncariEntityId();
                } else if ((null != context.get("syncariRecord")) && (context.get("syncariRecord") instanceof EntityData)){
                    contextKey = "_"+ ((EntityData)context.get("syncariRecord")).getSyncariEntityId();
                } else{
                    log.warn("Record does not exist in context, record should exist in context");
                }
                if (StringUtils.isNotEmpty(contextKey)){
                    if (!finalTempParts[2].endsWith("]")){
                        finalTempParts[2] = finalTempParts[2] + contextKey;
                    }else{

                        String prefix = finalTempParts[2].substring(0, finalTempParts[2].indexOf("["));
                        String suffix = finalTempParts[2].substring(finalTempParts[2].indexOf("["));
                        finalTempParts[2] = prefix + contextKey + suffix;
                    }
                }
                // Glue together and reattach the token identifiers
                replace = "{{" + StringUtils.join(finalTempParts, ".") + "}}";
                regexMatcher.appendReplacement(resultString, replace);
            }
        }
        return Optional.of(regexMatcher.appendTail(resultString).toString());
    }

    public Optional<String> extractTempVariableName(String template) {
    	if(StringUtils.isBlank(template) || !hasTokens(template)) return Optional.empty();
    	Matcher regexMatcher = regex.matcher(template);
    	while(regexMatcher.find()) {
    		String replace = regexMatcher.group().strip();
    		replace = replace.substring(2, replace.length()-2);
    		if(replace.startsWith(SYNCARI_TEMP_VAR_PREFIX)) {
    			String[] finalTempParts = replace.split("\\.");
                if (finalTempParts.length < 3) {
                    return Optional.empty();
                }
    			return Optional.of(finalTempParts[2]);
    		}
    	}
    	return Optional.empty();
    }
    
    public static String renameTokenPrefixes(Map<String,String> oldToNew,String template){
        if (StringUtils.isBlank(template) || !hasTokens(template)) return template;
        Matcher regexMatcher = regex.matcher(template);
        StringBuffer resultString = new StringBuffer();
        while (regexMatcher.find()) {
            String replace = regexMatcher.group().strip();
            // Strip the token identifiers to validate the token.
            replace = replace.substring(2, replace.length()-2);
            String[] finalTempParts = replace.split("\\.");
            final String nodeName = finalTempParts[0].replaceFirst("Value From |Lookup From |Lookup Count From |Record from |Records from |All Lookup Records From ", "");
            if(oldToNew.containsKey(nodeName)){
                finalTempParts[0] = finalTempParts[0].replace(nodeName,oldToNew.get(nodeName));
            }
            // Glue together and reattach the token identifiers
            replace = "{{" + StringUtils.join(finalTempParts, ".") + "}}";
            regexMatcher.appendReplacement(resultString, replace);
        }
        return regexMatcher.appendTail(resultString).toString();

    }
    public static List<String> extractTokensFromTemplate(String template){
        List<String> tokens = new ArrayList<>();
        if (StringUtils.isBlank(template) || !hasTokens(template)) return tokens;
        Matcher regexMatcher = regex.matcher(template);
        while (regexMatcher.find()) {
            String token = regexMatcher.group().strip();
            // Strip the token identifiers to validate the token.
            if(!token.isBlank()){
                tokens.add(token);
            }
        }
        return tokens;
    }
    
    public static List<String> extractTokensFromTemplateWithoutWrapper(String template){
      return extractTokensFromTemplate(template).stream().map(t -> t.substring(2, t.length() - 2)).collect(Collectors.toList());
  }
    
    private boolean hasFormula(String template) {
    	if(StringUtils.isBlank(template)) return false;
    	Matcher regexMatcher = FORMULA_REGEX.matcher(template);
        return regexMatcher.find();
    }
    
    private String evaluateFormula(Map<String, Object> context, String template) {
        StringBuilder resultString = new StringBuilder();
        Matcher regexMatcher = FORMULA_REGEX.matcher(template);
        while (regexMatcher.find()) {
            String formula = regexMatcher.group().strip();
            String unwrapped = sanitizeTemplate(stripFormulaWrapper(formula)).orElse("");
            String twigFormula = convertFormulaToJTwig(unwrapped);
            var resultPair = evaluateExpression(context, twigFormula);
            String result = "";
            if (resultPair != null && StringUtils.isNotBlank(resultPair.x)) {
                result = resultPair.x;
            }
            regexMatcher.appendReplacement(resultString, result);
        }
        regexMatcher.appendTail(resultString);
        return resultString.toString();
    }
    
    private String convertFormulaToJTwig(String unwrapped) {
    	String[] tokens = unwrapped.split(FORMULA_DELIM);
    	StringBuilder finalFormula = new StringBuilder();
    	for(String token: tokens) {
    		token = token.trim();
    		if(OPERATORS.contains(token)) {
    			finalFormula.append(token);
    		} else {
    			token = token.substring(2, token.length()-2);
                finalFormula.append(token);
    		}
    	}
    	return "{{" + finalFormula + "}}";
    }
    
    private String stripFormulaWrapper(String formula) {
    	if(hasFormula(formula)) {
    		return formula.substring(8, formula.length()-1);
    	} else {
    		return "";
    	}
    }

    public static Map<String, List<String>> extractTokenComponents(String template){
        if (StringUtils.isBlank(template) || !hasTokens(template)) return Map.of();
        Matcher regexMatcher = regex.matcher(template);
        Map<String, List<String>> results = new HashMap<>();
        while (regexMatcher.find()) {
            String replace = regexMatcher.group().strip();
            // Strip the token identifiers to validate the token.
            replace = replace.substring(2, replace.length() - 2);
            String[] finalTempParts = replace.split("\\.");
            results.put(replace, List.of(finalTempParts));
        }
        return results;
    }

}
