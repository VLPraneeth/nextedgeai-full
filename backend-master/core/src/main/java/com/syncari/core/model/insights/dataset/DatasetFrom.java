package com.syncari.core.model.insights.dataset;

import com.syncari.core.insights.DatasourceType;
import com.syncari.core.model.EntityDefinition;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Transient;

@Data
@Accessors(chain = true)
public class DatasetFrom {
    String apiName;
    String displayName;
    String datasetId;
    String datastoreName;
    String alias;
    String schemaName;
    DatasourceType datasetType;
    @Transient
    Dataset dataset;

    public DatasetFrom populateFromEntity(EntityDefinition entity){
        setApiName(entity.getApiName());
        setDisplayName(entity.getDisplayName());
        setDatasetId(entity.getId());
        setDatastoreName(entity.getDataStoreName());
        setDatasetType(DatasourceType.ENTITY);
        return this;
    }
}
