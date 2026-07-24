package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

@Data
@Accessors(chain = true)
public class QField{

    String name;
    Type type = Type.COLUMN;
    String dataType = "text";
    String datasetId;
    String datasourceAlias;
    QueryFunction queryFunction;

    public QField(){

    }

    public QField(String name, Type type){
        this.name = name;
        this.type = type;
    }

    public enum Type{
        COLUMN, // TODO: Check and Remove COLUMN if not being used
        LITERAL, ENTITY, DATASET, VARIABLE,FUNCTION
    }

    public boolean isLiteral(){
        return Type.LITERAL.equals(type);
    }

    public boolean isFunction(){
        return Type.FUNCTION.equals(type);
    }

    public boolean isVariable(){
        return Type.VARIABLE.equals(type);
    }

    public boolean isColumnType(){
        return Type.COLUMN.equals(type);
    }

    @Override
    public String toString(){
        return "name : " + name + " type : " + type + " dataType : " + dataType + " datasetId : " + datasetId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof QField) {
            QField field = (QField)obj;
            if (this.getDatasourceAlias() != null){
                return ((this.name.equals(field.name)) && (this.type.equals(field.type))
                        && (this.dataType.equals(field.dataType)) && (this.datasetId.equals(field.datasetId))
                        &&(this.datasourceAlias.equals(field.datasourceAlias)));
            }else{
                return ((this.name.equals(field.name)) && (this.type.equals(field.type))
                        && (this.dataType.equals(field.dataType)) && (this.datasetId.equals(field.datasetId)));
            }
        }
        return false;
    }


    @Override
    public int hashCode(){
        if (this.datasourceAlias != null){
            return this.name.hashCode() + this.type.hashCode() + this.dataType.hashCode() + this.datasetId.hashCode() + this.getDatasourceAlias().hashCode();
        }else{
            return this.name.hashCode() + this.type.hashCode() + this.dataType.hashCode() + this.datasetId.hashCode();
        }
    }

    public QField makeCopy(){
        return new QField().setName(name).setDataType(dataType).setType(type).setDatasetId(datasetId);
    }


}
