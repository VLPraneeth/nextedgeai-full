package com.syncari.core.token;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.syncari.connector.EntityData;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.TokenResolutionException;
import com.syncari.core.model.ActionResult;
import com.syncari.core.model.Instance;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.jtwig.TokenEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.jtwig.environment.DefaultEnvironmentConfiguration;
import org.jtwig.environment.EnvironmentFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@Slf4j
public class TokenHelperTest {
    TokenEnvironment environment = new TokenEnvironment(new EnvironmentFactory().create(new DefaultEnvironmentConfiguration()), Map.of());
    TokenHelper tokenHelper = new TokenHelper(environment);
    private static Instance lastInstance;
    private static boolean lastInstanceCaptured;

    @After
    public void tearDown() {
        SyncariContext.setInstance(lastInstance);
    }

    @Before
    public void setUp() {
        if (lastInstance == null && !lastInstanceCaptured) {
            lastInstance = SyncariContext.getInstance();
            lastInstanceCaptured = true;
        }
    }

    @Test
    public void tokensSanitization() {
        String token = "{{S3 Transactions.syncari_liquid_transactions}}";
        assertEquals("{{S3_Transactions.syncari_liquid_transactions}}", tokenHelper.sanitizeTemplate(token).get());
        String multiToken = "{{S3 Transactions.syncari_liquid_transactions.AUTHORIZATION_DATE}} {{S3 Transactions.syncari_liquid_transactions.MERCHANT_NAME}} {{S3 Transactions.syncari_liquid_transactions.CARDHOLDER}} {{S3 Transactions.syncari_liquid_transactions.TRIP_NAME}}";
        assertEquals("{{S3_Transactions.syncari_liquid_transactions.AUTHORIZATION_DATE}} {{S3_Transactions.syncari_liquid_transactions.MERCHANT_NAME}} {{S3_Transactions.syncari_liquid_transactions.CARDHOLDER}} {{S3_Transactions.syncari_liquid_transactions.TRIP_NAME}}", tokenHelper.sanitizeTemplate(multiToken).get());
        String multiTokenAddtnlSpace = "{{S3 Transactions.syncari_liquid_transactions.AUTHORIZATION_DATE}}   {{S3 Transactions.syncari_liquid_transactions.MERCHANT_NAME}} {{S3 Transactions.syncari_liquid_transactions.CARDHOLDER}} {{S3 Transactions.syncari_liquid_transactions.TRIP_NAME}}";
        assertEquals("{{S3_Transactions.syncari_liquid_transactions.AUTHORIZATION_DATE}}   {{S3_Transactions.syncari_liquid_transactions.MERCHANT_NAME}} {{S3_Transactions.syncari_liquid_transactions.CARDHOLDER}} {{S3_Transactions.syncari_liquid_transactions.TRIP_NAME}}", tokenHelper.sanitizeTemplate(multiTokenAddtnlSpace).get());
    }

    @Test
    public void testComplexExpression() {

        GraphContext map = new GraphContext();
        map.put("key", Map.of("foo", List.of(Map.of("bar", "baz"))));

        tokenHelper.resolveTokenV2(map, "{{key.foo[0].bar}}").getX();

        String result = tokenHelper.resolveTokenV2(map, "{{key.foo[0].bar}}").getX();
        assertEquals("baz", result);

        map = new GraphContext();
        map.put("key", Map.of("foo", List.of(Map.of("bar", List.of(Map.of("baz", "aux"))))));

        result = tokenHelper.resolveTokenV2(map, "{{key.foo[0].bar[0].baz}}").getX();

        assertEquals("aux", result);

        map = new GraphContext();
        map.put("key", Map.of("foo", List.of(Map.of("bar", List.of(Map.of("_baz-", "auxilary"))))));

        result = tokenHelper.resolveTokenV2(map, "{{key.foo[0].bar[0]._baz-}}").getX();

        assertEquals("auxilary", result);
    }

