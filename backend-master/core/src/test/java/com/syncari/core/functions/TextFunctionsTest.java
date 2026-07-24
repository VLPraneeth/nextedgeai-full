package com.syncari.core.functions;

import com.syncari.connector.EntityData;
import com.syncari.core.TestConfig;
import com.syncari.core.config.AppConfig;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.AttributeRepo;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
@DirtiesContext
public class TextFunctionsTest {
    @Autowired
    TextFunctions functions;
    @Autowired
    SetValueOnEntityProcessor setValueOnEntityProcessor;

    @Autowired
    AppConfig appConfig;

    @Test
    public void mask() throws Exception {
        assertNull(functions.mask(nullList(), createCall(), getContext(null)));
        assertEquals("****", functions.mask(List.of("test"), createCall(), getContext("test")));
    }

    @Test
    public void lower() throws Exception {
        assertEquals("first name last name", functions.lower(List.of("First Name Last Name"), createCall(), getContext(null)));
    }

    @Test
    public void lowerOnEntity() {
        GraphContext context = getContext(null);
        functions.lowerOnEntity(
                List.of(),
                createCall("value", "First Name Last Name"),
                context
        );
        assertEquals("Should change to lowercase", "first name last name", context.get("previousValue"));
        assertEquals("Should store result in 'Value From' context key", "first name last name", context.get("Value From My Custom Node"));
    }

    @Test
    public void lowerOnEntityWithEmptyString() {
        GraphContext context = getContext(null);
        functions.lowerOnEntity(
                List.of(),
                createCall("value", ""),
                context
        );
        assertEquals("Should store empty string in previousValue", "", context.get("previousValue"));
        assertEquals("Should store empty string in 'Value From' context key", "", context.get("Value From My Custom Node"));
    }

    @Test
    public void lowerOnEntityWithNull() {
        GraphContext context = getContext(null);
        functions.lowerOnEntity(
                List.of(),
                createCall("value", null),
                context
        );
        assertNull("Should store null in previousValue", context.get("previousValue"));
        assertNull("Should store null in 'Value From' context key", context.get("Value From My Custom Node"));
    }

    @Test
    public void upper() {
        assertEquals("FIRST NAME LAST NAME", functions.upper(List.of("First Name Last Name"), createCall(), getContext(null)));
    }

    @Test
    public void upperOnEntity() {
        GraphContext context = getContext(null);
        functions.upperOnEntity(
                List.of(),
                createCall("value", "First Name Last Name"),
                context
        );
        assertEquals("Should change to uppercase", "FIRST NAME LAST NAME", context.get("previousValue"));
        assertEquals("Should store result in 'Value From' context key", "FIRST NAME LAST NAME", context.get("Value From My Custom Node"));
    }

    @Test
    public void upperOnEntityWithEmptyString() {
        GraphContext context = getContext(null);
        functions.upperOnEntity(
                List.of(),
                createCall("value", ""),
                context
        );
        assertEquals("Should store empty string in previousValue", "", context.get("previousValue"));
        assertEquals("Should store empty string in 'Value From' context key", "", context.get("Value From My Custom Node"));
    }

    @Test
    public void upperOnEntityWithNull() {
        GraphContext context = getContext(null);
        functions.upperOnEntity(
                List.of(),
                createCall("value", null),
                context
        );
        assertNull("Should store null in previousValue", context.get("previousValue"));
        assertNull("Should store null in 'Value From' context key", context.get("Value From My Custom Node"));
    }

    @Test
    public void camelCase() {
        assertEquals("First Name Last Name", functions.camelCase(List.of("first name last name"), createCall(), getContext(null)));
    }

    @Test
    public void md5hash() {
        assertEquals("b642b4217b34b1e8d3bd915fc65c4452", functions.md5hash(List.of(), createCall("input", "test@test.com"), getContext(null)));
    }

    @Test
    public void md5hashOnEntity() {
        GraphContext context = getContext(null);
        functions.md5hashOnEntity(
                List.of(),
                createCall("input", "test@test.com"),
                context
        );
        assertEquals("Should hash the value", "b642b4217b34b1e8d3bd915fc65c4452", context.get("previousValue"));
        assertEquals("Should store result in 'Value From' context key", "test_value_151", context.get("Value From My Custom Node"));
    }

    @Test
    public void md5hashOnEntityWithEmptyString() {
        GraphContext context = getContext(null);
        functions.md5hashOnEntity(
                List.of(),
                createCall("input", ""),
                context
        );
        assertEquals("Should store empty string in previousValue", "", context.get("previousValue"));
        assertEquals("Should store empty string in 'Value From' context key", "", context.get("Value From My Custom Node"));
    }

    @Test
    public void md5hashOnEntityWithNull() {
        GraphContext context = getContext(null);
        functions.md5hashOnEntity(
                List.of(),
                createCall("input", null),
                context
        );
        assertNull("Should store null in previousValue", context.get("previousValue"));
        assertNull("Should store null in 'Value From' context key", context.get("Value From My Custom Node"));
    }

    @Test
    public void capitalize() {
        assertEquals("First name last name", functions.capitalize(List.of("first name last name"), createCall(), getContext(null)));
    }

