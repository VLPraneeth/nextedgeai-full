package com.syncari.core.model.insights;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.model.insights.dataset.DatasetFrom;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DatasetConfigTest {

    @Test
    public void validateAggregateTest_ValidConfig(){

        DatasetConfig dsConfig = getValidDatasetConfig();
        dsConfig.validateAggregate();
    }

    @Test
    public void validateAggregateTest_InvalidConfig(){

        DatasetConfig dsConfig = getValidDatasetConfig();
        List<AggregateConfig> aggregateConfigs = new ArrayList<>(dsConfig.getAggregate());
        QField typeFields = new QField().setName("type").setType(QField.Type.ENTITY).setDataType("string").setDatasetId("entityId");
        QueryFunction func = new NoQueryFunction();
        func.setColumns(List.of(typeFields));
        func.setAlias("Name").setDataType("string");
        aggregateConfigs.add(new AggregateConfig().setAggregateField(typeFields).setQueryFunction(func));
        dsConfig.setAggregate(aggregateConfigs);

        try {
            dsConfig.validateAggregate();
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid field 'type' selected in grouping configuration.", e.getMessage());
        }
    }


    private DatasetConfig getValidDatasetConfig(){
        DatasetConfig config = new DatasetConfig();
        DatasetFrom fromDs = new DatasetFrom().setDatasetId("entityId").setDatasetType(DatasourceType.ENTITY)
                .setApiName("Opportunity").setDisplayName("Opportunity");
        config.setFromDatasets(List.of(fromDs));

        QueryFunction func = new NoQueryFunction();
        QField nameField = new QField().setName("name").setType(QField.Type.ENTITY).setDataType("string").setDatasetId("entityId");
        func.setColumns(List.of(nameField));
        func.setAlias("Name").setDataType("string");
        Projection proj1 = new Projection().setFunction(func).setAliasName("Total");

        QueryFunction sumFunc = new SumQueryFunction();
        sumFunc.setColumns(List.of(new QField().setName("amount").setType(QField.Type.ENTITY).setDataType("integer").setDatasetId("entityId")));
        sumFunc.setAlias("amount").setDataType("integer");
        Projection proj2 = new Projection().setFunction(sumFunc).setAliasName("Total");

        config.setProjectionsList(List.of(proj1, proj2));

        AggregateConfig aggConfig = new AggregateConfig().setAggregateField(nameField).setQueryFunction(func);
        config.setAggregate(List.of(aggConfig));
        return config;
    }
}
