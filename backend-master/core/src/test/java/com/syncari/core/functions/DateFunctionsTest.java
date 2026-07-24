package com.syncari.core.functions;

import com.syncari.core.TestConfig;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.pipeline.GraphContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class DateFunctionsTest {
	@Autowired
	DateFunctions functions;

	@Test
	public void now() {
		assertNotNull(functions.now(null, null,null));
	}

	@Test
	public void dayOfMonth() {
		assertNull(functions.dayOfMonth(null,null,null));
		assertEquals(2, functions.dayOfMonth(ZonedDateTime.parse("2007-12-02T10:15:30.00Z"),null,null).intValue());
	}

	@Test
	public void nowOnEntity() {
		GraphContext context = getContext(null);
		functions.nowOnEntity(List.of(), createCall("returnValue", DateFunctions.DATETIME), context);
		assertEquals(ZonedDateTime.class, context.get("previousValue").getClass());
		functions.nowOnEntity(List.of(), createCall("returnValue", DateFunctions.DATE), context);
		assertEquals(LocalDate.class, context.get("previousValue").getClass());
		functions.nowOnEntity(List.of(), createCall("returnValue", DateFunctions.SECONDS_OPTION), context);
		assertEquals(Long.class, context.get("previousValue").getClass());
		functions.nowOnEntity(List.of(), createCall("returnValue", DateFunctions.MILLIS_OPTION), context);
		assertEquals(Long.class, context.get("previousValue").getClass());
	}

	@Test
	public void dayOfWeek() {
	    assertNull(functions.dayOfWeek(null,null,null));
		assertEquals(6, functions.dayOfWeek(ZonedDateTime.parse("2007-12-01T10:15:30.00Z"),null,null).intValue());
		assertEquals(1, functions.dayOfWeek(ZonedDateTime.parse("2022-08-08T10:15:30.00Z"),null,null).intValue());
	}

	@Test
	public void dayOfYear() {
	    assertNull(functions.dayOfYear(null,null,null));
		assertEquals(335, functions.dayOfYear(ZonedDateTime.parse("2007-12-01T10:15:30.00Z"),null,null).intValue());
	}

	@Test
	public void format() {
		ZonedDateTime now = ZonedDateTime.now();
		assertNull(functions.dateFormat(null, createCall(),null));
		assertNotNull(functions.dateFormat(new Date(), createCall("pattern","yyyy-MM-dd'T'HH:mm:ssZ"),null));
		assertNotNull(functions.dateFormat(now, createCall("pattern","yyyy-MM-dd'T'HH:mm:ssz"),null));
		assertNotNull(functions.dateFormat(now, createCall("pattern","yyyy-MM-dd'T'HH:mm:ss.SSSz"),null));
		assertEquals(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssz")),
				functions.dateFormat(now, createCall("pattern","yyyy-MM-dd'T'HH:mm:ssz"),null));
		assertEquals(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSz")),
				functions.dateFormat(now, createCall("pattern","yyyy-MM-dd'T'HH:mm:ss.SSSz"),null));
		assertEquals(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSz")),
				functions.dateFormat(Date.from(now.toInstant()), createCall("pattern","yyyy-MM-dd'T'HH:mm:ss.SSSz"),null));
	}


	@Test
	public void parse() {

		DateTimeFormatter validationFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

		assertNull(functions.parse(null, createCall(),null));

		assertEquals("2021-10-12 01:14:31", functions.parse("1634001271",
				createCall("format", "Epoch Timestamp in Seconds"),null).format(validationFormat));
		assertNotEquals("2021-10-12 01:14:31", functions.parse("1634001271",
				createCall("format", "Epoch Timestamp in Milliseconds"),null).format(validationFormat));

		assertEquals("2021-10-12 01:14:31", functions.parse("1634001271000",
				createCall("format", "Epoch Timestamp in Milliseconds"),null).format(validationFormat));
		assertNotEquals("2021-10-12 01:14:31", functions.parse("1634001271000",
				createCall("format", "Epoch Timestamp in Seconds"),null).format(validationFormat));

		assertEquals("2001-12-01 10:15:30", functions.parse("01_12_2001 10_15_30",
				createCall("format", "dd_MM_yyyy HH_mm_ss"),null).format(validationFormat));

		assertEquals("2001-12-01 10:15:30", functions.parse("01_12_2001 10_15_30+0000",
				createCall("format", "dd_MM_yyyy HH_mm_ssZ"),null).format(validationFormat));

		// invalid time(hour)
		assertNull(functions.parse("01_12_2001 24_15_30+0000",
				createCall("format", "dd_MM_yyyy HH_mm_ssZ"),null));

		assertNull(functions.parse(null, createCall("format", ""),null));
	}

	@Test
	public void plus() {
		assertNull(functions.plus(null, createCall("delta",10l, "unit",ChronoUnit.MONTHS.name()),null));
		assertEquals(ZonedDateTime.parse("2007-12-01T10:15:40Z"),
				functions.plus(ZonedDateTime.parse("2007-12-01T10:15:30.00Z"), createCall("delta","10", "unit",ChronoUnit.SECONDS.name()),null));
		assertEquals(ZonedDateTime.parse("2007-12-01T10:15:40Z"),
				functions.plus(ZonedDateTime.parse("2007-12-01T10:15:30.00Z"), createCall("delta",10, "unit",ChronoUnit.SECONDS.name()),null));
	}

	@Test
	public void minus() {
		assertNull(functions.minus(null, createCall("delta",10l, "unit",ChronoUnit.MONTHS.name()),null));
		assertEquals(ZonedDateTime.parse("2007-12-01T10:15:20Z"),
				functions.minus(ZonedDateTime.parse("2007-12-01T10:15:30.00Z"), createCall("delta","10", "unit",ChronoUnit.SECONDS.name()),null));
		assertEquals(ZonedDateTime.parse("2007-12-01T10:15:20Z"),
				functions.minus(ZonedDateTime.parse("2007-12-01T10:15:30.00Z"), createCall("delta",10, "unit",ChronoUnit.SECONDS.name()),null));
	}

	@Test
	public void dateDiff(){

		FunctionCall call1 = createCall("toDate", "15-08-2022 12:30", "fromDate", "16-09-2022 12:30", "unit", "MONTHS");
		assertEquals(Long.valueOf(-1), functions.dateDiff("", call1, null));

		//One of the operand is string and others are date,long,localDate,zonedDateTime,instant
		FunctionCall call = createCall("toDate", "2022/04/01", "fromDate", "2021/03/01", "unit", "MONTHS");
		assertEquals(Long.valueOf(13), functions.dateDiff("", call, null));

		Date date = new Date();

		call = createCall("toDate", date, "fromDate", "2022/09/25", "unit", "DAYS");
		assertNotNull(functions.dateDiff("", call, null));

		call = createCall("toDate", date.getTime(), "fromDate", "2022/09/25", "unit", "DAYS");
		assertNotNull(functions.dateDiff("", call, null));

		call = createCall("toDate", LocalDate.now(), "fromDate", "2022/09/24", "unit", "DAYS");
		assertNotNull(functions.dateDiff("", call, null));

		call = createCall("toDate", LocalDateTime.parse("2022-09-04T16:16:48.000"), "fromDate", "2022/09/24", "unit", "DAYS");
		assertEquals(Long.valueOf(-19), functions.dateDiff("", call, null));

		/*
			One of the operand is date and others are long,instant,localDate,zonedDateTime
		 */

		call = createCall("toDate", date, "fromDate", date.getTime(), "unit", "DAYS");
		assertNotNull(functions.dateDiff("", call, null));
		date.setTime(234564353);
		call = createCall("toDate", date, "fromDate", Instant.now(), "unit", "HOURS");
		assertNotNull(functions.dateDiff("", call, null));

		call = createCall("toDate", date, "fromDate", LocalDate.now(), "unit", "MINUTES");
		assertNotNull(functions.dateDiff("", call, null));

		call = createCall("toDate", date, "fromDate", ZonedDateTime.now(), "unit", "SECONDS");
		assertNotNull(functions.dateDiff("", call, null));


		//Test cases according to PRD
		call = createCall("toDate", "2022/04/01", "fromDate", "2021/03/01", "unit", "YEARS");
		assertEquals(Long.valueOf(1), functions.dateDiff("", call, null));

		call = createCall("toDate", "2022/04/01", "fromDate", "2021/03/01", "unit", "MONTHS");
		assertEquals(Long.valueOf(13), functions.dateDiff("", call, null));

		call = createCall("toDate", "2022/04/01", "fromDate", "2021/03/01", "unit", "WEEKS");
		assertEquals(Long.valueOf(56), functions.dateDiff("", call, null));

		call = createCall("toDate", "2022/04/01", "fromDate", "2021/03/01", "unit", "DAYS");
		assertEquals(Long.valueOf(396), functions.dateDiff("", call, null));

		call = createCall("toDate", "2022/04/01", "fromDate", "2021/03/01", "unit", "HOURS");
		assertEquals(Long.valueOf(9504), functions.dateDiff("", call, null));

		call = createCall("toDate", "2022/04/01", "fromDate", "2021/03/01", "unit", "MINUTES");
		assertEquals(Long.valueOf(570240), functions.dateDiff("", call, null));

		call = createCall("toDate", "2022/04/01", "fromDate", "2021/03/01", "unit", "SECONDS");
		assertEquals(Long.valueOf(34214400), functions.dateDiff("", call, null));

		call = createCall("toDate", "2022/04/01", "fromDate", "2021/03/01", "unit", "MILLISECONDS");
		assertEquals(Long.valueOf(34214400000L), functions.dateDiff("", call, null));

		//PRD Test Case: Date and datetime(utc)

		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String dateString = "2022-04-01";
		try {
			Date dateObject = sdf.parse(dateString);
			call = createCall("toDate", dateObject, "fromDate", LocalDateTime.parse("2022-09-04T16:16:48.00-7"), "unit", "MONTHS");
			assertEquals(Long.valueOf(1), functions.dateDiff("", call, null));

			call = createCall("toDate", dateObject, "fromDate", LocalDateTime.parse("2022-09-04T16:16:48.00-7"), "unit", "WEEKS");
			assertEquals(Long.valueOf(4), functions.dateDiff("", call, null));

			call = createCall("toDate", dateObject, "fromDate", LocalDateTime.parse("2022-09-04T16:16:48.00-7"), "unit", "DAYS");
			assertEquals(Long.valueOf(30), functions.dateDiff("", call, null));

			call = createCall("toDate", dateObject, "fromDate", LocalDateTime.parse("2022-09-04T16:16:48.00-7"), "unit", "HOURS");
			assertEquals(Long.valueOf(730), functions.dateDiff("", call, null));

			call = createCall("toDate", dateObject, "fromDate", LocalDateTime.parse("2022-09-04T16:16:48.00-7"), "unit", "MINUTES");
			assertEquals(Long.valueOf(43856), functions.dateDiff("", call, null));

			call = createCall("toDate", dateObject, "fromDate", LocalDateTime.parse("2022-09-04T16:16:48.00-7"), "unit", "SECONDS");
			assertEquals(Long.valueOf(2631365), functions.dateDiff("", call, null));

			call = createCall("toDate", dateObject, "fromDate", LocalDateTime.parse("2022-09-04T16:16:48.00-7"), "unit", "MILLISECONDS");
			assertEquals(Long.valueOf(2631365000L), functions.dateDiff("", call, null));

		} catch (Exception e) {

		}

		//PRD test cases - TZ1 vs TZ2
		//2022-04-01T01:34:01.00-07
		//2022-09-04T16:16:48.00-07

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

		call = createCall("toDate", ZonedDateTime.parse("2022-04-01T01:34:00-07", formatter), "fromDate", ZonedDateTime.parse("2022-03-01T20:03:55-07", formatter), "unit", "MONTHS");
		assertEquals(Long.valueOf(0), functions.dateDiff("", call, null));
		call = createCall("toDate", ZonedDateTime.parse("2022-04-01T01:34:00-07", formatter), "fromDate", ZonedDateTime.parse("2022-03-01T20:03:55-07", formatter), "unit", "WEEKS");
		assertEquals(Long.valueOf(4), functions.dateDiff("", call, null));
		call = createCall("toDate", ZonedDateTime.parse("2022-04-01T01:34:00-07", formatter), "fromDate", ZonedDateTime.parse("2022-03-01T20:03:55-07", formatter), "unit", "DAYS");
		assertEquals(Long.valueOf(30), functions.dateDiff("", call, null));
		call = createCall("toDate", ZonedDateTime.parse("2022-04-01T01:34:00-07", formatter), "fromDate", ZonedDateTime.parse("2022-03-01T20:03:55-07", formatter), "unit", "HOURS");
		assertEquals(Long.valueOf(725), functions.dateDiff("", call, null));
		call = createCall("toDate", ZonedDateTime.parse("2022-04-01T01:34:00-07", formatter), "fromDate", ZonedDateTime.parse("2022-03-01T20:03:55-07", formatter), "unit", "MINUTES");
		assertEquals(Long.valueOf(43530), functions.dateDiff("", call, null));
		call = createCall("toDate", ZonedDateTime.parse("2022-04-01T01:34:00-07", formatter), "fromDate", ZonedDateTime.parse("2022-03-01T20:03:55-07", formatter), "unit", "SECONDS");
		assertEquals(Long.valueOf(2611805), functions.dateDiff("", call, null));
		call = createCall("toDate", ZonedDateTime.parse("2022-04-01T01:34:00-07", formatter), "fromDate", ZonedDateTime.parse("2022-03-01T20:03:55-07", formatter), "unit", "MILLISECONDS");
		assertEquals(Long.valueOf(2611805000L), functions.dateDiff("", call, null));
		LocalDateTime currentTime = LocalDateTime.now();
		String initialValue = currentTime.format(DateTimeFormatter.ISO_DATE_TIME);
		call = createCall("toDate", currentTime, "fromDate", initialValue, "unit", "MILLISECONDS");
		assertNotNull(functions.dateDiff("", call, null));

	}

	@Test
	public void testDateDiffOutputDatatype() {
		FunctionDefinition functionDefinition = DateFunctionsSeed.dateDiff();
		assertEquals(IntegerType.NAME, functionDefinition.getOutputType().getName());
	}

	@Test
	public void testDateDiffOnEntityOutputDatatype() {
		FunctionDefinition functionDefinition = DateFunctionsSeed.dateDiffOnEntity();
		assertEquals(IntegerType.NAME, functionDefinition.getOutputType().getName());
	}

	private FunctionCall createCall(Object... keyValues) {
		Map<String, Object> config = new HashMap<>();
		if (keyValues != null) {
			for (int i = 0; i < keyValues.length; i += 2) {
				config.put(keyValues[i].toString(), keyValues[i + 1]);
			}
		}
		return new FunctionCall().setConfig(config);
	}

	private GraphContext getContext(String test) {
		return new GraphContext().set("param", test).setCurrentNode(new MappingNode().setName("My Custom Node")
				.setConfiguration(new SimpleFunctionNodeConfig()));
	}


}