    @Test
    public void testComplexExpressionTempVariable() {

        GraphContext map = new GraphContext();
        map.put("syncari", Map.of("temp",Map.of("foo_test", List.of(Map.of("bar", "baz")))));
        map.put("record", new EntityData().setSyncariEntityId("test"));

        String result = tokenHelper.resolveTokenV2(map, "{{syncari.temp.foo[0].bar}}").getX();
        assertEquals("baz", result);

        map = new GraphContext();
        map.put("syncari", Map.of("temp",Map.of("foo_test", List.of(Map.of("bar", List.of(Map.of("baz", "aux")))))));
        map.put("record", new EntityData().setSyncariEntityId("test"));
        result = tokenHelper.resolveTokenV2(map, "{{syncari.temp.foo[0].bar[0].baz}}").getX();

        assertEquals("aux", result);

        map = new GraphContext();
        map.put("syncari", Map.of("temp",Map.of("foo_test", List.of(Map.of("bar", List.of(Map.of("_baz", "auxilary")))))));
        map.put("record", new EntityData().setSyncariEntityId("test"));
        result = tokenHelper.resolveTokenV2(map, "{{syncari.temp.foo[0].bar[0]._baz}}").getX();

        assertEquals("auxilary", result);
    }

    @Test
    public void testTempVariableObject() {

        GraphContext map = new GraphContext();
        map.put("syncari", Map.of("temp",Map.of("foo", "baz")));
        String result = tokenHelper.resolveTokenV2(map, "{{syncari.temp.foo}}").getX();
        assertEquals("baz", result);

        map = new GraphContext();
        map.put("syncari", Map.of("temp",Map.of("foo_test", "test")));
        result = tokenHelper.resolveTokenV2(map, "{{syncari.temp.foo_test}}").getX();
        assertEquals("test", result);
    }

    @Test
    public void testTokenFeatureCompartor() {

        GraphContext map = new GraphContext();
        map.put("key", Map.of("foo", List.of(Map.of("bar", "baz"))));

        var pair1 = tokenHelper.resolveTokenV2(map, "{{key.foo[0].abc}}");

        var pair2 = tokenHelper.resolveJTwigToken(map, "{{key.foo[0].bar}}");

        assertFalse(tokenHelper.tokenComparator.test(pair1, pair2));

        pair1 = tokenHelper.resolveTokenV2(map, "{{key.foo[0].bar}}");

        pair2 = tokenHelper.resolveJTwigToken(map, "{{key.foo[0].bar}}");

        assertFalse(tokenHelper.tokenComparator.test(pair1, pair2));

        pair1 = tokenHelper.resolveTokenV2(map, "{{key.foo[0].abc}}");

        pair2 = tokenHelper.resolveJTwigToken(map, "{{key.foo[0].abc}}");

        assertTrue(tokenHelper.tokenComparator.test(pair1, pair2));
    }

    @Test
    public void testTokenSpecialCharacters() {

        GraphContext map = new GraphContext();
        map.put("Salesforce | Production", Map.of("Account", Map.of("phone", "1234567890")));

        var pair1 = tokenHelper.resolveTokenV2(map, "{{Salesforce | Production.Account.phone}}");
        var pair2 = tokenHelper.resolveJTwigToken(map, "{{Salesforce | Production.Account.phone}}");

        assertEquals(pair1.getX(), pair2.getX());

        map = new GraphContext();
        map.put("Salesforce & Production", Map.of("Account", Map.of("phone", "1234567890")));

        pair1 = tokenHelper.resolveTokenV2(map, "{{Salesforce & Production.Account.phone}}");
        pair2 = tokenHelper.resolveJTwigToken(map, "{{Salesforce & Production.Account.phone}}");

        assertEquals(pair1.getX(), pair2.getX());

        map = new GraphContext();
        map.put("Salesforce - Production", Map.of("Account", Map.of("phone", "1234567890")));

        pair1 = tokenHelper.resolveTokenV2(map, "{{Salesforce - Production.Account.phone}}");
        pair2 = tokenHelper.resolveJTwigToken(map, "{{Salesforce - Production.Account.phone}}");

        assertEquals(pair1.getX(), pair2.getX());
    }

