package com.syncari.core.model;

import com.syncari.connector.EntityData;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class MergeOperation implements Serializable {
    private EntityDefinition entity;
    List<EntityData> losingRecords=new ArrayList<>();
    EntityData winningRecord;
    private List<ReferencedRecords> loserReferencedEntities;
    private MergeAction mergeAction;
    private String batchId;
    private Map<String, Map<String, Object>> attributeDefinitionMap = new HashMap<>();
    private MergeInfo mergeInfo;
    private String maxAllowedDupes;
    private List<EntityData> records;
    private String filterCondition;
    public boolean hasLosers(){
        return losingRecords!=null && !losingRecords.isEmpty();
    }
    public Set<String> getLoserIds() {
        return losingRecords.stream().map(EntityData::getId).collect(Collectors.toSet());
    }
    public boolean isReportOnly() {
        return this.mergeAction == MergeAction.REPORT_ONLY;
    }
    
    public boolean isSkipOnly() {
      return this.mergeAction == null && CollectionUtils.isNotEmpty(records) && StringUtils.isNotBlank(filterCondition);
    }
    
    public boolean hasSkippedRecords() {
      return CollectionUtils.isNotEmpty(records) && StringUtils.isNotBlank(filterCondition);
    }

    public boolean hasMoreDupesThanMaxAllowedDupes(){
        return ((StringUtils.isNotEmpty(this.getMaxAllowedDupes())) && (this.getTotalDupes() > Integer.parseInt(this.getMaxAllowedDupes())));
    }
    public int getTotalDupes(){
        return this.getLoserIds().size() + 1; // +1 is to include winner in duplicates as well
    }
}
