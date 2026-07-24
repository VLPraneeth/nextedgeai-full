package com.syncari.connector.data.iterator;

import com.syncari.connector.AbstractConnectorTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.DataWithCursor;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.utils.Pair;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.*;


@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class CursorBasedIteratorTest extends AbstractConnectorTest {

    //private static int TOTAL_RECORDS = 1000;
    private static int PAGE_SIZE = 200;

    @Test
    public void testPageSizeOptions() {

        int numPages = 5;
        String basePage = "http://syncari.com/results/page";

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize,
                                                                               changeStream) -> {

            int pageIndex = !StringUtils.isEmpty(changeStream) ? Integer.parseInt(changeStream.replace(basePage, "")) : 1;
            String prevPageURL = basePage + pageIndex;
            String nextPageURL = pageIndex == numPages ? "" : basePage + ++pageIndex;

            if (StringUtils.isEmpty(nextPageURL)) {
                // last page, send less than page size
                pageSize = pageSize -1;
            }
            List<EntityData> entities = Stream.generate(EntityData::new).limit(pageSize).collect(Collectors.toList());
            return new DataWithCursor(prevPageURL, nextPageURL, entities);
        };

        final DefaultCursorBasedIterator cursorIterator = new DefaultCursorBasedIterator(new WatermarkInfo(), null,
                0, generator, new ArrayList<>(), PAGE_SIZE, 0);


        IntStream.range(1, numPages).forEach(i -> {
            assertTrue(cursorIterator.hasNext());
            assertEquals(PAGE_SIZE, cursorIterator.next().size());
            assertEquals(basePage + i  , cursorIterator.prevPageURL);
            assertEquals(basePage + (i + 1), cursorIterator.nextPageURL);
        });

        assertTrue(cursorIterator.hasNext());
        assertEquals(PAGE_SIZE -1, cursorIterator.next().size());
        assertEquals(basePage + numPages  , cursorIterator.prevPageURL);
        assertTrue(StringUtils.isEmpty(cursorIterator.nextPageURL));
        assertFalse(cursorIterator.hasNext());
    }

    @Test
    public void testCursorPageSizeIgnore() {

        int numPages = 3;
        String basePage = "http://syncari.com/results/page";
        int internalPageSize = 1000;

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize,
                                                                               changeStream) -> {

            int pageIndex = !StringUtils.isEmpty(changeStream) ? Integer.parseInt(changeStream.replace(basePage, "")) : 1;
            String prevPageURL = basePage + pageIndex;
            String nextPageURL = pageIndex == numPages ? "" : basePage + ++pageIndex;

            int size = StringUtils.isEmpty(nextPageURL) ? internalPageSize -1 : internalPageSize;
            List<EntityData> entities = Stream.generate(EntityData::new).limit(size).collect(Collectors.toList());
            return new DataWithCursor(prevPageURL, nextPageURL, entities);
        };

        final DefaultCursorBasedIterator cursorIterator = new DefaultCursorBasedIterator(new WatermarkInfo(), null,
                0, generator, new ArrayList<>(), PAGE_SIZE, 0, true);


        IntStream.range(1, numPages).forEach(i -> {
            assertTrue(cursorIterator.hasNext());
            assertEquals(internalPageSize, cursorIterator.next().size());
            assertEquals(basePage + i  , cursorIterator.prevPageURL);
            assertEquals(basePage + (i + 1), cursorIterator.nextPageURL);
        });

        assertTrue(cursorIterator.hasNext());
        assertEquals(internalPageSize -1, cursorIterator.next().size());
        assertEquals(basePage + numPages  , cursorIterator.prevPageURL);
        assertTrue(StringUtils.isEmpty(cursorIterator.nextPageURL));
        assertFalse(cursorIterator.hasNext());
    }


    private Function3<WatermarkInfo, Integer, String, DataWithCursor> generateFunction(int numPages, String basePage) {

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize,
                                                                               changeStream) -> {

            int pageIndex = !StringUtils.isEmpty(changeStream) ? Integer.parseInt(changeStream.replace(basePage, "")) : 1;
            String prevPageURL = basePage + pageIndex;
            String nextPageURL = pageIndex == numPages ? "" : basePage + ++pageIndex;

            if (StringUtils.isEmpty(nextPageURL)) {
                // last page, send less than page size
                pageSize = pageSize -1;
            }
            List<EntityData> entities = Stream.generate(EntityData::new).limit(pageSize).collect(Collectors.toList());
            return new DataWithCursor(prevPageURL, nextPageURL, entities);
        };

        return generator;
    }

    @Test
    public void customOffsetReset() {

        int numPages = 5;
        String basePage = "http://syncari.com/results/page";

        final DefaultCursorBasedIterator cursorIterator = new DefaultCursorBasedIterator(new WatermarkInfo(), null,
                0, generateFunction(numPages, basePage), new ArrayList<>(), PAGE_SIZE, 0, true);

        cursorIterator.prevPagesSizes = List.of(
                Pair.of(basePage + 1, 200),
                Pair.of(basePage + 2, 200),
                Pair.of(basePage + 3, 200),
                Pair.of(basePage + 4, 199)
                );

        cursorIterator.customOffsetReset(400);
        assertEquals(cursorIterator.nextPageURL, basePage + 2);
        cursorIterator.customOffsetReset(799);
        assertEquals(cursorIterator.nextPageURL, basePage + 1);

        cursorIterator.prevPagesSizes = List.of(
                Pair.of(basePage + 1, 200),
                Pair.of(basePage + 2, 200),
                Pair.of(basePage + 3, 200),
                Pair.of(basePage + 4, 200)
        );

        cursorIterator.customOffsetReset(400);
        assertEquals(cursorIterator.nextPageURL, basePage + 3);

        cursorIterator.customOffsetReset(0);
        // no change in nextPageURL if resetRecordCount = 0
        assertEquals(cursorIterator.nextPageURL, basePage + 3);

        cursorIterator.prevPagesSizes = List.of(
                Pair.of(basePage + 1, 35),
                Pair.of(basePage + 2, 40),
                Pair.of(basePage + 3, 28),
                Pair.of(basePage + 4, 50)
        );
        cursorIterator.customOffsetReset(120);
        assertEquals(cursorIterator.nextPageURL, basePage + 1);
        cursorIterator.customOffsetReset(78);
        assertEquals(cursorIterator.nextPageURL, basePage + 3);
    }

}