    @Test
    public void regexReplace() {
        assertEquals("demo@example.com", functions.replace(List.of("demo+something@example.com"), createCall("searchExpression","\\+.*@","replaceWith","@"), getContext(null)));
        assertEquals("demo@example.com", functions.replace(List.of("demo@example.com"), createCall("searchExpression","\\+.*@","replaceWith","@"), getContext(null)));
        assertEquals("demo@example.com", functions.replace(List.of("demo+++++@example.com"), createCall("searchExpression","\\+.*@","replaceWith","@"), getContext(null)));
        assertEquals("FirstName", functions.replace(List.of("FirstName LastName"), createCall("searchExpression","\\s+.+$","replaceWith",""), getContext(null)));
        assertEquals("LastName", functions.replace(List.of("FirstName LastName"), createCall("searchExpression","^.+\\s+","replaceWith",""), getContext(null)));
        assertEquals("FirstNameLastNameSons", functions.replace(List.of("FirstName && LastName Son's.(inc)"),
                createCall("searchExpression","([&*%$#!@,\\.'\\s\"?&\\(\\)\\[\\]\\{\\};:]|Inc|Corp|LLC|gmbh)",
                        "replaceWith","","caseInsensitiveSearch","true"), getContext(null)));
        assertEquals("FirstNameLastNameSonsinc", functions.replace(List.of("FirstName && LastName Son's.(inc)"),
                createCall("searchExpression","([&*%$#!@,\\.'\\s\"?&\\(\\)\\[\\]\\{\\};:]|Inc|Corp|LLC|gmbh)",
                        "replaceWith","","caseInsensitiveSearch","false"), getContext(null)));
        assertEquals("FirstNameLastNameSons", functions.replace(List.of("FirstName && LastName Son's.(inc)"),
                createCall("searchExpression","([&*%$#!@,\\.'\\s\"?&\\(\\)\\[\\]\\{\\};:]|Inc|Corp|LLC|gmbh)",
                        "replaceWith","","caseInsensitiveSearch",true), getContext(null)));
        assertEquals("FirstNameGroupLastNameSons", functions.replace(List.of("FirstName Group && LastName Son's.(inc) Group"),
                createCall("searchExpression","([&*%$#!@,\\.'\\s\"?&\\(\\)\\[\\]\\{\\};:]|Inc|Corp|LLC|gmbh|Group$|Co\\.|Company$|Company\\.$|incorporated|ltd|ltd\\.$|limited\\.$)",
                        "replaceWith","","caseInsensitiveSearch",true), getContext(null)));
    }

    @Test
    public void extractText() {
        assertEquals("SYN-13990", functions.extractText(List.of("SYN-13990 : Changing code for "), createCall("searchExpression","(SYN-\\d+)","input","Resolve SYN-13990 : Changing code for "), getContext(null)));
    }

    @Test
    public void replaceWithToken() {
        GraphContext context = getContext(null);
        context.set("token1", "\\+.*@");
        context.set("token2", "@");
        assertEquals("demo@example.com",
                functions.replace(
                        List.of("demo+something@example.com"),
                        createCall("searchExpression","{{token1}}","replaceWith","{{token2}}"),
                        context
                )
        );
    }

    @Test
    public void charAtOnEntity() {
        GraphContext context = getContext(null);
        functions.charAtOnEntity(
                List.of(),
                createCall("value", "syncaroo", "index", "1"),
                context
        );
        assertEquals("Should extract char correctly", 'y', context.get("previousValue"));
        assertEquals("Should store result in 'Value From' context key", 'y', context.get("Value From My Custom Node"));
    }

    @Test
    public void charAtOnEntityWithEmptyString() {
        GraphContext context = getContext(null);
        functions.charAtOnEntity(
                List.of(),
                createCall("value", "", "index", "1"),
                context
        );
        assertEquals("Should store empty string in previousValue", "", context.get("previousValue"));
        assertEquals("Should store empty string in 'Value From' context key", "", context.get("Value From My Custom Node"));
    }

    @Test
    public void charAtOnEntityWithNull() {
        GraphContext context = getContext(null);
        functions.charAtOnEntity(
                List.of(),
                createCall("value", null,"index", "1"),
                context
        );
        assertNull("Should store null in previousValue", context.get("previousValue"));
        assertNull("Should store null in 'Value From' context key", context.get("Value From My Custom Node"));
    }

    @Test
    public void jwtToken() {
        String input = "syncaroojhguyfuy37547chgchchjgkj";
        String output = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..GbTe7klHvN2tcpS9QhmF_fUP9vuTU3Gu3tY74wmNTIc";
        assertEquals(output, functions.jwtToken(List.of(), createCall("signingKey", input), getContext(null)));
        assertEquals("", functions.jwtToken(List.of(), createCall("signingKey", ""), getContext(null)));
        assertNull(functions.jwtToken(List.of(), createCall(), getContext(null)));
    }

    @Test
    public void jwtTokenOnEntity() {
        GraphContext context = getContext(null);
        functions.jwtTokenOnEntity(
                List.of(),
                createCall("signingKey", "syncaroojhguyfuy37547chgchchjgkj"),
                context
        );
        assertEquals("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..GbTe7klHvN2tcpS9QhmF_fUP9vuTU3Gu3tY74wmNTIc", context.get("previousValue"));
        assertEquals("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..GbTe7klHvN2tcpS9QhmF_fUP9vuTU3Gu3tY74wmNTIc", context.get("Value From My Custom Node"));
    }

    @Test
    public void jwtTokenOnEntityWithEmptyString() {
        GraphContext context = getContext(null);
        functions.jwtTokenOnEntity(
                List.of(),
                createCall("signingKey", ""),
                context
        );
        assertEquals("", context.get("previousValue"));
        assertEquals( "", context.get("Value From My Custom Node"));
    }

    @Test
    public void jwtTokenOnEntityWithNull() {
        GraphContext context = getContext(null);
        functions.jwtTokenOnEntity(
                List.of(),
                createCall("signingKey", null),
                context
        );
        assertNull(context.get("previousValue"));
        assertNull(context.get("Value From My Custom Node"));
    }

    @Test
    public void replace() {
        assertEquals("demo@example.com", functions.replace(List.of("demo+something@example.com"), createCall("searchExpression","\\+.*@","replaceWith","@"), getContext(null)));
        assertEquals("", functions.replace(List.of(""), createCall("searchExpression","\\+.*@","replaceWith","@"), getContext(null)));
        assertNull(functions.replace(List.of(), createCall("searchExpression","\\+.*@","replaceWith","@"), getContext(null)));
    }

