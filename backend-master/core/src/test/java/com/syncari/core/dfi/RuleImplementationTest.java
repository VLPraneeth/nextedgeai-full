package com.syncari.core.dfi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import com.syncari.core.TestConfig;
import com.syncari.utils.DateUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class RuleImplementationTest {
    @Autowired
    RuleImplementation ruleImplementation;

    @Before
    public void init() {
        if (ruleImplementation == null) {
            ruleImplementation = new RuleImplementation();
            ruleImplementation.dateUtil = new DateUtil();
        }
    }

    @Test
    public void isNumber() {
        for (int number: List.of(1, 3, 5, -3, 15, Integer.MAX_VALUE, Integer.MIN_VALUE)) {
            assertTrue(RuleResultType.match == ruleImplementation.execute(RuleConstants.IS_NUMBER, number).getResultType());
        }
        
    }

    @Test
    public void isNumberDouble() {
        for (Number number: List.of(1, 3, 5, -3, 15, Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, 5.00, -10.45678)) {
            assertTrue(RuleResultType.match == ruleImplementation.execute(RuleConstants.IS_NUMBER, number.doubleValue()).getResultType());
        }
    }

    @Test
    public void isNotNumber() {
        for (String number: List.of("blah", "blah2", "5.00", "")) {
            assertFalse(RuleResultType.match == ruleImplementation.execute(RuleConstants.IS_NUMBER, number).getResultType());
        }
    }

    @Test
    public void isCamelCase() {
        for (String number: List.of("Blah", "Blahblah", "Camel Case Case Camel")) {
            assertTrue(RuleResultType.match == ruleImplementation.execute(RuleConstants.IS_CAMEL_CASED, number).getResultType());
        }
    }

    @Test
    public void isNotCamelCase() {
        for (String number: List.of("Blah blah", "blah_Blah2", "abcAAC")) {
            assertFalse(RuleResultType.match == ruleImplementation.execute(RuleConstants.IS_CAMEL_CASED, number).getResultType());
        }
    }

    @Test
    public void matchesRegex() {
        for (String value: List.of("This is a ReGex test", "regex test", "test REGEX", "testregexinbetweentext")) {
            assertTrue(String.format("Value `%s` does not match regex.", value),
                RuleResultType.match == ruleImplementation.execute(RuleConstants.MATCHES_REGEX, value, List.of("ReGex")).getResultType());
        }
    }

    @Test
    public void noMatchesRegex() {
        for (String value: List.of("This is a nonrex test", "re gex test", "test RE-GEX", "")) {
            assertFalse(String.format("Value `%s` does match regex.", value),
                RuleResultType.match == ruleImplementation.execute(RuleConstants.MATCHES_REGEX, value, List.of("ReGex")).getResultType());
        }
    }

    @Test
    public void withinNumericRange() {
        for (int value: List.of(30, 31, 40, 60, 69, 70)) {
            assertTrue(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, value, List.of("30", "70")).getResultType());
            assertTrue(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, Integer.valueOf(value), List.of("30", "70")).getResultType());
        }
    }

    @Test
    public void withinNumericDoubleRange() {
        for (double value: List.of(30.5, 31.100, 40.200, 60.2999, 69.1934, 70.000)) {
            assertTrue(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, value, List.of("30", "70")).getResultType());
            assertTrue(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, BigDecimal.valueOf(value), List.of("30", "70")).getResultType());
        }
    }

    @Test
    public void withinNumericFloatRange() {
        for (float value: List.of(30.5f, 31.100f, 40.200f, 60.2999f, 69.1934f, 70.000f)) {
            assertTrue(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, value, List.of("30", "70")).getResultType());
            assertTrue(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, Float.valueOf(value), List.of("30", "70")).getResultType());
        }
    }
    
    @Test
    public void withinNumericLongRange() {
        for (long value: List.of(30l, 31l, 40l, 60l, 69l, 70l)) {
            assertTrue(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, value, List.of("30", "70")).getResultType());
            assertTrue(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, Long.valueOf(value), List.of("30", "70")).getResultType());
            assertTrue(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, BigInteger.valueOf(value), List.of("30", "70")).getResultType());
        }
    }

    @Test
    public void notWithinIntegerRange() {
        for (int value: List.of(Integer.MIN_VALUE, 10, 20, 29, 71, 100, Integer.MAX_VALUE)) {
            assertFalse(RuleResultType.match == 
                ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, value, List.of("30", "70")).getResultType());
            assertFalse(RuleResultType.match == 
                ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, Integer.valueOf(value), List.of("30", "70")).getResultType());
        }
    }

    @Test
    public void notWithinDoubleRange() {
        for (double value: List.of(10.00, 20.123, 29.999, 71.000, 100.989)) {
            assertFalse(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, value, List.of("30", "70")).getResultType());
            assertFalse(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, BigDecimal.valueOf(value), List.of("30", "70")).getResultType());
        }
    }

    @Test
    public void notWithinFloatRange() {
        for (float value: List.of(10.00f, 20.123f, 29.999f, 71.000f, 100.989f)) {
            assertFalse(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, value, List.of("30", "70")).getResultType());
            assertFalse(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, Float.valueOf(value), List.of("30", "70")).getResultType());
        }
    }

    @Test
    public void notWithinLongRange() {
        for (long value: List.of(10l, 20l, 29l, 71l, 100l)) {
            assertFalse(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, value, List.of("30", "70")).getResultType());
            assertFalse(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, Long.valueOf(value), List.of("30", "70")).getResultType());
            assertFalse(RuleResultType.match == 
               ruleImplementation.execute(RuleConstants.WITHIN_NUMERIC_RANGE, BigInteger.valueOf(value), List.of("30", "70")).getResultType());
        }
    }

    @Test
    public void withinLengthRange() {
        for (String value: List.of("five5", "five66", "helloworld")) {
            assertTrue(RuleResultType.match == 
                ruleImplementation.execute(RuleConstants.WITHIN_LENGTH_RANGE, value, List.of("5", "10")).getResultType());
        }
    }

    @Test
    public void notWithinLengthRange() {
        for (String value: List.of("five", "", "helloworldisalengthytext")) {
            assertFalse(RuleResultType.match == 
                ruleImplementation.execute(RuleConstants.WITHIN_LENGTH_RANGE, value, List.of("5", "10")).getResultType());
        }
    }

    @Test
    public void withinDateRange() {
        List dateRange = List.of("2021-03-01T00:00:00.000Z", "2021-03-29T23:59:59.999Z");
        for (String value: List.of("2021-03-01T00:00:00.000Z", "2021-03-29T23:59:59.999Z", 
                "2021-03-28T23:59:59.999Z", "2021-03-02T01:59:59.999Z")) {
            assertTrue(String.format("Failed to assert that %s is within range %s", value, dateRange),
                RuleResultType.match == ruleImplementation.execute(RuleConstants.WITHIN_DATE_RANGE, value, dateRange).getResultType());
        }
    }

    @Test
    public void notWithinDateRange() {
        List dateRange = List.of("2021-03-01T00:00:00.000Z", "2021-03-29T23:59:59.999Z");
        for (String value: List.of("2021-02-28T23:59:59.999Z", "2021-03-30T00:00:00.000Z", 
                "2021-03-30T23:59:59.999Z", "2021-01-01T01:59:59.999Z", "")) {
            assertFalse(String.format("Failed to assert that %s is not within range %s", value, dateRange),
                RuleResultType.match == ruleImplementation.execute(RuleConstants.WITHIN_DATE_RANGE, value, dateRange).getResultType());
        }
    }

    @Test
    public void validPhone() {
        for (String value: List.of("+16505554545","+1 (650) 555-4545","+16505554545","+919845424512","+91-98454-24512","(650)-555-4545","650-555-4545",
            "1 (650) 555-4545", "6505554545","91-98454-24512","919845424512","1-800-MY-APPLE",
            "1-800-MY-APPLE..","+1(650) 555-4545","+1650 555-4545","1800 FOR PZZA","800-345-6000","650) 253-0000")) {
            assertTrue("Failed for input: " + value, RuleResultType.match == 
                ruleImplementation.execute(RuleConstants.IS_PHONE_FORMATTED, value).getResultType());
        }
    }

    @Test
    public void invalidPhone() {
        for (String value: List.of("65083636489504093837","ABCD123456","+68919845424512","+165083636489504093837",
            "+1(650)83636489504093837","+650-555-4545","+1(650)-123-1234","+1876 1234-1234","+876 1234-1234","+1-876 1234-1234")) {
            assertFalse("Failed for input: " + value, RuleResultType.match == 
                ruleImplementation.execute(RuleConstants.IS_PHONE_FORMATTED, value).getResultType());
        }
    }
}