    @Test
    public void tokenReplacements(){
        assertEquals("{{Value From S3 Transactions From QS.syncari_liquid_transactions}}",
                tokenHelper.renameTokenPrefixes(Map.of("S3 Transactions","S3 Transactions From QS"),
                        "{{Value From S3 Transactions.syncari_liquid_transactions}}"));
        assertEquals("{{Record from S3 Transactions From QS.syncari_liquid_transactions}} and {{previous}}",
                tokenHelper.renameTokenPrefixes(Map.of("S3 Transactions","S3 Transactions From QS"),
                        "{{Record from S3 Transactions.syncari_liquid_transactions}} and {{previous}}"));
        assertEquals("{{Lookup From S3 Transactions From QS.syncari_liquid_transactions}} and {{previous}}",
                tokenHelper.renameTokenPrefixes(Map.of("S3 Transactions","S3 Transactions From QS"),
                        "{{Lookup From S3 Transactions.syncari_liquid_transactions}} and {{previous}}"));
        assertEquals("{{Lookup Count From S3 Transactions From QS.syncari_liquid_transactions}} and {{previous}}",
                tokenHelper.renameTokenPrefixes(Map.of("S3 Transactions","S3 Transactions From QS"),
                        "{{Lookup Count From S3 Transactions.syncari_liquid_transactions}} and {{previous}}"));
        assertEquals("{{Records from S3 Transactions From QS.syncari_liquid_transactions}} and {{previous}}",
                tokenHelper.renameTokenPrefixes(Map.of("S3 Transactions","S3 Transactions From QS"),
                        "{{Records from S3 Transactions.syncari_liquid_transactions}} and {{previous}}"));
        assertEquals("{{All Lookup Records From S3 Transactions From QS.syncari_liquid_transactions}} and {{previous}}",
                tokenHelper.renameTokenPrefixes(Map.of("S3 Transactions","S3 Transactions From QS"),
                        "{{All Lookup Records From S3 Transactions.syncari_liquid_transactions}} and {{previous}}"));
    }

    @Test
    public void tokenNumericPartsCase() {
        GraphContext graphContext = new GraphContext().set("Google_Sheets", 
            Map.of("1vWpgiN35p__fLYCISEL3vFa9JLN3Jcey", Map.of("company", "COMPANYVAL")));
        String token = "{{Google_Sheets.1vWpgiN35p__fLYCISEL3vFa9JLN3Jcey.company}}";
        assertEquals("{{Google_Sheets._1vWpgiN35p__fLYCISEL3vFa9JLN3Jcey.company}}", tokenHelper.sanitizeTemplate(token).get());
        assertEquals("COMPANYVAL", tokenHelper.resolveTokens(graphContext, token));

        graphContext = new GraphContext().set("1Google_Sheets", "GoogleSheet");
        token = "{{1Google_Sheets}}";
        assertEquals("{{_1Google_Sheets}}", tokenHelper.sanitizeTemplate(token).get());
        assertEquals("GoogleSheet", tokenHelper.resolveTokens(graphContext, token));

        graphContext = new GraphContext().set("Google_Sheets", 
            Map.of("s1vWpgiN35p__fLYCISEL3vFa9JLN3Jcey", Map.of("1company", "1COMPANYVAL")));
        token = "{{Google_Sheets.s1vWpgiN35p__fLYCISEL3vFa9JLN3Jcey.1company}}";
        assertEquals("{{Google_Sheets.s1vWpgiN35p__fLYCISEL3vFa9JLN3Jcey._1company}}", tokenHelper.sanitizeTemplate(token).get());
        assertEquals("1COMPANYVAL", tokenHelper.resolveTokens(graphContext, token));

        graphContext = new GraphContext().set("1Google_Sheets", 
            Map.of("2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey", Map.of("3company", "1All_COMPANYVAL")));
        token = "{{1Google_Sheets.2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey.3company}}";
        assertEquals("{{_1Google_Sheets._2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey._3company}}", tokenHelper.sanitizeTemplate(token).get());
        assertEquals("1All_COMPANYVAL", tokenHelper.resolveTokens(graphContext, token));

        // Multitokens
        graphContext = new GraphContext().set("1Google_Sheets", "GoogleSheets")
            .set("2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey", Map.of("3company", "1All_COMPANYVAL"));
        token = "{{1Google_Sheets}} {{2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey.3company}}";
        assertEquals("{{_1Google_Sheets}} {{_2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey._3company}}", tokenHelper.sanitizeTemplate(token).get());
        assertEquals("GoogleSheets 1All_COMPANYVAL", tokenHelper.resolveTokens(graphContext, token));
    }

