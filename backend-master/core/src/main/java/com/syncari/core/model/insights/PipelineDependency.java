package com.syncari.core.model.insights;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PipelineDependency {

    String entity;
    List<String> attributes;
}
