package com.syncari.connector.service;

import static java.lang.String.format;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.binary.Base64;

import com.syncari.connector.config.AuthConfig;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class OAuth1Authorization {

    private static final String ENCODING = "UTF-8";
    private static final String OAUTH_HMAC_SHA256 = "HMAC-SHA256";
    private static final String SIGNATURE_METHOD = "HmacSHA256";
    private static final String AUTHORIZATION = "OAuth %s";
    private static final String AUTHORIZATION_KEY_VALUE = "%s=\"%s\"";
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String VERSION = "1.0";

    // OAuth Authorization keys
    private static final String OAUTH_CONSUMER_KEY = "oauth_consumer_key";
    private static final String OAUTH_NONCE = "oauth_nonce";
    private static final String OAUTH_SIGNATURE_METHOD = "oauth_signature_method";
    private static final String OAUTH_TOKEN = "oauth_token";
    private static final String OAUTH_TIMESTAMP = "oauth_timestamp";
    private static final String OAUTH_VERSION = "oauth_version";
    private static final String OAUTH_SIGNATURE = "oauth_signature";
    private static final String OAUTH_REALM = "realm";

    AuthConfig authConfig;
    String realm;
    String url;
    String method;

    public OAuth1Authorization(AuthConfig authConfig) {
        this.authConfig = authConfig;
    }

    private Map<String,String> getUrlParams(String value) throws UnsupportedEncodingException {
        Map<String,String> res = new HashMap<String,String>();
        if (value == null || value == "") {
            return res;
        }
        for (String s : value.split("&")) {
            List<String> kv = Arrays.asList(s.split("="));
            if (kv.size()>1) {
                // RFC 5849 section 3.4.1.3.1 and 3.4.1.3.2 specify that parameter names
                // and values are decoded then encoded before being sorted and concatenated
                // Section 3.6 specifies that space must be encoded as %20 and not +
                String encName = URLEncoder.encode(URLDecoder.decode(kv.get(0), ENCODING), ENCODING).replace("+", "%20");
                String encValue = URLEncoder.encode(URLDecoder.decode(kv.get(1), ENCODING), ENCODING).replace("+", "%20");
                res.put(encName,encValue);
            }
        }
        return res;
    }

    private String createBaseString(Map<String,String> oauthParams, HttpRequest req, String host) throws UnsupportedEncodingException {
        Map<String,String> p = new HashMap<String, String>();
        p.putAll(oauthParams);

        Integer n = host.indexOf("?");
        if (n>-1) {
            p.putAll(getUrlParams(host.substring(n+1)));
            host = host.substring(0,n);
        }
        List<String> keys = new ArrayList<String>();
        keys.addAll(p.keySet());
        Collections.sort(keys);
        String s = keys.get(0) + "=" + p.get(keys.get(0));
        for (Integer i=1; i<keys.size(); i++) {
            s = s + "&" + keys.get(i) + "=" + p.get(keys.get(i));
        }

        // According to OAuth spec, host string should be lowercased, but Google and LinkedIn
        // both expect that case is preserved.
        return method + '&' +
                URLEncoder.encode(host, ENCODING) + '&' +
                URLEncoder.encode(s, ENCODING);
    }

    // Credit: https://salesforce.stackexchange.com/questions/5569/oauth-1-0-oauth-signature-generation-and-header-creation-for-the-authentication
    public String sign(String nOnce, String timestamp) throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {

        Map<String, String> parameters = Map.of(OAUTH_CONSUMER_KEY, authConfig.getConsumerKey(),
                OAUTH_NONCE, nOnce,
                OAUTH_SIGNATURE_METHOD, OAUTH_HMAC_SHA256,
                OAUTH_TIMESTAMP, timestamp,
                OAUTH_TOKEN, authConfig.getTokenId(),
                OAUTH_VERSION, VERSION);
        String baseString = createBaseString(
                parameters,
                null, url);

        String secret = authConfig.getConsumerSecret() + "&" + authConfig.getTokenSecret();
        Mac sha256_HMAC = Mac.getInstance(SIGNATURE_METHOD);
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), SIGNATURE_METHOD);
        sha256_HMAC.init(secret_key);
        String sig = Base64.encodeBase64String(sha256_HMAC.doFinal(baseString.getBytes()));
        String signature = URLEncoder.encode(sig, ENCODING);

        return signature;
    }

    // Generate a random byte array for cryptographic use.
    private static byte[] generateRandomBytes(final int size) {
        final byte[] key = new byte[size];
        secureRandom.nextBytes(key);
        return key;
    }

    public String getAuthorization() {
        final String nOnce = DatatypeConverter.printHexBinary(generateRandomBytes(16)).toLowerCase(Locale.US);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String authVal = "";
        try {
            String generatedSignature = sign(nOnce, timestamp);
            authVal = authVal
                    .concat(format(AUTHORIZATION_KEY_VALUE, OAUTH_REALM, realm)).concat(",")
                    .concat(format(AUTHORIZATION_KEY_VALUE, OAUTH_CONSUMER_KEY, authConfig.getConsumerKey())).concat(",")
                    .concat(format(AUTHORIZATION_KEY_VALUE, OAUTH_TOKEN, authConfig.getTokenId())).concat(",")
                    .concat(format(AUTHORIZATION_KEY_VALUE, OAUTH_SIGNATURE_METHOD, OAUTH_HMAC_SHA256)).concat(",")
                    .concat(format(AUTHORIZATION_KEY_VALUE, OAUTH_TIMESTAMP, timestamp)).concat(",")
                    .concat(format(AUTHORIZATION_KEY_VALUE, OAUTH_NONCE, nOnce)).concat(",")
                    .concat(format(AUTHORIZATION_KEY_VALUE, OAUTH_VERSION, VERSION)).concat(",")
                    .concat(format(AUTHORIZATION_KEY_VALUE, OAUTH_SIGNATURE, generatedSignature));
        } catch (Exception e) {
            log.error(format("Error in generating authorization header: %s", e.getMessage()));
            throw new RuntimeException(e);
        }
        return format(AUTHORIZATION, authVal);
    }
}
