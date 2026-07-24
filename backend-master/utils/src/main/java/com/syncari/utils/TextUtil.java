package com.syncari.utils;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextUtil {
    public static final String VALID_EMAIL_REGEX = "^(?=.{1,64}@)[A-Za-z0-9\\+_-]+(\\.[A-Za-z0-9\\+_-]+)*@" 
            + "[^-][A-Za-z0-9\\+-]+(\\.[A-Za-z0-9\\+-]+)*(\\.[A-Za-z]{2,4})$";


    // remove all special characters except underscore and dash, then replace consecutive blank spaces with single underscore, return string with lowercase
    public static String createApiName(String name){
        return name.replaceAll("[^a-zA-Z0-9\\s+_-]", "")
                .trim()
                .replaceAll("\\s+", "_")
                .toLowerCase();
    }

    // remove all special characters except underscore and dash, then replace consecutive blank spaces with single underscore, returns without lowercase
    public static String createApiNameWOLowercase(String name){
        return name.replaceAll("[^a-zA-Z0-9\\s+_-]", "")
                .trim()
                .replaceAll("\\s+", "_");
    }

    public static String toTokenName(String value) {
        return StringUtils.isBlank(value) ? value : value.replaceAll("[^a-zA-Z0-9_]+", "_");
    }

    public boolean isValidApiName(String name) {
        return !StringUtils.isBlank(name) && name.matches("^[-_+:a-zA-Z0-9]*$");
    }

    public static boolean isValidEmail(String email) {
        if (StringUtils.isBlank(email)) return false;
        Pattern r = Pattern.compile(VALID_EMAIL_REGEX);
        Matcher m = r.matcher(email);
        return m.matches();
    }

    public static byte[] getSha(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
    }

    public static String sanitizeHTML(String untrustedHTML){
        PolicyFactory policy = new HtmlPolicyBuilder()
                .allowAttributes("href", "target").onElements("a")
                .allowElements("a")
                .allowStandardUrlProtocols().toFactory();

        return policy.sanitize(untrustedHTML);
    }

    public static String hmacSha1InHex(String value, String key) {
        try {
            // Get an hmac_sha1 key from the raw key bytes
            byte[] keyBytes = key.getBytes();
            SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA1");

            // Get an hmac_sha1 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);

            // Compute the hmac on input data bytes
            byte[] rawHmac = mac.doFinal(value.getBytes());

            // Convert raw bytes to Hex
            byte[] hexBytes = new Hex().encode(rawHmac);

            //  Covert array of Hex bytes to a String
            return new String(hexBytes, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static String hmacSha256InHex(String value, String key) {
      try {
          // Get an HmacSHA256 key from the raw key bytes
          byte[] keyBytes = key.getBytes();
          SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA256");

          // Get an HmacSHA256 Mac instance and initialize with the signing key
          Mac mac = Mac.getInstance("HmacSHA256");
          mac.init(signingKey);

          // Compute the hmac on input data bytes
          byte[] rawHmac = mac.doFinal(value.getBytes());

          // Convert raw bytes to Hex
          byte[] hexBytes = new Hex().encode(rawHmac);

          //  Covert array of Hex bytes to a String
          return new String(hexBytes, "UTF-8");
      } catch (Exception e) {
          throw new RuntimeException(e);
      }
  }

    public static String sanitizeFieldName(String fieldName) {
        String sanitized = fieldName.replaceAll("\\.", "_");
        sanitized = StringUtils.strip(sanitized, "-_");
        return sanitized;
    }
}