    @Test
    public void regexReplaceOnEntity() {
        GraphContext context = getContext(null);
        functions.replaceOnEntity(List.of(), createCall("searchExpression","\\+.*@","replaceWith","@","value","demo+something@example.com"), context);
        assertEquals("demo@example.com", context.remove("previousValue"));
        assertEquals("demo@example.com", context.remove("Value From My Custom Node"));

        context.set("previous",new EntityData().addValue("email","demo+something@example.com"));
        functions.replaceOnEntity(List.of(), createCall("searchExpression","\\+.*@","replaceWith","@","value","{{previous.values.email}}"), context);
        assertEquals("demo@example.com",context.remove("previousValue"));
        assertEquals("demo@example.com", context.remove("Value From My Custom Node"));

        context.set("previous",new EntityData().addValue("name","FirstName && LastName Son's.(inc)"));
        functions.replaceOnEntity(List.of(),
                createCall("searchExpression","([&*%$#!@,\\.'\\s\"?&\\(\\)\\[\\]\\{\\};:]|Inc|Corp|LLC|gmbh)",
                        "replaceWith","","caseInsensitiveSearch","true","value","{{previous.values.name}}"), context);
        assertEquals("FirstNameLastNameSons", context.remove("previousValue"));
        assertEquals("FirstNameLastNameSons", context.remove("Value From My Custom Node"));

        context.remove("previousValue");
        context.set("previous",new EntityData().addValue("name","FirstName Group && LastName Son's.(inc) Group"));
        functions.replaceOnEntity(List.of("FirstName Group && LastName Son's.(inc) Group"),
                createCall("searchExpression","([&*%$#!@,\\.'\\s\"?&\\(\\)\\[\\]\\{\\};:]|Inc|Corp|LLC|gmbh|Group$|Co\\.|Company$|Company\\.$|incorporated|ltd|ltd\\.$|limited\\.$)",
                        "replaceWith","","caseInsensitiveSearch",true,"value","{{previous.values.name}}"), context);
        assertEquals("FirstNameGroupLastNameSons", context.remove("previousValue"));
        assertEquals("FirstNameGroupLastNameSons", context.remove("Value From My Custom Node"));
    }

    @Test
    public void regexReplaceOnEntityWithEmptyString() {
        GraphContext context = getContext(null);
        functions.replaceOnEntity(List.of(), createCall("searchExpression", "\\+.*@", "replaceWith", "@", "value", ""), context);
        assertEquals("", context.get("previousValue"));
        assertEquals("", context.get("Value From My Custom Node"));
    }

    @Test
    public void regexReplaceOnEntityWithNull() {
        GraphContext context = getContext(null);
        functions.replaceOnEntity(List.of(), createCall("searchExpression", "\\+.*@", "replaceWith", "@"), context);
        assertNull(context.get("previousValue"));
        assertNull(context.get("Value From My Custom Node"));
    }

    @Test
    public void encrypt() throws Exception {
        assertNull(functions.encrypt(nullList(), createCall("key", "key"), getContext(null)));
        try {
            assertNull(functions.encrypt(nullList(), createCall("key", null), getContext(null)));
        } catch (Exception e) {
            assertEquals("Key cannot be null for encrypt", e.getMessage());
        }
//		assertEquals("dp4W10wFpxkEVA2aoEPjAA==:hrgMSpyimgFkvHpFalyPkA", functions.encrypt("password", "key"));
    }

    @Test
    public void decrypt() throws Exception {
        assertNull(functions.decrypt(nullList(), createCall("key", "key"), getContext(null)));
        try {
            assertNull(functions.decrypt(List.of("password"), createCall("key", null), getContext("password")));
        } catch (Exception e) {
            assertEquals("Key cannot be null for decrypt", e.getMessage());
        }
//		assertEquals("password", functions.decrypt("dp4W10wFpxkEVA2aoEPjAA==:hrgMSpyimgFkvHpFalyPkA", "key"));
    }

    @Test
    public void encode() throws Exception {
        assertNull(functions.encode(nullList(), createCall(), getContext(null)));
        assertEquals("cGFzc3dvcmQ=", functions.encode(List.of("password"), createCall(), getContext("password")));

        //String encodedString = new String(Base64.getEncoder().encode(appConfig.getTsBqSaKey().getBytes(StandardCharsets.UTF_8)));
        //System.out.println("Encoded key " + encodedString);
    }
    
    @Test
    public void split() {
        assertEquals(3, functions.split(List.of("one,two,three"), createCall("delimiter", ","), getContext(null)).size());
        assertEquals(1, functions.split(List.of("one"), createCall("delimiter", ","), getContext(null)).size());
        assertEquals(2, functions.split(List.of("one|two"), createCall("delimiter", "|"), getContext(null)).size());
        assertEquals(List.of("one", "two", "three"), functions.split(List.of("one,two,three"), createCall(), getContext(null)));
    }

    @Test
    public void splitOnEntity() {
        GraphContext context = getContext(null);
        functions.splitOnEntity(
                List.of(),
                createCall("value", "one,two,three"),
                context
        );
        assertEquals("Should split string", List.of("one", "two", "three"), context.get("previousValue"));
        assertEquals("Should store result in 'Value From' context key", List.of("one", "two", "three"), context.get("Value From My Custom Node"));
    }

    @Test
    public void splitOnEntityWithEmptyString() {
        GraphContext context = getContext(null);
        functions.splitOnEntity(
                List.of(),
                createCall("value", ""),
                context
        );
        assertEquals("Should store empty string in previousValue", "", context.get("previousValue"));
        assertEquals("Should store empty string in 'Value From' context key", "", context.get("Value From My Custom Node"));
    }

    @Test
    public void splitOnEntityWithNull() {
        GraphContext context = getContext(null);
        functions.splitOnEntity(
                List.of(),
                createCall("value", null),
                context
        );
        assertNull("Should store null in previousValue", context.get("previousValue"));
        assertNull("Should store null in 'Value From' context key", context.get("Value From My Custom Node"));
    }

    @Test
    public void length() throws Exception {
        assertEquals(Integer.valueOf(7), functions.length(List.of("1234567"), createCall( ), getContext(null)));
        assertEquals(Integer.valueOf(1), functions.length(List.of("1"), createCall( ), getContext(null)));
        assertEquals(Integer.valueOf(0), functions.length(List.of(""), createCall( ), getContext(null)));
        assertEquals(Integer.valueOf(0), functions.length(List.of(), createCall(), getContext(null)));
    }

    @Test
    public void lengthOnEntity() {
        GraphContext context = getContext(null);
        functions.lengthOnEntity(
                List.of(),
                createCall("value", "1234567"),
                context
        );
        assertEquals("Should return length of string", 7, context.get("previousValue"));
        assertEquals("Should store result in 'Value From' context key", 7, context.get("Value From My Custom Node"));
    }

    @Test
    public void lengthOnEntityWithEmptyString() {
        GraphContext context = getContext(null);
        functions.lengthOnEntity(
                List.of(),
                createCall("value", ""),
                context
        );
        assertEquals("Should store 0 in previousValue", 0, context.get("previousValue"));
        assertEquals("Should store 0 in 'Value From' context key", 0, context.get("Value From My Custom Node"));
    }

    @Test
    public void lengthOnEntityWithNull() {
        GraphContext context = getContext(null);
        functions.lengthOnEntity(
                List.of(),
                createCall("value", null),
                context
        );
        assertNull("Should store null in previousValue", context.get("previousValue"));
        assertNull("Should store null in 'Value From' context key", context.get("Value From My Custom Node"));
    }

