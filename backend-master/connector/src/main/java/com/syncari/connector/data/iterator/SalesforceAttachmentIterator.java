package com.syncari.connector.data.iterator;

import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.Stats;
import com.syncari.connector.service.Transformer;
import com.syncari.utils.DateUtil;

import java.util.List;

public class SalesforceAttachmentIterator extends SObjectIterator {
    private final int MAX_ATTACHMENT_RECORDS_PER_CYCLE = 100;

    public SalesforceAttachmentIterator(List<QueryResult> result, String entityName, Transformer transformer, List<AttributeSchema> attributes, PartnerConnection conn, DateUtil dateUtil, String query, String queryPredicate, long offset, boolean isInitial, ConnectorInfo connector, int currentBatchSize, Stats stats, long latestTs, int maxResults, int resultsSoFar) {
        super(result, entityName, transformer, attributes, conn, dateUtil, query, queryPredicate, offset, isInitial, connector, currentBatchSize, stats, latestTs, maxResults, resultsSoFar);
    }

    @Override
    public int getMaxRecordsPerEntitySyncCycle() {
        return MAX_ATTACHMENT_RECORDS_PER_CYCLE;
    }
}
