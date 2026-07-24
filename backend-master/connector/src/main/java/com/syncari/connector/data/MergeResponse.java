package com.syncari.connector.data;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString
public class MergeResponse {
    SyncResponse winnerResult;
    SyncResponse loserResult;
    List<SyncResponse> referenceResults = new ArrayList<>();
    
    public MergeResponse combine(MergeResponse other) {
        if(winnerResult == null) winnerResult = other.winnerResult;
        else winnerResult.merge(other.winnerResult);
        if(loserResult == null) loserResult = other.loserResult;
        else loserResult.merge(other.loserResult);
        return this;
        
    }

}
