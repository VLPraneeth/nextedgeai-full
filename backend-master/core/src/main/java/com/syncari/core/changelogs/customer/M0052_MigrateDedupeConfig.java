package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.WinnerSelection;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.mongodb.client.model.Filters.*;

@ChangeLog(order = "0052")
@Slf4j
public class M0052_MigrateDedupeConfig {

    @ChangeSet(order = "001", id = "migrateDedupeConfig", author = "neelesh")
    public void migrateDedupeConfig(MongoTemplate template) {
        MappingNodeRepo mappingNodeRepo = MigrationContext.getMappingNodeRepo();
        mappingNodeRepo.findCoreNodes().forEach(node->{
            CoreEntityNodeConfig config =node.getTypedConfiguration();
            if(config.getAdvancedDedupeConfig()!=null
                    //no new style dedupe
                    && config.getAdvancedDedupeConfig().getWinnerSelectionPredicates().isEmpty()
                    //and old style winners are present
                    && !config.getAdvancedDedupeConfig().getWinnerSelectionPolicies().isEmpty()){

                Map<String, Object> selectWinner = toSelectWinnerMap(config.getAdvancedDedupeConfig().getWinnerSelectionPolicies());
                config.getAdvancedDedupeConfig().setSelectWinner(selectWinner);
                mappingNodeRepo.save(node);
            }
        });
    }

    private Map<String, Object> toSelectWinnerMap(List<WinnerSelection> policies) {

        List<Map<String, Object>> predicateMaps =new ArrayList<>();
        for(WinnerSelection policy : policies) {
            String label = "record".equals(policy.getWinnerSelectionType()) ? "Record" :
                    MigrationContext.getAttributeRepo().findById(policy.getWinnerSelectionType())
                            .map(a->a.getDisplayName())
                            .orElse(policy.getWinnerSelectionType());
            Map<String, Object> predicateMap = new HashMap<>();
            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "AND");
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            predicate.put("predicates", List.of(
                    Map.of(
                            "operator",policy.getWinnerSelectionValue().toLowerCase(),
                            "predicateId",ObjectId.get().toHexString(),
                            "name","winnerSelectionPredicate",
                            "left", Map.of("label",label,"value",policy.getWinnerSelectionType())
                    )
            ));
            predicateMap.put("winnerSelectionPredicate",Map.of("name","winnerSelectionPredicate","value",predicate));
            predicateMaps.add(predicateMap);
        }
        return Map.of("configId", ObjectId.get().toHexString(), "name", "selectWinner", "compositeValues", predicateMaps);
    }
}
