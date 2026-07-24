package com.syncari.core.pipeline;

import com.syncari.core.model.MappingNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class DiffInfoContext {
    MappingNode currentNode;
}
