package com.syncari.core;

import com.syncari.utils.KeyValue;

import lombok.Data;

@Data
public class Route extends KeyValue {

    private static final String ROUTE = "route";

    public Route (RouteConstants route){
        this.set(ROUTE, route.name());
    }

    public Route (RouteConstants route, KeyValue params){
        this(route);
        this.addAll(params);
    }

    public Route setRouteParams(KeyValue params){
        this.addAll(params);
        return this;
    }

    public enum RouteConstants{
        ENTITY_PIPELINE_GRAPH_VERSION,
        FIELD_PIPELINE_GRAPH_VERSION
    }

}