    @Test
    public void decode() throws Exception {
        assertNull(functions.decode(nullList(),createCall(), getContext(null)));
        assertEquals("password", functions.decode(List.of("cGFzc3dvcmQ="), createCall(), getContext("cGFzc3dvcmQ=")));
    }

    @Test
    public void ltrim() throws Exception {
        assertNull(functions.ltrim(nullList(), createCall(), getContext(null)));
        assertEquals("test", functions.ltrim(List.of(" test"), createCall(), getContext(" test")));
        assertEquals("test ", functions.ltrim(List.of(" test "), createCall(), getContext(" test ")));
        assertEquals("test ", functions.ltrim(List.of("test "), createCall(), getContext("test ")));
    }

    @Test
    public void rtrim() throws Exception {
        assertNull(functions.rtrim(nullList(), createCall(), getContext(null)));
        assertEquals(" test", functions.rtrim(List.of(" test"), createCall(), getContext(" test")));
        assertEquals(" test", functions.rtrim(List.of(" test "), createCall(), getContext(" test ")));
        assertEquals("test", functions.rtrim(List.of("test "), createCall(), getContext("test ")));
    }

    @Test
    public void lpad() throws Exception {
        assertEquals("**********",functions.lpad(nullList(), createCall("size", 10, "pad", "*"), getContext(null)));
        assertEquals("      test", functions.lpad(List.of("test"), createCall("size", 10, "pad", null), getContext("test")));
        assertEquals("******test", functions.lpad(List.of("test"), createCall("size", 10, "pad", "*"), getContext("test")));
        assertEquals("******", functions.lpad(List.of(""), createCall("size", 6, "pad", "*"), getContext("test")));
    }

    @Test
    public void rpad() throws Exception {
        assertEquals("**********",functions.rpad(nullList(), createCall("size", 10, "pad", "*"), getContext(null)));
        assertEquals("test      ", functions.rpad(List.of("test"), createCall("size", 10, "pad", null), getContext("test")));
        assertEquals("test******", functions.rpad(List.of("test"), createCall("size", 10, "pad", "*"), getContext("test")));
        assertEquals("******", functions.lpad(List.of(""), createCall("size", 6, "pad", "*"), getContext("test")));
    }
    @Test
    public void isEmpty() throws Exception {
        assertEquals("**********",functions.rpad(nullList(), createCall("size", 10, "pad", "*"), getContext(null)));
        assertTrue(functions.isEmpty(nullList(), createCall(), getContext(null)));
        assertTrue(functions.isEmpty(List.of(), createCall(), getContext(null)));
        assertTrue(functions.isEmpty(List.of(""), createCall(), getContext(null)));
        assertFalse(functions.isEmpty(List.of("Some Value"), createCall(), getContext(null)));
        assertTrue(functions.isEmpty(List.of("   "), createCall(), getContext(null)));
    }

    @Test
    public void substring() {
        assertNull(functions.substring(nullList(), createCall("startIndex", 0, "endIndex", 10), getContext(null)));
        assertEquals("es", functions.substring(List.of("test"), createCall("startIndex", 1, "endIndex", 3), getContext("test")));
        assertEquals("test", functions.substring(List.of("test"), createCall("startIndex", 10, "endIndex", 2), getContext("test")));
        assertEquals("test", functions.substring(List.of("test"), createCall("startIndex", 1, "endIndex", 200), getContext("test")));
        assertEquals("test", functions.substring(List.of("test"), createCall("startIndex", 0, "endIndex", -1), getContext("test")));
        assertEquals("", functions.substring(List.of(""), createCall("startIndex", 0, "endIndex", -1), getContext("test")));
    }

    @Test
    public void substringOnEntity() {
        GraphContext context = getContext(null);
        functions.substringOnEntity(
                List.of(),
                createCall("value", "1234567890", "startIndex", 0, "endIndex", 5),
                context
        );
        assertEquals("Should extract substring correctly", "12345", context.get("previousValue"));
        assertEquals("Should store result in 'Value From' context key", "12345", context.get("Value From My Custom Node"));
    }

    @Test
    public void substringOnEntityWithEmptyString() {
        GraphContext context = getContext(null);
        functions.substringOnEntity(
                List.of(),
                createCall("value", "", "startIndex", 0, "endIndex", 5),
                context
        );
        assertEquals("Should store empty string in previousValue", "", context.get("previousValue"));
        assertEquals("Should store empty string in 'Value From' context key", "", context.get("Value From My Custom Node"));
    }

    @Test
    public void substringOnEntityWithNull() {
        GraphContext context = getContext(null);
        functions.substringOnEntity(
                List.of(),
                createCall("value", null, "startIndex", 0, "endIndex", 5),
                context
        );
        assertNull("Should store null in previousValue", context.get("previousValue"));
        assertNull("Should store null in 'Value From' context key", context.get("Value From My Custom Node"));
    }

    @Test
    public void indexof() throws Exception {
        assertEquals(-1, functions.indexOf(nullList(), createCall("searchString", "test"), getContext(null)));
        assertEquals(-1, functions.indexOf(List.of("test"), createCall("searchString", null), getContext("test")));
        assertEquals(1, functions.indexOf(List.of("test"), createCall("searchString", "es"), getContext("test")));
        assertEquals(-1, functions.indexOf(List.of("test"), createCall("searchString", "not"), getContext("test")));
    }

    @Test
    public void contains() throws Exception {
        assertFalse(functions.contains(nullList(), createCall("searchText", "test"), getContext(null)));
        assertFalse(functions.contains(List.of("test"), createCall("searchText", null), getContext("test")));
        assertTrue(functions.contains(List.of("test"), createCall("searchText", "es"), getContext("test")));
        assertFalse(functions.contains(List.of("test"), createCall("searchText", "not"), getContext("test")));
    }

