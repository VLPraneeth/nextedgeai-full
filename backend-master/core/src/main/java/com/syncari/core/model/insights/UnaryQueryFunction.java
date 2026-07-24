package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
@Slf4j
public abstract class UnaryQueryFunction extends QueryFunction {

    @Override
    public String getName() {
        return columns.stream().filter(c -> !c.isLiteral()).findFirst().get().getType() != QField.Type.DATASET ? columns.stream().filter(c -> !c.isLiteral()).findFirst().get().getName().toLowerCase() : columns.stream().filter(c -> !c.isLiteral()).findFirst().get().getName();
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public boolean validate() {
        return CollectionUtils.isNotEmpty(this.getColumns()) && (this.getColumns().stream().filter(c -> !c.isLiteral()).collect(Collectors.toList()).size() == 1 );
    }

    @Override
    protected String getEscapedName(String escapeChar, Map<String, String> entityIdAliasMap){
        String entityDatasetId = columns.stream().filter(c -> !c.isLiteral()).findFirst().get().getDatasetId();
        String alias = null;
        if (StringUtils.isNotEmpty(entityDatasetId)){
            alias = entityIdAliasMap.get(entityDatasetId);
        }
        return (null != alias) ? ("\"" + alias + "\"." + escapeChar + getName() + escapeChar) : (escapeChar + getName() + escapeChar);
    }

    @Override
    public String toString(){
        return super.toString();
    }
}