    @Test
    public void negativeCases() {
        try {
            GraphContext graphContext = new GraphContext().set("", "GoogleSheet");
            String token = "{{}}";
            assertEquals("{{}}", tokenHelper.sanitizeTemplate(token).get());
            assertEquals("{{}}", tokenHelper.resolveTokenV2(graphContext, token).getX());
        } catch (TokenResolutionException e) {
            log.info("Expected exception thrown " + e.getMessage());
            fail();
        }

        // Partial tokens throw error
        try {
            GraphContext graphContext = new GraphContext().set("Key", "GoogleSheet");
            String token = "{{Key}";
            assertEquals("{{Key}", tokenHelper.sanitizeTemplate(token).get());
            assertEquals("{{Key}", tokenHelper.resolveTokenV2(graphContext, token).getX());
        } catch (TokenResolutionException e) {
            fail();
        }
        // Partial tokens throw error
        try {
            Map<String, String> map = new HashMap<>();
            map.put("key2", null);
            GraphContext graphContext = new GraphContext().set("Key", new Object());
            String token = "{{Key.key2.key3}}";
            String resolved = tokenHelper.resolveTokens(graphContext, token);
        } catch (TokenResolutionException e) {
        	assertTrue(e.getCause() instanceof NullPointerException);
            log.info("Expected exception thrown " + e.getMessage());
        }

        Map<String, String> map = new HashMap<>();
        map.put("key2", null);
        GraphContext graphContext = new GraphContext().set("Key", ActionResult.NO_RESULTS);
        String token = "{{Key.key2.key3}}";
        String resolved = tokenHelper.resolveTokens(graphContext, token);
        assertEquals("", resolved);
    }

    @Test
    public void tokenIdentifierAsTokenResultsInNothing() {
//        GraphContext graphContext = new GraphContext().set("{}", "GoogleSheet");
//        String token = "{{{}}}";
//        assertEquals("{{{}}}", tokenHelper.sanitizeTemplate(token).get());
//        assertEquals("{}", tokenHelper.resolveTokens(graphContext, token));

        GraphContext graphContext = new GraphContext().set("Key", "GoogleSheet");
        String token = "{Key}}";
        assertEquals("{Key}}", tokenHelper.sanitizeTemplate(token).get());
        assertEquals("{Key}}", tokenHelper.resolveTokens(graphContext, token));
    }

    @Test
    public void randomCharactersStartsToken() {

        List<Character> replacements = List.of('-','$','&','^','*','@','~','(',')','%','#','`',':','"','\'','\\');
        replacements.forEach(x -> {
            GraphContext graphContext = new GraphContext().set(x + "tokenKey", "GoogleSheet");
            String token = "{{" + x + "tokenKey}}";
            assertEquals("{{_tokenKey}}", tokenHelper.sanitizeTemplate(token).get());
            assertEquals("GoogleSheet", tokenHelper.resolveTokens(graphContext, token));
        });

        List<Character> chars = List.of('a','Z','_');
        chars.forEach(x -> {
            GraphContext graphContext = new GraphContext().set(x + "tokenKey", "GoogleSheet");
            String token = "{{" + x + "tokenKey}}";
            assertEquals("{{" + x + "tokenKey}}", tokenHelper.sanitizeTemplate(token).get());
            assertEquals("GoogleSheet", tokenHelper.resolveTokens(graphContext, token));
        });

        List<Character> numbers = List.of('1','0');
        numbers.forEach(x -> {
            GraphContext graphContext = new GraphContext().set(x + "tokenKey", "GoogleSheet");
            String token = "{{" + x + "tokenKey}}";
            assertEquals("{{" + "_" + x + "tokenKey}}", tokenHelper.sanitizeTemplate(token).get());
            assertEquals("GoogleSheet", tokenHelper.resolveTokens(graphContext, token));
        });
    }

