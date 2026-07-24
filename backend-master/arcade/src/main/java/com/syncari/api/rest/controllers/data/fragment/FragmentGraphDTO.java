package com.syncari.api.rest.controllers.data.fragment;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class FragmentGraphDTO {

    List<FragmentNodeDTO> nodes = Collections.emptyList();
    List<FragmentEdgeDTO> edges = Collections.emptyList();
}
