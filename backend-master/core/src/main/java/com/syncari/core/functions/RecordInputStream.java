package com.syncari.core.functions;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.DateType;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.utils.MongoCriteria;
import lombok.SneakyThrows;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RecordInputStream extends InputStream {
    public static final int PAGE_SIZE = 100;
    private EntityRepo entityRepo;

    private static DateTimeFormatter defaultDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static DateTimeFormatter defaultDatetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private String defaultDateTimeFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private DateTimeFormatter dateFormat;
    private DateTimeFormatter dateTimeFormat;
    private EntityDefinition entityDefinition;
    private List<AttributeDefinition> selectedAttributes;
    private Optional<MongoCriteria> criteria;
    private int maxRecords;
    private CSVPrinter csvPrinter;

    AppendableQueue appendable = new AppendableQueue();
    List<String> selectedAttributeNames;
    private PageInfo pageInfo;
    private int totalRecords = 0;
    private boolean maxRecordsReached;

    @SneakyThrows
    public RecordInputStream(EntityRepo entityRepo, EntityDefinition entityDefinition, List<AttributeDefinition> selectedAttributes,
                             Optional<MongoCriteria> criteria, int maxRecords, boolean useDisplayNameAsHeader,
                             String dateFormat, String dateTimeFormat) {
        this.entityRepo = entityRepo;
        this.dateFormat = getDateTimeFormat(dateFormat, defaultDateFormatter);
        this.dateTimeFormat = getDateTimeFormat(dateTimeFormat, defaultDatetimeFormatter);
        this.entityDefinition = entityDefinition;
        this.selectedAttributes = selectedAttributes;
        this.criteria = criteria;
        this.maxRecords = maxRecords;
        selectedAttributeNames = this.selectedAttributes.stream().map(a -> a.getApiName()).collect(Collectors.toList());
        List<String> headers = useDisplayNameAsHeader ?
                this.selectedAttributes.stream().map(a -> a.getDisplayName()).collect(Collectors.toList())
                : selectedAttributeNames;
        csvPrinter = new CSVPrinter(appendable, CSVFormat.DEFAULT
                .withHeader(headers.toArray(new String[headers.size()])).withQuoteMode(QuoteMode.ALL));
    }

    private DateTimeFormatter getDateTimeFormat(String dateTimeFormat, DateTimeFormatter defaultDateTimeFormat) {
      return Optional.ofNullable(dateTimeFormat)
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(DateTimeFormatter::ofPattern)
          .orElse(defaultDateTimeFormat);
    }


    @Override
    public int read() throws IOException {
        if (!appendable.hasMore() && !maxRecordsReached) {
            generate();
        }
        if (appendable.hasMore()) {
            return appendable.next();
        }
        return -1;
    }

    @SneakyThrows
    protected void generate() {
        final PageCursor cursor = getPageCursor();
        Page<EntityData> search = entityRepo.search(entityDefinition, criteria, cursor);
        for (EntityData d : search.getRecords()) {
            List<String> values = selectedAttributes.stream().map(a -> getValueAsString(d, a)).collect(Collectors.toList());
            csvPrinter.printRecord(values.toArray(new String[values.size()]));
            totalRecords++;
            if (totalRecords >= maxRecords) {
                maxRecordsReached = true;
                break;
            }
        }
        if (search.getRecords().isEmpty()) {
            maxRecordsReached = true;
        }
        pageInfo = search.getPageInfo();
    }

    private String getValueAsString(EntityData d, AttributeDefinition a) {
        final String apiName = a.getApiName();

        switch (a.getDataType().getName()) {
            case DatetimeType.NAME:
                final ZonedDateTime datetime = DatetimeType.VALUE.convert(d.getValue(apiName));
                if (datetime != null) {
                    return dateTimeFormat.format(datetime);
                } else {
                    return null;
                }
            case DateType.NAME:
                //We need to convert Dates to ZoneDates because the format string
                //might have time components. formatter fails because Date/LocalDate TemporalAccessors
                //don't have time components
                final ZonedDateTime date = DatetimeType.VALUE.convert(d.getValue(apiName));
                if (date != null) {
                    return dateFormat.format(date);
                } else {
                    return null;
                }
            default:
                return d.getValueAsString(apiName);
        }
    }

    public int getTotalRecordsRead() {
        return totalRecords;
    }

    private PageCursor getPageCursor() {
        if (pageInfo == null) {
            return new PageCursor(null, PageDirection.next, PAGE_SIZE);
        }
        return new PageCursor(pageInfo.getEnd(), PageDirection.next, PAGE_SIZE);
    }
}
