package com.syncari.connector.data.iterator;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import com.sforce.soap.partner.fault.ApiFault;
import com.sforce.soap.partner.fault.ExceptionCode;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import com.google.common.collect.Lists;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.bind.XmlObject;
import com.syncari.connector.EntityData;
import com.syncari.connector.Status;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.Stats;
import com.syncari.connector.service.Transformer;
import com.syncari.utils.DateUtil;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import static com.syncari.connector.service.helper.SalesforceHelper.handleException;

@Slf4j
@Builder
public class SObjectIterator implements EntityDataBatchIterator {
    @Builder.Default
    List<QueryResult> result = new ArrayList<>(); // Make a list of Query result each query with set of attributes
    String entityName;
    private Transformer transformer;
    @Builder.Default
    List<AttributeSchema> attributes = new ArrayList<>();
    private PartnerConnection conn;
    DateUtil dateUtil;
    String query;
    String queryPredicate = "";
    long offset;
    boolean isInitial;
    ConnectorInfo connectorInfo;
    @Builder.Default
    int currentBatchSize = 1;
    @Builder.Default
    private Stats stats = new Stats();
    public static final int MAX_BATCHSIZE = 100000;
    public static final int MAX_QUERY_SIZE = 100000;
    long latestTs = Instant.EPOCH.toEpochMilli();
    private int maxResults=0;
    private int resultsSoFar =0;
    private static final String ID = "Id";

    @Override
    public boolean hasNext() {
        return result.isEmpty() || !result.get(0).isDone();
    }

    @Override
    @Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<EntityData> next() {
        List<EntityData> entityData = new ArrayList<>();
        try {
            long now = System.currentTimeMillis();
            if(!result.isEmpty()){
                // iterate over list of query result if not empty to fetch more records
                for(int i = 0; i < result.size(); i++){
                    result.set(i, conn.queryMore(result.get(i).getQueryLocator()));
                }
            }else{
                // Split query and fetch records
                List<String> queries = getQueries(query, queryPredicate, attributes);
                for(String q: queries){
                    log.debug("Query getting executed:" + q);
                    result.add(conn.queryAll(q));
                }
            }

            // combine the query results
            Map<Object, Set<XmlObject>> map= new LinkedHashMap<>();
            for(QueryResult r: result){
                SObject[] records = r.getRecords();
                for(SObject obj: records){
                    var id = obj.getSObjectField("Id");
                    map.putIfAbsent(id, new HashSet<>());
                    Set<XmlObject> set = map.get(id);
                    obj.getChildren().forEachRemaining(c -> set.add(c));
                    map.put(id, set);
                }
            }

            // Form a single SObject
            SObject[] records = new SObject[map.size()];
            int i = 0;
            for(Object k: map.keySet()){
                records[i] = new SObject();
                for(XmlObject o: map.get(k)){
                    records[i].setSObjectField(o.getName().getLocalPart(), o);
                }
                i++;
            }


            long done = System.currentTimeMillis();
            //SObject[] records = (result == null ? new SObject[0] : result.getRecords());
            if (result != null && records != null) {
                currentBatchSize = records.length;
                offset = offset + currentBatchSize;
            }
            stats.addLatencyCount(done - now, currentBatchSize);
            entityData = transformer.toEntityData(connectorInfo.getId(), entityName, records, attributes);
            latestTs  = findLatestTs(entityData);
            return entityData;
        } catch (ConnectionException e) {
            handleException(e, connectorInfo);
        }
        return entityData;
    }

    protected long findLatestTs(List<EntityData> entityData) {
        return entityData.stream().max((e1,e2)-> (int)(e1.getLastModified() - e2.getLastModified())).map(e -> e.getLastModified()).orElse(-1l);
    }

    // Return list of split queries if size exceeds MAX_QUERY_SIZE
    private List<String> getQueries(String query, String queryPredicate, List<AttributeSchema> attributes){
        List<String> queries = new ArrayList<>();
        String parts = String.join(", ", attributes.stream().filter(a -> !a.isIdField() && a.getStatus() == Status.ACTIVE
                    && !a.isFileLink() && !EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME.equalsIgnoreCase(a.getApiName()))
                .map(a -> a.getApiName()).collect(Collectors.toList()));
        Optional<AttributeSchema> idField = attributes.stream().filter(a -> a.isIdField()).findFirst();
        String id = idField.isPresent() ? idField.get().getApiName() : ID;
        String fields = parts.isBlank() ? id : parts + ", "+ id;
        String formattedQuery = String.format(query, fields, queryPredicate);
        log.debug("FormattedQuery: "+ formattedQuery );
        if(formattedQuery.length() > MAX_QUERY_SIZE){
            log.debug("MAX_QUERY_SIZE exceeds, Splitting the query: "+ formattedQuery);
            List<List<AttributeSchema>> splitAttributes = Lists.partition(attributes.stream().filter(a -> !a.isIdField()
                && a.getStatus() == Status.ACTIVE
                && !a.isFileLink() && !EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME.equalsIgnoreCase(a.getApiName()))
                .collect(Collectors.toList()), attributes.size()/2);
            queries.addAll(getQueries(query, queryPredicate, splitAttributes.get(0)));
            queries.addAll(getQueries(query, queryPredicate, splitAttributes.get(1)));
        }else{
            queries.add(formattedQuery);
        }
        return queries;
    }

    @Override
    public long getLastWatermark() {
        return latestTs;
    }

    @Override
    public Stats getStats() {
        return stats;
    }
    
    public List<QueryResult> getResult() {
        return result;
    }

}
