package com.syncari.viper.streams.stages;

import com.syncari.core.model.CoreAttributeNodeConfig;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.StagedBatchRecord;
import lombok.Data;

import java.util.List;
import java.util.Optional;

@Data
public class SelectedConnectorAuthority {
    final List<StagedBatchRecord> records;
    final List<AttributeValue> contenders;
    final CoreAttributeNodeConfig coreConfig;

    public Optional<AttributeValue> authority(){
        StagedBatchRecord winner = findAuthority(records);
        Optional<AttributeValue> authority =  Optional.empty();
        if(winner!=null) {
            authority = contenders.stream().filter(contender -> contender.connectorId.equals(winner.getEntityData().getConnectorId())
                    && contender.attribute.getEntityId().equals(winner.getExternalEntityDefinitionId())
            ).findFirst();
        }
        return authority.isPresent() ? authority : new LatestRecordAuthority(records, contenders,coreConfig).authority();
    }

    private StagedBatchRecord findAuthority(List<StagedBatchRecord> records) {
        for (StagedBatchRecord record : records){
            if(record.getEntityData().getConnectorId().equals(coreConfig.getDataAuthority().getDataAuthorityConfiguration().get("connectorId"))){
                return record;
            }
        }
        return null;
    }
}
