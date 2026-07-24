package com.syncari.core.quickstart.v2;

import com.syncari.core.model.misc.sharable.SharableGraph;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Pipeline {
    private SharableGraph entityGraph;
    private List<SharableGraph> fieldGraphs = new ArrayList<>();

    public void addFieldGraph(SharableGraph fieldGraph){
        fieldGraphs.add(fieldGraph);
    }
}