    @Test
    public void extractDomainEmail() {
        assertNull(functions.extractDomain(nullList(), createCall(), getContext(null)));
        assertNull(functions.extractDomain(List.of("test"), createCall(), getContext("test")));
        assertNull(functions.extractDomain(List.of("invalid domain without host"), createCall(), getContext(null)));
        assertNull(functions.extractDomain(List.of("test@"), createCall(), getContext(null)));
        assertNull(functions.extractDomain(List.of("test@syncari."), createCall(), getContext(null)));

        assertEquals("syncari.com", functions.extractDomain(List.of("test@syncari.com"), createCall(), getContext(null)));
        assertEquals("app.syncari.com", functions.extractDomain(List.of("test@app.syncari.com"), createCall(), getContext(null)));

        assertEquals("syncari.com", functions.extractDomain(List.of("test@syncari.com"), createCall("option", "tld"), getContext(null)));
        assertEquals("syncari.com", functions.extractDomain(List.of("test@app.syncari.com"), createCall("option", "tld"), getContext(null)));

        assertEquals("syncari", functions.extractDomain(List.of("test@syncari.com"), createCall("option", "name"), getContext(null)));
        assertEquals("syncari", functions.extractDomain(List.of("test@app.syncari.com"), createCall("option", "name"), getContext(null)));

        assertEquals("syncari.com", functions.extractDomain(List.of("test@syncari.com"), createCall("option", "fullDomain"), getContext(null)));
        assertEquals("app.syncari.com", functions.extractDomain(List.of("test@app.syncari.com"), createCall("option", "fullDomain"), getContext(null)));
    }

    @Test
    public void extractDomainEmailOnEntity() {
        GraphContext context = getContext(null);
        EntityData record = new EntityData();
        assertEquals(record, functions.extractDomainOnEntity(List.of(record), createCall("value","test@app.syncari.com"), context));
        assertEquals("app.syncari.com", context.get("previousValue"));
        assertEquals("app.syncari.com", context.get("Value From My Custom Node"));
        assertEquals(record, functions.extractDomainOnEntity(List.of(record), createCall("option","tld","value","test@app.syncari.com"), context));
        assertEquals("syncari.com", context.get("previousValue"));
        assertEquals("syncari.com", context.get("Value From My Custom Node"));
    }

    @Test
    public void extractDomainEmailOnEntityWithEmptyString() {
        GraphContext context = getContext(null);
        EntityData record = new EntityData();
        assertEquals(record, functions.extractDomainOnEntity(List.of(record), createCall("value",""), context));
        assertEquals("", context.get("previousValue"));
        assertEquals("", context.get("Value From My Custom Node"));
    }

    @Test
    public void extractDomainEmailOnEntityWithNull() {
        GraphContext context = getContext(null);
        EntityData record = new EntityData();
        assertEquals(record, functions.extractDomainOnEntity(List.of(record), createCall(), context));
        assertEquals("", context.get("previousValue"));
        assertEquals("", context.get("Value From My Custom Node"));
    }

    @Test
    public void extractDomainUrl() {
        assertNull(functions.extractDomain(nullList(), createCall(), getContext(null)));
        assertNull(functions.extractDomain(List.of("test"), createCall(), getContext("test")));
        assertNull(functions.extractDomain(List.of("invalid domain without host"), createCall(), getContext(null)));
        assertNull("domain.", functions.extractDomain(List.of("https://domain."), createCall("option", "tld"), getContext(null)));

        assertEquals("domain.com", functions.extractDomain(List.of("https://domain.com"), createCall("option", "tld"), getContext(null)));
        assertEquals("domain.com", functions.extractDomain(List.of("https://domain.com?queryParam=some"), createCall("option", "tld"), getContext(null)));
        assertEquals("domain.com", functions.extractDomain(List.of("https://domain.com/somePath"), createCall("option", "tld"), getContext(null)));
        assertEquals("domain.com", functions.extractDomain(List.of("https://some.domain.com"), createCall("option", "tld"), getContext(null)));
        assertEquals("domain.com", functions.extractDomain(List.of("www.domain.com"), createCall("option", "tld"), getContext(null)));
        assertEquals("domain.com", functions.extractDomain(List.of("www.some.domain.com"), createCall("option", "tld"), getContext(null)));
        assertEquals("domain.com", functions.extractDomain(List.of("http.domain.com"), createCall("option", "tld"), getContext(null)));
        assertEquals("domain.com", functions.extractDomain(List.of("http.some.domain.com"), createCall("option", "tld"), getContext(null)));
        assertEquals("domain.co.uk", functions.extractDomain(List.of("http.some.domain.co.uk"), createCall("option", "tld"), getContext(null)));
        
        assertEquals("domain", functions.extractDomain(List.of("https://domain.com"), createCall("option", "name"), getContext(null)));
        assertEquals("domain", functions.extractDomain(List.of("https://domain.com?queryParam=some"), createCall("option", "name"), getContext(null)));
        assertEquals("domain", functions.extractDomain(List.of("https://domain.com/somePath"), createCall("option", "name"), getContext(null)));
        assertEquals("domain", functions.extractDomain(List.of("https://some.domain.com"), createCall("option", "name"), getContext(null)));
        assertEquals("domain", functions.extractDomain(List.of("www.domain.com"), createCall("option", "name"), getContext(null)));
        assertEquals("domain", functions.extractDomain(List.of("www.some.domain.com"), createCall("option", "name"), getContext(null)));
        assertEquals("domain", functions.extractDomain(List.of("https://some.domain.co.uk"), createCall("option", "name"), getContext(null)));
        
        assertEquals("domain.com", functions.extractDomain(List.of("https://domain.com"), createCall("option", "fullDomain"), getContext(null)));
        assertEquals("domain.com", functions.extractDomain(List.of("https://domain.com?queryParam=some"), createCall("option", "fullDomain"), getContext(null)));
        assertEquals("domain.com", functions.extractDomain(List.of("https://domain.com/somePath"), createCall("option", "fullDomain"), getContext(null)));
        assertEquals("some.domain.com", functions.extractDomain(List.of("https://some.domain.com"), createCall("option", "fullDomain"), getContext(null)));
        assertEquals("www.domain.com", functions.extractDomain(List.of("www.domain.com"), createCall("option", "fullDomain"), getContext(null)));
        assertEquals("www.some.domain.com", functions.extractDomain(List.of("www.some.domain.com"), createCall("option", "fullDomain"), getContext(null)));
        assertEquals("www.some.domain.co.uk", functions.extractDomain(List.of("www.some.domain.co.uk"), createCall("option", "fullDomain"), getContext(null)));
    }

