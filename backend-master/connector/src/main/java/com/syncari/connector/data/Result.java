package com.syncari.connector.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ToString
@Accessors(chain=true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Result {
	boolean isSuccess;
	List<String> errors = new ArrayList<>();
	String errorCode;
	String id;
	String syncariId;
	String additionalInfo;

    private Result() {
    }

	//A map of child field API name to list of results for the children
	//This helps keep id-mappings for line items for nested objects
	//Supports only a single level of nesting
	Map<String,List<Result>> childrenResults = new HashMap<>();
	private Result(boolean isSuccess) {
		this.isSuccess = isSuccess;
	}
	public Result(boolean isSuccess, String id,String syncariId) {
		this(isSuccess,id);
		this.syncariId = syncariId;
	}

	public Result addChildResult(String childApiName, Result result){
		childrenResults.putIfAbsent(childApiName, new ArrayList<>());
		childrenResults.get(childApiName).add(result);
		return this;
	}
	public Result(boolean isSuccess, String id) {
		this(isSuccess);
		this.id = id;
	}
	
	public Result addError(String error) {
	    this.errors.add(error);
	    return this;
	}
}
