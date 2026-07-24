package com.syncari.core.model.cache;

import com.syncari.core.datatype.Datatype;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CacheIndexAttribute {

    private Datatype dataType;
    private String path;
    private String alias;
    private boolean isCaseInSensitive;
    private boolean isSortable;
    private String separator;

    @Override
    public String toString(){
        return "Datatype : " + dataType.toString() + " path: " + path + " alias: " + alias + " isCaseSenstive:" + isCaseInSensitive;
    }

}