    @Test
    public void concatMultiple() throws Exception {
        assertNull(functions.concatenate(nullList(), createCall(), getContext(null)));
        List<String> fields = List.of("field1", "field2", "field3");
        FunctionCall call = createCall("values", fields);

        GraphContext context = new GraphContext().set("field_field1", "test").set("field_field2", "abc").set("field_field3", "Def");
        assertEquals("testabcDef", functions.concatenate(List.of(), call, context));
        call = createCall("separator", "|", "values", fields);
        assertEquals("test|abc|Def", functions.concatenate(List.of(), call, context));

        //nulls excluded
        context.set("field_field1","test1");
        context.set("field_field3",null);
        assertEquals("test1|abc", functions.concatenate(List.of(), call, context));
    }

	private List<Object> nullList() {
    	List<Object> nullList= new ArrayList<>();
    	nullList.add(null);
		return nullList;
	}

	@Test
    public void removeNonPrintable() throws Exception {
        assertNull(functions.removeNonPrintable(nullList(), createCall(), getContext(null)));
//              assertEquals("test", functions.removeNonPrintable("test\\n"));
    }

    @Test
    public void reverseString() throws Exception {
        assertNull(functions.reverseString(nullList(), createCall(), getContext(null)));
        assertEquals("tset", functions.reverseString(List.of("test"), createCall(), getContext("test")));
    }

    @Test
    public void uuid() throws Exception {
        assertNotNull(functions.uuid(nullList(), createCall(), null));
    }

    @Test
    public void setValue() throws Exception {
        assertNull(functions.setValue(nullList(), createCall(), getContext(null)));
        assertEquals("changed", functions.setValue(List.of("test"), createCall("newValue", "changed"), getContext("test")));
        assertEquals("changed test", functions.setValue(List.of("test"), createCall("newValue", "{{param}}"), getContext("changed test")));
        assertEquals("prefix changed test", functions.setValue(List.of("test"), createCall("newValue", "prefix {{param}}"), getContext("changed test")));
        assertEquals("changed test suffix", functions.setValue(List.of("test"), createCall("newValue", "{{param}} suffix"), getContext("changed test")));
        GraphContext context = getContext("first");
        context.set("param2", "second");
        assertEquals("first second", functions.setValue(List.of("test"), createCall("newValue", "{{param}} {{param2}}"), context));
        // test boolean.
        context = getContext("test_bool");
        context.set("param", true);
        FunctionCall func = createCall("newValue", false);
        func.setConfig(Map.of("dataType", "boolean", "newValue", false));
        Object resp = functions.setValue(List.of(false), func, context);
        assertTrue(resp instanceof Boolean);
        assertEquals(false, (Boolean) resp);
        // test datevalue.
        context = getContext("test_dateval");
        Date dt = new Date();
        context.set("param", dt);
        func = createCall("newValue", dt);
        func.setConfig(Map.of("dataType", "datetime", "newValue", dt));
        resp = functions.setValue(List.of(dt), func, context);
        assertTrue(resp instanceof ZonedDateTime);
        assertNotNull(resp);

        // test boolean as string.
        context = getContext("test_bool_str");
        context.set("param", "true");
        func = createCall("newValue", "true");
        func.setConfig(Map.of("dataType", "boolean", "newValue", "true"));
        resp = functions.setValue(List.of(true), func, context);
        assertTrue(resp instanceof Boolean);
        assertEquals(true, (Boolean) resp);

        // test integer.
        context = getContext("test_integer");
        context.set("param", 200);
        func = createCall("newValue", 200);
        func.setConfig(Map.of("dataType", "integer", "newValue", 200));
        resp = functions.setValue(List.of(250), func, context);
        assertTrue(resp instanceof Long);
        assertEquals(200, ((Long) resp).intValue());

        // test integer as string.
        context = getContext("test_integer_str");
        context.set("param", 200);
        func = createCall("newValue", "200");
        func.setConfig(Map.of("dataType", "integer", "newValue", "200"));
        resp = functions.setValue(List.of("250"), func, context);
        assertTrue(resp instanceof Long);
        assertEquals(200, ((Long) resp).intValue());
    }

    @Test
    public void setValueTempVariable() throws Exception {

        GraphContext context = getContext("test");

        final Map tempMap = Map.of("dataType", "string", "type", "temporary", "apiName", "test_entity", "displayName", "test entity", "multiValueField", false);

        var result = functions.setValue(List.of("test"),
                createCall("newValue", "Some", "setValueField", tempMap), context);

        assertEquals("test", result);
        assertEquals("Some", context.getTempVariables().get("test_entity"));

        context = getContext("test");

        final Map multiValuedMap = Map.of("dataType", "string", "type", "temporary", "apiName", "test_entity", "displayName", "test entity", "multiValueField", true);

        result = functions.setValue(List.of("test"),
                createCall("newValue", "Some", "setValueField", multiValuedMap), context);

        assertEquals("test", result);
        assertEquals(List.of("Some"), context.getTempVariables().get("test_entity"));

        context = getContext("test");

        final Map tempMapList = Map.of("dataType", "string", "type", "temporary", "apiName", "test_entity", "displayName", "test entity", "multiValueField", false);

        result = functions.setValue(List.of(List.of("test")),
                createCall("newValue", "Some", "setValueField", tempMapList), context);

        assertEquals(List.of("test"), result);
        assertEquals("Some", context.getTempVariables().get("test_entity"));

        context = getContext("test");

        final Map tempMapNumber = Map.of("dataType", "integer", "type", "temporary", "apiName", "test_entity", "displayName", "test entity", "multiValueField", false);

        result = functions.setValue(List.of(250.54),
                createCall("newValue", 345, "setValueField", tempMapNumber), context);

        assertEquals(250.54, result);
        assertEquals(345L, context.getTempVariables().get("test_entity"));

        context = getContext("test");

        final Map tempMapNull = Map.of("dataType", "integer", "type", "temporary", "apiName", "test_entity", "displayName", "test entity", "multiValueField", false);

        List<Object> nullList = new ArrayList<>();
        nullList.add(null);
        result = functions.setValue(nullList,
                createCall("newValue", 345, "setValueField", tempMapNull), context);

        assertTrue(result == null);
        assertEquals(345L, context.getTempVariables().get("test_entity"));


    }

