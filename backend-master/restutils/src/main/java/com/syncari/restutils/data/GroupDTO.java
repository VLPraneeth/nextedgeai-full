package com.syncari.restutils.data;

import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class GroupDTO implements Serializable {
    private String id;
    private String label;
    private String shape;
    private boolean collapsed;
    private String color;
    private String childNodeSummary;
	private List<String> childNodeIds;
	private List<String> tags;
    private String name;
    private String description;
    private String apiName;
    private String graphDirection;
    private String nodeType;
    private String iconPath;
    private String originalId;
}


