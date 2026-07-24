package com.syncari.core.model;

import com.syncari.core.datatype.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class ParameterValue  implements Serializable {
    private Datatype dataType;
    private String contextName;
    private String paramSource;
    private boolean vararg=false;
    public ParameterValue() {

    }
    public ParameterValue(Datatype dataType, String contextName, String paramSource) {
        this.dataType = dataType;
        this.contextName = contextName;
        this.paramSource = paramSource;
    }


    public String getContextRoot(){
        return contextName.split("\\.")[0];
    }
    public static ParameterValue string(String contextName, String src){
        return new ParameterValue(new StringType(),contextName,src);
    }

    public static ParameterValue dbl(String contextName, String src){
        return new ParameterValue(new DoubleType(),contextName,src);
    }

    public static ParameterValue integer(String contextName, String src){
        return new ParameterValue(new IntegerType(),contextName,src);
    }
    public static ParameterValue date(String contextName, String src){
        return new ParameterValue(new DateType(),contextName,src);
    }
    public static ParameterValue dateTime(String contextName, String src){
        return new ParameterValue(new DatetimeType(),contextName,src);
    }
    public static ParameterValue bool(String contextName, String src){
        return new ParameterValue(new BooleanType(),contextName,src);
    }

}
