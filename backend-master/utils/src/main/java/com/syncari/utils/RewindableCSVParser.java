package com.syncari.utils;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class RewindableCSVParser implements Iterable<CSVRecord>, Closeable {
    private CSVParser csvParser;
    private RewindableIterator<CSVRecord> rewindableIterator;

    public RewindableCSVParser(CSVParser csvParser) {
        this.csvParser = csvParser;
        rewindableIterator = new RewindableIterator<>(csvParser.iterator());
        rewindableIterator.collect(true);
    }

    @Override
    public void close() throws IOException {
        csvParser.close();
    }

    public List<String> getHeaderNames() {
        return csvParser.getHeaderNames();
    }

    @Override
    public Iterator<CSVRecord> iterator() {
        return rewindableIterator;
    }

    public RewindableIterator<CSVRecord> rewindableIterator() {
        return rewindableIterator;
    }

    public void rewind() {
        rewindableIterator.rewind();
    }

    public void rewind(int numRecords) {
        rewindableIterator.rewind(numRecords);
    }

    public void collect(boolean collect) {
        rewindableIterator.collect(collect);
    }

    public boolean isClosed() {
        return csvParser.isClosed();
    }
}