    @Test
    public void randomCharactersMiddleToken() {

        List<Character> replacements = List.of('-','$','&','^','*','@','~','(',')','%','#','`',':','"','\'','\\');
        replacements.forEach(x -> {
            GraphContext graphContext = new GraphContext().set("middle" + x + "tokenKey", "GoogleSheet");
            String token = "{{middle" + x + "tokenKey}}";
            assertEquals("{{middle_tokenKey}}", tokenHelper.sanitizeTemplate(token).get());
            assertEquals("GoogleSheet", tokenHelper.resolveTokens(graphContext, token));
        });

        // Note, numbers in middle are valid
        List<Character> chars = List.of('a','Z','_', '1', '0');
        chars.forEach(x -> {
            GraphContext graphContext = new GraphContext().set("middle" + x + "tokenKey", "GoogleSheet");
            String token = "{{middle" + x + "tokenKey}}";
            assertEquals("{{middle" + x + "tokenKey}}", tokenHelper.sanitizeTemplate(token).get());
            assertEquals("GoogleSheet", tokenHelper.resolveTokens(graphContext, token));
        });
    }

    @Test
    public void randomCharactersEndsToken() {

        List<Character> replacements = List.of('-','$','&','^','*','@','~','(',')','%','#','`',':','"','\'','\\');
        replacements.forEach(x -> {
            GraphContext graphContext = new GraphContext().set("tokenKey" + x, "GoogleSheet");
            String token = "{{tokenKey" + x + "}}";
            assertEquals("{{tokenKey_}}", tokenHelper.sanitizeTemplate(token).get());
            assertEquals("Token is " + token, "GoogleSheet", tokenHelper.resolveTokens(graphContext, token));
        });

        // Note, numbers in middle are valid
        List<Character> chars = List.of('a','Z','_', '1', '0');
        chars.forEach(x -> {
            GraphContext graphContext = new GraphContext().set("tokenKey" + x, "GoogleSheet");
            String token = "{{tokenKey" + x + "}}";
            assertEquals("{{tokenKey" + x + "}}", tokenHelper.sanitizeTemplate(token).get());
            assertEquals("GoogleSheet", tokenHelper.resolveTokens(graphContext, token));
        });
    }
    
    @Test
    public void spacesInToken() {
        GraphContext graphContext = new GraphContext().set("Corelight Google Sheets",
                Map.of("1vWpgiN35p__fLYCISEL3vFa9JLN3Jcey", Map.of("company", "COMPANYVAL")));
        String token = "{{Corelight Google Sheets.1vWpgiN35p__fLYCISEL3vFa9JLN3Jcey.company}}";
        assertEquals("{{Corelight_Google_Sheets._1vWpgiN35p__fLYCISEL3vFa9JLN3Jcey.company}}",
                tokenHelper.sanitizeTemplate(token).get());
        assertEquals("COMPANYVAL", tokenHelper.resolveTokens(graphContext, token));

        graphContext = new GraphContext().set("Corelight 1Google Sheets", "CorelightGoogleSheet");
        token = "{{Corelight 1Google Sheets}}";
        assertEquals("{{Corelight_1Google_Sheets}}", tokenHelper.sanitizeTemplate(token).get());
        assertEquals("CorelightGoogleSheet", tokenHelper.resolveTokens(graphContext, token));

        graphContext = new GraphContext().set("Corelight Google Sheets",
                Map.of("s1vWpgiN35p__fLYCISEL3vFa9JLN3Jcey", Map.of("1company", "1COMPANYVAL")));
        token = "{{Corelight Google Sheets.s1vWpgiN35p__fLYCISEL3vFa9JLN3Jcey.1company}}";
        assertEquals("{{Corelight_Google_Sheets.s1vWpgiN35p__fLYCISEL3vFa9JLN3Jcey._1company}}",
                tokenHelper.sanitizeTemplate(token).get());
        assertEquals("1COMPANYVAL", tokenHelper.resolveTokens(graphContext, token));

        graphContext = new GraphContext().set("Corelight 1Google Sheets",
                Map.of("2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey", Map.of("3company", "1All_COMPANYVAL")));
        token = "{{Corelight 1Google Sheets.2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey.3company}}";
        assertEquals("{{Corelight_1Google_Sheets._2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey._3company}}",
                tokenHelper.sanitizeTemplate(token).get());
        assertEquals("1All_COMPANYVAL", tokenHelper.resolveTokens(graphContext, token));

        // Multitokens
        graphContext = new GraphContext().set("Corelight 1Google Sheets", "GoogleSheets").set("2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey",
                Map.of("3company", "1All_COMPANYVAL"));
        token = "{{Corelight 1Google Sheets}} {{2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey.3company}}";
        assertEquals("{{Corelight_1Google_Sheets}} {{_2vWpgiN35p__fLYCISEL3vFa9JLN3Jcey._3company}}",
                tokenHelper.sanitizeTemplate(token).get());
        assertEquals("GoogleSheets 1All_COMPANYVAL", tokenHelper.resolveTokens(graphContext, token));
    }

