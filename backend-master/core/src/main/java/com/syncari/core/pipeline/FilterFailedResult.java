package com.syncari.core.pipeline;
 
import java.util.List;
import java.util.stream.Collectors;

import com.syncari.core.model.FunctionResult;

import lombok.ToString;

@ToString
public class FilterFailedResult {
    public static final Object INVALID_RESULT = new Object();
    public static final FilterFailedResult VALUE = new FilterFailedResult(INVALID_RESULT);

    private Object value;

    public static boolean isFailedFilter(Object other){
        return other == VALUE || (other!=null && isFailedResult(other));
    }

    /*
     * Converts a list of filterfailedresults to a filterfailedresult of lists
     */
	public static FilterFailedResult normalizedFailedResult(Object result) {
		if (result == null)
			return null;
		boolean isFailure = FilterFailedResult.class.isAssignableFrom(result.getClass());
		if (isFailure) {
			return FilterFailedResult.class.cast(result);
		}
		boolean isFunctionResult = FunctionResult.class.isAssignableFrom(result.getClass());
		if (isFunctionResult) {
			return normalizedFailedResult(FunctionResult.class.cast(result).getResult());
		}
		boolean isList = List.class.isAssignableFrom(result.getClass());
		if (isList) {
			return new FilterFailedResult(List.class.cast(result).stream()
					.map(r -> isFailedFilter(r) ? normalizedFailedResult(r) : r).collect(Collectors.toList()));
		}
		return null;
	}

    public static Object valueOf(Object result){
		if(result == null) return false;
		boolean isFailure = FilterFailedResult.class.isAssignableFrom(result.getClass());
		if(isFailure) {
			return FilterFailedResult.class.cast(result).getValue();
		}
		boolean isFunctionResult = FunctionResult.class.isAssignableFrom(result.getClass());
		if(isFunctionResult) {
			return valueOf(FunctionResult.class.cast(result).getResult());
		}

		boolean isList = List.class.isAssignableFrom(result.getClass());
		if(isList) {
			return List.class.cast(result).stream().map(r->isFailedFilter(r) ? valueOf(result) : r).collect(Collectors.toList());
		}
		return result;

    }

	private static boolean isFailedResult(Object other) {
		if(other == null) return false;
		boolean isList = List.class.isAssignableFrom(other.getClass());
		boolean allResultsAreFailures = isList && !List.class.cast(other).isEmpty()
				&& List.class.cast(other).stream().allMatch(o -> isFailedResult(o));
		boolean isFailure = FilterFailedResult.class.isAssignableFrom(other.getClass());
		return allResultsAreFailures || isFailure;
	}
	
	public static boolean isInvalidResult(Object other) {
		if(other == null) return false;
		boolean isList = List.class.isAssignableFrom(other.getClass());
		boolean allResultsAreFailures = isList && !List.class.cast(other).isEmpty()
				&& List.class.cast(other).stream().allMatch(o -> isInvalidResult(o));
		boolean isInvalid = FilterFailedResult.class.isAssignableFrom(other.getClass()) && FilterFailedResult.class.cast(other).hasInvalidResults();
		return allResultsAreFailures || isInvalid;
	}

    public boolean hasInvalidResults(){
        return value == INVALID_RESULT;
    }
    private FilterFailedResult() {
    }

    public FilterFailedResult(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }
}
