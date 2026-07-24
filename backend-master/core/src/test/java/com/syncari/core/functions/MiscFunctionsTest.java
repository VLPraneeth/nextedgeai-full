package com.syncari.core.functions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.core.model.MappingNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.syncari.core.TestConfig;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionResult;
import com.syncari.core.model.ParameterValue;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.GraphContext;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class MiscFunctionsTest {
    @Autowired
    MiscFunctions functions;

    @Test
    public void isFalse(){
        //If the input is a typical failed filter, return the original value
        assertEquals("success",functions.isFalse(new FilterFailedResult("success"),null, new GraphContext()));
        //If the input is succwesful, return a failure
        assertTrue(FilterFailedResult.isFailedFilter(functions.isFalse(new FunctionResult("success", StringType.VALUE),null, new GraphContext())));
        //if input is a failed filter with invalid results, return the same
        Object failedFilter = functions.isFalse(FilterFailedResult.VALUE, null, new GraphContext());
        assertTrue(FilterFailedResult.isFailedFilter(failedFilter));
        assertTrue(FilterFailedResult.class.cast(failedFilter).hasInvalidResults());
    }

    @Test
    public void predicateNode(){
        //If the config value is true, return the original value
        assertEquals("success",functions.predicate("success",new FunctionCall().setConfig(Map.of("value", true)), new GraphContext()));
        //If the config value is true, return the original value
        var result = functions.predicate("success",new FunctionCall().setConfig(Map.of("value", false)), new GraphContext());
        assertTrue(FilterFailedResult.isFailedFilter(result));
        assertEquals("success",functions.predicate(new FilterFailedResult("success"),new FunctionCall().setConfig(Map.of("value", false)), new GraphContext()));
    }

    @Test
    public void formatPhone() throws Exception {
        GraphContext context = getContext(null);
        context.setCurrentNode(new MappingNode().setName("PhoneFormat").setApiName("PhoneFormat"));
        context.put("Validation From " + context.getCurrentNode().getName(), null);
        GraphContext usContext = getContext("US");
        usContext.setCurrentNode(new MappingNode().setName("PhoneFormat").setApiName("PhoneFormat"));

        // phone with leading zeros
        String formatted = functions.formatPhone("000(740) 928-7035 x322", createCall("format", "E164", "countryCodeField", null, "defaultCountryCode", "US"), context);
        assertNotNull(formatted);
        assertEquals("+17409287035", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        // invalid phone with country code but no +
        formatted = functions.formatPhone("234-8023247048", createCall("format", "E164", "countryCodeField", null, "defaultCountryCode", "US"), context);
        assertNotNull(formatted);
        assertEquals("234-8023247048", formatted);
        // valid phone with country code but no +
        formatted = functions.formatPhone("650 230 6994", createCall("format", "E164", "countryCodeField", null, "defaultCountryCode", "US"), context);
        assertNotNull(formatted);
        assertEquals("+16502306994", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        //country token empty, default not empty, number is valid with EXT, has country code - formatted
        formatted = functions.formatPhone("(740) 928-7035 x322", createCall("format", "E164", "countryCodeField", null, "defaultCountryCode", "US"), context);
        assertNotNull(formatted);
        assertEquals("+17409287035", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);
        formatted = functions.formatPhone("(919) 544-7030 x.31158", createCall("format", "E164", "countryCodeField", null, "defaultCountryCode", "US"), context);
        assertNotNull(formatted);
        assertEquals("+19195447030", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);
        formatted = functions.formatPhone("(212) 944-6000 press 0", createCall("format", "E164", "countryCodeField", null, "defaultCountryCode", "US"), context);
        assertNotNull(formatted);
        assertEquals("(212) 944-6000 press 0", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);
        formatted = functions.formatPhone("+27 42) 125-8455", createCall("format", "E164", "countryCodeField", null, "defaultCountryCode", "US"), context);
        assertNotNull(formatted);
        assertEquals("+27 42) 125-8455", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        //country token empty, default empty, number is valid, has country code - formatted
        formatted = functions.formatPhone("+1 650 230 6994", createCall("format", "E164", "countryCodeField", null, "defaultCountryCode", null), context);
        assertNotNull(formatted);
        assertEquals(true, context.get("Validation From " + context.getCurrentNode().getName()));
        assertEquals("+16502306994", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        //country token empty, default empty, number is valid, does not have country code - NOT formatted
        formatted = functions.formatPhone("650 230 6994", createCall("format", "E164", "countryCodeField", null, "defaultCountryCode", null), context);
        assertNotNull(formatted);
        assertEquals(false, context.get("Validation From " + context.getCurrentNode().getName()));
        assertEquals("650 230 6994", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        //country token empty, default not empty, number is valid, has country code - formatted
        formatted = functions.formatPhone("+1 650 230 6994", createCall("format", "E164", "countryCodeField", null, "defaultCountryCode", "IN"), context);
        assertNotNull(formatted);
        assertEquals("+1 650 230 6994", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        //country token empty, default not empty, number is valid, does not have country code - formatted
        formatted = functions.formatPhone("080 230 88401", createCall("format", "E164", "countryCodeField", null, "defaultCountryCode", "IN"), context);
        assertNotNull(formatted);
        assertEquals("+918023088401", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        //country token empty, default not empty, number is invalid, does not have country code - NOT formatted
        formatted = functions.formatPhone("66994", createCall("format", "E164", "countryCodeField", null, "defaultCountryCode", "IN"), context);
        assertNotNull(formatted);
        assertEquals(false, context.get("Validation From " + context.getCurrentNode().getName()));
        assertEquals("66994", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        //country token not empty, default not empty, number is valid, has country code - formatted
        formatted = functions.formatPhone("+1 650 230 6994", createCall("format", "E164", "countryCodeField", "{{param}}", "defaultCountryCode", "IN"), usContext);
        assertNotNull(formatted);
        assertEquals("+16502306994", formatted);
        usContext.put("Validation From " + context.getCurrentNode().getName(), null);

        //country token not empty, default not empty, number is valid, does not have country code - formatted
        formatted = functions.formatPhone("650 230 6994", createCall("format", "E164", "countryCodeField", "{{param}}", "defaultCountryCode", "IN"), usContext);
        assertNotNull(formatted);
        assertEquals("+16502306994", formatted);
        usContext.put("Validation From " + context.getCurrentNode().getName(), null);

        //country token not empty, default not empty, number is invalid, does not have country code - NOT formatted
        formatted = functions.formatPhone("6 6994", createCall("format", "E164", "countryCodeField", "{{param}}", "defaultCountryCode", "IN"), usContext);
        assertNotNull(formatted);
        assertEquals(false, usContext.get("Validation From " + context.getCurrentNode().getName()));
        assertEquals("6 6994", formatted);
        usContext.put("Validation From " + context.getCurrentNode().getName(), null);

        //country token not empty, default empty, number is valid, has country code - formatted
        formatted = functions.formatPhone("+1 650 230 6994", createCall("format", "E164", "countryCodeField", "{{param}}", "defaultCountryCode", null), usContext);
        assertNotNull(formatted);
        assertEquals("+16502306994", formatted);
        usContext.put("Validation From " + context.getCurrentNode().getName(), null);

        //country token not empty, default not empty, number is valid, does not have country code - formatted
        formatted = functions.formatPhone("650 230 6994", createCall("format", "E164", "countryCodeField", "{{param}}", "defaultCountryCode", null), usContext);
        assertNotNull(formatted);
        assertEquals("+16502306994", formatted);
        usContext.put("Validation From " + context.getCurrentNode().getName(), null);

        //country token not empty, default not empty, number is invalid, does not have country code - NOT formatted
        formatted = functions.formatPhone("6 6994", createCall("format", "E164", "countryCodeField", "{{param}}", "defaultCountryCode", null), usContext);
        assertNotNull(formatted);
        assertEquals(false, usContext.get("Validation From " + usContext.getCurrentNode().getName()));
        assertEquals("6 6994", formatted);
        usContext.put("Validation From " + context.getCurrentNode().getName(), null);

        formatted = functions.formatPhone("+1 650 440 6901", createCall("format", "NATIONAL", "countryCodeField", null), context);
        assertNotNull(formatted);
        assertEquals("(650) 440-6901", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        formatted = functions.formatPhone("+16507306981", createCall("format", "NATIONAL", "countryCodeField", null), context);
        assertNotNull(formatted);
        assertEquals("(650) 730-6981", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        formatted = functions.formatPhone("+9129977591", createCall("format", "NATIONAL", "countryCodeField", null), context);
        assertNotNull(formatted);
        assertEquals("+9129977591", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        formatted = functions.formatPhone("2032136894", createCall("format", PhoneNumberFormat.INTERNATIONAL.name(), "defaultCountryCode", "US"), context);
        assertNotNull(formatted);
        assertEquals("+1 203-213-6894", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        formatted = functions.formatPhone("+2032136894", createCall("format", PhoneNumberFormat.INTERNATIONAL.name(), "defaultCountryCode", "US"), context);
        assertNotNull(formatted);
        assertEquals("+1 203-213-6894", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        formatted = functions.formatPhone("+2032136894", createCall("format", PhoneNumberFormat.INTERNATIONAL.name(), "countryCodeField", "{{param}}", "defaultCountryCode", null), usContext);
        assertNotNull(formatted);
        assertEquals("+1 203-213-6894", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        formatted = functions.formatPhone("+2032136894", createCall("format", PhoneNumberFormat.INTERNATIONAL.name(), "countryCodeField", null), context);
        assertNotNull(formatted);
        assertEquals("+20 3 2136894", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        formatted = functions.formatPhone("+20 3 2136894", createCall("format", PhoneNumberFormat.INTERNATIONAL.name(), "defaultCountryCode", "US"), context);
        assertNotNull(formatted);
        assertEquals("+1 203-213-6894", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        formatted = functions.formatPhone("+20 3 2136894", createCall("format", PhoneNumberFormat.INTERNATIONAL.name(), "countryCodeField", "{{param}}", "defaultCountryCode", null), usContext);
        assertNotNull(formatted);
        assertEquals("+1 203-213-6894", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

        formatted = functions.formatPhone("+20 3 2136894", createCall("format", PhoneNumberFormat.INTERNATIONAL.name(), "countryCodeField", null), context);
        assertNotNull(formatted);
        assertEquals("+20 3 2136894", formatted);
        context.put("Validation From " + context.getCurrentNode().getName(), null);

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
    
    private GraphContext getContext(String test) {
        return new GraphContext().set("param", test);
    }
}
