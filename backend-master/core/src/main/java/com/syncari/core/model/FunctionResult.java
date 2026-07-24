package com.syncari.core.model;

import com.syncari.core.datatype.Datatype;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Wither;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
@ToString
@Wither
public class FunctionResult {
    private final Object result;
    private final Datatype datatype;
    public static final Object NO_RESULTS = new Object();
    private Object lookupResult = NO_RESULTS;
    private Long lookupCount = null;

    private List<Long> lookupCounts = new ArrayList<>();

    public FunctionResult(Object result, Datatype datatype) {
        this.result = result;
        this.datatype = datatype;
    }
    public FunctionResult(Object result, Datatype datatype, Object lookupResult) {
        this.result = result;
        this.datatype = datatype;
        this.lookupResult =lookupResult;
    }
    public FunctionResult(Object result, Datatype datatype, Object lookupResult,List<Long> lookupCounts) {
        this.result = result;
        this.datatype = datatype;
        this.lookupResult =lookupResult;
        this.lookupCounts = lookupCounts;
    }

    public FunctionResult(Object result, Datatype datatype, Object lookupResult, Long lookupCount) {
        this.result = result;
        this.datatype = datatype;
        this.lookupResult =lookupResult;
        this.lookupCount = lookupCount;
    }
    public static boolean isLookupResult(Object lookupResult){
        return lookupResult != NO_RESULTS;
    }
    public Object typedValue() {
        //Simple rendered values must still go through conversion
        if(result!= null && (result instanceof  String)) {
            if(StringUtils.isBlank(result.toString())) {
                return result;
            }
            return datatype.convert(result.toString());
        }
        return result;
    }

    public boolean isNull(){
        return  result==null;
    }

    public boolean isBlank(){
        return  result!=null && StringUtils.isBlank(result.toString());
    }

}
