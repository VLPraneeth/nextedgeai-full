package com.syncari.core.model.insights.dataset;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
public class VariableValue {

    String datasetName;
    Object defaultValue;
    Map<String, Object> additionalParamForDefaultVal = new HashMap<>();
    VariableType defaultValueType = VariableType.LITERAL;
    String datasetId;
    String datatype = "text";

    public enum VariableType{
        LITERAL, ENTITY, DATASET
    }

    public boolean isLiteral(){
        return defaultValueType == VariableType.LITERAL;
    }

    public VariableValue makeCopy(){
        return new VariableValue().setDatasetId(this.getDatasetId()).setDatasetName(this.getDatasetName()).setAdditionalParamForDefaultVal(this.getAdditionalParamForDefaultVal())
                .setDatatype(this.getDatatype()).setDefaultValue(this.getDefaultValue()).setDefaultValueType(this.getDefaultValueType());
    }

    public String toString(){
        return "datasetName: " + datasetName + " defaultValue: " + defaultValue + " defaultValueType: "+defaultValueType+" additionalParamForDefaultVal: "
                + additionalParamForDefaultVal + " datasetId: " + datasetId;
    }
}
