package com.syncari.connector.data.iterator;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.Stats;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.Offset.OffsetType;
import com.syncari.connector.exception.RetriableException;
import com.syncari.utils.DateUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.ODataClientErrorException;
import org.apache.olingo.client.api.communication.request.retrieve.ODataDeltaRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetIteratorRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetRequest;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.ClientDeletedEntity;
import org.apache.olingo.client.api.domain.ClientDelta;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.domain.ClientEntitySetIterator;
import org.apache.olingo.client.api.uri.URIBuilder;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeException;
import org.apache.olingo.commons.api.format.ContentType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import static com.syncari.connector.ConnectorHelper.withBackoffAndHttpErrorHandling;

@Slf4j
@Builder
public class ODataEntityDataIterator implements EntityDataBatchIterator {
    private static final int QUERY_BATCHSIZE = 2000;

    @Builder.Default
    ClientEntitySetIterator<ClientEntitySet, ClientEntity> resultsIterator = null;
    String entityName;
    String entityPluralName;
    @Builder.Default
    List<AttributeSchema> attributes = new ArrayList<>();
    private ODataClient client;
    private String serviceUri;
    DateUtil dateUtil;
    String filter;
    @Builder.Default
    boolean entitySupportsChangeTracking = true;
    long offset;
    boolean isInitial;
    String connectorId;
    WatermarkInfo watermarkInfo;
    String origChangeStream;
    String changeStream;
    @Builder.Default
    int currentBatchSize = 1;
    @Builder.Default
    private Stats stats = new Stats();
    @Builder.Default
    int limit = 0;
    @Builder.Default
    int pageSize = 2000;
    long latestTs = Instant.EPOCH.toEpochMilli();
    ODataRetrieveResponse<ClientEntitySetIterator<ClientEntitySet, ClientEntity>> response = null;

    URI nextLink;
    @Override
    public boolean hasNext() {
        return resultsIterator == null || resultsIterator.hasNext();
    }

    @Override
    public String getChangeStream(){
		return changeStream;
	}

    @Override
    public Offset getOffsetInfo() {
        return new Offset(OffsetType.CUSTOM, pageSize);
    }

    @Override
    public void customOffsetReset(int resetRecordCount) {
        changeStream = origChangeStream;
    }

    public ODataEntitySetRequest<ClientEntitySet> getOdataEntitySetRequest() {
        return client.getRetrieveRequestFactory()
            .getEntitySetRequest(client.newURIBuilder(serviceUri).appendEntitySetSegment(entityPluralName).build());
    }

    private String resetDeltaLink() {
        AtomicReference<String> newChangeStream = new AtomicReference<>("");
        withBackoffAndHttpErrorHandling(() -> {
            ODataEntitySetRequest<ClientEntitySet> request = getOdataEntitySetRequest();
            request.setAccept("application/json");
            request.setPrefer(client.newPreferences().trackChanges());
            try {
                final ClientEntitySet entitySet = request.execute().getBody();
                if (entitySet != null && entitySet.getDeltaLink() != null) {
                    newChangeStream.set(entitySet.getDeltaLink().toASCIIString());
                    log.debug("Retrieved deltaLink from MSD Object endpoint, changeStream: {} ", newChangeStream.get());
                }
            } catch (ODataClientErrorException e) {
                log.error("Entity {} does not support change tracking", entityName, e);
            }
        });
        return newChangeStream.get();
    }