    @Test
    public void tokenWithPrefixAndSuffix() {

        GraphContext graphContext = new GraphContext().set("tokenKey 1", "GoogleSheet");
        String token = "0{{tokenKey 1}} - suffix";
        assertEquals("0{{tokenKey_1}} - suffix", tokenHelper.sanitizeTemplate(token).get());
        assertEquals("0GoogleSheet - suffix", tokenHelper.resolveTokens(graphContext, token));
    }

    @Test
    public void multipleTokensWithPrefixAndSuffix() {

        GraphContext graphContext = new GraphContext()
                .set("tokenKey 1", "Value1")
                .set("tokenKey 2", "Value2");
        String token = "First-{{tokenKey 1}}_Second-{{tokenKey 2}} - SUFFIX";
        assertEquals("First-{{tokenKey_1}}_Second-{{tokenKey_2}} - SUFFIX", tokenHelper.sanitizeTemplate(token).get());
        assertEquals("First-Value1_Second-Value2 - SUFFIX", tokenHelper.resolveTokens(graphContext, token));
    }

    @Test
    public void multipleTokensWithPrefixAndSuffixWithUnsupportedV2TokenChars() {

        GraphContext graphContext = new GraphContext()
                .set("tokenKey: 1", "Value1")
                .set("tokenKey: 2", "Value2");
        String token = "First-{{tokenKey: 1}}_Second-{{tokenKey: 2}} - SUFFIX";
        //assertEquals("First-{{tokenKey_1:}}_Second-{{tokenKey_2:}} - SUFFIX", tokenHelper.sanitizeTemplate(token).get());
        assertEquals("First-Value1_Second-Value2 - SUFFIX", tokenHelper.resolveTokens(graphContext, token));
    }

    @Test
    public void extractToken_MultipleTokensWithPrefixAndSuffix() {

        String template = "First-{{tokenKey_1}}_Second-{{tokenKey_2}} - SUFFIX";
        List<String> extractedTokens = tokenHelper.extractTokensFromTemplate(template);
        assertEquals(2, extractedTokens.size());
        assertTrue(extractedTokens.contains("{{tokenKey_1}}"));
        assertTrue(extractedTokens.contains("{{tokenKey_2}}"));
    }

    @Test
    public void extractToken_SingleToken() {

        String template = "{{tokenKey_1}}";
        List<String> extractedTokens = tokenHelper.extractTokensFromTemplate(template);
        assertEquals(1, extractedTokens.size());
        assertTrue(extractedTokens.contains("{{tokenKey_1}}"));
    }

    @Test
    public void hiphenInEntityDataToken() {
        GraphContext graphContext = new GraphContext().set("previous",
                Map.of("values", Map.of("Full-Name", "Value")));
        String token = "{{previous.values.Full-Name}}";
        assertEquals("{{previous.values.Full_Name}}",
                tokenHelper.sanitizeTemplate(token).get());
        assertEquals("Value", tokenHelper.resolveTokens(graphContext, token));
    }
    
