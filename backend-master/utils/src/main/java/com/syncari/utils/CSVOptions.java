package com.syncari.utils;

import lombok.Getter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

@Getter
public class CSVOptions {
    private CSVFormat format = CSVFormat.DEFAULT.withQuote('"').withDelimiter(',').
            withTrim(true).withFirstRecordAsHeader();
    private String skipLinePattern;
    private boolean headerPresent = true;

    public CSVOptions() {
    }

    public CSVOptions withSkipLinePattern(String skipLinePattern) {
        this.skipLinePattern = skipLinePattern;
        return this;
    }

    public CSVOptions withTrim(boolean trim) {
        format = format.withTrim(trim);
        return this;
    }

    public CSVOptions withQuote(char quoteChar) {
        format = format.withQuote(quoteChar);
        return this;
    }

    public CSVOptions withDelimiter(char delimiter) {
        format = format.withDelimiter(delimiter);
        return this;
    }

    public CSVOptions withDelimiter(Optional<Character> delimiter) {
        delimiter.ifPresent(c -> {
            format = format.withDelimiter(c);
        });
        return this;
    }

    public CSVOptions withHeader(boolean header) {
        headerPresent = header;

        if (headerPresent) {
            format = format.withFirstRecordAsHeader();
        } else {
            //only way to tell the parser that file has no header
            // or that we will handle header (when skipLinePattern is set), is by setting it to null
            format = format.withHeader((String[]) null);
        }
        return this;
    }

    public boolean hasSkipLinePattern() {
        return StringUtils.isNotBlank(skipLinePattern);
    }
}
