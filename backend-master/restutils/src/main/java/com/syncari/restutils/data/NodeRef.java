package com.syncari.restutils.data;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class NodeRef implements Serializable {
    private String nodeId;
    private PortDTO port;
    private String anchor;

}
