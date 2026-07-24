package com.syncari.core.model;


import com.syncari.core.model.util.MappingNodeType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
public class Layout extends UUIDAuditModel{

    public static final String EDGE_TYPE="Edge";
    public static final String NODE_TYPE="MappingNode";
    public static final String DEFAULT_CENTER_X = "600";
    public static final String DEFAULT_CENTER_Y = "400";

    //id of the object that needs a layout
    private String targetId;
    //type of the object (MappingNode/Edge etc)
    private String targetType;
    //could have x, y coordinates for MappingNode
    // or could have sourceAnchor and destinationAnchor for Edge
    private Map<String, Object> layoutProperties;

    public static Layout edge(String edgeId,String srcAnchor, String destAnchor){
        return new Layout().setTargetId(edgeId).setTargetType(EDGE_TYPE).setLayoutProperties(new HashMap<>(Map.of("srcAnchor",srcAnchor,"destAnchor",destAnchor)));
    }

    public static Layout node(String nodeId,String x, String y){
        return new Layout().setTargetId(nodeId).setTargetType(NODE_TYPE).setLayoutProperties(Map.of("x",x,"y",y));
    }

    public Layout copyWithTargetId(String targetId){
        Layout layout = new Layout().setTargetId(this.targetId).setLayoutProperties(new HashMap<>(this.getLayoutProperties())).setTargetType(this.targetType);
        layout.setTargetId(targetId);
        return layout;
    }

    public static boolean isCoreType(MappingNodeType type) {
        return type == MappingNodeType.CORE_ENTITY || type == MappingNodeType.CORE_ATTRIBUTE;
    }

    public static int cappedRandom() {
        int min = 0;
        int max = 800;
        return min + (int) ((max - min) * Math.random());
    }
}
