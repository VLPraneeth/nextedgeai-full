package com.syncari.connector.data.iterator.hubspot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.AbstractEntityDataBatchIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.HubspotRestClient;
import com.syncari.connector.service.HubspotService;
import com.syncari.utils.Pair;
import com.syncari.utils.StatisticsUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.connector.service.seed.HubspotSeed.HS_OBJECT_ID;
import static com.syncari.utils.ExceptionUtils.rethrow;

@Slf4j
public class HubspotIncrementalIterator extends AbstractEntityDataBatchIterator {
    private static final int MAX_OFFSET = 10000;
    public static final String GET_ASSOCIATION_URL = "https://api.hubapi.com/crm-associations/v1/associations/%s/HUBSPOT_DEFINED/%s?limit=100&offset=%s";
    public static final String DEAL_CONTACT_ASSOCIATIONS_URL = "https://api.hubapi.com/crm/v3/associations/deal/contact/batch/read";
    public static final String DEAL_COMPANY_ASSOCIATIONS_URL = "https://api.hubapi.com/crm/v3/associations/deal/company/batch/read";
    public static final String LINE_ITEM_DEAL_ASSOCIATIONS_URL = "https://api.hubapi.com/crm/v3/associations/line_item/deal/batch/read";
    public static final String LINE_ITEM_QUOTE_ASSOCIATIONS_URL = "https://api.hubapi.com/crm/v3/associations/line_item/quote/batch/read";
    public static final String QUOTE_CONTACT_ASSOCIATIONS_URL = "https://api.hubapi.com/crm/v3/associations/quote/contact/batch/read";
    public static final String QUOTE_COMPANY_ASSOCIATIONS_URL = "https://api.hubapi.com/crm/v3/associations/quote/company/batch/read";
    public static final String QUOTE_DEAL_ASSOCIATIONS_URL = "https://api.hubapi.com/crm/v3/associations/quote/deal/batch/read";
    private final String lastModifiedProperty;
    private WatermarkInfo currentWatermark;
    private WatermarkInfo initialWatermark;
    private int pageSize;
    private EntitySchema schema;
    private long offset;
    private boolean applyUpperBoundWM;
    private ConnectorInfo connector;
    private List<String> properties;
    private Results results;
    public static final List<DateTimeFormatter> UTC_FORMATS = List.of(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSz"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssz"));
    private Supplier<AuthConfig> tokenHandler;
    public boolean offsetOverflow;

    ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    static final Map<String, String> SEARCH_URLS = Stream.of(Map.of(
            "contact", "https://api.hubapi.com/crm/v3/objects/contacts/search",
            "lead", "https://api.hubapi.com/crm/v3/objects/leads/search",
            "deal", "https://api.hubapi.com/crm/v3/objects/deals/search",
            "company", "https://api.hubapi.com/crm/v3/objects/companies/search",
            "ticket", "https://api.hubapi.com/crm/v3/objects/tickets/search",
            "line_item", "https://api.hubapi.com/crm/v3/objects/line_items/search",
            "product", "https://api.hubapi.com/crm/v3/objects/products/search",
            "invoice", "https://api.hubapi.com/crm/v3/objects/invoices/search",
            "subscription", "https://api.hubapi.com/crm/v3/objects/subscriptions/search",
            "custom", "https://api.hubapi.com/crm/v3/objects/%s/search"
    ), Map.of("quote", "https://api.hubapi.com/crm/v3/objects/quotes/search")).flatMap(m -> m.entrySet().stream())
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));


        //, Map.of("quotes", "https://api.hubapi.com/crm/v3/objects/quotes/search"));
    HubspotRestClient client;

    public HubspotIncrementalIterator(WatermarkInfo baseWatermark, long offset, int maxRecords, int pageSize, ConnectorInfo connector, 
            EntitySchema schema, HubspotRestClient client, boolean applyUpperBoundWM, Supplier<AuthConfig> tokenHandler) {
        currentWatermark = baseWatermark;
        this.initialWatermark = baseWatermark;
        this.schema = schema;
        this.offset = offset;
        this.applyUpperBoundWM = applyUpperBoundWM;
        this.maxRecords = maxRecords == 0 ? Integer.MAX_VALUE : maxRecords;
        this.client = client;
        //Pagesize is pinned to a maximum of 100, and a minimum of maxRecords (if maxRecords < (pageSize || 100))
        this.pageSize = pageSize == 0 ? 100 : Math.min(Math.min(100, pageSize), this.maxRecords);

        this.connector = connector;
        this.lastModifiedProperty = "contact".equalsIgnoreCase(schema.getApiName()) ? "lastmodifieddate" : "hs_lastmodifieddate";
        properties = new ArrayList<>();
        for (AttributeSchema a : schema.getAttributes()) {
            if ("deal".equalsIgnoreCase(schema.getApiName()) && (
                    "accountId".equalsIgnoreCase(a.getApiName()) || "associatedVids".equalsIgnoreCase(a.getApiName())
            )) continue;
            properties.add(a.getApiName());
        }
        this.tokenHandler = tokenHandler;
        this.offsetOverflow = baseWatermark.getStreamState() != null && baseWatermark.getStreamState().isOffsetOverflow();
    }

    @Override
    public long getLastOffset() {
        return offset;
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(Offset.OffsetType.RECORD_COUNT, pageSize);
    }

    public HubspotIncrementalIterator(WatermarkInfo baseWatermark, long offset, int maxRecords, int pageSize, ConnectorInfo connector, 
            EntitySchema schema, boolean applyUpperBoundWM, Supplier<AuthConfig> tokenHandler) {
        this(baseWatermark, offset, maxRecords, pageSize, connector, schema, new HubspotRestClient(), applyUpperBoundWM, tokenHandler);
    }

    protected void resetWatermarkAndOffsetIfNeeded() {
        // Reset the current watermark to the highest lastWatermark so that
        if (currentWatermark.getStart() < lastWatermark){
            long prevUpperboundWM = currentWatermark.getEnd();
            currentWatermark = currentWatermark.withStart(lastWatermark);
            currentWatermark.setEnd(prevUpperboundWM);
            offset = 0;
            offsetOverflow = false;
            currentWatermark.getStreamState().setOffsetOverflow(offsetOverflow);
            log.info("HubSpot iterator watermark updated, {}:{}", currentWatermark, offset);
        }
    }

    @Override
    public boolean hasNext() {
        if (hasFetchedMaxRecords()) {
            log.info("Iterator reached maxrecords for this cycle.");
			return false;
        }
        if (results == null || (results.hasNextPageMarker() && results.isConsumed())) {
            results = getNextResults(results);
            List<String> fetchedIds = results.results.stream().map(HSResult::getId).collect(Collectors.toList());
            int totalCount = results.total;
            log.info("Fetched total {} records", totalCount);
            log.debug("Fetched ids - {}", fetchedIds);
        }
        boolean hasNext = results != null && !results.isConsumed() && !results.results.isEmpty();
        if(!hasNext) {
            offset = 0L;
        }
        return hasNext;
    }

    private Results getNextResults(Results current) {
        resetWatermarkAndOffsetIfNeeded();
        FilterGroup fg = new FilterGroup();
        List sorts = List.of(Map.of("propertyName", lastModifiedProperty, "direction", "ASCENDING"));
        fg.addFilter(new Filter().setPropertyName(lastModifiedProperty).setOperator("GTE").setValue(String.valueOf(currentWatermark.getStart())));
        fg.addFilter(new Filter().setPropertyName(lastModifiedProperty).setOperator("LTE").setValue(String.valueOf(currentWatermark.getEnd())));
        if (offset >= MAX_OFFSET && !offsetOverflow) {
            // Hubspot only supports offset til 10k mark via api for timestamp filter. Value provided over 10000 for `after` filter will result in error
            // Use sorted id and id filter to fetch next in such cases, but need to restart the sync
            log.info("Setting HubSpot iterator offset overflow to true, {}:{}", currentWatermark, offset);
            offsetOverflow = true;
            currentWatermark.getStreamState().setOffsetOverflow(offsetOverflow);
            offset = 0;
        }
        if(offsetOverflow) {
            // When the offset overflows, use sort by id and filter by id
            log.info("HubSpot iterator offset is true, {}:{}", currentWatermark, offset);
            sorts = List.of(Map.of("propertyName", schema.getIdField().getApiName(), "direction", "ASCENDING"));
            if(offset != 0 && !StringUtils.isBlank(currentWatermark.getStreamState().getPreviousCursor())) {
                String idFilter = currentWatermark.getStreamState().getPreviousCursor();
                fg.addFilter(new Filter().setPropertyName(schema.getIdField().getApiName()).setOperator("GTE").setValue(idFilter));
                log.info("Adding id filter with value {} for field {}", idFilter, schema.getIdField().getApiName());
            }
        }
        Search search = new Search().setLimit(Math.min(100, pageSize))
                // Set the after by offset
                .setAfter(Long.toString(offset))
                .setProperties(properties)
                .setSorts(sorts)
                .addFilterGroup(fg);
        try {

            String path = schema.isCustom() ? String.format(SEARCH_URLS.get("custom"), schema.getApiName()) : SEARCH_URLS.get(schema.getApiName());

            String searchBody = mapper.writeValueAsString(search);
            log.info("URL: {} ", path);
            log.info("SearchFilter/SearchAfter: {} {}", search.getFilterGroups(), search.getAfter());
            log.debug("Entity {}, SearchBody: {}", schema.getApiName(), searchBody);
            log.debug("Entity {}, Offset: {}", schema.getApiName(), offset);
            ResponseEntity<String> response = client.postRaw(path, searchBody, connector, tokenHandler);
            Results nextResults = mapper.readValue(response.getBody(), Results.class);
            if(offsetOverflow && nextResults.results != null && !nextResults.results.isEmpty()) {
                HSResult hsResult = nextResults.results.get(nextResults.results.size() - 1);
                if(hsResult != null) {
                    currentWatermark.getStreamState().setPreviousCursor(hsResult.getId());
                }
            } else {
                currentWatermark.getStreamState().setPreviousCursor(null).setOffsetOverflow(false);
            }
            return nextResults;
        } catch (IOException e) {
            throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), ErrorCodes.UNKNOWN_ERROR.toString());
        }
    }

    public static void fixMultivaluedFields(List<EntityData> result, EntitySchema entitySchema) {
        for (EntityData e : result) {
            fixMultivaluedFields(entitySchema, e);
        }
    }

    private static EntityData fixMultivaluedFields(EntitySchema entitySchema, EntityData e) {
        for (AttributeSchema property : entitySchema.getAttributes()) {
            if(property.isMultiValueField() && !property.isReference()){
                if(!StringUtils.isBlank(e.getValueAsString(property.getApiName()))){
                    e.addValue(property.getApiName(), Arrays.asList(e.getValueAsString(property.getApiName()).split(";")));
                }
            }
        }
        return e;
    }

    @Override
    public List<EntityData> next() {
        List<EntityData> page =
                results.results.stream()
                        .filter(result -> filterContact(result))
                        .map(result ->
                        fixMultivaluedFields(this.schema, new EntityData(schema.getApiName())
                                .setId(result.getId())
                                .setDeleted(result.isArchived()).setValues(result.getProperties())
                                .setCreatedAt(toTimestamp(result.getCreatedAt()))
                                .setLastModified(toTimestamp(result.getUpdatedAt())))
                ).collect(Collectors.toList());
        int recordsToTake = Math.min(page.size(), maxRecords - (int) totalRecordsFetched);
        page = page.subList(0, recordsToTake);

        if(Constants.DEAL.equalsIgnoreCase(schema.getApiName())) {
            addDealAssociations(page, connector, client, mapper, tokenHandler);
        }

        if("line_item".equalsIgnoreCase(schema.getApiName())) {
            addLineItemAssociations(page, connector, client, mapper, tokenHandler);
        }

        if(HubspotService.QUOTE.equalsIgnoreCase(schema.getApiName())) {
            addQuoteAssociations(page, connector, client, mapper, tokenHandler);
        }
        final List<EntityData> outliers = StatisticsUtil.getOutliers(page, r -> ((Long) r.getLastModified()).doubleValue());
        outliers.forEach(o -> o.setOutlierTimestamp(true));
        Set<String> outlierIds = outliers.stream().map(e -> e.getId()).collect(Collectors.toSet());
        Predicate<EntityData> isOutlier = r -> !outlierIds.contains(r.getId());
        long maxTS = page.stream().filter(isOutlier)
                .max(Comparator.comparingLong(EntityData::getLastModified))
                .map(e -> e.getLastModified()).orElse(-1l);

        // Increment the offset
        log.info("Results count {} Page Size {}", page.size(), pageSize);
        if (page.size() >= pageSize){
            // If its a new sync cycle lastWatermark will be -1 and hence consider the same for resetting offset
            if ((lastWatermark == -1 && initialWatermark.getStart() == maxTS)|| maxTS == lastWatermark) {
                // pagesize if the maxTS in the current iteration is equal to lastwatermark implying the offset needs an increment by pagesize
                offset += page.size();
            } else{
                // Set the watermark to number of records obtained with maxTS in the current iteration
                offset = page.stream().filter(e->e.getLastModified() == maxTS).count();
            }
            log.info("Incremented the offset to {}", offset);
        } else {
            // Reset the offset
            log.info("Resetting the offset to 0 from {}", offset);
            offset = 0L;
            offsetOverflow = false;
            currentWatermark.getStreamState().setOffsetOverflow(offsetOverflow);
        }

        lastWatermark = Math.max(maxTS, lastWatermark);

        if(Constants.CONTACT.equalsIgnoreCase(schema.getApiName())) {
            // Delete the merged contacts
            List<EntityData> idsToDelete = generateContactIdsToDelete(results);
            log.info("Generated {} records to delete", idsToDelete.size());
            page.addAll(idsToDelete);
        }

        if (Constants.DEAL.equalsIgnoreCase(schema.getApiName()) || Constants.COMPANY.equalsIgnoreCase(schema.getApiName())) {
            // Delete the merged entities
            List<EntityData> idsToDelete = generateIdsToDelete(results);
            log.info("Generated {} records to delete", idsToDelete.size());
            page.addAll(idsToDelete);
        }

        totalRecordsFetched += page.size();

        // Reset the offset to 0 at the end of the synccycle to make sure next sync cycle starts with new watermark
        // If a non zero offset is returned then next sync cycle also starts with old start watermark with an offset
        if (initialWatermark.getStart() != lastWatermark && totalRecordsFetched >= MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE){
            log.info("Resetting the offset to 0 from {} as max_records reached with a change in watermark from {} to {}", offset, initialWatermark.getStart(), lastWatermark);
            offset = 0L;
            offsetOverflow = false;
            currentWatermark.getStreamState().setOffsetOverflow(offsetOverflow);
        }

        results.setConsumed(true);
        return page;
    }

    private List<EntityData> generateIdsToDelete(Results results) {
        Optional<Long> maxTS = results.getResults().stream().map(result -> toTimestamp(result.getUpdatedAt()))
                .max(Long::compareTo);
        if(maxTS.isPresent()) {
            return results.results.stream().filter(result -> result.properties.get("hs_merged_object_ids") != null)
                    .map(result -> {
                        var mergeIds = (List<String>)result.properties.get("hs_merged_object_ids");
                        return mergeIds.stream().map(m -> Pair.of(m, result.getUpdatedAt())).collect(Collectors.toList());
                    }).flatMap(List::stream)
                    .map(mergePair -> generateEntityData(mergePair.x, maxTS.get()))
                    .collect(Collectors.toList());
        } else {
            throw new RuntimeException("Failed to fetch max timestamp from Hubspot results");
        }
    }

    private EntityData generateEntityData(String mergedId, Long updatedAt) {
        return new EntityData(schema.getApiName())
                .setId(mergedId)
                .setDeleted(true)
                .setLastModified(updatedAt);
    }

    private List<EntityData> generateContactIdsToDelete(Results results) {
        Optional<Long> maxTS = results.getResults().stream().map(result -> toTimestamp(result.getUpdatedAt()))
                .max(Long::compareTo);
        if(maxTS.isPresent()) {
            return results.results.stream()
                    .filter(result -> result.properties.get("hs_calculated_merged_vids") != null)
                    .map(result -> String.valueOf(result.properties.get("hs_calculated_merged_vids")).split(";"))
                    .flatMap(Stream::of)
                    .map(result -> generateContactEntityData(result, maxTS.get()))
                    .collect(Collectors.toList());
        } else {
            throw new RuntimeException("Failed to fetch max timestamp from Hubspot contact results");
        }
    }

    private EntityData generateContactEntityData(String calculatedMergedId, Long maxTS) {
        String[] splitString = calculatedMergedId.split(":");
        String mergedId = splitString[0];
        return new EntityData(schema.getApiName())
                    .setId(mergedId)
                    .setDeleted(true)
                    .setLastModified(maxTS);
    }

    // filter our contacts where
    private boolean filterContact(HSResult result) {
        Object value = result.getProperties().get(HS_OBJECT_ID);
        boolean filter =  !"contact".equalsIgnoreCase(this.schema.getApiName())
                || Optional.ofNullable(value).map(v -> v.equals(result.getId())).orElse(true);

        if (!filter) log.warn("Mismatch in vid: {} and hs_object_id: {} received from Hubspot Search API", result.getId(), value);
        return filter;
    }

    private void addAssociations(List<EntityData> page) {
        addAssociations(schema.getApiName(), page, connector, client, mapper, tokenHandler);
    }

    public static void addAssociations(String entityName, List<EntityData> page, ConnectorInfo connector, HubspotRestClient client, ObjectMapper mapper, Supplier<AuthConfig> tokenHandler) {
        page.forEach(entity -> {
            if ("deal".equalsIgnoreCase(entityName)) {
                List<Long> dealToContacts = getAssociations(entity, 3, connector, client, mapper, tokenHandler);
                List<Long> dealToCompanies = getAssociations(entity, 5, connector, client, mapper, tokenHandler);
                entity.addValue("associatedVids", dealToContacts);
                if (!dealToCompanies.isEmpty()) {
                    entity.addValue("associatedcompanyid", dealToCompanies.get(dealToCompanies.size()-1));
                }
            }
        });
    }

    public static void addLineItemAssociations(List<EntityData> page, ConnectorInfo connector, HubspotRestClient client, ObjectMapper mapper, Supplier<AuthConfig> tokenHandler){
        AssociationEntries entries = new AssociationEntries();
        Map<String, EntityData> idToRecordMap = new HashMap<>();
        page.forEach(entity -> {
            idToRecordMap.put(entity.getId(), entity);
            entries.addAssociationEntry(new AssociationEntry().setId(entity.getId()));
        });
        if(entries.inputs.isEmpty()){
            return;
        }
        ResponseEntity<String> dealAssociationResponse = client.postRaw(LINE_ITEM_DEAL_ASSOCIATIONS_URL,
                rethrow(() -> mapper.writeValueAsString(entries)),
                connector, tokenHandler);

        BatchAssociationResults dealAssociations = rethrow(() -> mapper.readValue(dealAssociationResponse.getBody(), BatchAssociationResults.class));
        if (dealAssociations != null) {
            dealAssociations.results.forEach(association -> {
                String dealId = association.from == null ? null : association.from.getId();
                EntityData entity = idToRecordMap.get(dealId);
                List<Long> dealIds = association.getTo().stream().map(a -> Long.valueOf(a.getId())).collect(Collectors.toList());
                if (entity != null && dealIds != null && !dealIds.isEmpty()) {
                    entity.addValue("hs_deal_id", dealIds.get(dealIds.size() - 1));
                }
            });
        }

        ResponseEntity<String> quoteAssociationResponse = client.postRaw(LINE_ITEM_QUOTE_ASSOCIATIONS_URL,
                rethrow(() -> mapper.writeValueAsString(entries)),
                connector, tokenHandler);

        BatchAssociationResults quoteAssociations = rethrow(() -> mapper.readValue(quoteAssociationResponse.getBody(), BatchAssociationResults.class));
        if (quoteAssociations != null) {
            quoteAssociations.results.forEach(association -> {
                String quoteId = association.from == null ? null : association.from.getId();
                EntityData entity = idToRecordMap.get(quoteId);
                List<Long> quoteIds = association.getTo().stream().map(a -> Long.valueOf(a.getId())).collect(Collectors.toList());
                if (entity != null && quoteIds != null && !quoteIds.isEmpty()) {
                    entity.addValue("hs_quote_id", quoteIds.get(quoteIds.size() - 1));
                }
            });
        }
    }

    public static void addDealAssociations(List<EntityData> page, ConnectorInfo connector, HubspotRestClient client, ObjectMapper mapper, Supplier<AuthConfig> tokenHandler) {
        AssociationEntries entries = new AssociationEntries();
        Map<String, EntityData> idToRecordMap = new HashMap<>();
        page.forEach(entity -> {
            idToRecordMap.put(entity.getId(), entity);
            entries.addAssociationEntry(new AssociationEntry().setId(entity.getId()));
        });
        if(entries.inputs.isEmpty()){
            return;
        }
        ResponseEntity<String> companyAssociationResponse = client.postRaw(DEAL_COMPANY_ASSOCIATIONS_URL, rethrow(() -> mapper.writeValueAsString(entries)), connector, tokenHandler);
        BatchAssociationResults companyAssociations = rethrow(() -> mapper.readValue(companyAssociationResponse.getBody(), BatchAssociationResults.class));
        if (companyAssociations != null) {
            companyAssociations.results.forEach(association -> {
                String dealId = association.from == null ? null : association.from.getId();
                EntityData entity = idToRecordMap.get(dealId);
                List<Long> companyIds = new ArrayList<>(association.getTo().stream().map(a -> Long.valueOf(a.getId())).collect(Collectors.toList()));
                fetchAllAssociations(connector, client, mapper, tokenHandler, association, companyIds);
                if (entity != null && companyIds != null && !companyIds.isEmpty()) {
                    entity.addValue("associatedcompanyid", companyIds.get(companyIds.size() - 1));
                }
            });
        }

        ResponseEntity<String> contactAssociationResponse = client.postRaw(DEAL_CONTACT_ASSOCIATIONS_URL, rethrow(() -> mapper.writeValueAsString(entries)), connector, tokenHandler);
        BatchAssociationResults contactAssociations = rethrow(() -> mapper.readValue(contactAssociationResponse.getBody(), BatchAssociationResults.class));
        if (contactAssociations != null) {
            contactAssociations.results.forEach(association -> {
                String dealId = association.from == null ? null : association.from.getId();
                EntityData entity = idToRecordMap.get(dealId);
                List<Long> contactIds = new ArrayList<>(association.getTo().stream().map(a -> Long.valueOf(a.getId())).collect(Collectors.toList()));
                fetchAllAssociations(connector, client, mapper, tokenHandler, association, contactIds);
                if (entity != null && contactIds != null && !contactIds.isEmpty()) {
                    entity.addValue("associatedVids", contactIds);
                }
            });
        }
    }

    public static void addQuoteAssociations(List<EntityData> page, ConnectorInfo connector, HubspotRestClient client, ObjectMapper mapper, Supplier<AuthConfig> tokenHandler) {
        AssociationEntries entries = new AssociationEntries();
        Map<String, EntityData> idToRecordMap = new HashMap<>();
        page.forEach(entity -> {
            idToRecordMap.put(entity.getId(), entity);
            entries.addAssociationEntry(new AssociationEntry().setId(entity.getId()));
        });
        if(entries.inputs.isEmpty()){
            return;
        }
        ResponseEntity<String> companyAssociationResponse = client.postRaw(QUOTE_COMPANY_ASSOCIATIONS_URL, rethrow(() -> mapper.writeValueAsString(entries)), connector, tokenHandler);
        BatchAssociationResults companyAssociations = rethrow(() -> mapper.readValue(companyAssociationResponse.getBody(), BatchAssociationResults.class));
        if (companyAssociations != null) {
            companyAssociations.results.forEach(association -> {
                String quoteId = association.from == null ? null : association.from.getId();
                EntityData entity = idToRecordMap.get(quoteId);
                List<Long> companyIds = new ArrayList<>(association.getTo().stream().map(a -> Long.valueOf(a.getId())).collect(Collectors.toList()));
                fetchAllAssociations(connector, client, mapper, tokenHandler, association, companyIds);
                if (entity != null && companyIds != null && !companyIds.isEmpty()) {
                    entity.addValue("associatedcompanyid", companyIds.get(companyIds.size() - 1));
                }
            });
        }

        ResponseEntity<String> contactAssociationResponse = client.postRaw(QUOTE_CONTACT_ASSOCIATIONS_URL, rethrow(() -> mapper.writeValueAsString(entries)), connector, tokenHandler);
        BatchAssociationResults contactAssociations = rethrow(() -> mapper.readValue(contactAssociationResponse.getBody(), BatchAssociationResults.class));
        if (contactAssociations != null) {
            contactAssociations.results.forEach(association -> {
                String quoteId = association.from == null ? null : association.from.getId();
                EntityData entity = idToRecordMap.get(quoteId);
                List<Long> contactIds = new ArrayList<>(association.getTo().stream().map(a -> Long.valueOf(a.getId())).collect(Collectors.toList()));
                fetchAllAssociations(connector, client, mapper, tokenHandler, association, contactIds);
                if (entity != null && contactIds != null && !contactIds.isEmpty()) {
                    entity.addValue("associatedVids", contactIds);
                }
            });
        }

        ResponseEntity<String> dealAssociationResponse = client.postRaw(QUOTE_DEAL_ASSOCIATIONS_URL, rethrow(() -> mapper.writeValueAsString(entries)), connector, tokenHandler);
        BatchAssociationResults dealAssociations = rethrow(() -> mapper.readValue(dealAssociationResponse.getBody(), BatchAssociationResults.class));
        if (dealAssociations != null) {
            dealAssociations.results.forEach(association -> {
                String quoteId = association.from == null ? null : association.from.getId();
                EntityData entity = idToRecordMap.get(quoteId);
                List<Long> dealIds = new ArrayList<>(association.getTo().stream().map(a -> Long.valueOf(a.getId())).collect(Collectors.toList()));
                fetchAllAssociations(connector, client, mapper, tokenHandler, association, dealIds);
                if (entity != null && dealIds != null && !dealIds.isEmpty()) {
                    entity.addValue("associateddealid", dealIds.get(dealIds.size() - 1));
                }
            });
        }
    }

    private static void fetchAllAssociations(ConnectorInfo connector, HubspotRestClient client, ObjectMapper mapper, Supplier<AuthConfig> tokenHandler, BatchAssociation association, List<Long> ids) {
        String nextPage = "";
        if(association.paging != null && association.paging.next != null && association.paging.next.link != null) {
            nextPage = association.paging.next.link;
        }
        while (StringUtils.isNotBlank(nextPage)) {
            ResponseEntity<String> response = client.getResponse(nextPage, connector, tokenHandler);
            AssociationNextPageResponse nextPageResponse = rethrow(() -> mapper.readValue(response.getBody(), AssociationNextPageResponse.class));
            if(nextPageResponse.paging != null && nextPageResponse.paging.next != null && nextPageResponse.paging.next.link != null) {
                nextPage = nextPageResponse.paging.next.link;
            } else {
                nextPage = "";
            }
            ids.addAll(nextPageResponse.getResults().stream().map(a -> Long.valueOf(a.getId())).collect(Collectors.toList()));
        }
    }

    private static List<Long> getAssociations(EntityData entity, int associationType, ConnectorInfo connector, HubspotRestClient client, ObjectMapper mapper, Supplier<AuthConfig> tokenHandler) {
        boolean hasMore = true;
        String offset = "0";
        List<Long> associations = new ArrayList<>();
        while (hasMore && associations.size() < 10000) {
            String dealToContactURL = String.format(GET_ASSOCIATION_URL, entity.getId(), associationType, offset);
            ResponseEntity<String> response = client.getResponse(dealToContactURL, connector, tokenHandler);
            Map<String, Object> results = rethrow(() -> mapper.readValue(response.getBody(), Map.class));
            associations.addAll((List) results.getOrDefault("results", List.of()));
            hasMore = Boolean.valueOf(results.getOrDefault("hasMore", "false").toString());
            offset = results.getOrDefault("offset", "0").toString();
        }
        return associations;
    }

    public static long toTimestamp(String result) {
        for (DateTimeFormatter format : UTC_FORMATS) {
            try {
                return ZonedDateTime.parse(result, format).toInstant().toEpochMilli();
            } catch (DateTimeParseException ex) {
            }
        }
        log.error("Could not parse date {}", result);
        throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, "Could not parse date " + result, ErrorCodes.UNKNOWN_ERROR.toString());
    }

    public static List<EntityData> toEntityData(String results, String entityName, String connectorId, ObjectMapper mapper) {
        Results results1 = rethrow(() -> mapper.readValue(results, Results.class));
        return results1.results.stream().map(result ->
                new EntityData(entityName)
                        .setId(result.getId())
                        .setConnectorId(connectorId)
                        .setDeleted(result.isArchived()).setValues(result.getProperties())
                        .setCreatedAt(toTimestamp(result.getCreatedAt()))
                        .setLastModified(toTimestamp(result.getUpdatedAt()))

        ).collect(Collectors.toList());
    }
}

@Data
class AssociationEntries {
    List<AssociationEntry> inputs=new ArrayList<>();

    public AssociationEntries addAssociationEntry(AssociationEntry entry){
        inputs.add(entry);
        return this;
    }
}

@Data
@Accessors(chain = true)
class AssociationEntry{
    private String id;
}


@Data
@Accessors
class BatchAssociationResults{
    String status;
    List<BatchAssociation> results=List.of();
}
@Data
@Accessors
class BatchAssociation{
    AssociationEntry from;
    List<AssociationEntry> to=List.of();
    AssociationPaging paging;
}

@Data
@Accessors
class AssociationPaging {
    AssociationNext next;
}

@Data
@Accessors
class AssociationNext {
    String after;
    String link;
}

@Data
@Accessors
class AssociationNextPageResponse {
    List<AssociationEntry> results;
    AssociationPaging paging;
}
