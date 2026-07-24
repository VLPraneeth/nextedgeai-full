package com.syncari.connector.data.iterator;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

import java.util.ArrayList;
import java.util.List;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.data.AttributeSchema;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.google.common.collect.Iterators;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.Status;
import com.syncari.connector.service.Transformer;

public class SObjectIteratorTest {

    @Test
    public void nextQuerySplit() throws ConnectionException {
        PartnerConnection connection = Mockito.mock(PartnerConnection.class);
        Transformer transformer = new Transformer();
        String query = "SELECT %s FROM Contact WHERE SystemModstamp >= 2020-01-30T07:26:08.806+0000 ORDER BY SystemModstamp";
        SObjectIterator iterator = SObjectIterator.builder().attributes(getMockAttributes(5000, "test_field_for_query_limit")).entityName(Constants.CONTACT)
                .conn(connection).isInitial(false)
                .offset(0).transformer(transformer).query(query)
                .connectorInfo(new ConnectorInfo().setId("123")).build();

        QueryResult result1 = getQueryResult(1, 2500, "test_field_for_query_limit");
        QueryResult result2 = getQueryResult(2501, 5000, "test_field_for_query_limit");

        doReturn(result1, result2).when(connection).queryAll(ArgumentMatchers.any());

        List<EntityData> data = iterator.next();

        // returned EntityData should have 5000 fields
        assertEquals(5000, data.get(0).getValues().size());

        // Query should split in 2 so iterator should have 2 QueryResult
        assertEquals(2, iterator.getResult().size());

        // 1st QueryResult size
        int size = Iterators.size(iterator.getResult().get(0).getRecords()[0].getChildren());
        assertEquals(2501, size);

        // 2nd QueryResult size
        size = Iterators.size(iterator.getResult().get(1).getRecords()[0].getChildren());
        assertEquals(2501, size);

    }

    @Test
    public void latestWatermarkForEmptyData(){
        SObjectIterator iterator = SObjectIterator.builder().build();
        assertEquals(-1l, iterator.findLatestTs(new ArrayList<>()));
    }

    private List<AttributeSchema> getMockAttributes(int numOfAttributes, String baseFieldName){
        List<AttributeSchema> attributes = new ArrayList<>();
        for(int i = 1; i <= numOfAttributes; i++){
            AttributeSchema schema = new AttributeSchema();
            schema.setApiName(baseFieldName + "_" + i + "__c");
            schema.setDataType("Text");
            schema.setDisplayName("New_test_field_"+i);
            schema.setStatus(Status.ACTIVE);
            attributes.add(schema);
        }

        return attributes;
    }

    private QueryResult getQueryResult(int start, int end, String baseFieldName) {
        QueryResult result = new QueryResult();

        SObject[] records = new SObject[1];
        SObject record = new SObject();

        //List<SObject> children = new ArrayList<>();
        for(int i = start; i <= end; i++){
            record.setSObjectField(baseFieldName + "_" + i + "__c", "Field"+i);
        }

        // Add Id
        record.setId("123");
        records[0] = record;

        result.setRecords(records);
        result.setDone(true);
        result.setSize(1);

        return result;
    }
}
