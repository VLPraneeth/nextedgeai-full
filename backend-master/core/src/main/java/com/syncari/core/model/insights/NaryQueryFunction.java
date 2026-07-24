package com.syncari.core.model.insights;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Data
@Slf4j
@Accessors(chain = true)
@ToString
public abstract class NaryQueryFunction extends QueryFunction {

    List<QueryField> innerQueryFields;

    @Override
    public String getName() {
        return this.getAlias();
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public boolean validate() {
        return CollectionUtils.isNotEmpty(this.getColumns()) && (this.getColumns().size() > 1 );
    }

}
