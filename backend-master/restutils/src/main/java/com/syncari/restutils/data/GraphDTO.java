package com.syncari.restutils.data;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
@Accessors(chain = true)
public class GraphDTO implements Serializable {

    List<MappingNodeDTO> nodes = Collections.emptyList();
    List<EdgeDTO> edges = Collections.emptyList();
    List<GroupDTO> groups = Collections.emptyList();
}