    @Test
    public void testTempToken() {
        GraphContext graphContext = new GraphContext().set("syncari",Map.of("temp",Map.of("test_temp", "new value")));
        assertEquals("new value", tokenHelper.resolveTokens(graphContext, "{{syncari.temp.test_temp}}"));

        String recordId = ObjectId.get().toHexString();

        graphContext = new GraphContext().set("syncari",Map.of("temp",Map.of("test_temp_" + recordId, "new value"))).set("record", new EntityData().setSyncariEntityId(recordId));
        assertEquals("new value", tokenHelper.resolveTokens(graphContext, "{{syncari.temp.test_temp}}"));

        graphContext = new GraphContext().set("syncari",Map.of("temp",Map.of("testTemp_" + recordId, 10))).set("record", new EntityData().setSyncariEntityId(recordId));
        assertEquals(10, tokenHelper.resolveTokensObject(graphContext, "{{syncari.temp.testTemp}}"));

        graphContext = new GraphContext().set("syncari",Map.of("temp",Map.of("testTemp_" + recordId, true))).set("record", new EntityData().setSyncariEntityId(recordId));
        assertEquals( true, tokenHelper.resolveTokensObject(graphContext, "{{syncari.temp.testTemp}}"));

        graphContext = new GraphContext().set("syncari",Map.of("temp",Map.of("testTemp_" + recordId, 1234.67))).set("record", new EntityData().setSyncariEntityId(recordId));
        assertEquals( 1234.67, tokenHelper.resolveTokensObject(graphContext, "{{syncari.temp.testTemp}}"));

        var datetime = ZonedDateTime.now();
        graphContext = new GraphContext().set("syncari",Map.of("temp",Map.of("testTemp_" + recordId, datetime))).set("record", new EntityData().setSyncariEntityId(recordId));
        assertEquals( datetime, tokenHelper.resolveTokensObject(graphContext, "{{syncari.temp.testTemp}}"));
    }
    
    @Test
    public void validateToken() {
        String token = "{{previous.values.Full-Name}}";
        assertTrue(tokenHelper.isValid(token));
        token = "{{previous.values.Full-Name";
        assertFalse(tokenHelper.isValid(token));
        token = "previous.values.Full-Name}}";
        assertFalse(tokenHelper.isValid(token));
        token = "{{previous.values.Full-Name}} test {{previous.values.Full-Name";
        assertFalse(tokenHelper.isValid(token));
        token = "previous.values.Full-Name}} test {{previous.values.Full-Name}}";
        assertFalse(tokenHelper.isValid(token));
        token = "/previous/values/Full-Name";
        assertTrue(tokenHelper.isValidSyntax(token));
        token = "/previous/values/Full-Name.length()";
        assertFalse(tokenHelper.isValidSyntax(token));

    }

    @Test
    public void extractValueFromJson() throws JsonProcessingException {

        Map<String, Object> jsonObject = Map.of("success", true, "result", List.of(Map.of("name", "John Doe", "email", "john@syncari.com"),
                Map.of("name", "Jane Doe", "email", "jane@syncari.com")));

        GraphContext graphContext = new GraphContext().set("response",
                jsonObject);
        String token = "{{response.result[0].name}}";
        assertEquals("{{response.result[0].name}}",
                tokenHelper.sanitizeTemplate(token).get());
        assertEquals("John Doe", tokenHelper.resolveTokens(graphContext, token));

        token = "{{response.result[0].email}}";
        assertEquals("{{response.result[0].email}}",
                tokenHelper.sanitizeTemplate(token).get());
        assertEquals("john@syncari.com", tokenHelper.resolveTokens(graphContext, token));

        token = "{{response.result[1].name}}";
        assertEquals("{{response.result[1].name}}",
                tokenHelper.sanitizeTemplate(token).get());
        assertEquals("Jane Doe", tokenHelper.resolveTokens(graphContext, token));

        token = "{{response.result[1].email}}";
        assertEquals("{{response.result[1].email}}",
                tokenHelper.sanitizeTemplate(token).get());
        assertEquals("jane@syncari.com", tokenHelper.resolveTokens(graphContext, token));

        token = "{{response.result[2].name}}";
        assertEquals("{{response.result[2].name}}",
                tokenHelper.sanitizeTemplate(token).get());
        assertEquals("", tokenHelper.resolveTokens(graphContext, token));
    }

