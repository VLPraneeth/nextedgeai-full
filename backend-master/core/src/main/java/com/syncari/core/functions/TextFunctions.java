package com.syncari.core.functions;

import com.google.common.net.InternetDomainName;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.service.EncryptionService;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Pair;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.jtwig.util.HtmlUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TextFunctions extends FunctionsBase {
    @Autowired
    EncryptionService encryptionService;
    @Autowired
    AttributeRepo attributeProxyRepo;
    @Autowired
    TokenHelper tokenHelper;
    @Autowired
    SetValueFactory setValueFactory;
    

    private java.util.function.Function<String, java.util.function.Function<String,String>> curriedDomainExtractor = (option) -> i -> {
        try {
            if (isEmail(i)) {
                String domain = i.substring(i.indexOf("@")).substring(1);
                return extract(domain, option);
            } else if (isUrl(i)) {
                try {
                    if (!i.startsWith("https://") && !i.startsWith("http://")) {
                        i = "http://" + i;
                    }
                    URI uri = new URI(i);
                    String host = uri.getHost();
                    return extract(host, option);
                } catch (URISyntaxException e) {
                    return null;
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    };

    @Function
    public String mask(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, i -> "*".repeat(i.length()), null, context);
    }

    @Function
    public String lower(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, String::toLowerCase, null, context);
    }

    @Function
    public Object lowerOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String value = tokenHelper.resolveTokens(context, getConfig("value", functionCall, context));
        if (StringUtils.isBlank(value)) {
            context.addResult(value);
            return getInput(inputs);
        }
        String result = lower(List.of(value), functionCall, context);
        context.addResult(result);
        return getInput(inputs);
    }

    @Function
    public String upper(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, String::toUpperCase, null, context);
    }

    @Function
    public Object upperOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String value = tokenHelper.resolveTokens(context, getConfig("value", functionCall, context));
        if (StringUtils.isBlank(value)) {
            context.addResult(value);
            return getInput(inputs);
        }
        String result = upper(List.of(value), functionCall, context);
        context.addResult(result);
        return getInput(inputs);
    }

    @Function
    public String camelCase(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, i -> StringUtils.join((Arrays.stream(StringUtils.split(i, " "))
                .map(j -> StringUtils.capitalize(j.toLowerCase())).collect(Collectors.toList())), " "), null, context);
    }

    @Function
    public String md5hash(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return md5(functionCall, context);
    }

    @Function
    public Object md5hashOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        md5(functionCall, context);
        return getParam(inputs, context).orElse(null);
    }

    private String md5(FunctionCall functionCall, GraphContext context) {
        String input = tokenHelper.resolveTokens(context, getConfig("input", functionCall, context));
        if (StringUtils.isBlank(input)) {
            context.addResult(input);
            return input;
        }
        context.recordNodeInputs("input", input);

        try {
            String md5 = DigestUtils.md5Hex(input);
            context.put("previousValue", md5);
            context.put("Value From " + context.getCurrentNode().getName(), md5);

            return md5;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Function
    public String capitalize(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, StringUtils::capitalize, null, context);
    }

    @Function
    public String encrypt(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String key = getConfig("key", functionCall, context);
        if (key == null)
            throw new RuntimeException("Key cannot be null for encrypt");
        log.debug("Encrypt: Adding {} to context for transaction log masking", context.get("current_source_attribute_id") + "_encrypted");
        context.put(context.get("current_source_attribute_id") + "_encrypted", true);
        return transform(inputs, i -> encryptionService.encrypt(i, key), null, context);
    }

    @Function
    public String concatenate(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return getConcat(functionCall, context);
    }

    private String getConcat(FunctionCall functionCall, GraphContext context) {
        String seperator = functionCall.getConfig().get("separator")==null? "": functionCall.getConfig().get("separator").toString();
        List<Object> values = (List<Object>) functionCall.getConfig().getOrDefault("values", List.of());
        return values.stream()
                .map(v ->
                        //nuances of how tokens are stored in the context.
                        !v.toString().startsWith("output_") ? tokenHelper.evaluateExpression(context, String.format("{{field_%s}}", v))
                                : tokenHelper.evaluateExpression(context, String.format("{{%s}}", v))
                )
                .filter(pair -> !(pair.y instanceof FilterFailedResult) && StringUtils.isNotBlank(pair.x))
                .map(Pair::getX)
                .reduce((a, b) -> a + seperator + b).orElse(null);
    }

    @Function
    public String decrypt(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String key = getConfig("key", functionCall, context);
        if (key == null)
            throw new RuntimeException("Key cannot be null for decrypt");
        log.debug("Decrypt: Adding {} to context for transaction log masking", context.get("current_sink_attribute_id"));
        context.put("decrypted_sink_attribute_id", context.get("current_sink_attribute_id"));
        return transform(inputs, i -> encryptionService.decrypt(i, key), null, context);
    }

    @Function
    public Object setValue(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        final ProcessFunction processor = setValueFactory.getProcessor(functionCall, FunctionConstants.SET_VALUE);
        final Object result = processor.process(inputs, functionCall, context);
        if (TempVariableProcessor.class.isAssignableFrom(processor.getClass())) {
            return getParam(inputs, context).orElse(null);
        }
        return result;
    }

    @Function
    public Object setValueOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        EntityData syncariRecord = context.getSyncariRecord();
        if(syncariRecord==null){
            //Check if stagedBatchRecord is in context
            StagedBatchRecord stagedBatchRecord = context.getStagedBatchRecord();
            if(stagedBatchRecord!=null){
                syncariRecord = stagedBatchRecord.getEntityData();
            }
        }
        Object input = syncariRecord != null ? syncariRecord : getParam(inputs, context).orElse(null);
        if(input==null || !EntityData.class.isAssignableFrom(input.getClass())){
            try {
                log.warn("Cannot move with setValueOnEntity. Expected EntityData but got {}. " +
                                "Details - inputClass: {}, inputClassLoader: {}, " +
                                "EntityDataClass: {}, EntityDataClassLoader: {}, " +
                                "isAssignable: {}, instanceof: {}, " +
                                "syncariRecordNull: {}, stagedBatchRecordNull: {}, inputsSize: {}",
                        input,
                        input != null ? input.getClass().getName() : "null",
                        input != null && input.getClass().getClassLoader() != null ? input.getClass().getClassLoader() : "null",
                        EntityData.class.getName(),
                        EntityData.class.getClassLoader() != null ? EntityData.class.getClassLoader() : "bootstrap",
                        input != null ? EntityData.class.isAssignableFrom(input.getClass()) : "N/A",
                        input instanceof EntityData,
                        context != null ? context.getSyncariRecord() == null : "N/A",
                        context != null ? context.getStagedBatchRecord() == null : "N/A",
                        inputs != null ? inputs.size() : 0);
            } catch (Exception e) {
                //For extra safety
                log.warn(e.getMessage(), e);
            }
            return input;
        }
        var res = setValueFactory.getProcessor(functionCall, FunctionConstants.SET_VALUE_ON_ENTITY).process(inputs, functionCall, context);
        return res instanceof EntityData ? res :input;
    }

    @Function
    public List<String> split(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String rawDelimiter = getConfigOrDefault("delimiter", functionCall, ",", context);
        String delimiter = rawDelimiter.equalsIgnoreCase("|") ? "\\|" : rawDelimiter;
        return transform(inputs, i -> Arrays.asList(i.split(delimiter)), List.of(), context);
    }

    @Function
    public Object splitOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String value = tokenHelper.resolveTokens(context, getConfig("value", functionCall, context));
        if (StringUtils.isBlank(value)) {
            context.addResult(value);
            return getInput(inputs);
        }
        String delimiter = getConfigOrDefault("delimiter", functionCall, ",", context);
        List<String> result = transform(List.of(value), i -> Arrays.asList(i.split(delimiter)), List.of(), context);
        context.addResult(result);
        return getInput(inputs);
    }

    @Function
    public boolean isEmpty(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, StringUtils::isBlank, true, context);
    }

    protected <T> T transform(List<Object> inputs, java.util.function.Function<String, T> transformer, T defaultValue, GraphContext context) {
        Optional<Object> params = getParam(inputs, context);
        final T t = params.map(Object::toString).map(transformer).orElse(defaultValue);
        if (null != context){
            context.recordNodeInputs("input", t);
        }
        return t;
    }

    private List<Object> sanitizeInput(List<Object> inputs) {
        return inputs.stream()
                .map(object -> (null == object) ? "" : object)
                .collect(Collectors.toList());
    }

    protected <I, O> O transformObject(List<Object> inputs, java.util.function.Function<I, O> transformer, O defaultValue, GraphContext context) {
        Optional<I> params = getParam(inputs, context);
        return params.map(transformer).orElse(defaultValue);
    }


    protected String transformList(List<Object> inputs,
                                   java.util.function.Function<List<String>, Optional<String>> transformer) {
        List<String> params = getParams(inputs);
        return transformer.apply(params).orElse(null);
    }


    @Function
    public String encode(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, i -> Base64.getEncoder().encodeToString(i.getBytes()), null, context);
    }

    @Function
    public String decode(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, i -> new String(Base64.getDecoder().decode(i)), null, context);
    }

    @Function
    public String ltrim(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, String::stripLeading, null, context);
    }

    @Function
    public String rtrim(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, String::stripTrailing, null, context);
    }

    @Function
    public String trim(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, i -> i == null ? null : i.strip(), null, context);
    }

    @Function
    public Integer length(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transformObject(inputs, i -> i.toString().length(), 0, context);
    }

    @Function
    public Object lengthOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String value = tokenHelper.resolveTokens(context, getConfig("value", functionCall, context));
        if (value == null) {
            context.addResult(value);
            return getInput(inputs);
        }
        Integer result = transformObject(List.of(value), i -> i.toString().length(), 0, context);
        context.addResult(result);
        return getInput(inputs);
    }

    @Function
    public String jwtToken(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return getJWTToken(functionCall, context);
    }

    @Function
    public Object jwtTokenOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String result = getJWTToken(functionCall, context);
        context.addResult(result);
        return getInput(inputs);
    }

    protected String getJWTToken(FunctionCall functionCall, GraphContext context) {
        String headers = tokenHelper.resolveTokens(context, getConfig("headers", functionCall, context));
        String claimsMap = tokenHelper.resolveTokens(context, getConfig("claims", functionCall, context));
        String signingKey = tokenHelper.resolveTokens(context, getConfig("signingKey", functionCall, context));
        if (StringUtils.isBlank(signingKey)) {
            context.addResult(signingKey);
            return signingKey;
        }
        JwsHeader jwsHeader = Jwts.jwsHeader();
        Map<String, String> headerMap = populateMap(headers);
        if(!headerMap.containsKey("alg") || StringUtils.isBlank(headerMap.get("alg"))) {
            headerMap.put("alg", "HS256");
        }
        if(!headerMap.containsKey("typ") || StringUtils.isBlank(headerMap.get("typ"))) {
            headerMap.put("typ", "JWT");
        }
        jwsHeader.putAll(headerMap);
        Claims claims = Jwts.claims();
        claims.putAll(populateMap(claimsMap));
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        if(headerMap.containsKey("alg") && !StringUtils.isBlank(headerMap.get("alg"))) {
            try {
                signatureAlgorithm = SignatureAlgorithm.valueOf(headerMap.get("alg"));
            } catch (Exception e) {
            }
        }
        String result = Jwts.builder().setHeaderParams(jwsHeader).setClaims(claims)
                .signWith(signatureAlgorithm, Base64.getEncoder().encodeToString(signingKey.getBytes())).compact();
        context.addResult(result);
        return result;
    }

    private Map<String, String> populateMap(String value) {
        Map<String, String> map = new HashMap<>();
        if(!StringUtils.isBlank(value)) {
            List<String> entries = List.of(value.split(","));
            for (String e : entries) {
                String[] parts = e.split(":");
                if(parts.length == 2 && parts[0] != null) {
                    map.put(parts[0], parts[1]);
                }
            }
        }
        return  map;
    }

    @Function
    public String lpad(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        Object times = getConfig("size", functionCall, context);
        String timesString = Objects.isNull(times) ? "0" : times.toString();
        String padString = getConfig("pad", functionCall, context);
        inputs = sanitizeInput(inputs);
        return transform(inputs, i -> StringUtils.leftPad(i, Integer.valueOf(timesString), StringUtils.isBlank(padString) ? "" : padString), null, context);
    }

    @Function
    public String rpad(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        Object times = getConfig("size", functionCall, context);
        String timesString = Objects.isNull(times) ? "0" : times.toString();
        String padString = getConfig("pad", functionCall, context);
        inputs = sanitizeInput(inputs);
        return transform(inputs, i -> StringUtils.rightPad(i, Integer.valueOf(timesString), StringUtils.isBlank(padString) ? "" : padString), null, context);
    }

    @Function
    public String replace(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String search = tokenHelper.resolveTokens(context, getConfig("searchExpression", functionCall, context));
        String replaceWith = tokenHelper.resolveTokens(context, getConfig("replaceWith", functionCall, context));
        Boolean caseInsensitive = BooleanType.VALUE.convert(getConfigOrDefault("caseInsensitiveSearch", functionCall, "false", context));
        Pattern pattern = caseInsensitive == null || !caseInsensitive ? Pattern.compile(search) : Pattern.compile(search, Pattern.CASE_INSENSITIVE);
        String sanitizeReplaceWith = sanitizeReplaceWith(replaceWith);
        return transform(inputs, i -> pattern.matcher(i).replaceAll(sanitizeReplaceWith), null, context);
    }

    @Function
    public Object extractText(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String input = tokenHelper.resolveTokens(context, getConfig("input", functionCall, context));
        String search = tokenHelper.resolveTokens(context, getConfig("searchExpression", functionCall, context));
        Boolean findAllMatches = BooleanType.VALUE.convert(getConfigOrDefault("findAllMatches", functionCall, "false", context));
        Boolean caseInsensitive = BooleanType.VALUE.convert(getConfigOrDefault("caseInsensitiveSearch", functionCall, "false", context));
        Pattern pattern = caseInsensitive == null || !caseInsensitive ? Pattern.compile(search) : Pattern.compile(search, Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(input);
        List<String> matches = new ArrayList<>();
        while (m.find()) {
            matches.add(m.group());
        }
        if (!matches.isEmpty()) {
            return findAllMatches ? matches : matches.get(0);
        }
        return null;
    }

    private String sanitizeReplaceWith(String replaceWith) {
        if (replaceWith == null) return "";
        switch (replaceWith) {
            case "\\n": return "\n";
            case "\\t": return  "\t";
            case "\\r": return "\r";
            default: return replaceWith;
        }
    }

    @Function
    public Object replaceOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String value = getConfig("value", functionCall, context);
        if (StringUtils.isBlank(value)) {
            context.addResult(value);
            return getInput(inputs);
        }
        String resolvedValue = tokenHelper.resolveTokens(context, value);
        String replaced = replace(List.of(resolvedValue), functionCall, context);
        context.put("previousValue", replaced);
        context.put("Value From " + context.getCurrentNode().getName(), replaced);
        return getInput(inputs);
    }

    @Function
    public String substring(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return getSubString(inputs, functionCall, context);
    }

    @Function
    public Object substringOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String value = tokenHelper.resolveTokens(context, getConfig("value", functionCall, context));
        if (StringUtils.isBlank(value)) {
            context.addResult(value);
            return getInput(inputs);
        }
        String result = getSubString(List.of(value), functionCall, context);
        context.addResult(result);
        return getInput(inputs);
    }

    private String getSubString(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        Object begin = getConfigOrDefault("startIndex", functionCall, "0", context);
        Object end = getConfigOrDefault("endIndex", functionCall, "-1", context);

        int beginIndex = Integer.parseInt(StringUtils.isBlank(begin.toString()) ? "0" : begin.toString());
        int endIndex = Integer.parseInt(StringUtils.isBlank(end.toString()) ? "-1" : end.toString());
        java.util.function.Function<String, String> sub = (value) -> {
            if (StringUtils.isBlank(value)) {
                return value;
            }
            if (endIndex == -1) {
                return value.substring(beginIndex);
            } else if (endIndex < beginIndex) {
                return value;
            } else if (endIndex > value.length()) {
                return value;
            } else {
                return value.substring(beginIndex, endIndex);
            }
        };
        return transform(inputs, sub, null, context);
    }

    @Function
    public char charAt(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return getCharAt(inputs, functionCall, context);
    }

    @Function
    public Object charAtOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String value = tokenHelper.resolveTokens(context, getConfig("value", functionCall, context));
        if (StringUtils.isBlank(value)) {
            context.addResult(value);
            return getInput(inputs);
        }
        int index;
        String indexVal = tokenHelper.resolveTokens(context, getConfig("index", functionCall, context));
        try {
            index = Integer.parseInt(indexVal);
        } catch (NumberFormatException e) {
            log.error("Invalid index {}", indexVal);
            return getInput(inputs);
        }
        char result = value.charAt(index);
        context.addResult(result);
        return getInput(inputs);
    }

    private char getCharAt(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String value = tokenHelper.resolveTokens(context, getConfig("value", functionCall, context));
        Object index = getConfigOrDefault("index", functionCall, "0", context);

        int indexVal = Integer.parseInt(StringUtils.isBlank(index.toString()) ? "0" : index.toString());
        if (StringUtils.isBlank(value)) {
            return 0;
        }
        return value.charAt(indexVal);
    }

    @Function
    public String numberFormat(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        int numberOfFractionalDigits = Integer.parseInt(functionCall.getConfig().getOrDefault("noofractionalDigits", "2").toString());
        String decimalSeparator = functionCall.getConfig().getOrDefault("decimalSeparator", ".").toString();
        String groupingSeparator = functionCall.getConfig().getOrDefault("groupingSeparator", ",").toString();
        StringUtils.repeat("#", numberOfFractionalDigits);
        try {
            return new  DecimalFormat(String.format("#%s###%s%s",groupingSeparator,decimalSeparator,StringUtils.repeat("#", numberOfFractionalDigits)))
                    .format(DoubleType.VALUE.convert(inputs.isEmpty() ? "" : inputs.get(0).toString()));
        }catch(Exception e) {
            log.error(e.getMessage(), e);
        }
        return inputs.isEmpty() ? "" : inputs.get(0).toString();
    }

    @Function
    public int indexOf(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String searchString = getConfig("searchString", functionCall, context);
        Optional<String> params = getParam(functionCall, context);
        return transform(inputs, i -> searchString == null ? -1 : i.indexOf(searchString), -1, context);
    }

    @Function
    public boolean contains(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String searchText = getConfig("searchText", functionCall, context);
        return transform(inputs, i -> searchText != null && i.contains(searchText), false, context);
    }

    @Function
    public String extractDomain(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        if (inputs == null || inputs.isEmpty()) return null;
        String option = getConfigOrDefault("option", functionCall, "fullDomain", context);
        return transform(inputs, curriedDomainExtractor.apply(option), null, context);
    }

    @Function
    public Object extractDomainOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String option = getConfigOrDefault("option", functionCall, "fullDomain", context);
        String value = tokenHelper.resolveTokens(context, getConfigOrDefault("value", functionCall, "", context));
        if (StringUtils.isBlank(value)) {
            context.addResult(value);
            return getInput(inputs);
        }
        String extractedDomain = curriedDomainExtractor.apply(option).apply(value);
        context.addResult(extractedDomain);
        return getParam(inputs, context).orElse(null);
    }

    private String extract(String domain, String option) {
        if(domain == null) return null;
        String[] parts = domain.split("\\.");
        if(parts.length < 2) return null;
        switch (option) {
        case "tld":
            return InternetDomainName.from(domain).topPrivateDomain().toString();
        case "name":
            return InternetDomainName.from(domain).topPrivateDomain().parts().get(0);
        case "fullDomain":
        default:
            return domain;
        }
    }

    @Function
    public String removeNonPrintable(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, i -> i.replaceAll("\\p{Cntrl}", ""), null, context);
    }

    @Function
    public String reverseString(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, i -> StringUtils.reverse(i), null, context);
    }

    @Function
    public String urlEncode(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return transform(inputs, i -> URLEncoder.encode(i, Charset.defaultCharset()), null, context);
    }

    @Function
    public String striptags(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String inputTags = getConfig("allowedTags", functionCall, context);
        String allowedTags = inputTags != null ? inputTags : "";
        return transform(inputs, i -> HtmlUtils.stripTags(i, allowedTags), null, context);
    }

    @Function
    public String uuid(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        return UUID.randomUUID().toString();
    }

    @Function
    public Object extractValue(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        String fieldName = getConfig("fieldName", functionCall, context);
        return tokenHelper.resolveTokensObject(context, fieldName);
    }

    private boolean isEmail(String input) {
        return input != null && input.contains("@");
    }

    private boolean isUrl(String input) {
        try {
            new URI(input);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
