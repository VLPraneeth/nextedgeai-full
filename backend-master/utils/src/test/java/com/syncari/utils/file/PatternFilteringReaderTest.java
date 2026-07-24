package com.syncari.utils.file;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PatternFilteringReaderTest {
    private static final String BASE_RESOURCE_PATH = "src/test/resources/csv/";

    @Test
    public void testPatternFilteringReader() throws Exception {
        final String source = "";
        InputStreamReader fileStream = new InputStreamReader(new FileInputStream(BASE_RESOURCE_PATH + "movies_extra_header_footer.csv"));
        final BufferedReader patternFilteringReader = new BufferedReader(new PatternFilteringReader(fileStream, "\\-\\-custom.*"));
        String line = patternFilteringReader.readLine();
        int count = 0;
        while (line != null) {
            assertFalse(line.matches("\\-\\-custom.*"));
            count++;
            line = patternFilteringReader.readLine();
        }
        assertEquals(9, count);

    }

    @Test
    public void testPatternFilteringReaderNoMatching() throws Exception {
        final String source = "";
        InputStreamReader fileStream = new InputStreamReader(new FileInputStream(BASE_RESOURCE_PATH + "movies.csv"));
        final BufferedReader patternFilteringReader = new BufferedReader(new PatternFilteringReader(fileStream, "\\-\\-custom.*"));
        String line = patternFilteringReader.readLine();
        int count = 0;
        while (line != null) {
            assertFalse(line.matches("\\-\\-custom.*"));
            count++;
            line = patternFilteringReader.readLine();
        }
        assertEquals(876, count);

    }

}