    @Test
    public void testValuesNewline(){
        String token = "{{previousLookup.externalIds.SFDC.\nAccount}}";
        var context = new GraphContext().set("previousLookup", new EntityData().addExternalRecordId("SFDC", "Account", "123"));

        var resolved1 = tokenHelper.resolveJTwigToken(context, token);
        var resolved2 = tokenHelper.resolveTokenV2(context, token);

        assertTrue(resolved1.getX().equals(resolved2.getX()));

        token = "{{previousLookup.externalIds.SFDC.\r\nAccount}}";
        resolved1 = tokenHelper.resolveJTwigToken(context, token);
        resolved2 = tokenHelper.resolveTokenV2(context, token);

        assertTrue(resolved1.getX().equals(resolved2.getX()));

        token = "Check this \n token {{previousLookup.externalIds.SFDC.\nAccount}}";
        resolved1 = tokenHelper.resolveJTwigToken(context, token);
        resolved2 = tokenHelper.resolveTokenV2(context, token);
        assertTrue(resolved1.getX().equals(resolved2.getX()));

        token = "{{previousLookup.externalIds.SFDC.\nAccount}}";
        resolved1 = tokenHelper.resolveJTwigToken(context, token);
        resolved2 = tokenHelper.resolveTokenV2(context, token);
        assertTrue(resolved1.getX().equals(resolved2.getX()));
    }

    @Test
    public void testTempVariableExtraction() {

        GraphContext map = new GraphContext();
        var variable = tokenHelper.extractTempVariableName("{{syncari.temp.foo}}");
        assertEquals("foo", variable.get());

        variable = tokenHelper.extractTempVariableName("{{previous.values.FullName}}");
        assertTrue(variable.isEmpty());

        variable = tokenHelper.extractTempVariableName("{{response.result[0].name}}");
        assertTrue(variable.isEmpty());

        variable = tokenHelper.extractTempVariableName("{{syncari.temp.foo}}test");
        assertEquals("foo", variable.get());
    }

    @Test
    public void testTokenResolutionWithPeriod() {
        GraphContext map = new GraphContext();
        map.put("Records from Campaign", List.of(new EntityData().addValue("Contact__r\\.xyz", "abc")));
        assertEquals("abc", tokenHelper.resolveTokens(map,"{{Records from Campaign[0].values.Contact__r\\.xyz}}"));
    }

//    @Test
//    public void resolveTokensEvalFormula() throws JsonProcessingException {
//    	GraphContext graphContext = new GraphContext();
//        graphContext.put("var1", 4);
//        graphContext.put("var2", 5);
//        
//        assertEquals("123", tokenHelper.resolveTokens(graphContext, "123"));
//        assertEquals("4", tokenHelper.resolveTokens(graphContext, "{{var1}}"));
//        assertEquals("4 + 5", tokenHelper.resolveTokens(graphContext, "{{var1}} + {{var2}}"));
//        assertEquals("45", tokenHelper.resolveTokens(graphContext, "{{var1}}{{var2}}"));
//        assertEquals("abc 4 5 xyz", tokenHelper.resolveTokens(graphContext, "abc {{var1}} {{var2}} xyz"));
//        assertEquals("9", tokenHelper.resolveTokens(graphContext, "FORMULA({{var1}} + {{var2}})"));
//        assertEquals("9", tokenHelper.resolveTokens(graphContext, "FORMULA({{var1}}+{{var2}})"));
//        assertEquals("-1", tokenHelper.resolveTokens(graphContext, "FORMULA({{var1}} - {{var2}})"));
//        assertEquals("-1", tokenHelper.resolveTokens(graphContext, "FORMULA({{var1}}-{{var2}})"));
//        assertEquals("20", tokenHelper.resolveTokens(graphContext, "FORMULA({{var1}} * {{var2}})"));
//        assertEquals("20", tokenHelper.resolveTokens(graphContext, "FORMULA({{var1}}*{{var2}})"));
//        assertEquals("0.8", tokenHelper.resolveTokens(graphContext, "FORMULA({{var1}} / {{var2}})"));
//        assertEquals("0.8", tokenHelper.resolveTokens(graphContext, "FORMULA({{var1}}/{{var2}})"));
//        assertEquals("9 -1", tokenHelper.resolveTokens(graphContext, "FORMULA({{var1}} + {{var2}}) FORMULA({{var1}} - {{var2}})"));
//        assertEquals("test29 test1", tokenHelper.resolveTokens(graphContext, "test2FORMULA({{var1}} + {{var2}}) test1"));
//        assertEquals("4 9 5", tokenHelper.resolveTokens(graphContext, "{{var1}} FORMULA({{var1}} + {{var2}}) {{var2}}"));
//    }
}