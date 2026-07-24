package com.syncari.connector.data;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
@ToString
public class SyncResponse {
    boolean isSuccess = true;
    List<String> errors = new ArrayList<>();
    private List<Result> results = new ArrayList<>();

    public SyncResponse(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public SyncResponse() {
    }

    public boolean isSuccess(){
        return isSuccess && results.stream().map(r->r.isSuccess).reduce((r1,r2)->r1 &&r2).orElse(true);
    }


    public SyncResponse merge(SyncResponse other) {
    	if(other == null) return this;
        isSuccess = isSuccess && other.isSuccess;
        errors.addAll(other.errors);
        results.addAll(other.results);
        return this;
    }

    public List<String> getErrors() {
        List<String> allErrors = new ArrayList<>(this.errors);
        List<String> allResultErrors = results.stream().flatMap(r -> r.getErrors().stream()).collect(Collectors.toList());
        allErrors.addAll(allResultErrors);
        return allErrors;
    }

    public void appendError(Exception e) {
        errors.add(e.getMessage());
        isSuccess = false;
    }

    public void appendError(String error) {
        errors.add(error);
        isSuccess = false;
    }
}
