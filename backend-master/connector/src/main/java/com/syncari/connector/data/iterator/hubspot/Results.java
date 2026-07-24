package com.syncari.connector.data.iterator.hubspot;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class Results {

    public void clearPageMarker() {
        if(paging!=null && paging.next!=null)
        paging.next.setAfter(null);
    }
    public String getNextPageMarker(){
        if(paging!=null && paging.next!=null){
            return paging.next.after;
        }
        return null;
    }

    public boolean hasNextPageMarker(){
        return paging!=null && paging.next!=null && paging.next.after!=null;
    }
    int total;
    List<HSResult> results;
    PageInfo paging;
    boolean consumed;
}
