package com.syncari.viper.streams.stages;

import com.syncari.core.model.CoreAttributeNodeConfig;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.StagedBatchRecord;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Data
@Slf4j
public class LatestRecordAuthority {
    final List<StagedBatchRecord> records;
    final List<AttributeValue> contenders;
    final CoreAttributeNodeConfig coreConfig;

    public Optional<AttributeValue> authority(){
        Set<StagedBatchRecord> discarded = new HashSet<>();
        Optional<AttributeValue> authority = Optional.empty();

        while(authority.isEmpty() ) {
            StagedBatchRecord winner = findAuthority(records,discarded);
            if(winner==null){
                break;
            }
            authority = contenders.stream().filter(contender -> contender.connectorId.equals(winner.getEntityData().getConnectorId())
                    && contender.attribute.getEntityId().equals(winner.getExternalEntityDefinitionId())
            ).findFirst();
            if (authority.isPresent()) return authority;
            discarded.add(winner);
        }
        return contenders.stream().findFirst();
    }

    private StagedBatchRecord findAuthority(List<StagedBatchRecord> records, Set<StagedBatchRecord> excluded) {
        //fetch latest
        StagedBatchRecord latest = null;
        long lastModified = Long.MIN_VALUE;
        for (StagedBatchRecord record : records){
            if(record.getEntityData().getLastModified() > lastModified && !excluded.contains(record)){
                lastModified =record.getEntityData().getLastModified();
                latest= record;
            }
        }
        return latest;
    }
}