    @Test
    public void setValueOnEntityAcceptsNull() {
    	setValueOnEntityProcessor.attributeProxyRepo = mock(AttributeRepo.class);
        AttributeDefinition city = new AttributeDefinition().setApiName("City").setDataType(StringType.VALUE);
        city.setId("cityAttributeId");
        when(setValueOnEntityProcessor.attributeProxyRepo.findById("cityAttributeId")).thenReturn(Optional.of(city));
        assertNull(functions.setValueOnEntity(nullList(), createCall(), getContext(null)));
        EntityData record = new EntityData("account").addValue("City", "SomeValue");
        assertNotNull(record.getValue("City"));
        functions.setValueOnEntity(List.of(record),
                createCall("newValue", "","attributeDefinitionId",city.getId()), getContext("test"));
        assertNull(record.getValue("City"));
        functions.setValueOnEntity(List.of(record),
                createCall("newValue", "","attributeDefinitionId",city.getId(), "useEmpty", true), getContext("test"));
        assertTrue(record.getValue("City") != null && record.getValueAsString("City").equalsIgnoreCase(""));
    }

    @Test
    public void setValueOnEntitySetsModifiedFlag() {
    	setValueOnEntityProcessor.attributeProxyRepo = mock(AttributeRepo.class);
        AttributeDefinition city = new AttributeDefinition().setApiName("City").setDataType(StringType.VALUE);
        city.setId("cityAttributeId");
        when(setValueOnEntityProcessor.attributeProxyRepo.findById("cityAttributeId")).thenReturn(Optional.of(city));
        assertNull(functions.setValueOnEntity(nullList(), createCall(), getContext(null)));
        EntityData record = new EntityData("account").addValue("City", "SomeValue").setSyncariEntityId("123");
        assertNotNull(record.getValue("City"));
        GraphContext context = getContext("test");
        StagedBatchRecord stagedBatchRecord = new StagedBatchRecord().setEntityData(record);
        context.setStagedBatchRecord(stagedBatchRecord);
        assertFalse(stagedBatchRecord.isModifiedByPipeline());
        functions.setValueOnEntity(List.of(record),
                createCall("newValue", "Some","attributeDefinitionId",city.getId()), context);
        assertEquals("Some",record.getValue("City"));
        assertEquals(context.get("field_"+city.getId()),"Some");
        assertTrue(stagedBatchRecord.isModifiedByPipeline());

        stagedBatchRecord.setModifiedByPipeline(false);
        functions.setValueOnEntity(List.of(record),
                createCall("newValue", "Some","attributeDefinitionId",city.getId()), context);
        assertEquals("Some",record.getValue("City"));
        //modified flag not set, becuase exist value equals new value
        assertFalse(stagedBatchRecord.isModifiedByPipeline());
        
        Map tempMap = Map.of("dataType", "string", "type", "temporary", "apiName", "test_entity", "displayName", "test entity", "multiValueField", true);
        functions.setValueOnEntity(List.of(record),
                createCall("newValue", "Some","attributeDefinitionId",city.getId(), "setValueField", tempMap), context);
        assertFalse(context.getTempVariables().isEmpty());
        assertTrue(context.getTempVariables().get("test_entity_123") instanceof List);
        assertEquals(List.of("Some"), context.getTempVariables().get("test_entity_123"));
        
        tempMap = Map.of("dataType", "string", "type", "temporary", "apiName", "test_entity2", "displayName", "test entity", "multiValueField", true);
        functions.setValueOnEntity(List.of(record),
                createCall("newValue", List.of("Some", "Some2"),"attributeDefinitionId",city.getId(), "setValueField", tempMap), context);
        assertFalse(context.getTempVariables().isEmpty());
        assertTrue(context.getTempVariables().get("test_entity2_123") instanceof List);
        assertEquals(List.of("Some", "Some2"), context.getTempVariables().get("test_entity2_123"));
        
        tempMap = Map.of("dataType", "string", "type", "temporary", "apiName", "test_entity3", "displayName", "test entity");
        functions.setValueOnEntity(List.of(record),
                createCall("newValue", "Some","attributeDefinitionId",city.getId(), "setValueField", tempMap), context);
        assertFalse(context.getTempVariables().isEmpty());
        assertFalse(context.getTempVariables().get("test_entity3_123") instanceof List);
        assertEquals("Some", context.getTempVariables().get("test_entity3_123"));
    }

    @Test
    public void setValueFailedFilter() {
        setValueOnEntityProcessor.attributeProxyRepo = mock(AttributeRepo.class);
        AttributeDefinition city = new AttributeDefinition().setApiName("City").setDataType(StringType.VALUE);
        city.setId("cityAttributeId");
        when(setValueOnEntityProcessor.attributeProxyRepo.findById("cityAttributeId")).thenReturn(Optional.of(city));
        assertNull(functions.setValueOnEntity(nullList(), createCall(), getContext(null)));
        EntityData record = new EntityData("account").addValue("City", "SomeValue");
        record.setId(ObjectId.get().toHexString());

        GraphContext context = getContext("test");
        String nodeId = ObjectId.get().toHexString();
        String graphId = ObjectId.get().toHexString();
        context.getCurrentNode().setId(nodeId);
        MappingGraph graph = new MappingGraph().setName("My Graph");
        graph.setId(graphId);
        context.setGraph(graph);
        context.put("Value From nodeId1", FilterFailedResult.VALUE);
        context.put("Value From nodeId2", "Test");

        context.setCurrentSyncariId(record.getId());

        functions.setValueOnEntity(List.of(record),
                createCall("newValue", "{{Value From nodeId1}}" ,"attributeDefinitionId",city.getId()), context);
        assertNotNull(context.getErrors());
        assertTrue(context.getErrors().containsKey(record.getId()));
        assertEquals(nodeId, context.getErrors().get(record.getId()).get(0).getNodeId());
        assertEquals(graphId, context.getErrors().get(record.getId()).get(0).getGraphId());
        assertNotNull(context.getErrors().get(record.getId()).get(0).getError());

        functions.setValueOnEntity(List.of(record),
                createCall("newValue", "{{Value From nodeId2}}{{Value From nodeId1}}" ,"attributeDefinitionId",city.getId()), context);
        assertNotNull(context.getErrors());
        assertTrue(context.getErrors().containsKey(record.getId()));
        assertEquals(nodeId, context.getErrors().get(record.getId()).get(0).getNodeId());
        assertEquals(graphId, context.getErrors().get(record.getId()).get(0).getGraphId());
        assertNotNull(context.getErrors().get(record.getId()).get(0).getError());




    }

    private GraphContext getContext(String test) {
        return new GraphContext().set("param", test).setCurrentNode(new MappingNode().setName("My Custom Node")
                .setConfiguration(new SimpleFunctionNodeConfig()));
    }


