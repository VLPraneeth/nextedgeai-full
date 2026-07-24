package com.syncari.core.model.insights.dataset;

import com.syncari.core.model.Tag;
import com.syncari.core.model.insights.QueryConfig;
import com.syncari.core.model.misc.DraftableModel;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Transient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class Dataset extends DraftableModel<Dataset> {

    // Name has to be unique
    private String name;
    private String displayName;
    private String description;
    private String rawQuery;
    private DatasetConfig datasetConfig;
    private Map<String, Variable> variablesMap;
    // Todo remove this after moving all old datasets to new model
    private String version;
    boolean seeded=false;
    private String entityDefinitionId;
    private String insightsProviderSQLViewId;
    private String insightsProviderId;
    private DatasetType datasetType = DatasetType.WORKSHEET;


    @Transient
    List<Tag> tags = new ArrayList<>();

    @Override
    public Dataset makeCopy() {
        return new Dataset().setName(name).setDisplayName(displayName).setDescription(description)
                .setDatasetConfig(datasetConfig.makeCopy()).setVersion(version).setSeeded(seeded).setVariablesMap(variablesMap);
    }

    public boolean isDatasetTableType(){
        return ((null != this.getDatasetType()) && (this.getDatasetType().name().equals(DatasetType.TABLE.name())));
    }

    @Override
    public void copyValuesFrom(Dataset model) {
        setName(model.getName()).setVersion(model.getVersion())
                .setDisplayName(model.getDisplayName()).setDatasetConfig(model.getDatasetConfig())
                .setSeeded(model.isSeeded()).setVariablesMap(model.getVariablesMap());
    }

    @Override
    public String toString() {
        return "Dataset{" + "name='" + name + '\'' + ", displayName='" + displayName + '\'' + ", description='" + description + '\''
                + ", variablesMap='" + variablesMap + '\'' + ", version='" + version + '\'' + ", seeded='" + seeded + ", datasetConfig='" + datasetConfig + '}';
    }

    public QueryConfig getQueryConfig() {
        assert (null != getDatasetConfig());
        return getDatasetConfig().toQueryConfig();
    }

    public boolean isSQLMode(){
        return ((null != datasetConfig) && (datasetConfig.getConfigMode().equals(DatasetConfig.ConfigMode.SQL)));
    }

    public enum DatasetType{
        TABLE, WORKSHEET
    }
}


