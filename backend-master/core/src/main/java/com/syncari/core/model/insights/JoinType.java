package com.syncari.core.model.insights;

import lombok.experimental.Accessors;

public enum
JoinType {
    Inner("INNER JOIN"), LeftOuter("LEFT OUTER JOIN"),RightOuter("RIGHT OUTER JOIN"), Full("FULL JOIN"), Self("SELF JOIN");

    private String value;
    JoinType(String value){
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
