package com.syncari.core.model.misc.test;

import com.syncari.core.model.Edge;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/*
This class is created to persist nodes and edges as well in the snapshot of graph when simulation is run
 */
@Data
public class SimulationMappingGraph extends MappingGraph {

    List<MappingNode> nodes = new ArrayList<>();
    List<Edge> edges = new ArrayList<>();

    public SimulationMappingGraph createSimulationMappingGraph(MappingGraph graph){
        this.copyValuesFrom(graph);
        this.setId(graph.getId());
        this.setDraftStatus(graph.getDraftStatus());
        this.setParentId(graph.getParentId());
        this.nodes = graph.getNodes();
        this.edges = graph.getEdges();
        return this;
    }

    @Override
    public List<MappingNode> getNodes(){
        return this.nodes;
    }

    @Override
    public List<Edge> getEdges(){
        return this.edges;
    }

    @Override
    public MappingGraph setNodes(List<MappingNode> nodes){
        this.nodes = nodes;
        return this;
    }

    @Override
    public MappingGraph setEdges(List<Edge> edges){
        this.edges = edges;
        return this;
    }
}