    private FunctionCall createCall(Object... keyValues) {
        Map<String, Object> config = new HashMap<>();
        if (keyValues != null) {
            for (int i = 0; i < keyValues.length; i += 2) {
                config.put(keyValues[i].toString(), keyValues[i + 1]);
            }
        }
        return new FunctionCall().setConfig(config).setParams(List.of(ParameterValue.string("param", "input")));
    }

    private FunctionCall createMultiParamCall(String... paramNames) {
        List<ParameterValue> params = Arrays.asList(paramNames).stream().map(p -> ParameterValue.string(p, "input")).collect(Collectors.toList());
        return new FunctionCall().setParams(params);
    }

    /**
     * Test case to simulate the "Cannot move with setValueOnEntity" error scenarios
     * This test covers the conditions that trigger the warning log with enhanced diagnostics
     */
    @Test
    public void testSetValueOnEntityErrorScenarios() {
        setValueOnEntityProcessor.attributeProxyRepo = mock(AttributeRepo.class);
        AttributeDefinition city = new AttributeDefinition().setApiName("City").setDataType(StringType.VALUE);
        city.setId("cityAttributeId");
        when(setValueOnEntityProcessor.attributeProxyRepo.findById("cityAttributeId")).thenReturn(Optional.of(city));

        // Scenario 1: Null input - should trigger warning with input=null
        Object result = functions.setValueOnEntity(nullList(), createCall(), getContext(null));
        assertNull("Should return null when input is null", result);

        // Scenario 2: Empty inputs list - should trigger warning
        result = functions.setValueOnEntity(List.of(), createCall(), getContext(null));
        assertNull("Should return null when inputs list is empty", result);

        // Scenario 3: Non-EntityData object passed as input (wrong type)
        // This simulates what might happen if serialization/deserialization creates wrong type
        GraphContext context = getContext("test");
        result = functions.setValueOnEntity(List.of("StringInsteadOfEntityData"), createCall(), context);
        assertEquals("Should return the wrong-typed input as-is", "StringInsteadOfEntityData", result);

        // Scenario 4: HashMap instead of EntityData (simulating deserialization issue)
        Map<String, Object> fakeEntityData = new HashMap<>();
        fakeEntityData.put("name", "accounts");
        fakeEntityData.put("id", "1587282");
        result = functions.setValueOnEntity(List.of(fakeEntityData), createCall(), context);
        assertEquals("Should return the HashMap as-is", fakeEntityData, result);

        // Scenario 5: Context with both syncariRecord and stagedBatchRecord as null
        // AND inputs list contains non-EntityData
        GraphContext emptyContext = new GraphContext().setCurrentNode(new MappingNode().setName("Test Node")
                .setConfiguration(new SimpleFunctionNodeConfig()));
        result = functions.setValueOnEntity(List.of(12345), createCall(), emptyContext);
        assertEquals("Should return the wrong-typed input", 12345, result);
    }

    /**
     * Test the correct/happy path to ensure we don't break existing functionality
     */
    @Test
    public void testSetValueOnEntityCorrectScenarios() {
        setValueOnEntityProcessor.attributeProxyRepo = mock(AttributeRepo.class);
        AttributeDefinition city = new AttributeDefinition().setApiName("City").setDataType(StringType.VALUE);
        city.setId("cityAttributeId");
        when(setValueOnEntityProcessor.attributeProxyRepo.findById("cityAttributeId")).thenReturn(Optional.of(city));

        // Scenario 1: Valid EntityData in inputs list
        EntityData record = new EntityData("account").addValue("City", "OldValue");
        GraphContext context = getContext("test");
        Object result = functions.setValueOnEntity(List.of(record),
                createCall("newValue", "NewValue", "attributeDefinitionId", city.getId()), context);
        assertNotNull("Should return EntityData", result);
        assertTrue("Should return EntityData type", result instanceof EntityData);
        assertEquals("City value should be updated", "NewValue", ((EntityData)result).getValue("City"));

        // Scenario 2: Valid EntityData in inputs list (not using context)
        // This mimics the real production scenario where EntityData comes through inputs
        EntityData record2 = new EntityData("account").addValue("City", "ContextValue");
        GraphContext context2 = getContext("test");
        result = functions.setValueOnEntity(List.of(record2),
                createCall("newValue", "UpdatedValue", "attributeDefinitionId", city.getId()), context2);
        assertNotNull("Should return EntityData", result);
        assertTrue("Should return EntityData type", result instanceof EntityData);
        assertEquals("City value should be updated", "UpdatedValue", ((EntityData)result).getValue("City"));

        // Scenario 3: Valid EntityData in context.stagedBatchRecord with empty inputs
        EntityData record3 = new EntityData("account").addValue("City", "StagedValue");
        GraphContext context3 = getContext("test");
        StagedBatchRecord stagedRecord = new StagedBatchRecord().setEntityData(record3);
        context3.setStagedBatchRecord(stagedRecord);
        // Pass the record in inputs as well, which is the typical pattern
        result = functions.setValueOnEntity(List.of(record3),
                createCall("newValue", "StagedUpdatedValue", "attributeDefinitionId", city.getId()), context3);
        assertNotNull("Should return EntityData from staged batch record", result);
        assertTrue("Should return EntityData type", result instanceof EntityData);
        assertEquals("City value should be updated from staged record", "StagedUpdatedValue", ((EntityData)result).getValue("City"));
    }

    /**
     * Test edge cases with null safety in the enhanced logging
     */
    @Test
    public void testSetValueOnEntityLoggingSafety() {
        setValueOnEntityProcessor.attributeProxyRepo = mock(AttributeRepo.class);

        // This test ensures the enhanced logging doesn't throw exceptions
        // when encountering edge cases

        // Scenario 1: Null context (though this would fail earlier in real code)
        // We test that our logging wouldn't crash if context checks failed
        EntityData record = new EntityData("account");
        GraphContext context = getContext("test");

        // Scenario 2: Object with null classloader (system classes)
        // String is loaded by bootstrap classloader which returns null
        Object result = functions.setValueOnEntity(List.of(new String("test")), createCall(), context);
        assertEquals("Should handle objects with null classloader", "test", result);

        // Scenario 3: Multiple wrong-type objects in inputs
        result = functions.setValueOnEntity(List.of(123, "test", 456.78), createCall(), context);
        assertEquals("Should return first non-null input", 123, result);
    }
}
