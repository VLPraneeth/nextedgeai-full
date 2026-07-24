package com.syncari.api.rest.controllers.data.test;

import lombok.Data;

import java.util.List;

@Data
public class SimulationRunRequest {

    String name;
    List<String> testIds;
}