    @Override
    @Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<EntityData> next() {
        if(StringUtils.isBlank(changeStream)) {
            changeStream = watermarkInfo == null ? "" : watermarkInfo.getChangeStream() == null ? "" : watermarkInfo.getChangeStream();
        }
        List<EntityData> entities = new ArrayList<>();

        if (resultsIterator == null) {
            withBackoffAndHttpErrorHandling(() -> {
                URI link= StringUtils.isNotBlank(changeStream) ? client.newURIBuilder(changeStream).build() : null;
                if(link==null){
                    URIBuilder absoluteUri = client.newURIBuilder(serviceUri).appendEntitySetSegment(entityPluralName);
                    absoluteUri.filter(filter);
                    if(limit > 0){
                        absoluteUri.top(Math.min(QUERY_BATCHSIZE,limit));
                    }
                    absoluteUri.orderBy("modifiedon");
                    link = absoluteUri.build();
                }
                ODataEntitySetIteratorRequest<ClientEntitySet, ClientEntity> request = getClientEntitySetRequest(link);
                //set either limit or page size, but not both

                if(limit == 0) {
                    int effectivePageSize = pageSize == 0 ? QUERY_BATCHSIZE : Math.min(pageSize, QUERY_BATCHSIZE);
                    request.setPrefer(client.newPreferences().maxPageSize(effectivePageSize));

                }
                response = request.execute();
                resultsIterator = response.getBody();
            });
        }
        while (resultsIterator != null && resultsIterator.hasNext()) {
            EntityData data = toEntityData(resultsIterator.next());
            if (data != null) {
                entities.add(data);
                latestTs = data.getLastModified();
            }
        }
        //set up for the next page
        URI next = resultsIterator.getNext();
        if(next!=null){
            resultsIterator = null;
            this.changeStream = next.toASCIIString();
        } else {
            this.changeStream = "";
        }
        if (response != null) {
            response.close();
        }
        return entities;
    }

    public ODataEntitySetIteratorRequest<ClientEntitySet, ClientEntity> getClientEntitySetRequest(URI link) {
        ODataEntitySetIteratorRequest<ClientEntitySet, ClientEntity> request = client
            .getRetrieveRequestFactory()
            .getEntitySetIteratorRequest(link);
        request.setAccept("application/json");
        return request;
    }

    @Override
    public long getLastWatermark() {
        return latestTs;
    }

    @Override
    public Stats getStats() {
        return stats;
    }

    private EntityData toEntityData(ClientEntity clientEntityData) {
        EntityData data = new EntityData(entityName);
        if (clientEntityData.getProperty("modifiedon") != null) {
            data.setLastModified(
                    dateUtil.toEpochMilli(clientEntityData.getProperty("modifiedon").getValue().toString()));
        }
        if (clientEntityData.getProperty("createdon") != null) {
            data.setCreatedAt(dateUtil.toEpochMilli(clientEntityData.getProperty("createdon").getValue().toString()));
        }
        data.setConnectorId(connectorId);
        for (AttributeSchema attr : attributes) {
            if (clientEntityData.getProperty(attr.getApiName()) != null) {
                // TODO: should we handle other datatypes as well?
                String cvType = clientEntityData.getProperty(attr.getApiName()).getValue().getTypeName();
                try {
                    switch (cvType) {
                        case "Edm.Double":
                            data.addValue(attr.getApiName(), clientEntityData.getProperty(attr.getApiName())
                                .getPrimitiveValue().toCastValue(Double.class));
                            break;
                        case "Edm.Int32":
                            data.addValue(attr.getApiName(), clientEntityData.getProperty(attr.getApiName())
                                .getPrimitiveValue().toCastValue(Integer.class));
                            break;
                        case "Edm.Boolean":
                            data.addValue(attr.getApiName(), clientEntityData.getProperty(attr.getApiName())
                                .getPrimitiveValue().toCastValue(Boolean.class));
                            break;
                        default:
                            data.addValue(attr.getApiName(), clientEntityData.getProperty(attr.getApiName())
                                .getPrimitiveValue().toCastValue(String.class));
                            break;
                    }
                } catch (EdmPrimitiveTypeException e) {
                    log.error(String.format(
                        "Value for attribute '%s' not proccessed in MS Dynamics for entity %s due to ",
                        attr.getApiName(), e.getMessage()));
                }
            } else if (attr.isReference() && clientEntityData.getProperty("_" + attr.getApiName() + "_value") != null) {
                try {
                    data.addValue(attr.getApiName(), clientEntityData.getProperty("_" + attr.getApiName() + "_value")
                        .getPrimitiveValue().toCastValue(String.class));
                } catch (EdmPrimitiveTypeException e) {
                    log.error(String.format(
                        "Value for attribute '%s' not proccessed in MS Dynamics for entity %s due to ",
                        attr.getApiName(), e.getMessage()));
                }
            } else {
                log.debug(String.format(
                    "Value for attribute '%s' not proccessed in MS Dynamics for entity %s because it is not found",
                    attr.getApiName(), entityName));
            }
            if (attr.isIdField()) {
                data.setId(clientEntityData.getProperty(attr.getApiName()).getValue().toString());
            }
        }
		return data;
    }
    
}
