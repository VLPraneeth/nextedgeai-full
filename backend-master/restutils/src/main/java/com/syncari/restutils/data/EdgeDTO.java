package com.syncari.restutils.data;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class EdgeDTO implements Serializable {
    private String id;
    private NodeRef source;
    private NodeRef destination;
    private String originalId;

}


