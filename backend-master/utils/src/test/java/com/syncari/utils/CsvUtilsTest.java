package com.syncari.utils;


import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class CsvUtilsTest {
	private static final String BASE_RESOURCE_PATH = "src/test/resources/csv/";
	@Autowired
	CsvUtils utils;

    @Test
    public void csvWithQuotesIsValid() {
        try {
            try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "movies.csv")) {
				utils.validate(fileStream, new CSVOptions().withDelimiter(Optional.empty()));
			}
        } catch (Exception e) {
			fail();
		}
	}

	@Test
	public void csvHeadersWithSkipLines() {
		try {
			try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "movies_extra_header_footer.csv")) {
				final List<String> headers = utils.getHeaders(fileStream, new CSVOptions().withSkipLinePattern("\\-\\-custom.*"));
				assertEquals(List.of("Id", "Title", "Year", "Runtime", "Country", "Rating", "Votes", "Budget", "Gross", "WinsNoms", "IsGoodRating"), headers);

			}
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void fabricatesHeadersWhenNoHeader() {
		try {
			final CSVOptions csvFormat = new CSVOptions().withHeader(false);
			try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "movies.csv")) {
				final List<String> headers = utils.getHeaders(fileStream, csvFormat);
				assertEquals(List.of("field1", "field2", "field3", "field4", "field5", "field6", "field7", "field8"
						, "field9", "field10", "field11"), headers);

			}
			try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "movies.csv")) {
				final RewindableCSVParser parser = new RewindableCSVParser(utils.getCSVParser(fileStream, csvFormat));
				final List<String> headers = CsvUtils.getHeaders(parser, csvFormat);
				assertEquals(List.of("field1", "field2", "field3", "field4", "field5", "field6", "field7", "field8"
						, "field9", "field10", "field11"), headers);
				int count = 0;
				while (parser.iterator().hasNext()) {
					parser.iterator().next();
					count++;
				}
				final List<String> numLines = FileUtils.readLines(new File(BASE_RESOURCE_PATH + "movies.csv"), StandardCharsets.UTF_8);
				//assert that getting headers in a no-header CSV doesnt skip the first line when reading
				//actual csv data
				assertEquals(numLines.size(), count);
			}
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void fabricatesHeadersWhenNoHeaderAndSkipLines() {
		try {
			try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "movies_extra_header_footer.csv")) {
				final List<String> headers = utils.getHeaders(fileStream, new CSVOptions().withSkipLinePattern("\\-\\-custom.*")
						.withHeader(false));
				assertEquals(List.of("field1", "field2", "field3", "field4", "field5", "field6", "field7", "field8"
						, "field9", "field10", "field11"), headers);

			}
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void csvDatatypesWithSkipLines() {
		try {
			try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "movies_extra_header_footer.csv")) {
				final Map<String, String> headers = utils.detectDatatypes(fileStream, new CSVOptions().withSkipLinePattern("\\-\\-custom.*"));
				assertEquals("integer", headers.get("Runtime"));
				assertEquals("number", headers.get("Rating"));
				assertEquals("integer", headers.get("Year"));
				assertEquals("string", headers.get("Title"));
			}
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	@Ignore
	public void nonCsvFileIsInvalid() {
		try {
			try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "non-csv.png")) {
				utils.validate(fileStream, new CSVOptions().withDelimiter(Optional.empty()));
			}
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Invalid csv file"));
		}
	}

	@Test
	public void validCsvFile() {
		try {
			try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "valid.csv")) {
				utils.validate(fileStream, new CSVOptions().withDelimiter(Optional.empty()));
			}
		} catch (Exception e) {
			fail();
		}
	}
	
	@Test
	public void getDatatype() {
		try {
			try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "datatype.csv")) {
				Map<String, String> headers = utils.detectDatatypes(fileStream, new CSVOptions());
				assertEquals(14, headers.size());
				assertEquals("boolean", headers.get("Boolean_true"));
				assertEquals("boolean", headers.get("Boolean_false"));
				assertEquals("boolean", headers.get("Boolean_yes"));
				assertEquals("boolean", headers.get("Boolean_no"));
				assertEquals("integer", headers.get("Integer"));
				assertEquals("number", headers.get("Decimal"));
				assertEquals("number", headers.get("Double"));
				assertEquals("string", headers.get("String"));
				assertEquals("string", headers.get("Email"));
				assertEquals("number", headers.get("Number"));
				assertEquals("string", headers.get("Currency"));
				assertEquals("string", headers.get("Url"));
				assertEquals("date", headers.get("Date"));
				assertEquals("datetime", headers.get("Datetime"));
			}
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void getDatatypeTest2() {
		try {
			try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "datatype_1.csv")) {
				Map<String, String> headers = utils.detectDatatypes(fileStream, new CSVOptions().withDelimiter(Optional.empty()));
				assertEquals(14, headers.size());
				assertEquals("boolean", headers.get("Boolean_true"));
				assertEquals("boolean", headers.get("Boolean_false"));
				assertEquals("boolean", headers.get("Boolean_yes"));
				assertEquals("boolean", headers.get("Boolean_no"));
				assertEquals("string", headers.get("String_mix_integer"));
				assertEquals("number", headers.get("Decimal"));
				assertEquals("number", headers.get("Double"));
				assertEquals("string", headers.get("String"));
				assertEquals("string", headers.get("Email"));
				assertEquals("number", headers.get("Number"));
				assertEquals("string", headers.get("Currency"));
				assertEquals("string", headers.get("Url"));
				assertEquals("date", headers.get("Date"));
				assertEquals("datetime", headers.get("Datetime"));
			}
		} catch (Exception e) {
			fail();
		}
	}

	@Test
	public void getHeaders() {
		try {
			try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "valid.csv")) {
				List<String> headers = utils.getHeaders(fileStream, new CSVOptions().withDelimiter(Optional.empty()));
				assertEquals(2, headers.size());
			}
		} catch (Exception e) {
			fail();
		}
	}

}
