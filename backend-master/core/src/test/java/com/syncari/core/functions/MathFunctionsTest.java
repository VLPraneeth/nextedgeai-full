package com.syncari.core.functions;

import com.syncari.connector.EntityData;
import com.syncari.connector.datastore.PostgresqlDatastoreService;
import com.syncari.core.SyncariContext;
import com.syncari.core.TestConfig;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.Instance;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.service.ConnectorService;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.math.RoundingMode.HALF_DOWN;
import static java.math.RoundingMode.HALF_UP;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class MathFunctionsTest {
	@Autowired
	MathFunctions functions;
	@Autowired
	PostgresqlDatastoreService service;
	@Autowired
	ConnectorService connectorService;

	@Test
	public void random() throws Exception {
		assertTrue(functions.random(null, null, null) > 0);
		assertTrue(functions.random(null, null, null) < 1);
	}

	@Test
	public void abs() throws Exception {
		assertNull(functions.abs(nulls(), null, getContext()));
		assertEquals(1, functions.abs(List.of(-1.0), null, getContext()), 0);
	}

	private static GraphContext getContext() {
		GraphContext gc = new GraphContext();
		gc.setCurrentNode(new MappingNode().setApiName("test").setName("test").setConfiguration(new SimpleFunctionNodeConfig()));
		return gc;
	}

	@Test
	public void ceil() throws Exception {
		assertNull(functions.ceil(nulls(), null, getContext()));
		assertEquals(2, functions.ceil(List.of(1.3), null, getContext()), 2);
	}

	@Test
	public void floor() throws Exception {
		assertNull(functions.floor(nulls(), null, getContext()));
		assertEquals(1, functions.floor(List.of(1.3), null, getContext()), 0);
	}

	@Test
	public void max() throws Exception {
		assertNull(functions.max(nulls(), null, getContext()));
		assertEquals(2, functions.max(List.of(1, 2), null, getContext()), 0);
	}

	@Test
	public void min() throws Exception {
		assertNull(functions.min(nulls(), null, getContext()));
		assertEquals(1, functions.min(List.of(1, 2), null, getContext()), 0);
	}

	@Test
	public void round() throws Exception {
		assertNull(functions.round(nulls(), new FunctionCall(), getContext()));
		assertEquals(1, functions.round(List.of(1.3), new FunctionCall(), getContext()), 0);
		assertEquals(2, functions.round(List.of(1.6), new FunctionCall(), getContext()), 0);
	}

	@Test
	public void multiply() throws Exception {
		// case 1: valid input and multiplier
		FunctionCall multiplyBy = new FunctionCall().setConfig(Map.of("multiplyBy", "2"));
		assertEquals(6, functions.multiply(List.of(3), multiplyBy, getContext()), 0);

		// case 2: null input
		assertNull(functions.multiply(nulls(), multiplyBy, getContext()));

		// case 3: default multiplier as 1.0 if not specified
		multiplyBy = new FunctionCall().setConfig(Map.of());
		assertEquals(3, functions.multiply(List.of(3), multiplyBy, getContext()), 0);

		// case 4: null multiplier
		Map<String, Object> config = new HashMap<>();
		config.put("multiplyBy", null);
		multiplyBy = new FunctionCall().setConfig(config);
		assertEquals(0.0, functions.multiply(List.of(3), multiplyBy, getContext()), 0);
	}

	@Test
	public void increment() throws Exception {
		FunctionCall amountToAdd = new FunctionCall().setConfig(Map.of("amountToAdd", "5.3"));
		assertNull(functions.increment(nulls(), amountToAdd, getContext()));
		assertEquals(6.3, functions.increment(List.of(1), amountToAdd, getContext()), 0);
	}

	@Test
	public void decrement() throws Exception {
		FunctionCall amountToSubtract = new FunctionCall().setConfig(Map.of("amountToSubtract", "1"));
		assertNull(functions.increment(nulls(), amountToSubtract, getContext()));
		assertEquals(0, functions.decrement(List.of(1), amountToSubtract, getContext()), 0);
		amountToSubtract = new FunctionCall().setConfig(Map.of("amountToSubtract", "649.98"));
		assertEquals(0.02d, functions.decrement(List.of(650), amountToSubtract, getContext()), 0);
	}

	@Test
	public void autoIncrement() throws Exception {
		FunctionCall func = new FunctionCall().setConfig(Map.of("sequenceName", "contactId"));
		if(SyncariContext.getInstance() == null) {
			Instance instance = new Instance();
			instance.setSyncariId("AAAAA");
			SyncariContext.setInstance(instance);
		}
		try {
			service.createSequence(connectorService.getDataStoreSharedDb(), "contactId", new Long("1"));
			assertEquals("2", functions.autoIncrement(nulls(), func, getContext()).toString());

			service.createSequence(connectorService.getDataStoreSharedDb(), "accountId", new Long("200"));
			func = new FunctionCall().setConfig(Map.of("sequenceName", "accountId"));
			assertEquals("201", functions.autoIncrement(nulls(), func, getContext()).toString());

			func = new FunctionCall().setConfig(Map.of("sequenceName", "nonexisting"));
			assertEquals("1", functions.autoIncrement(nulls(), func, getContext()).toString());

			func = new FunctionCall().setConfig(Map.of("sequenceName", "nonexisting1", "startValue", "100"));
			assertEquals("100", functions.autoIncrement(nulls(), func, getContext()).toString());
		} finally {
			service.deleteSequence(connectorService.getDataStoreSharedDb(), "contactId");
			service.deleteSequence(connectorService.getDataStoreSharedDb(), "accountId");
			service.deleteSequence(connectorService.getDataStoreSharedDb(), "nonexisting");
			service.deleteSequence(connectorService.getDataStoreSharedDb(), "nonexisting1");
			if(SyncariContext.getSyncariId().equalsIgnoreCase("AAAAA")) {
				SyncariContext.setInstance(null);
			}
		}
	}

	@Test
	public void simpleComputeRatio() {
		FunctionCall computeRatio = new FunctionCall().setConfig(Map.of(
				"numerator", "12.3",
				"denominator", "2"
		));
		Double result = functions.computeRatio(nulls(), computeRatio, getContext());
		assertEquals(6.15d,result.doubleValue(),0.0001d);
	}

	@Test
	public void computeRatioAsPercentage() {
		FunctionCall computeRatio = new FunctionCall().setConfig(Map.of(
				"numerator", "4",
				"denominator", "16",
				"asPercentage", "true"
		));
		Double result = functions.computeRatio(nulls(), computeRatio, getContext());
		assertEquals(25,result.doubleValue(),0.0001d);
	}

	@Test
	public void computeRatioWithNonNumbers() {
		FunctionCall computeRatio = new FunctionCall().setConfig(Map.of(
				"numerator", "badnumber",
				"denominator", "12",
				"asPercentage", "true"
		));
		Double result = functions.computeRatio(nulls(), computeRatio, getContext());
		assertEquals(0,result.doubleValue(),0.0001d);
		FunctionCall computeRatio2 = new FunctionCall().setConfig(Map.of(
				"numerator", "12",
				"denominator", "noNumber",
				"asPercentage", "true"
		));
		Double result2 = functions.computeRatio(nulls(), computeRatio2, getContext());
		assertEquals(0,result2.doubleValue(),0.0001d);
	}
	@Test
	public void computeRatioWithNoRounding() {
		FunctionCall computeRatio = new FunctionCall().setConfig(Map.of(
				"numerator", "2",
				"denominator", "3"
		));
		Double result = functions.computeRatio(nulls(), computeRatio, getContext());
		assertEquals(2.0/3.0,result.doubleValue(),0.0001d);
	}
	@Test
	public void computeRatioDivideByZero() {
		FunctionCall computeRatio = new FunctionCall().setConfig(Map.of(
				"numerator", "2",
				"denominator", "0"
		));
		Double result = functions.computeRatio(nulls(), computeRatio, getContext());
		assertEquals(0d,result.doubleValue(),0.0001d);
	}

	@Test
	public void computeRatioRound() {
		FunctionCall computeRatio = new FunctionCall().setConfig(Map.of(
				"numerator", "2",
				"denominator", "3",
				"asPercentage", "true",
				"roundTo", "2"
		));
		Double result = functions.computeRatio(nulls(), computeRatio, getContext());
		assertEquals(66.67,result.doubleValue(),0.0001d);
	}

	@Test
	public void computeRatioNumber() {
		FunctionCall computeRatio = new FunctionCall().setConfig(Map.of(
				"numerator", "2",
				"denominator", "3",
				"roundTo", "2"
		));
		Double result = functions.computeRatio(nulls(), computeRatio, getContext());
		assertEquals(0.67d,result.doubleValue(),0.0001d);
	}

	@Test
	public void computeRatioforNullPercentage() {
		Map<String, Object > map = new HashMap<>();
		map.put("numerator", "2");
		map.put("denominator", "3");
		map.put("asPercentage", null);
		map.put("roundTo", "2");
		FunctionCall computeRatio = new FunctionCall().setConfig(map);
		Double result = functions.computeRatio(nulls(), computeRatio, getContext());
		assertEquals(0.67d,result.doubleValue(),0.0001d);
	}

	@Test
	public void computeRatioforNullRound() {
		Map<String, Object > map = new HashMap<>();
		map.put("numerator", "4");
		map.put("denominator", "2");
		map.put("asPercentage", null);
		map.put("roundTo", null);
		FunctionCall computeRatio = new FunctionCall().setConfig(map);
		Double result = functions.computeRatio(nulls(), computeRatio, getContext());
		assertEquals(2.0d,result.doubleValue(),0.0001d);
	}

	@Test
	public void decrementWithToken() throws Exception {
		assertEquals(950, functions.decrement(List.of(1000),new FunctionCall().setConfig(Map.of("amountToSubtract","{{account.revenue}}")),
				new GraphContext(Map.of("account",Map.of("revenue",50)))), 0);
	}

    @Test
    public void roundWithParams() throws Exception {
        assertEquals(13, functions.round(List.of(12.74757), new FunctionCall()
                        .setConfig(Map.of()),
                new GraphContext()), 0);
        assertEquals(4567, functions.round(List.of(12.74757), new FunctionCall()
                        .setConfig(Map.of("value", "{{account.values.revenue}}", "roundingMode", "HALF_UP")),
                new GraphContext().set("account", new EntityData().addValue("revenue", "4567.323"))), 0);
        assertEquals(4567.32, functions.round(List.of(12.74757), new FunctionCall()
                        .setConfig(Map.of("value", "{{account.values.revenue}}", "roundingMode",
                                HALF_UP.name(), "decimalPoints", "2")),
                new GraphContext().set("account", new EntityData().addValue("revenue", 4567.323))), 0);
        assertEquals(12.60, functions.round(List.of(12.74757), new FunctionCall()
                        .setConfig(Map.of("value", "{{account.values.revenue}}", "roundingMode", HALF_UP.name(), "decimalPoints", "1")),
                new GraphContext().set("account", new EntityData().addValue("revenue", 12.55))), 0);
		assertEquals(12.50, functions.round(List.of(), new FunctionCall()
                        .setConfig(Map.of("value", "{{account.values.revenue}}", "roundingMode", HALF_DOWN.name(), "decimalPoints", "1")),
				new GraphContext().set("account", new EntityData().addValue("revenue", 12.545))), 0);
		assertEquals(12, functions.round(List.of(), new FunctionCall()
                .setConfig(Map.of(
                        "value", "{{account.values.revenue}}",
                        "roundingMode", HALF_DOWN.name(),
						"decimalPoints", 0)
				), new GraphContext().set("account", new EntityData().addValue("revenue", 12.45))), 0);
		assertEquals(5.13, functions.round(List.of(), new FunctionCall()
                .setConfig(Map.of(
                        "value", "{{account.values.revenue}}",
						"roundingMode", HALF_UP.name(),
						"decimalPoints", 2)
				), new GraphContext().set("account", new EntityData().addValue("revenue", 5.127456))), 0);


    }

	protected List<Object> nulls() {
		List<Object> nulls = new ArrayList<>();
		nulls.add(null);
		return nulls;
	}

}
