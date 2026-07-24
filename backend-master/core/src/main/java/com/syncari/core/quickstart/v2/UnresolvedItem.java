package com.syncari.core.quickstart.v2;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class UnresolvedItem {

    QSDependency parent;
    QSDependency dependency;
    List<UnresolvedItem> children;
}
