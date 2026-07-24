package com.syncari.core.pipeline;

import com.syncari.core.model.UnresolvedReference;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Data
public class BatchedOperations {
    private List<UnresolvedReference> unresolvedReferences = new ArrayList<>();
}
